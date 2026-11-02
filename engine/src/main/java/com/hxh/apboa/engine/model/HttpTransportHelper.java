package com.hxh.apboa.engine.model;

import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.model.transport.HttpTransportConfig;
import io.agentscope.core.model.transport.JdkHttpTransport;
import io.agentscope.core.model.transport.OkHttpTransport;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 描述：HttpTransport 生成器
 *
 * <p>注意：模型实例是「每个请求构建一次」的（agent 工厂每次 getAgent 都会 new 一个 Model），
 * 如果每次调用都 new 一个 OkHttpTransport，就会连带创建一套 OkHttp dispatcher 线程池。
 * 2026-11-02 压测实测：225 并发对话后 runtime 里留下 229 个 `OkHttp api.deepseek.com`
 * 线程且 10 分钟不回收，再叠加几轮就会把线程数推爆。</p>
 *
 * <p>OkHttpClient 本身是线程安全、设计上就该复用的。这里按「超时组合」缓存 transport，
 * 让所有模型共享同一套连接池与线程池。</p>
 *
 * @author huxuehao
 **/
public class HttpTransportHelper {
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 300;

    /** key = 协议 + 超时组合 */
    private static final Map<String, HttpTransport> TRANSPORT_CACHE = new ConcurrentHashMap<>();

    public static HttpTransport createJdkHttpTransport() {
        return TRANSPORT_CACHE.computeIfAbsent(
                "jdk",
                k -> JdkHttpTransport.builder()
                        .config(getHttpTransportConfig(10, DEFAULT_READ_TIMEOUT_SECONDS))
                        .build());
    }

    public static HttpTransport createOkHttpTransport() {
        return createOkHttpTransport(10, DEFAULT_READ_TIMEOUT_SECONDS);
    }

    public static HttpTransport createOkHttpTransport(int connectTimeout, int readTimeout) {
        String key = "okhttp:" + connectTimeout + ":" + readTimeout;
        return TRANSPORT_CACHE.computeIfAbsent(
                key,
                k ->
                        OkHttpTransport.builder()
                                .config(getHttpTransportConfig(connectTimeout, readTimeout))
                                .build());
    }

    private static HttpTransportConfig getHttpTransportConfig(int connectTimeout, int readTimeout) {
        return HttpTransportConfig.builder()
                .connectTimeout(Duration.ofSeconds(connectTimeout))
                .readTimeout(Duration.ofSeconds(readTimeout))
                .ignoreSsl(true)
                .build();
    }
}
