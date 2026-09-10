package com.hxh.apboa.common.enums;

/**
 * 描述：WebSocket 消息类型
 *
 * @author huxuehao
 **/
public enum WsMessageType {
    /**
     * 服务端向客户端发送客户端在线验证
     */
    PING,
    /**
     * 户端向服务端响应在连接正常的
     */
    PONG,
    /**
     * 账号
     */
    CLIENT,
    /**
     * 工作区文件变化
     */
    WORKSPACE_FILE_CHANGE,
    /**
     * 用户的角色变化（用户切换租户后通知其他页面刷新）
     */
    ACCOUNT_ROLE_CHANGE
}
