package com.hxh.apboa.assessment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.agent.mapper.AgentDefinitionMapper;
import com.hxh.apboa.assessment.mapper.ExamSubmissionMapper;
import com.hxh.apboa.assessment.mapper.GradingKbBindingMapper;
import com.hxh.apboa.assessment.mapper.GradingKbConfigMapper;
import com.hxh.apboa.assessment.mapper.GradingRagDocumentChunkMapper;
import com.hxh.apboa.assessment.mapper.GradingRagDocumentMapper;
import com.hxh.apboa.assessment.mapper.GradingSkillBindingMapper;
import com.hxh.apboa.assessment.mapper.GradingSkillFileMapper;
import com.hxh.apboa.assessment.mapper.GradingSkillPackageMapper;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.AgentKnowledgeBase;
import com.hxh.apboa.common.entity.AgentSkillPackage;
import com.hxh.apboa.common.entity.ExamQuestion;
import com.hxh.apboa.common.entity.ExamSubmission;
import com.hxh.apboa.common.entity.KnowledgeBaseConfig;
import com.hxh.apboa.common.entity.RagDocument;
import com.hxh.apboa.common.entity.RagDocumentChunk;
import com.hxh.apboa.common.entity.SkillFile;
import com.hxh.apboa.common.entity.SkillPackage;
import com.hxh.apboa.common.util.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 阅卷证据组装引擎：把考生智能体的真实配置组装为待评证据文档。
 * 依据 exam-agent-config-scoring skill 的取证规则：
 * - 只读取真实配置（提示词全文 / 知识库文档切片 / Skill 文件正文），自述表单仅作旁证；
 * - 不读取截图、视频等运行证据；
 * - 完整性校验（提交后变更 / 租户错配 / 依赖缺失）显式标注，不静默；
 * - 敏感字段（身份证号、手机号）不进入证据；
 * - 考生内容视为不可信输入，仅作为待审材料。
 * 另预计算跨考生信号：通道 B（提示词同源相似度）与通道 E（行为信号）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExamGradingSnapshotService {

    private static final int MAX_PROMPT_CHARS = 16000;
    private static final int MAX_KB_TOTAL_CHARS = 30000;
    private static final int MAX_DOC_CHARS = 8000;
    private static final int MAX_CHUNKS_PER_DOC = 15;
    private static final int MAX_SKILL_TOTAL_CHARS = 30000;
    private static final int MAX_SKILL_MAIN_CHARS = 12000;
    private static final int MAX_SKILL_OTHER_CHARS = 4000;
    private static final int MAX_FORM_TEXT_CHARS = 6000;
    /** 结构相似度报告阈值（组合相似度） */
    private static final double PEER_SIM_THRESHOLD = 0.80;

    private final AgentDefinitionMapper agentDefinitionMapper;
    private final ExamSubmissionMapper submissionMapper;
    private final GradingKbBindingMapper kbBindingMapper;
    private final GradingKbConfigMapper kbConfigMapper;
    private final GradingRagDocumentMapper ragDocumentMapper;
    private final GradingRagDocumentChunkMapper chunkMapper;
    private final GradingSkillBindingMapper skillBindingMapper;
    private final GradingSkillPackageMapper skillPackageMapper;
    private final GradingSkillFileMapper skillFileMapper;
    private final ObjectMapper objectMapper;

    public record GradingSnapshot(String evidenceDoc, List<String> integrityFlags, Long agentId, String agentName) {
    }

    public GradingSnapshot build(ExamSubmission submission, ExamQuestion question) {
        List<String> flags = new ArrayList<>();
        StringBuilder doc = new StringBuilder(4096);

        appendQuestionSection(doc, question, submission);

        Long agentId = submission.getAgentId();
        AgentDefinition agent = null;
        if (agentId == null) {
            flags.add("未关联智能体");
        } else {
            agent = agentDefinitionMapper.selectById(agentId);
            if (agent == null) {
                flags.add("智能体不存在（可能已删除）");
            } else if (agent.getTenantId() != null && submission.getTenantId() != null
                    && !agent.getTenantId().equals(submission.getTenantId())) {
                flags.add("智能体租户与提交记录租户不一致");
            }
        }

        if (agent != null) {
            // 时点校验：配置更新时间晚于提交时间 → 异常（1 分钟容差）
            if (agent.getUpdatedAt() != null && submission.getSubmittedAt() != null
                    && agent.getUpdatedAt().isAfter(submission.getSubmittedAt().plusMinutes(1))) {
                flags.add("配置时点异常（提交后配置变更，当前配置可能≠考试时配置）");
            }
            appendAgentSection(doc, agent);
            appendKnowledgeSection(doc, agent, flags);
            appendSkillSection(doc, agent, flags);
        } else {
            doc.append("## 智能体配置\n\n（未关联智能体或智能体已不存在，所有依赖配置的分项按\"无法验证\"处理）\n\n");
        }

        appendFormSection(doc, submission);
        appendPrecomputedSection(doc, submission, question, agent, flags);

        return new GradingSnapshot(doc.toString(), flags, agentId, agent == null ? null : agent.getName());
    }

    // ---------------------------------------------------------------- 题目

    private void appendQuestionSection(StringBuilder doc, ExamQuestion question, ExamSubmission submission) {
        doc.append("# 待评分材料\n\n## 题目要求（题面优先）\n\n");
        if (question != null) {
            doc.append("- 题目：").append(safe(question.getTitle())).append('\n');
            if (notBlank(question.getTaskScenario())) {
                doc.append("\n### 场景\n").append(question.getTaskScenario().trim()).append('\n');
            }
            if (notBlank(question.getTaskRequirement())) {
                doc.append("\n### 任务要求\n").append(question.getTaskRequirement().trim()).append('\n');
            }
            if (notBlank(question.getSubmissionDeliverable())) {
                doc.append("\n### 交付要求\n").append(question.getSubmissionDeliverable().trim()).append('\n');
            }
            if (notBlank(question.getAttachmentName())) {
                doc.append("\n（题目附带数据/资料文件：").append(question.getAttachmentName())
                        .append("；附件正文未随本材料提供，涉及对照该文件的分项按量表特殊锚点处理）\n");
            }
        } else {
            doc.append("（题目信息缺失，按量表通用要求评分）\n");
        }
        doc.append("\n- 提交状态：").append(safe(submission.getStatus()))
                .append("；提交时间：").append(safe(String.valueOf(submission.getSubmittedAt())))
                .append('\n');
    }

    // ---------------------------------------------------------------- 智能体

    private void appendAgentSection(StringBuilder doc, AgentDefinition agent) {
        doc.append("\n## 智能体基本信息\n\n")
                .append("- 名称：").append(safe(agent.getName()))
                .append("；类型：").append(safe(String.valueOf(agent.getAgentType())))
                .append("；版本：").append(safe(agent.getVersion()))
                .append("；创建时间：").append(safe(String.valueOf(agent.getCreatedAt())))
                .append("；最后修改：").append(safe(String.valueOf(agent.getUpdatedAt())))
                .append('\n');
        if (notBlank(agent.getDescription())) {
            doc.append("- 描述：").append(truncate(agent.getDescription(), 500)).append('\n');
        }
        doc.append("\n### 系统提示词全文\n\n```text\n")
                .append(truncate(agent.getSystemPrompt() == null ? "" : agent.getSystemPrompt().trim(), MAX_PROMPT_CHARS));
        if (agent.getSystemPrompt() != null && agent.getSystemPrompt().length() > MAX_PROMPT_CHARS) {
            doc.append("\n（……提示词超长，已截断，原长度 ").append(agent.getSystemPrompt().length()).append(" 字符）");
        }
        doc.append("\n```\n");
    }

    // ---------------------------------------------------------------- 知识库

    private void appendKnowledgeSection(StringBuilder doc, AgentDefinition agent, List<String> flags) {
        List<AgentKnowledgeBase> bindings = kbBindingMapper.selectList(new LambdaQueryWrapper<AgentKnowledgeBase>()
                .eq(AgentKnowledgeBase::getAgentDefinitionId, agent.getId()));
        if (bindings.isEmpty()) {
            doc.append("\n## 知识库\n\n（该智能体未绑定任何知识库）\n");
            return;
        }
        doc.append("\n## 知识库（已绑定 ").append(bindings.size()).append(" 个）\n");
        int totalChars = 0;
        boolean anyDoc = false;
        for (AgentKnowledgeBase binding : bindings) {
            KnowledgeBaseConfig kb = binding.getKnowledgeBaseConfigId() == null
                    ? null : kbConfigMapper.selectById(binding.getKnowledgeBaseConfigId());
            if (kb == null) {
                doc.append("\n### 知识库（配置已不存在，绑定 id=").append(binding.getKnowledgeBaseConfigId()).append("）\n");
                flags.add("知识库配置缺失");
                continue;
            }
            doc.append("\n### 知识库：").append(safe(kb.getName()))
                    .append("（类型 ").append(safe(String.valueOf(kb.getKbType())))
                    .append("，模式 ").append(safe(String.valueOf(kb.getRagMode())))
                    .append("，健康状态 ").append(safe(String.valueOf(kb.getHealthStatus())))
                    .append("）\n");
            if (kb.getConnectionConfig() != null) {
                doc.append("（外部连接型知识库：仅记录类型，不展示连接配置）\n");
            }
            List<RagDocument> documents = ragDocumentMapper.selectList(new LambdaQueryWrapper<RagDocument>()
                    .eq(RagDocument::getKnowledgeBaseConfigId, kb.getId())
                    .orderByAsc(RagDocument::getFileName));
            if (documents.isEmpty()) {
                doc.append("\n（知识库内无文档）\n");
                flags.add("知识库无文档：" + safe(kb.getName()));
                continue;
            }
            for (RagDocument document : documents) {
                anyDoc = true;
                doc.append("\n#### 文档：").append(safe(document.getFileName()))
                        .append("（类型 ").append(safe(String.valueOf(document.getFileType())))
                        .append("，状态 ").append(safe(String.valueOf(document.getStatus())))
                        .append("，切片 ").append(document.getChunkCount() == null ? "?" : document.getChunkCount())
                        .append(" 段）\n");
                if (document.getStatus() != null && String.valueOf(document.getStatus()).toUpperCase(Locale.ROOT).contains("FAIL")) {
                    flags.add("文档处理失败：" + safe(document.getFileName()));
                }
                if (totalChars >= MAX_KB_TOTAL_CHARS) {
                    doc.append("（知识库正文总量已达上限，后续文档仅列名称）\n");
                    continue;
                }
                int docChars = appendDocChunks(doc, document);
                totalChars += docChars;
            }
        }
        if (!anyDoc) {
            flags.add("知识库无有效文档内容");
        }
        doc.append('\n');
    }

    private int appendDocChunks(StringBuilder doc, RagDocument document) {
        List<RagDocumentChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<RagDocumentChunk>()
                .eq(RagDocumentChunk::getDocumentId, document.getId())
                .orderByAsc(RagDocumentChunk::getChunkIndex)
                .last("limit " + MAX_CHUNKS_PER_DOC));
        int used = 0;
        StringBuilder body = new StringBuilder();
        for (RagDocumentChunk chunk : chunks) {
            if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                continue;
            }
            int room = MAX_DOC_CHARS - used;
            if (room <= 200) {
                body.append("（……切片内容已达单文档上限，仅展示前 ").append(chunks.indexOf(chunk)).append(" 段）\n");
                break;
            }
            String content = truncate(chunk.getContent().trim(), Math.min(room, 2000));
            body.append("> ").append(content.replace("\n", "\n> ")).append("\n\n");
            used += content.length();
        }
        if (used == 0) {
            body.append("（无可读切片内容）\n");
        }
        doc.append(body);
        return used;
    }

    // ---------------------------------------------------------------- Skill

    private void appendSkillSection(StringBuilder doc, AgentDefinition agent, List<String> flags) {
        List<AgentSkillPackage> bindings = skillBindingMapper.selectList(new LambdaQueryWrapper<AgentSkillPackage>()
                .eq(AgentSkillPackage::getAgentDefinitionId, agent.getId()));
        if (bindings.isEmpty()) {
            doc.append("\n## Skill 包\n\n（该智能体未绑定任何 Skill 包）\n");
            return;
        }
        doc.append("\n## Skill 包（已绑定 ").append(bindings.size()).append(" 个）\n");
        int totalChars = 0;
        for (AgentSkillPackage binding : bindings) {
            SkillPackage skill = binding.getSkillPackageId() == null
                    ? null : skillPackageMapper.selectById(binding.getSkillPackageId());
            if (skill == null) {
                doc.append("\n### Skill（包已不存在，绑定 id=").append(binding.getSkillPackageId()).append("）\n");
                flags.add("Skill 包缺失");
                continue;
            }
            doc.append("\n### Skill：").append(safe(skill.getName()))
                    .append("（分类 ").append(safe(skill.getCategory())).append("）\n");
            if (notBlank(skill.getDescription())) {
                doc.append("描述：").append(truncate(skill.getDescription(), 300)).append('\n');
            }
            List<SkillFile> files = skillFileMapper.selectList(new LambdaQueryWrapper<SkillFile>()
                    .eq(SkillFile::getSkillId, skill.getId())
                    .orderByAsc(SkillFile::getSort)
                    .orderByAsc(SkillFile::getId));
            if (files.isEmpty()) {
                doc.append("\n（Skill 包内无文件正文，正文相关分项按\"无法验证\"处理）\n");
                flags.add("Skill 包无文件正文：" + safe(skill.getName()));
                continue;
            }
            for (SkillFile file : files) {
                boolean main = file.getFileName() != null && file.getFileName().toLowerCase(Locale.ROOT).contains("skill.md");
                int cap = main ? MAX_SKILL_MAIN_CHARS : MAX_SKILL_OTHER_CHARS;
                String content = file.getContent() == null ? "" : file.getContent().trim();
                doc.append("\n#### 文件：").append(safe(file.getFilePath() == null ? file.getFileName() : file.getFilePath()))
                        .append("（类型 ").append(safe(String.valueOf(file.getFileType())))
                        .append("，").append(content.length()).append(" 字符）\n\n```text\n");
                if (totalChars + Math.min(content.length(), cap) <= MAX_SKILL_TOTAL_CHARS) {
                    String shown = truncate(content, cap);
                    doc.append(shown.isEmpty() ? "（空文件）" : shown);
                    if (content.length() > cap) {
                        doc.append("\n（……内容超长已截断）");
                    }
                    totalChars += shown.length();
                } else {
                    doc.append("（Skill 正文总量已达上限，仅记录文件名）");
                }
                doc.append("\n```\n");
            }
        }
        doc.append('\n');
    }

    // ---------------------------------------------------------------- 自述材料（旁证）

    private void appendFormSection(StringBuilder doc, ExamSubmission submission) {
        doc.append("\n## 考生自述材料（旁证：仅用于设计主导权通道 A/C 判定，不作为配置证据）\n\n");
        JsonNode form = parseJson(submission.getFormJson());
        JsonNode work = form.path("work");
        List<String> lines = new ArrayList<>();
        putText(lines, "作品标题", work, "title");
        putText(lines, "场景", work, "scene");
        putText(lines, "功能描述", work, "functionDescription");
        putText(lines, "数据说明", work, "dataDescription");
        putText(lines, "技术说明", work, "technicalDescription");
        putText(lines, "创新说明", work, "innovationDescription");
        putText(lines, "推广说明", work, "promotionDescription");
        putText(lines, "运行环境", work, "runtimeEnvironment");
        putText(lines, "团队说明", work, "teamDescription");
        if (lines.isEmpty()) {
            doc.append("（无自述文本，通道 C 记\"不可用\"）\n");
            return;
        }
        int total = 0;
        for (String line : lines) {
            if (total >= MAX_FORM_TEXT_CHARS) {
                doc.append("（自述文本过长，已截断）\n");
                break;
            }
            doc.append("- ").append(line).append('\n');
            total += line.length();
        }
        JsonNode materials = form.path("materials");
        List<String> fileNames = new ArrayList<>();
        collectFileNames(materials.path("screenshotFiles"), fileNames);
        collectFileNames(materials.path("videoFiles"), fileNames);
        collectFileNames(materials.path("packageFiles"), fileNames);
        collectFileNames(materials.path("supplementaryFiles"), fileNames);
        if (!fileNames.isEmpty()) {
            doc.append("- 提交附件（仅文件名，内容不作为评分依据）：").append(String.join("、", fileNames)).append('\n');
        }
        doc.append("（考生身份字段已脱敏，不参与评分）\n");
    }

    private void collectFileNames(JsonNode arr, List<String> out) {
        if (!arr.isArray()) {
            return;
        }
        arr.forEach(item -> {
            String name = null;
            if (item.isTextual()) {
                name = item.asText();
            } else if (item.isObject()) {
                JsonNode n = item.path("name");
                JsonNode o = item.path("originalName");
                name = n.isValueNode() && !n.isNull() ? n.asText() : (o.isValueNode() && !o.isNull() ? o.asText() : null);
            }
            if (notBlank(name)) {
                out.add(name);
            }
        });
    }

    // ---------------------------------------------------------------- 预计算信号（通道 B/E）

    private void appendPrecomputedSection(StringBuilder doc, ExamSubmission submission, ExamQuestion question,
                                          AgentDefinition agent, List<String> flags) {
        doc.append("\n## 预计算信号（系统提供的事实，通道 B / 通道 E 输入）\n\n");
        // 通道 E：行为信号
        List<String> behavior = new ArrayList<>();
        if (agent != null && agent.getCreatedAt() != null && agent.getUpdatedAt() != null) {
            if (agent.getCreatedAt().equals(agent.getUpdatedAt())) {
                behavior.add("智能体创建后从未修改（一次性粘贴特征）");
            } else {
                behavior.add("智能体创建后有过修改（存在迭代痕迹的可能性，需结合配置内容判断）");
            }
        }
        if (submission.getCreatedAt() != null && submission.getSubmittedAt() != null) {
            long minutes = Math.max(0, Duration.between(submission.getCreatedAt(), submission.getSubmittedAt()).toMinutes());
            behavior.add("该题作答用时约 " + minutes + " 分钟");
        }
        doc.append("### 通道 E 行为信号\n");
        doc.append(behavior.isEmpty() ? "- 不可用\n" : "");
        behavior.forEach(b -> doc.append("- ").append(b).append('\n'));

        // 通道 B：同源比对（跨租户只读）
        doc.append("\n### 通道 B 同源比对\n");
        try {
            TenantUtils.setIgnoreTenant();
            PeerSignal peer = findMostSimilarPeer(submission, question);
            if (peer == null) {
                doc.append("- 不可用（无可比样本或相似度未达阈值 ").append(PEER_SIM_THRESHOLD).append("）\n");
            } else {
                doc.append("- 与同批次同题另一位考生的系统提示词结构相似度：").append(String.format(Locale.ROOT, "%.2f", peer.similarity))
                        .append("（达到报告阈值，若同时命中通道 A 三问全缺可视为同源实锤）\n");
                doc.append("- 同批次同题相似度达阈值的考生数：").append(peer.peerCount).append(" 人\n");
            }
        } finally {
            TenantUtils.clearIgnoreTenant();
        }
    }

    private record PeerSignal(double similarity, int peerCount) {
    }

    private PeerSignal findMostSimilarPeer(ExamSubmission submission, ExamQuestion question) {
        if (submission.getEnrollmentId() == null || question == null || question.getCampaignId() == null) {
            return null;
        }
        AgentDefinition self = submission.getAgentId() == null ? null : agentDefinitionMapper.selectById(submission.getAgentId());
        if (self == null || self.getSystemPrompt() == null || self.getSystemPrompt().isBlank()) {
            return null;
        }
        List<ExamSubmission> peers = submissionMapper.selectList(
                new LambdaQueryWrapper<ExamSubmission>()
                        .ne(ExamSubmission::getEnrollmentId, submission.getEnrollmentId())
                        .eq(ExamSubmission::getQuestionId, question.getId())
                        .ne(ExamSubmission::getStatus, "DRAFT")
                        .isNotNull(ExamSubmission::getAgentId));
        if (peers.isEmpty()) {
            return null;
        }
        List<Long> agentIds = peers.stream().map(ExamSubmission::getAgentId).filter(java.util.Objects::nonNull).distinct().toList();
        if (agentIds.isEmpty()) {
            return null;
        }
        List<AgentDefinition> peerAgents = agentDefinitionMapper.selectList(new LambdaQueryWrapper<AgentDefinition>()
                .in(AgentDefinition::getId, agentIds)
                .isNotNull(AgentDefinition::getSystemPrompt));
        double max = 0;
        int count = 0;
        for (AgentDefinition peer : peerAgents) {
            double sim = combinedSimilarity(self.getSystemPrompt(), peer.getSystemPrompt());
            if (sim >= PEER_SIM_THRESHOLD) {
                count++;
                max = Math.max(max, sim);
            }
        }
        return count == 0 ? null : new PeerSignal(max, count);
    }

    // ---------------------------------------------------------------- 相似度工具

    /** 组合相似度 = 0.6 × 字符三元组余弦 + 0.4 × 结构行 Jaccard。 */
    static double combinedSimilarity(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return 0;
        }
        return 0.6 * trigramCosine(a, b) + 0.4 * skeletonJaccard(a, b);
    }

    static double trigramCosine(String a, String b) {
        Map<String, Integer> ga = trigrams(a);
        Map<String, Integer> gb = trigrams(b);
        if (ga.isEmpty() || gb.isEmpty()) {
            return 0;
        }
        Set<String> keys = new HashSet<>(ga.keySet());
        keys.addAll(gb.keySet());
        long dot = 0;
        long na = 0;
        long nb = 0;
        for (String k : keys) {
            int va = ga.getOrDefault(k, 0);
            int vb = gb.getOrDefault(k, 0);
            dot += (long) va * vb;
            na += (long) va * va;
            nb += (long) vb * vb;
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static Map<String, Integer> trigrams(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", "");
        Map<String, Integer> grams = new LinkedHashMap<>();
        for (int i = 0; i + 3 <= normalized.length(); i++) {
            grams.merge(normalized.substring(i, i + 3), 1, Integer::sum);
        }
        return grams;
    }

    /** 结构骨架：提取标题/编号/列表类结构行，比较集合重合度。 */
    static double skeletonJaccard(String a, String b) {
        Set<String> sa = skeletonLines(a);
        Set<String> sb = skeletonLines(b);
        if (sa.isEmpty() || sb.isEmpty()) {
            return 0;
        }
        Set<String> union = new HashSet<>(sa);
        union.addAll(sb);
        Set<String> intersection = new HashSet<>(sa);
        intersection.retainAll(sb);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private static Set<String> skeletonLines(String text) {
        if (text == null) {
            return Set.of();
        }
        Set<String> lines = new HashSet<>();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.length() < 2 || line.length() > 48) {
                continue;
            }
            if (line.matches("^#{1,6}\\s*.*") || line.matches("^[-*•>]\\s*.*")
                    || line.matches("^\\d+[.、)）].*") || line.matches("^[一二三四五六七八九十]+[、.].*")
                    || line.matches("^[（(][一二三四五六七八九十0-9]+[)）].*") || line.matches("^[①②③④⑤⑥⑦⑧⑨⑩].*")
                    || line.endsWith("：") || line.endsWith(":")) {
                lines.add(line.replaceAll("\\s+", ""));
            }
        }
        return lines;
    }

    // ---------------------------------------------------------------- 小工具

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node == null ? objectMapper.createObjectNode() : node;
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private void putText(List<String> lines, String label, JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isValueNode() && !value.isNull()) {
            String text = value.asText("").trim();
            if (notBlank(text)) {
                lines.add(label + "：" + truncate(text, 1500));
            }
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null || value.isBlank() || "null".equals(value) ? "-" : value;
    }
}
