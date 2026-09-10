package com.hxh.apboa.console.v2portal.clone;

import com.hxh.apboa.console.v2portal.clone.AgentCloneRequestDTO;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.vo.AgentCloneResponseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 广场克隆 agent 接口
 * <p>
 * 路径：{@code POST /api/v2-portal/clone/agent}
 * <p>
 * 由 {@code Explore} 广场点击"复制到我的空间"触发。
 * 当前用户必须是某个租户的成员；接口把源 agent_definition
 * 复制到 caller 租户下，返回新 agent 的关键信息。
 *
 * @author agent-clone-feature
 */
@RestController
@RequestMapping("/v2-portal/clone")
@RequiredArgsConstructor
public class AgentCloneController {

    private final AgentCloneService cloneService;

    /**
     * 克隆源 agent 到当前用户所在租户
     */
    @PostMapping("/agent")
    public R<AgentCloneResponseVO> cloneAgent(@RequestBody AgentCloneRequestDTO req) {
        Long callerTenantId = TenantUtils.getCurrentTenantId();
        return R.data(cloneService.cloneAgent(req, callerTenantId));
    }
}
