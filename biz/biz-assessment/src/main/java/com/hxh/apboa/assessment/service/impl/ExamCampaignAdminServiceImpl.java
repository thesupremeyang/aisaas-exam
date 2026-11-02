package com.hxh.apboa.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hxh.apboa.assessment.mapper.ExamCampaignMapper;
import com.hxh.apboa.assessment.service.ExamCampaignAdminService;
import com.hxh.apboa.assessment.service.ExamCampaignScoreMode;
import com.hxh.apboa.assessment.service.ExamCampaignTiming;
import com.hxh.apboa.common.dto.ExamCampaignScheduleRequest;
import com.hxh.apboa.common.dto.ExamCampaignUpdateRequest;
import com.hxh.apboa.common.entity.ExamCampaign;
import com.hxh.apboa.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExamCampaignAdminServiceImpl implements ExamCampaignAdminService {
    private static final int MAX_DURATION_MINUTES = 7 * 24 * 60;

    private final ExamCampaignMapper campaignMapper;

    @Override
    public List<ExamCampaign> listCampaigns() {
        return campaignMapper.selectList(new LambdaQueryWrapper<ExamCampaign>()
                .orderByDesc(ExamCampaign::getCreatedAt))
                .stream().peek(ExamCampaignScoreMode::decorate).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamCampaign createCampaign(ExamCampaignUpdateRequest request) {
        CampaignSchedule schedule = validateRequest(request);
        ExamCampaign campaign = new ExamCampaign();
        campaign.setName(request.getName().trim());
        campaign.setSubmissionStart(schedule.startAt());
        campaign.setSubmissionEnd(schedule.endAt());
        campaign.setStatus("OPEN");
        campaign.setRubricVersion("v1");
        campaign.setEnabled(request.getEnabled() == null || request.getEnabled());
        ExamCampaignScoreMode.decorate(campaign);
        campaignMapper.insert(campaign);
        return campaign;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamCampaign updateCampaign(Long campaignId, ExamCampaignUpdateRequest request) {
        CampaignSchedule schedule = validateRequest(request);
        ExamCampaign campaign = findCampaign(campaignId);
        validateReviewDeadline(campaign, schedule.endAt());
        campaign.setName(request.getName().trim());
        campaign.setSubmissionStart(schedule.startAt());
        campaign.setSubmissionEnd(schedule.endAt());
        if (request.getEnabled() != null) {
            campaign.setEnabled(request.getEnabled());
        }
        ExamCampaignScoreMode.decorate(campaign);
        campaignMapper.updateById(campaign);
        return campaign;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamCampaign updateEnabled(Long campaignId, Boolean enabled) {
        if (enabled == null) {
            throw new BusinessException("请指定考试批次是否启用");
        }
        ExamCampaign campaign = findCampaign(campaignId);
        campaign.setEnabled(enabled);
        ExamCampaignScoreMode.decorate(campaign);
        campaignMapper.updateById(campaign);
        return campaign;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamCampaign updateSchedule(Long campaignId, ExamCampaignScheduleRequest request) {
        if (request == null) {
            throw new BusinessException("请填写考试时间");
        }
        CampaignSchedule schedule = validateSchedule(request.getStartAt(), request.getDurationMinutes());
        ExamCampaign campaign = findCampaign(campaignId);
        validateReviewDeadline(campaign, schedule.endAt());
        campaign.setSubmissionStart(schedule.startAt());
        campaign.setSubmissionEnd(schedule.endAt());
        ExamCampaignScoreMode.decorate(campaign);
        campaignMapper.updateById(campaign);
        return campaign;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamCampaign startNow(Long campaignId) {
        ExamCampaign campaign = findCampaign(campaignId);
        if (!Boolean.TRUE.equals(campaign.getEnabled())) {
            throw new BusinessException("请先启用该考试批次");
        }
        if ("DRAFT".equalsIgnoreCase(campaign.getStatus())
                || "CLOSED".equalsIgnoreCase(campaign.getStatus())) {
            throw new BusinessException("当前考试批次不可开考");
        }

        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (campaign.getSubmissionEnd() != null && !now.isBefore(campaign.getSubmissionEnd())) {
            throw new BusinessException("该场次已结束，请先编辑考试时间");
        }
        if (campaign.getSubmissionStart() == null
                || campaign.getSubmissionEnd() == null
                || !campaign.getSubmissionEnd().isAfter(campaign.getSubmissionStart())) {
            throw new BusinessException("该场次没有有效的考试时长，请先编辑考试时间");
        }

        long durationMinutes = java.time.Duration.between(
                campaign.getSubmissionStart(), campaign.getSubmissionEnd()).toMinutes();
        if (durationMinutes < 1 || durationMinutes > MAX_DURATION_MINUTES) {
            throw new BusinessException("该场次的考试时长无效，请先编辑考试时间");
        }

        LocalDateTime endAt = now.plusMinutes(durationMinutes);
        validateReviewDeadline(campaign, endAt);
        campaign.setSubmissionStart(now);
        campaign.setSubmissionEnd(endAt);
        ExamCampaignScoreMode.decorate(campaign);
        campaignMapper.updateById(campaign);
        return campaign;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamCampaign updateScoreQueryEnabled(Long campaignId, Boolean enabled) {
        if (enabled == null) {
            throw new BusinessException("请指定开启或关闭查分模式");
        }
        ExamCampaign campaign = findCampaign(campaignId);
        if (enabled && ExamCampaignTiming.state(campaign, LocalDateTime.now()) != ExamCampaignTiming.State.ENDED) {
            throw new BusinessException("考试结束后才可以开启查分模式");
        }
        // 关闭查分不限制考试状态，便于随时收回考生查分入口
        campaign.setConfigJson(ExamCampaignScoreMode.withEnabled(campaign.getConfigJson(), enabled));
        ExamCampaignScoreMode.decorate(campaign);
        campaignMapper.updateById(campaign);
        return campaign;
    }

    private CampaignSchedule validateRequest(ExamCampaignUpdateRequest request) {
        if (request == null) {
            throw new BusinessException("考试批次信息不能为空");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException("请填写考试名称");
        }
        if (request.getName().trim().length() > 200) {
            throw new BusinessException("考试名称不能超过 200 个字符");
        }
        CampaignSchedule schedule = validateSchedule(request.getStartAt(), request.getDurationMinutes());
        return schedule;
    }

    private CampaignSchedule validateSchedule(LocalDateTime startAt, Integer durationMinutes) {
        if (startAt == null) {
            throw new BusinessException("请填写考试开考时间");
        }
        if (durationMinutes == null || durationMinutes < 1 || durationMinutes > MAX_DURATION_MINUTES) {
            throw new BusinessException("考试时长必须在 1 分钟到 7 天之间");
        }
        return new CampaignSchedule(startAt, startAt.plusMinutes(durationMinutes));
    }

    private void validateReviewDeadline(ExamCampaign campaign, LocalDateTime endAt) {
        if (campaign.getReviewEnd() != null && endAt.isAfter(campaign.getReviewEnd())) {
            throw new BusinessException("考试结束时间不能晚于评审结束时间");
        }
    }

    private ExamCampaign findCampaign(Long campaignId) {
        if (campaignId == null) {
            throw new BusinessException("考试批次不能为空");
        }
        ExamCampaign campaign = campaignMapper.selectById(campaignId);
        if (campaign == null) {
            throw new BusinessException("考试批次不存在");
        }
        return campaign;
    }

    private record CampaignSchedule(LocalDateTime startAt, LocalDateTime endAt) {
    }
}
