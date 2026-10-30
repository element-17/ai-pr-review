package com.asuka.backend.controller.admin;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.asuka.backend.common.constant.AuthConstant;
import com.asuka.backend.common.result.Result;
import com.asuka.backend.pojo.dto.ModelConfigCreateDTO;
import com.asuka.backend.pojo.dto.ModelConfigTestDTO;
import com.asuka.backend.pojo.dto.ModelConfigUpdateDTO;
import com.asuka.backend.service.ModelConfigService;
import com.asuka.backend.pojo.vo.ModelConfigVO;
import com.asuka.backend.pojo.vo.ModelTestResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员模型配置接口。
 *
 * <p>整个 Controller 要求 ADMIN 角色；普通用户访问返回 403。
 * API Key 只写不读，列表/详情只返回掩码。
 */
@Tag(name = "管理员模型配置")
@RestController
@RequestMapping("/api/admin/model-configs")
@RequiredArgsConstructor
@Validated
@SaCheckRole(AuthConstant.ROLE_ADMIN)
@SecurityRequirement(name = "sa-token")
public class AiModelConfigController {

    private final ModelConfigService modelConfigService;

    @Operation(summary = "模型配置列表")
    @GetMapping
    public Result<List<ModelConfigVO>> list(@RequestParam(required = false) Boolean enabled) {
        return Result.success(modelConfigService.list(enabled));
    }

    @Operation(summary = "模型配置详情")
    @GetMapping("/{id}")
    public Result<ModelConfigVO> detail(@PathVariable Long id) {
        return Result.success(modelConfigService.get(id));
    }

    @Operation(summary = "新增模型配置")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ModelConfigCreateDTO dto) {
        return Result.success(modelConfigService.create(dto));
    }

    @Operation(summary = "更新模型配置", description = "apiKey 为空表示保留原 Key")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ModelConfigUpdateDTO dto) {
        modelConfigService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "启用/停用模型")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Boolean enabled) {
        modelConfigService.changeStatus(id, enabled);
        return Result.success();
    }

    @Operation(summary = "设为默认模型")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        modelConfigService.setDefault(id);
        return Result.success();
    }

    @Operation(summary = "测试未保存模型配置")
    @PostMapping("/test")
    public Result<ModelTestResultVO> testDraft(@Valid @RequestBody ModelConfigTestDTO dto) {
        return Result.success(modelConfigService.testDraft(dto));
    }

    @Operation(summary = "测试已保存模型配置")
    @PostMapping("/{id}/test")
    public Result<ModelTestResultVO> testSaved(@PathVariable Long id) {
        return Result.success(modelConfigService.testSaved(id));
    }
}
