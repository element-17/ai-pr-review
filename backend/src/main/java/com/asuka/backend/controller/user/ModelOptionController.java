package com.asuka.backend.controller.user;

import com.asuka.backend.common.result.Result;
import com.asuka.backend.pojo.vo.ModelOptionVO;
import com.asuka.backend.service.ModelConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 登录用户可选择的模型。
 */
@Tag(name = "用户模型")
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
@SecurityRequirement(name = "sa-token")
public class ModelOptionController {

    private final ModelConfigService modelConfigService;

    @Operation(summary = "可用模型列表", description = "只返回管理员已启用的模型和安全字段")
    @GetMapping("/available")
    public Result<List<ModelOptionVO>> available() {
        return Result.success(modelConfigService.listEnabledOptions());
    }
}
