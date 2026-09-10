package com.hxh.apboa.console.config;

import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.console.agent.ChatSessionController;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * v2 门户租户控制器增强
 *
 * <p>在 {@link ChatSessionController} 所有方法执行前，
 * 检测 {@code X-Portal-V2: true} 请求头，若存在则将租户上下文覆写为 1。
 * 此 Advice 在 {@code AuthInterceptor} 之后执行，确保能正确覆盖 JWT 中的租户。</p>
 */
@Slf4j
@ControllerAdvice(assignableTypes = ChatSessionController.class)
public class PortalTenantAdvice {

    private static final long PORTAL_FIXED_TENANT_ID = 1L;

    @ModelAttribute
    public void overrideTenant(HttpServletRequest request) {
        String portal = request.getHeader("X-Portal-V2");
        if ("true".equalsIgnoreCase(portal)) {
            log.debug("[PortalTenantAdvice] override tenant to {} for {}",
                    PORTAL_FIXED_TENANT_ID, request.getRequestURI());
            TenantUtils.setCurrentTenant(PORTAL_FIXED_TENANT_ID, null);
        }
    }
}
