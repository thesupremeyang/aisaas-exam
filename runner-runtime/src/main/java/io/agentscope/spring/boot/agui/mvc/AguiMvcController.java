package io.agentscope.spring.boot.agui.mvc;

import com.hxh.apboa.engine.agui.AgentContext;
import io.agentscope.core.agui.AguiException;
import io.agentscope.core.agui.adapter.AguiAdapterConfig;
import io.agentscope.core.agui.encoder.AguiEventEncoder;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.model.RunAgentInput;
import io.agentscope.core.agui.processor.AguiRequestProcessor;
import io.agentscope.core.agui.registry.AguiAgentRegistry;
import io.agentscope.core.session.Session;
import io.agentscope.spring.boot.agui.common.DefaultAgentResolver;
import io.agentscope.spring.boot.agui.common.ThreadSessionManager;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

public class AguiMvcController {

    private static final Logger logger = LoggerFactory.getLogger(AguiMvcController.class);

    private static final String DEFAULT_AGENT_ID_HEADER = "X-Agent-Id";

    private final AguiRequestProcessor processor;
    private final AguiEventEncoder encoder;
    private final String agentIdHeader;
    private final long sseTimeout;
    private final ExecutorService executorService;
    private final ThreadSessionManager sessionManager;
    private final RunTracker runTracker;
    private final AguiAgentRegistry registry;
    private final Consumer<String> agentMissingHandler;
    /** 单个 run 的总时长上限（毫秒），到点主动中断并给前端一个可读错误 */
    private final long runTimeoutMs;
    private final ScheduledExecutorService runTimeoutScheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "agui-run-timeout");
                        t.setDaemon(true);
                        return t;
                    });

    private AguiMvcController(Builder builder) {
        Session session = builder.session;
        JdbcTemplate jdbcTemplate = builder.jdbcTemplate;
        this.processor =
                AguiRequestProcessor.builder()
                        .agentResolver(
                                DefaultAgentResolver.builder()
                                        .registry(builder.registry)
                                        .sessionManager(builder.sessionManager)
                                        .serverSideMemory(builder.serverSideMemory)
                                        .build())
                        .config(
                                builder.config != null
                                        ? builder.config
                                        : AguiAdapterConfig.defaultConfig())
                        .session(session)
                        .jdbcTemplate(jdbcTemplate)
                        .build();
        this.encoder = new AguiEventEncoder();
        this.agentIdHeader =
                builder.agentIdHeader != null ? builder.agentIdHeader : DEFAULT_AGENT_ID_HEADER;
        this.sseTimeout = builder.sseTimeout > 0 ? builder.sseTimeout : 600000L;
        this.executorService = buildExecutor(builder);
        this.sessionManager = builder.sessionManager;
        this.runTracker = builder.runTracker != null ? builder.runTracker : new RunTracker(this.encoder);
        this.registry = builder.registry;
        this.agentMissingHandler = builder.agentMissingHandler;
        this.runTimeoutMs = builder.runTimeoutMs > 0 ? builder.runTimeoutMs : 120000L;
    }

    /**
     * Handle an AG-UI run request.
     *
     * @param input The run agent input
     * @param headerAgentId The agent ID from HTTP header (may be null)
     * @return An SseEmitter for streaming AG-UI events
     */
    public SseEmitter handle(RunAgentInput input, String headerAgentId) {
        return handleInternal(input, headerAgentId, null);
    }

    /**
     * Handle an AG-UI run request with agent ID in the URL path.
     *
     * @param input The run agent input
     * @param headerAgentId The agent ID from HTTP header (may be null)
     * @param pathAgentId The agent ID from URL path variable
     * @return An SseEmitter for streaming AG-UI events
     */
    public SseEmitter handleWithAgentId(
            RunAgentInput input, String headerAgentId, String pathAgentId) {
        return handleInternal(input, headerAgentId, pathAgentId);
    }

    /**
     * 构造 SSE 运行线程池。
     *
     * <p>原先使用 {@code Executors.newCachedThreadPool()}：线程数无上限、任务无队列，
     * 一旦上游变慢就会出现「线程无限增长 + 没有背压」，最终整机被拖死而没有任何降级。
     * 这里改为有界线程池：超过容量后立刻快速失败，并给前端一条明确的错误事件。</p>
     */
    private static ExecutorService buildExecutor(Builder builder) {
        int core = builder.runThreadsCore > 0 ? builder.runThreadsCore : 32;
        int max = builder.runThreadsMax > 0 ? builder.runThreadsMax : 256;
        int queue = builder.runQueueCapacity >= 0 ? builder.runQueueCapacity : 128;
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory =
                r -> {
                    Thread t = new Thread(r, "agui-run-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                };
        ThreadPoolExecutor executor =
                new ThreadPoolExecutor(
                        core,
                        max,
                        60L,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(Math.max(queue, 1)),
                        factory,
                        new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private SseEmitter handleInternal(
            RunAgentInput input, String headerAgentId, String pathAgentId) {
        SseEmitter emitter = new SseEmitter(sseTimeout);
        String threadId = input.getThreadId();
        String runId = input.getRunId();

        try {
            executorService.submit(
                () -> {
                    Disposable subscription = null;
                    final AtomicReference<ScheduledFuture<?>> timeoutRef = new AtomicReference<>();
                    try {
                        // 注册自愈：agent 可能因为 pub/sub 丢消息而没注册上，
                        // 这里在进执行器前先补一次，避免考生被「Agent not found」直接打死。
                        healAgentIfMissing(pathAgentId);

                        // 初始化上下文
                        AgentContext.init(input, threadId);

                        // Process request - returns both agent and event stream
                        AguiRequestProcessor.ProcessResult result =
                                processor.process(input, headerAgentId, pathAgentId);

                        // 注册到 RunTracker（在订阅前完成，保证事件不丢失）
                        runTracker.registerRun(threadId, result.agent(), null);

                        // run 级总超时：上游"连得上但不返回"时，
                        // 一个 run 会一直占着线程池槽位直到读超时（300s）。
                        // 这里到点主动中断，并给前端一条可读错误。
                        timeoutRef.set(
                                runTimeoutScheduler.schedule(
                                        () -> onRunTimeout(threadId, runId),
                                        runTimeoutMs,
                                        TimeUnit.MILLISECONDS));

                        // SSE 回调不再中断 Agent，仅清理 SSE 连接
                        emitter.onCompletion(
                                () -> {
                                    logger.debug("SSE connection completed for run {}", runId);
                                    runTracker.removeEmitter(threadId, emitter);
                                });
                        emitter.onTimeout(
                                () -> {
                                    logger.info(
                                            "SSE connection timed out for run {}", runId);
                                    runTracker.removeEmitter(threadId, emitter);
                                });
                        emitter.onError(
                                (ex) -> {
                                    logger.info(
                                            "SSE connection error for run {}: {}",
                                            runId,
                                            ex.getMessage());
                                    runTracker.removeEmitter(threadId, emitter);
                                });

                        // 初始 emitter 加入 RunTracker 的 subscriber 列表
                        runTracker.reconnect(threadId, emitter);

                        // 订阅 Flux 管道：事件推入 RunTracker，而非直接发送 SSE
                        // onErrorResume：将 publish 侧异常转为正常完成（避免 Flux 被取消）
                        // 注意：不用 doFinally —— cancel 信号（SSE 断连）不应触发 markCompleted，
                        // 只有 agent 正常结束或异常→完成时才标记完成
                        subscription =
                                result.events()
                                        .onErrorResume(e -> {
                                            String reason = e != null ? e.getMessage() : null;
                                            logger.warn(
                                                    "Agent flux error for thread {}: {} -> {}",
                                                    threadId,
                                                    e != null ? e.getClass().getSimpleName() : "null",
                                                    reason);
                                            // 关键：异常必须变成一条前端可见的错误事件。
                                            // 原先直接 Flux.empty() 会让 SSE 正常结束，
                                            // 前端既没有回复也没有报错，考生只能反复重发。
                                            try {
                                                runTracker.pushEvent(
                                                        threadId,
                                                        new AguiEvent.Raw(
                                                                threadId,
                                                                runId,
                                                                Map.of("error", friendlyError(reason))));
                                            } catch (Exception pushError) {
                                                logger.warn(
                                                        "Failed to push agent error event for thread {}: {}",
                                                        threadId,
                                                        pushError.getMessage());
                                            }
                                            return Flux.empty();
                                        })
                                        .subscribe(
                                                event -> runTracker.pushEvent(threadId, event),
                                                error -> {
                                                    // 理论上不应到达这里（已被 onErrorResume 拦截）
                                                    logger.error(
                                                            "Residual flux error for thread {}: {}",
                                                            threadId,
                                                            error != null ? error.getMessage() : "null");
                                                    cancelQuietly(timeoutRef);
                                                    runTracker.markCompleted(threadId);
                                                },
                                                () -> {
                                                    logger.debug(
                                                            "AG-UI run stream completed for thread {}",
                                                            threadId);
                                                    cancelQuietly(timeoutRef);
                                                    runTracker.markCompleted(threadId);
                                                });

                        // 更新 subscription 引用（不替换 ActiveRun，保住 buffer）
                        runTracker.updateSubscription(threadId, subscription);

                    } catch (AguiException.AgentNotFoundException e) {
                        logger.error("Agent not found: {}", e.getMessage());

                        AgentContext.clean();

                        cancelQuietly(timeoutRef);
                        sendErrorAndComplete(emitter, threadId, runId, fuzzyMessage(e));
                    } catch (Exception e) {
                        logger.error("Error processing AG-UI request: {}", e.getMessage());

                        AgentContext.clean();

                        cancelQuietly(timeoutRef);
                        sendErrorAndComplete(emitter, threadId, runId, fuzzyMessage(e));
                    } finally {
                        // 在同一个线程中清理上下文
                        AgentContext.clean();
                    }
                });
        } catch (RejectedExecutionException rejected) {
            // 线程池已满：明确告诉前端「系统繁忙」，而不是让请求悬着或直接 500。
            logger.warn(
                    "AG-UI run rejected (pool saturated) thread={} run={} activeCount={}",
                    threadId,
                    runId,
                    ((ThreadPoolExecutor) executorService).getActiveCount());
            sendErrorAndComplete(emitter, threadId, runId, "系统繁忙，请稍后重试（并发已满）");
        }

        return emitter;
    }

    /**
     * 注册自愈：当路径里带了 agentCode 但注册表里没有时，尝试从库里补注册一次。
     *
     * <p>平台创建智能体后依靠 Redis pub/sub 通知 runtime 注册，消息一旦丢失，
     * 该智能体会一直 404 直到人工重启。这里做一次同步补注册，代价是一条主键查询。</p>
     */
    private void healAgentIfMissing(String pathAgentId) {
        if (pathAgentId == null
                || pathAgentId.isEmpty()
                || registry == null
                || agentMissingHandler == null) {
            return;
        }
        boolean present = true;
        try {
            present = registry.contains(pathAgentId);
        } catch (Throwable ignore) {
            // 同名类可能来自依赖包（没有 contains）：此时保守跳过，靠定时对账兜底
            present = true;
        }
        if (present) {
            return;
        }
        try {
            logger.warn("Agent {} not registered, trying self-heal", pathAgentId);
            agentMissingHandler.accept(pathAgentId);
        } catch (Exception e) {
            logger.warn("Agent self-heal failed for {}: {}", pathAgentId, e.getMessage());
        }
    }

    /**
     * run 总超时处理：中断 agent，并给前端一条明确错误。
     */
    private void onRunTimeout(String threadId, String runId) {
        if (!runTracker.getStatus(threadId)) {
            return;
        }
        logger.warn(
                "AG-UI run timed out after {} ms, thread={} run={}",
                runTimeoutMs,
                threadId,
                runId);
        try {
            runTracker.pushEvent(
                    threadId,
                    new AguiEvent.Raw(
                            threadId,
                            runId,
                            Map.of(
                                    "error",
                                    "AI 响应超时（超过 " + (runTimeoutMs / 1000) + " 秒），请重试")));
            runTracker.pushEvent(
                    threadId, new AguiEvent.RunFinished(threadId, runId));
        } catch (Exception e) {
            logger.warn("Failed to push run-timeout event: {}", e.getMessage());
        }
        try {
            runTracker.stopRun(threadId);
        } catch (Exception e) {
            logger.warn("Failed to stop timed-out run: {}", e.getMessage());
        }
        runTracker.markCompleted(threadId);
    }

    private static void cancelQuietly(AtomicReference<ScheduledFuture<?>> ref) {
        ScheduledFuture<?> future = ref == null ? null : ref.get();
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 业务侧异常转可读文案；未知异常保留原文，避免丢掉"会话已归档"这类有效提示。
     */
    private static String fuzzyMessage(Throwable e) {
        String msg = e == null ? null : e.getMessage();
        if (msg == null || msg.isBlank()) {
            return "AI 调用失败，请重试";
        }
        String lower = msg.toLowerCase();
        if (lower.contains("agent not found")) {
            return "智能体尚未就绪，正在自动修复，请 10 秒后重试";
        }
        if (lower.contains("401") || lower.contains("authentication")) {
            return "模型鉴权失败，请检查 API Key 配置";
        }
        return msg;
    }

    /**
     * 把上游异常翻译成考生能看懂的提示。
     *
     * <p>只暴露「该查哪一处配置」，不回显上游原始报文（里面可能带 key 片段）。</p>
     */
    private static String friendlyError(String reason) {
        String raw = reason == null ? "" : reason.trim();
        String lower = raw.toLowerCase();
        if (raw.isEmpty()) {
            return "AI 调用失败，请重试";
        }
        if (lower.contains("agent not found")) {
            return "智能体尚未就绪，正在自动修复，请 10 秒后重试";
        }
        if (lower.contains("401") || lower.contains("authentication") || lower.contains("invalid api key")) {
            return "模型鉴权失败，请检查 API Key 配置";
        }
        if (lower.contains("429") || lower.contains("rate limit") || lower.contains("quota")) {
            return "模型调用被限流，请稍后重试";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "AI 响应超时，请重试";
        }
        if (lower.contains("max_tokens") || lower.contains("invalidparameter")) {
            return "模型参数超出上游允许范围，请检查 max_tokens 等配置";
        }
        if (lower.contains("model")) {
            return "模型名称或模型配置不被上游接受，请检查模型配置";
        }
        return "AI 调用失败，请重试";
    }

    private void sendErrorAndComplete(
            SseEmitter emitter, String threadId, String runId, String errorMessage) {
        try {
            String errorJson =
                    encoder.encodeToJson(
                            new AguiEvent.Raw(threadId, runId, Map.of("error", errorMessage)));
            String finishJson = encoder.encodeToJson(new AguiEvent.RunFinished(threadId, runId));
            emitter.send(SseEmitter.event().data(errorJson, MediaType.APPLICATION_JSON));
            emitter.send(SseEmitter.event().data(finishJson, MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException e) {
            logger.debug("Failed to send error event: {}", e.getMessage());
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                logger.debug("Failed to complete emitter with error: {}", ex.getMessage());
            }
        }
    }

    /**
     * Get the agent ID header name.
     *
     * @return The header name
     */
    public String getAgentIdHeader() {
        return agentIdHeader;
    }

    /**
     * SSE 重连：创建新的 SseEmitter 并回放缓冲事件。
     *
     * @param threadId 会话 ID
     * @return SseEmitter
     */
    public SseEmitter reconnect(String threadId) {
        SseEmitter emitter = new SseEmitter(sseTimeout);
        emitter.onCompletion(() -> runTracker.removeEmitter(threadId, emitter));
        emitter.onTimeout(() -> runTracker.removeEmitter(threadId, emitter));
        emitter.onError(ex -> runTracker.removeEmitter(threadId, emitter));
        runTracker.reconnect(threadId, emitter);
        return emitter;
    }

    /**
     * 获取指定会话的运行状态。
     *
     * @param threadId 会话 ID
     * @return 是否正在运行
     */
    public boolean getStatus(String threadId) {
        return runTracker.getStatus(threadId);
    }

    /**
     * 强制停止指定会话的智能体。
     *
     * @param threadId 会话 ID
     */
    public void stop(String threadId) {
        runTracker.stopRun(threadId);
    }

    /**
     * 获取所有活跃运行的 threadId 列表。
     *
     * @return 活跃会话 ID 集合
     */
    public java.util.Set<String> getActiveRuns() {
        return runTracker.getActiveRuns();
    }

    /**
     * 获取 RunTracker（供外围 Bean 直接调用）。
     *
     * @return RunTracker 实例
     */
    public RunTracker getRunTracker() {
        return runTracker;
    }

    /**
     * Creates a new builder for AguiMvcController.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for AguiMvcController. */
    public static class Builder {

        private AguiAgentRegistry registry;
        private ThreadSessionManager sessionManager;
        private AguiAdapterConfig config;
        private boolean serverSideMemory = false;
        private String agentIdHeader;
        private long sseTimeout = 600000L;
        private Session session;
        private JdbcTemplate jdbcTemplate;
        private RunTracker runTracker;
        private Consumer<String> agentMissingHandler;
        private int runThreadsCore = 32;
        private int runThreadsMax = 256;
        private int runQueueCapacity = 128;
        private long runTimeoutMs = 120000L;

        /**
         * 注册自愈回调：当请求里的 agentCode 不在注册表时被调用一次。
         *
         * @param handler 入参为 agentCode
         * @return This builder
         */
        public Builder agentMissingHandler(Consumer<String> handler) {
            this.agentMissingHandler = handler;
            return this;
        }

        /**
         * SSE 执行线程池核心线程数。
         *
         * @param core 核心线程数
         * @return This builder
         */
        public Builder runThreadsCore(int core) {
            this.runThreadsCore = core;
            return this;
        }

        /**
         * SSE 执行线程池最大线程数（超过后快速失败）。
         *
         * @param max 最大线程数
         * @return This builder
         */
        public Builder runThreadsMax(int max) {
            this.runThreadsMax = max;
            return this;
        }

        /**
         * SSE 执行线程池等待队列长度。
         *
         * @param capacity 队列长度
         * @return This builder
         */
        public Builder runQueueCapacity(int capacity) {
            this.runQueueCapacity = capacity;
            return this;
        }

        /**
         * 单个 run 的总时长上限（毫秒）。0 或负数按 120000 处理。
         *
         * @param timeoutMs 超时毫秒
         * @return This builder
         */
        public Builder runTimeoutMs(long timeoutMs) {
            this.runTimeoutMs = timeoutMs;
            return this;
        }

        /**
         * Set the agent registry.
         *
         * @param registry The agent registry
         * @return This builder
         */
        public Builder agentRegistry(AguiAgentRegistry registry) {
            this.registry = registry;
            return this;
        }

        /**
         * Set the thread session manager for server-side memory support.
         *
         * @param sessionManager The session manager
         * @return This builder
         */
        public Builder sessionManager(ThreadSessionManager sessionManager) {
            this.sessionManager = sessionManager;
            return this;
        }

        /**
         * Enable or disable server-side memory management.
         *
         * @param enabled Whether to enable server-side memory
         * @return This builder
         */
        public Builder serverSideMemory(boolean enabled) {
            this.serverSideMemory = enabled;
            return this;
        }

        /**
         * Set the adapter configuration.
         *
         * @param config The adapter configuration
         * @return This builder
         */
        public Builder config(AguiAdapterConfig config) {
            this.config = config;
            return this;
        }

        /**
         * Set the HTTP header name to read agent ID from.
         *
         * @param agentIdHeader The header name (default: X-Agent-Id)
         * @return This builder
         */
        public Builder agentIdHeader(String agentIdHeader) {
            this.agentIdHeader = agentIdHeader;
            return this;
        }

        /**
         * Set the SSE timeout in milliseconds.
         *
         * @param sseTimeout The timeout value
         * @return This builder
         */
        public Builder sseTimeout(long sseTimeout) {
            this.sseTimeout = sseTimeout;
            return this;
        }

        /**
         * Set the session storage.
         *
         * @param session The session storage (InMemorySession or MysqlSession)
         * @return This builder
         */
        public Builder session(Session session) {
            this.session = session;
            return this;
        }

        /**
         * Set the jdbcTemplate storage.
         *
         * @param jdbcTemplate jdbcTemplate
         * @return This builder
         */
        public Builder jdbcTemplate(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
            return this;
        }

        /**
         * Set the RunTracker for managing active runs.
         *
         * @param runTracker The RunTracker instance
         * @return This builder
         */
        public Builder runTracker(RunTracker runTracker) {
            this.runTracker = runTracker;
            return this;
        }

        /**
         * Build the controller.
         *
         * @return The built controller
         * @throws IllegalStateException if registry is not set
         */
        public AguiMvcController build() {
            if (registry == null) {
                throw new IllegalStateException("Agent registry must be set");
            }
            return new AguiMvcController(this);
        }
    }
}
