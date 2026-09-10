/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.core.agui.adapter;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.agui.converter.AguiMessageConverter;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.model.RunAgentInput;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.util.JsonException;
import io.agentscope.core.util.JsonUtils;

import java.util.*;

import reactor.core.publisher.Flux;

/**
 * Adapter that bridges AgentScope agents to the AG-UI protocol.
 *
 * <p>This adapter converts AG-UI protocol inputs to AgentScope messages,
 * invokes the agent, and converts the streaming events back to AG-UI events.
 *
 * <p><b>Event Mapping:</b>
 * <ul>
 *   <li>AgentScope REASONING and AGENT_RESULT events → AG-UI TEXT_MESSAGE_* events (for TextBlock)</li>
 *   <li>AgentScope REASONING events → AG-UI REASONING_* events (for ThinkingBlock, when enabled)</li>
 *   <li>AgentScope TOOL_RESULT events → AG-UI TOOL_CALL_END events</li>
 *   <li>ToolUseBlock content → AG-UI TOOL_CALL_START events</li>
 * </ul>
 *
 * <p><b>Reasoning Support:</b>
 * <ul>
 *   <li>ThinkingBlock content is converted to REASONING_* events according to AG-UI Reasoning draft</li>
 *   <li>Reasoning output is disabled by default (enableReasoning=false) for backward compatibility</li>
 *   <li>Set enableReasoning=true in AguiAdapterConfig to enable reasoning events</li>
 * </ul>
 */
public class AguiAgentAdapter {
    private final Map<String, Map<String, Object>> TOOL_CACHE_MAP = new HashMap<>();

    private final Agent agent;
    private final AguiAdapterConfig config;
    private final AguiMessageConverter messageConverter;

    /**
     * Creates a new AguiAgentAdapter.
     *
     * @param agent The agent to adapt
     * @param config The adapter configuration
     */
    public AguiAgentAdapter(Agent agent, AguiAdapterConfig config) {
        this.agent = Objects.requireNonNull(agent, "agent cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.messageConverter = new AguiMessageConverter();
    }

    /**
     * Run the agent with AG-UI protocol input.
     *
     * <p>This method converts the input messages, invokes the agent's streaming API,
     * and emits AG-UI protocol events.
     *
     * @param input The AG-UI run input
     * @return A Flux of AG-UI events
     */
    public Flux<AguiEvent> run(RunAgentInput input) {
        String threadId = input.getThreadId();
        String runId = input.getRunId();

        // Convert AG-UI messages to AgentScope messages
        List<Msg> msgs = messageConverter.toMsgList(input.getMessages());

        // Create stream options - use incremental mode for true streaming
        StreamOptions options =
                StreamOptions.builder().eventTypes(EventType.ALL).incremental(true).build();

        // Track state for event conversion
        EventConversionState state = new EventConversionState(threadId, runId);

        return Flux.concat(
                        // Emit RUN_STARTED
                        Flux.just(new AguiEvent.RunStarted(threadId, runId)),
                        // Stream agent events and convert to AG-UI events
                        // Use concatMapIterable to preserve strict event ordering
                        agent.stream(msgs, options)
                                .concatMapIterable(event -> convertEvent(event, state)),
                        // Emit any pending end events and RUN_FINISHED
                        Flux.defer(() -> finishRun(state)))
                .onErrorResume(
                        error -> {
                            // On error, emit RawEvent with error info followed by RunFinished
                            String errorMessage =
                                    error.getMessage() != null
                                            ? error.getMessage()
                                            : error.getClass().getSimpleName();
                            return Flux.concat(
                                    Flux.just(
                                            new AguiEvent.Raw(
                                                    threadId, runId, Map.of("error", errorMessage))),
                                    finishRun(state));
                        });
    }

    /**
     * Convert an AgentScope event to AG-UI events.
     *
     * @param event The AgentScope event
     * @param state The conversion state
     * @return List of AG-UI events
     */
    private List<AguiEvent> convertEvent(Event event, EventConversionState state) {
        List<AguiEvent> events = new ArrayList<>();
        Msg msg = event.getMessage();
        EventType type = event.getType();

        if (type == EventType.REASONING) {
            // Handle reasoning events - convert to text messages and tool calls
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof TextBlock textBlock) {
                    String text = textBlock.getText();
                    if (text != null && !text.isEmpty()) {
                        // Apboa 如果当前已有活跃的文本消息，复用其 messageId，避免产生多个 TEXT_MESSAGE_START
                        String messageId;
                        if (state.hasActiveTextMessage()) {
                            messageId = state.getCurrentTextMessageId();
                        } else {
                            messageId = state.resolveTextMessageId(msg.getId());
                        }

                        // Start message if not started
                        if (!state.hasStartedMessage(messageId)) {
                            events.add(
                                    new AguiEvent.ReasoningMessageEnd(
                                            state.threadId, state.runId, messageId));
                            events.add(
                                    new AguiEvent.TextMessageStart(
                                            state.threadId, state.runId, messageId, "assistant"));
                            state.startMessage(messageId);
                        }

                        // Incremental events carry a delta; the last event carries the
                        // accumulated text. In both cases emit only text that has not
                        // already been sent to the client.
                            String delta = state.appendText(messageId, text, event.isLast());
                            if (!delta.isEmpty()) {
                                events.add(
                                        new AguiEvent.TextMessageContent(
                                                state.threadId, state.runId, messageId, delta));
                            }
                    }
                } else if (block instanceof ThinkingBlock thinkingBlock) {
                    // Handle thinking blocks - convert to REASONING_* events (only if enabled)
                    // According to AG-UI Reasoning draft: https://docs.ag-ui.com/drafts/reasoning
                    if (config.isEnableReasoning()) {
                        String thinking = thinkingBlock.getThinking();
                        if (thinking != null && !thinking.isEmpty()) {
                            // Apboa 如果当前已有活跃的文本消息，复用其 messageId，避免产生多个 TEXT_MESSAGE_START
                            String messageId;
                            if (state.hasActiveTextMessage()) {
                                messageId = state.getCurrentTextMessageId();
                            } else {
                                messageId = msg.getId();
                            }

                            // Start reasoning message if not started
                            if (!state.hasStartedReasoningMessage(messageId)) {
                                events.add(
                                        new AguiEvent.ReasoningMessageStart(
                                                state.threadId,
                                                state.runId,
                                                messageId,
                                                "assistant"));
                                state.startReasoningMessage(messageId);
                            }

                            if (!event.isLast()) {
                                // In incremental mode, thinking is already the delta
                                String delta = state.appendReasoning(messageId, thinking, false);
                                if (!delta.isEmpty()) {
                                    events.add(
                                            new AguiEvent.ReasoningMessageContent(
                                                    state.threadId, state.runId, messageId, delta));
                                }
                            } else {
                                // The final reasoning event contains the accumulated text.
                                // Emit a missing suffix before closing the reasoning message.
                                String delta = state.appendReasoning(messageId, thinking, true);
                                if (!delta.isEmpty()) {
                                    events.add(
                                            new AguiEvent.ReasoningMessageContent(
                                                    state.threadId, state.runId, messageId, delta));
                                }
                                if (!state.hasEndedReasoningMessage(messageId)) {
                                    events.add(
                                            new AguiEvent.ReasoningMessageEnd(
                                                    state.threadId, state.runId, messageId));
                                    state.endReasoningMessage(messageId);
                                }
                            }
                        }
                    }
                    // If reasoning is disabled, ThinkingBlock content is ignored (backward
                    // compatibility)
                } else if (block instanceof ToolUseBlock toolUse) {
                    // End any active text message before starting tool call
                    if (state.hasActiveTextMessage()) {
                        String activeMessageId = state.getCurrentTextMessageId();
                        events.add(
                                new AguiEvent.TextMessageEnd(
                                        state.threadId, state.runId, activeMessageId));
                        events.add(
                                new AguiEvent.ReasoningMessageEnd(
                                        state.threadId, state.runId, activeMessageId));
                        state.endMessage(activeMessageId);
                    }

                    // Emit tool call start
                    String toolCallId = toolUse.getId();
                    if (toolCallId == null) {
                        toolCallId = UUID.randomUUID().toString();
                    }

                    if (!state.hasStartedToolCall(toolCallId)) {
                        events.add(
                                new AguiEvent.ToolCallStart(
                                        state.threadId,
                                        state.runId,
                                        toolCallId,
                                        toolUse.getName()));
                        state.startToolCall(toolCallId);
                    }

                    // Emit tool call args if enabled
                    if (config.isEmitToolCallArgs() && !event.isLast()) {
                        String args = toolUse.getContent();
                        if (args != null && !args.isEmpty()) {
                            events.add(
                                    new AguiEvent.ToolCallArgs(
                                            state.threadId, state.runId, toolCallId, args));
                        }
                    }
                }
            }
        } else if (type == EventType.AGENT_RESULT) {
            // AgentScope emits the final answer as AGENT_RESULT after the last
            // reasoning event. Ignoring it leaves the client showing only thinking.
            String finalText = extractMessageText(msg);
            if (!finalText.isEmpty()) {
                String messageId = state.hasActiveTextMessage()
                        ? state.getCurrentTextMessageId()
                        : state.resolveTextMessageId(msg.getId());

                // The final result can repeat text already streamed by the provider.
                // Do not create a duplicate assistant message in that case.
                if (state.hasEndedMessage(messageId)
                        && finalText.equals(state.getText(messageId))) {
                    return events;
                }

                if (!state.hasStartedMessage(messageId)) {
                    events.add(
                            new AguiEvent.TextMessageStart(
                                    state.threadId, state.runId, messageId, "assistant"));
                    state.startMessage(messageId);
                }

                String delta = state.appendText(messageId, finalText, true);
                if (!delta.isEmpty()) {
                    events.add(
                            new AguiEvent.TextMessageContent(
                                    state.threadId, state.runId, messageId, delta));
                }

                if (!state.hasEndedMessage(messageId)) {
                    events.add(
                            new AguiEvent.TextMessageEnd(
                                    state.threadId, state.runId, messageId));
                    state.endMessage(messageId);
                }
            } else if (state.hasActiveTextMessage()) {
                String messageId = state.getCurrentTextMessageId();
                events.add(new AguiEvent.TextMessageEnd(state.threadId, state.runId, messageId));
                state.endMessage(messageId);
            }
        } else if (type == EventType.TOOL_RESULT) {
            if (event.isLast()) {
                // Handle tool results
                for (ContentBlock block : msg.getContent()) {
                    if (block instanceof ToolResultBlock toolResult) {
                        String toolCallId = toolResult.getId();
                        String result = extractToolResultText(toolResult);

                        boolean hasStarted = state.hasStartedToolCall(toolCallId);
                        if (!hasStarted) {
                            events.add(
                                    new AguiEvent.ToolCallStart(
                                            state.threadId, state.runId, toolCallId, "unknown"));
                            state.startToolCall(toolCallId);
                        }

                        // Ensure ToolCallEnd is emitted to close arguments phase
                        events.add(new AguiEvent.ToolCallEnd(state.threadId, state.runId, toolCallId));

                        events.add(
                                new AguiEvent.ToolCallResult(
                                        state.threadId,
                                        state.runId,
                                        toolCallId,
                                        result,
                                        "tool",
                                        msg.getId()));
                        state.endToolCall(toolCallId);
                    }
                }
            }
            /**
             * 【子Agent流式事件发送】
             * 此处可以实现子Agent流式事件包装成AguiEvent并发送，但是目前前端适配效果不好。
             * 此处若想可用，需要对AguiEvent事件进行扩展，同时前端要实现合理的子Agent交互效果，并且支持多个子Agent并行运行。
             * TODO: 待前端适配完善后再启用
             */
//            else {
//                for (ContentBlock block : msg.getContent()) {
//                    if (block instanceof ToolResultBlock toolResult) {
//                        Object o = toolResult.getMetadata().get("subagent_id");
//                        if (o != null) {
//                            List<ContentBlock> output = toolResult.getOutput();
//                            for (ContentBlock contentBlock : output) {
//                                if (contentBlock instanceof TextBlock textBlock){
//                                    String text = textBlock.getText();
//                                    Event subEvent = JsonUtils.getJsonCodec().fromJson(text, Event.class);
//                                    events.addAll(convertEvent(subEvent, state));
//                                }
//                            }
//                        }
//                    }
//                }
//            }

        }

        return events;
    }

    /**
     * Finish the run by emitting any pending end events and RUN_FINISHED.
     *
     * @param state The conversion state
     * @return Flux of final events
     */
    private Flux<AguiEvent> finishRun(EventConversionState state) {
        List<AguiEvent> events = new ArrayList<>();

        // End any messages that weren't properly ended
        for (String messageId : state.getStartedMessages()) {
            if (!state.hasEndedMessage(messageId)) {
                events.add(new AguiEvent.TextMessageEnd(state.threadId, state.runId, messageId));
                state.endMessage(messageId);
            }
        }

        // End any tool calls that weren't properly ended
        for (String toolCallId : state.getStartedToolCalls()) {
            if (!state.hasEndedToolCall(toolCallId)) {
                events.add(new AguiEvent.ToolCallEnd(state.threadId, state.runId, toolCallId));
                state.endToolCall(toolCallId);
            }
        }

        // End any reasoning messages that weren't properly ended
        for (String messageId : state.getStartedReasoningMessages()) {
            if (!state.hasEndedReasoningMessage(messageId)) {
                events.add(
                        new AguiEvent.ReasoningMessageEnd(state.threadId, state.runId, messageId));
                state.endReasoningMessage(messageId);
            }
        }

        // Emit RUN_FINISHED
        events.add(new AguiEvent.RunFinished(state.threadId, state.runId));

        return Flux.fromIterable(events);
    }

    /**
     * Extract text content from a tool result block.
     *
     * @param toolResult The tool result block
     * @return The text content, or null if not present
     */
    private String extractToolResultText(ToolResultBlock toolResult) {
        if (toolResult.getOutput() == null || toolResult.getOutput().isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (ContentBlock output : toolResult.getOutput()) {
            if (output instanceof TextBlock textBlock) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(textBlock.getText());
            }
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Extract only visible text blocks from an AgentScope message.
     *
     * @param msg the AgentScope message
     * @return visible text, or an empty string when no text block exists
     */
    private String extractMessageText(Msg msg) {
        if (msg == null || msg.getContent() == null || msg.getContent().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock textBlock
                    && textBlock.getText() != null
                    && !textBlock.getText().isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(textBlock.getText());
            }
        }
        return sb.toString();
    }

    /**
     * Serialize tool arguments to JSON string.
     *
     * @param input The tool input map
     * @return JSON string representation
     */
    private String serializeToolArgs(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return "{}";
        }
        try {
            return JsonUtils.getJsonCodec().toJson(input);
        } catch (JsonException e) {
            return "{}";
        }
    }

    /**
     * State tracker for event conversion.
     * Uses LinkedHashSet to preserve insertion order for proper event sequencing.
     */
    private static class EventConversionState {
        final String threadId;
        final String runId;
        private final Set<String> startedMessages = new LinkedHashSet<>();
        private final Set<String> endedMessages = new LinkedHashSet<>();
        private final Set<String> startedToolCalls = new LinkedHashSet<>();
        private final Set<String> endedToolCalls = new LinkedHashSet<>();
        private final Set<String> startedReasoningMessages = new LinkedHashSet<>();
        private final Set<String> endedReasoningMessages = new LinkedHashSet<>();
        private final Map<String, StringBuilder> textContents = new HashMap<>();
        private final Map<String, StringBuilder> reasoningContents = new HashMap<>();
        private String currentTextMessageId = null;

        EventConversionState(String threadId, String runId) {
            this.threadId = threadId;
            this.runId = runId;
        }

        boolean hasStartedMessage(String messageId) {
            return startedMessages.contains(messageId);
        }

        void startMessage(String messageId) {
            startedMessages.add(messageId);
            currentTextMessageId = messageId;
            textContents.computeIfAbsent(messageId, ignored -> new StringBuilder());
        }

        void endMessage(String messageId) {
            endedMessages.add(messageId);
            if (messageId.equals(currentTextMessageId)) {
                currentTextMessageId = null;
            }
        }

        boolean hasEndedMessage(String messageId) {
            return endedMessages.contains(messageId);
        }

        String getCurrentTextMessageId() {
            return currentTextMessageId;
        }

        boolean hasActiveTextMessage() {
            return currentTextMessageId != null && !hasEndedMessage(currentTextMessageId);
        }

        /**
         * Resolve a text message id without reusing a previously closed message.
         */
        String resolveTextMessageId(String preferredId) {
            if (!hasStartedMessage(preferredId) || !hasEndedMessage(preferredId)) {
                return preferredId;
            }
            return preferredId + "-response-" + UUID.randomUUID();
        }

        /**
         * Append either an incremental delta or an accumulated final value.
         * AgentScope's final event contains the complete text, so only the
         * suffix after the streamed prefix is returned in that case.
         */
        String appendText(String messageId, String text, boolean accumulated) {
            if (text == null || text.isEmpty()) {
                return "";
            }

            StringBuilder currentBuilder = textContents.computeIfAbsent(
                    messageId, ignored -> new StringBuilder());
            String current = currentBuilder.toString();
            String delta = text;
            if (accumulated) {
                if (text.equals(current)) {
                    return "";
                }
                if (text.startsWith(current)) {
                    delta = text.substring(current.length());
                }
            }

            if (!delta.isEmpty()) {
                currentBuilder.append(delta);
            }
            return delta;
        }

        String getText(String messageId) {
            StringBuilder text = textContents.get(messageId);
            return text == null ? "" : text.toString();
        }

        Set<String> getStartedMessages() {
            return startedMessages;
        }

        boolean hasStartedToolCall(String toolCallId) {
            return startedToolCalls.contains(toolCallId);
        }

        void startToolCall(String toolCallId) {
            startedToolCalls.add(toolCallId);
        }

        void endToolCall(String toolCallId) {
            endedToolCalls.add(toolCallId);
        }

        boolean hasEndedToolCall(String toolCallId) {
            return endedToolCalls.contains(toolCallId);
        }

        Set<String> getStartedToolCalls() {
            return startedToolCalls;
        }

        boolean hasStartedReasoningMessage(String messageId) {
            return startedReasoningMessages.contains(messageId);
        }

        void startReasoningMessage(String messageId) {
            startedReasoningMessages.add(messageId);
            reasoningContents.computeIfAbsent(messageId, ignored -> new StringBuilder());
        }

        String appendReasoning(String messageId, String text, boolean accumulated) {
            if (text == null || text.isEmpty()) {
                return "";
            }

            StringBuilder currentBuilder = reasoningContents.computeIfAbsent(
                    messageId, ignored -> new StringBuilder());
            String current = currentBuilder.toString();
            String delta = text;
            if (accumulated) {
                if (text.equals(current)) {
                    return "";
                }
                if (text.startsWith(current)) {
                    delta = text.substring(current.length());
                }
            }

            if (!delta.isEmpty()) {
                currentBuilder.append(delta);
            }
            return delta;
        }

        void endReasoningMessage(String messageId) {
            endedReasoningMessages.add(messageId);
        }

        boolean hasEndedReasoningMessage(String messageId) {
            return endedReasoningMessages.contains(messageId);
        }

        Set<String> getStartedReasoningMessages() {
            return startedReasoningMessages;
        }
    }
}
