package com.hxh.apboa.console.sk;

import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.vo.SecretKeyVo;
import com.hxh.apboa.sk.service.SecretKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 描述：访问秘钥Controller
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/sk")
@RequiredArgsConstructor
public class SecretKeyController {

    private final SecretKeyService secretKeyService;

    /**
     * 查询全部秘钥列表（value已脱敏）
     */
    @GetMapping("/list")
    public R<List<SecretKeyVo>> list() {
        return R.data(secretKeyService.listAll());
    }

    /**
     * 新增秘钥
     */
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    @PostMapping
    public R<SecretKeyVo> create(@RequestBody SecretKeyVo vo) {
        return R.data(secretKeyService.create(vo));
    }

    /**
     * 更新秘钥（仅允许更新名称）
     */
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    @PutMapping
    public R<Boolean> update(@RequestBody SecretKeyVo vo) {
        return R.data(secretKeyService.updateName(vo));
    }

    /**
     * 删除秘钥
     */
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    @DeleteMapping
    public R<Boolean> delete(@RequestBody List<Long> ids) {
        return R.data(secretKeyService.delete(ids));
    }
}
