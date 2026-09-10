package com.hxh.apboa.engine.tool;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.file.ReadFileTool;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 描述：加载文件文本内容工具，用于 AI 自主读取已解析的文档附件纯文本
 *
 * @author huxuehao
 **/
public class LoadFileTextContentTool {

    private final ReadFileTool readFileTool;

    public LoadFileTextContentTool() {
        // 限制文件访问范围到 .apboa 临时文件目录
        String baseDir = ApboaFileTool.getTempFilesDir().toString();
        this.readFileTool = new ReadFileTool(baseDir);
    }

    /**
     * 读取已解析的文档附件纯文本内容
     *
     * @param apboaFileName .apboa 文件名（即 {fileId}.apboa）
     * @param ranges 可选的行范围，格式 "start,end"，不传则返回全文
     * @return 文件文本内容
     */
    @Tool(
            name = "load_file_text_content",
            description =
                    "Read the extracted plain text content of an uploaded document file"
                            + " (Word, Excel, PPT, PDF, CSV, or plain text). The file content is"
                            + " automatically extracted and saved as a .apboa file when the user"
                            + " uploads a document. Use this tool to read the text content for analysis."
                            + " The apboa_file_name parameter is provided in the user's message hint"
                            + " (e.g., '123.apboa'). Optionally specify line ranges for large files.")
    public Mono<ToolResultBlock> loadFileTextContent(
            @ToolParam(
                            name = "apboa_file_name",
                            description = "The .apboa file name from the user's message hint, e.g. '123.apboa'")
                    String apboaFileName,
            @ToolParam(
                            name = "ranges",
                            required = false,
                            description =
                                    "Optional line range to read, e.g. '1,100' for first 100 lines"
                                            + " or '-100,-1' for last 100 lines. Omit to read the entire file.")
                    String ranges) {
        // 防御 null 参数：AI 模型可能传入异常值，避免 NPE 穿透
        if (apboaFileName == null || apboaFileName.isBlank()) {
            return Mono.just(ToolResultBlock.builder()
                    .id("load_file_text_content")
                    .output(TextBlock.builder()
                            .text("Error: apboa_file_name is required and cannot be empty")
                            .build())
                    .build());
        }

        // 校验文件存在性，文件不存在时返回友好提示而非底层异常
        Path filePath = ApboaFileTool.getTempFilesDir().resolve(apboaFileName).normalize();
        if (!filePath.startsWith(ApboaFileTool.getTempFilesDir()) || !Files.exists(filePath)) {
            return Mono.just(ToolResultBlock.builder()
                    .id("load_file_text_content")
                    .output(TextBlock.builder()
                            .text("Error: .apboa file not found for '" + apboaFileName
                                    + "'. The file may not have been parsed yet or was deleted.")
                            .build())
                    .build());
        }

        return readFileTool.viewTextFile(apboaFileName, ranges);
    }
}
