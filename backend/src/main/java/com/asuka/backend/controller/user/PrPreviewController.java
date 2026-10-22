package com.asuka.backend.controller.user;

import cn.dev33.satoken.annotation.SaIgnore;
import com.asuka.backend.common.result.Result;
import com.asuka.backend.dto.PrPreviewDTO;
import com.asuka.backend.service.PrPreviewService;
import com.asuka.backend.vo.PrPreviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PR 预览接口。
 *
 * <p>允许游客访问；带 Authorization 时会校验登录状态，
 * 令牌无效返回 401，不会静默降级为游客。
 */
@Tag(name = "PR 预览")
@RestController
@RequestMapping("/api/prs")
@RequiredArgsConstructor
public class PrPreviewController {

    private final PrPreviewService prPreviewService;

    @Operation(summary = "PR 预览", description = "校验 PR URL 并返回 PR 信息和可复用任务提示")
    @SaIgnore
    @PostMapping("/preview")
    public Result<PrPreviewVO> preview(@Valid @RequestBody PrPreviewDTO dto) {
        return Result.success(prPreviewService.preview(dto.getUrl()));
    }
}
