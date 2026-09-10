package com.hxh.apboa.console.hook;

import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.dto.HookConfigDTO;
import com.hxh.apboa.common.entity.HookConfig;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.mp.support.MP;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.BeanUtils;
import com.hxh.apboa.common.vo.HookConfigVO;
import com.hxh.apboa.hook.service.HookConfigService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Hook配置Controller
 *
 * @author huxuehao
 */
@RestController
@RequestMapping("/hook-config")
@RequiredArgsConstructor
public class HookConfigController {

    private final HookConfigService hookConfigService;

    /**
     * 分页查询
     */
    @GetMapping("/page")
    public R<IPage<HookConfigVO>> page(PageParams pageParams, HookConfigDTO query) {
        IPage<HookConfig> page = hookConfigService.page(MP.getPage(pageParams), MP.getQueryWrapper(query));
        return R.data(BeanUtils.copyPage(page, HookConfigVO.class));
    }

    /**
     * 详情
     */
    @GetMapping("/{id}")
    public R<HookConfigVO> detail(@PathVariable("id") Long id) {
        HookConfig entity = hookConfigService.getById(id);
        if (entity == null) {
            return R.data(null);
        }

        HookConfigVO vo = BeanUtils.copy(entity, HookConfigVO.class);
        if (vo != null) {
            vo.setUsed(hookConfigService.usedWithAgent(List.of(id)));
        }

        return R.data(vo);
    }

    /**
     * 新增
     */
    @PostMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> save(@RequestBody HookConfig entity) {
        return R.data(hookConfigService.save(entity));
    }

    /**
     * 修改
     */
    @PutMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> update(@RequestBody HookConfig entity) {
        return R.data(hookConfigService.doUpdate(entity));
    }

    /**
     * 删除
     */
    @DeleteMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> delete(@RequestBody List<Long> ids) {
        return R.data(hookConfigService.deleteByIds(ids));
    }

    /**
     * 被哪些Agent使用
     */
    @PostMapping("used-with-agent")
    public R<List<Object>> usedWithAgent(@RequestBody List<Long> ids) {
        return R.data(hookConfigService.usedWithAgent(ids));
    }
}
