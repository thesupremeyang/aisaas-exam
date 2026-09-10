package com.hxh.apboa.scheduler.consts;

import com.hxh.apboa.common.util.RedisKeyBuilder;

/**
 * 描述：Redis消息通道常量
 * 用于集群节点间通信的频道定义
 *
 * @author huxuehao
 **/
public class JobRedisKey {

    /**
     * 节点心跳通道
     * 用于节点状态同步
     */
    public static final String JOB_CLUSTER_HEARTBEAT = "apboa:job:cluster:heartbeat";

    /**
     * 获取任务执行锁的key
     *
     * @param jobId 任务ID
     * @return 锁key
     */
    public static String getJobLockKey(String jobId) {
        return RedisKeyBuilder.tenantKey("job:lock:" + jobId);
    }

    /**
     * 获取任务执行历史的key
     *
     * @param jobId 任务ID
     * @return 历史key
     */
    public static String getJobExecHistoryKey(String jobId) {
        return RedisKeyBuilder.tenantKey("job:exec:history:" + jobId);
    }
}
