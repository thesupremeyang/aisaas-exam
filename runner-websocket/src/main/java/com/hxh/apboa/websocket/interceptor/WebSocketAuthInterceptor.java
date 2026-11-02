package com.hxh.apboa.websocket.interceptor;

import com.hxh.apboa.common.UserDetail;
import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.util.RedisKeyBuilder;
import com.hxh.apboa.common.util.RedisUtils;
import com.hxh.apboa.common.util.TokenUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 描述：WebSocket 握手拦截器 - 实现连接认证
 *
 * @author huxuehao
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {
    private final RedisUtils redisUtils;
    /**
     * 握手前执行
     *
     * @param request    HTTP 请求
     * @param response   HTTP 响应
     * @param wsHandler  WebSocket 处理器
     * @param attributes 属性集合（可用于传递到 WebSocketSession）
     * @return true 允许握手，false 拒绝握手
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        if (request instanceof ServletServerHttpRequest) {
            try {
                // 验证 token
                String token = TokenUtils.getToken();
                if (token == null || token.isEmpty()) {
                    return reject(response);
                }
                Claims claims = TokenUtils.parseAndValidateToken(token);
                if (claims == null
                        || redisUtils.get(RedisKeyBuilder.globalKey("login:" + token)) == null) {
                    return reject(response);
                }

                // 将用户信息存入 attributes，会在 WebSocketSession 建立后传递
                attributes.put(SysConst.USER_DETAIL, JsonUtils.parse(claims.getSubject(), UserDetail.class));
                return true;
            } catch (Exception e) {
                // 握手鉴权失败是常态（token 过期/未登录），不应打 ERROR 刷屏：
                // 9/5 事故里 45 个浏览器各自重连，一次故障就是几十万行日志。
                log.debug("WebSocket handshake auth failed: {}", e.getMessage());
                return reject(response);
            }
        }

        return false;
    }

    /**
     * 拒绝握手并返回 401。
     *
     * <p>原先直接抛 RuntimeException，Spring 兜底成 500 + 堆栈，
     * 前端拿不到「未登录」的语义，日志也会被重连风暴刷爆。</p>
     */
    private boolean reject(ServerHttpResponse response) {
        try {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
        } catch (Exception ignored) {
            // 响应已提交时忽略
        }
        return false;
    }

    /**
     * 握手后执行
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手后可以做一些日志记录或其他操作
        if (exception != null) {
            log.error("WebSocket 握手异常：{}", exception.getMessage(), exception);
        }
    }
}
