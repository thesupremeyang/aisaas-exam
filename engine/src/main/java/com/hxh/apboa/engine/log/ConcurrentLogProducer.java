package com.hxh.apboa.engine.log;

import com.hxh.apboa.common.entity.ChatMessage;
import com.hxh.apboa.common.util.BeanUtils;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 描述：日志生产者
 *
 * @author huxuehao
 **/
public class ConcurrentLogProducer {
    private static final Object INIT_LOCK = new Object();
    @Getter
    private static final LinkedBlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>(50000);
    private static volatile ConcurrentLogConsumer logConsumer = null;

    public static void pushLog(ChatMessage log) {
        if (logConsumer == null) {
            synchronized (INIT_LOCK) {
                if (logConsumer == null) {
                    logConsumer = BeanUtils.getBean(ConcurrentLogConsumer.class);
                    logConsumer.tryStart();
                }
            }
        }

        log.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));

        try {
            // 尝试加入队列, 如果队列已满，则阻塞, 等待3毫秒
            queue.offer(log, 3, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {}
    }

}
