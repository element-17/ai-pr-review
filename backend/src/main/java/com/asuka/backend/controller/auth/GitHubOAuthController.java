package com.asuka.backend.controller.auth;

import com.asuka.backend.common.context.UserContext;
import com.asuka.backend.common.result.Result;
import com.asuka.backend.pojo.dto.OAuthTicketDTO;
import com.asuka.backend.pojo.vo.GitHubBindingStatusVO;
import com.asuka.backend.pojo.vo.LoginVO;
import com.asuka.backend.pojo.vo.OAuthAuthorizeVO;
import com.asuka.backend.service.GitHubBindingService;
import com.asuka.backend.service.GitHubOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * GitHub OAuth 登录与账号绑定接口。
 *
 * <p>login/authorize、callback、exchange 是公开端点；bind/authorize、binding 和
 * unbind 需要平台登录态。callback 只返回 302 重定向，不返回 token 明文。
 */
@Tag(name = "GitHub 登录与绑定")
@RestController
@RequestMapping("/api/auth/github")
@RequiredArgsConstructor
public class GitHubOAuthController {

    private final GitHubOAuthService gitHubOAuthService;
    private final GitHubBindingService gitHubBindingService;

    @Operation(summary = "获取 GitHub 登录授权地址", description = "公开接口；前端拿到 authorizeUrl 后跳转 GitHub")
    @GetMapping("/login/authorize")
    public Result<OAuthAuthorizeVO> loginAuthorize() {
        return Result.success(gitHubOAuthService.beginLogin());
    }

    @Operation(summary = "GitHub OAuth 回调", description = "校验 state，交换 token，302 回前端；不返回 token 明文")
    @GetMapping("/callback")
    public void callback(@RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error,
                         HttpServletResponse response) throws IOException {
        response.sendRedirect(gitHubOAuthService.handleCallback(code, state, error));
    }

    @Operation(summary = "用 ticket 兑换平台双 token", description = "ticket 一次性、短有效期")
    @PostMapping("/exchange")
    public Result<LoginVO> exchange(@Valid @RequestBody OAuthTicketDTO dto) {
        return Result.success(gitHubOAuthService.exchangeTicket(dto.getTicket()));
    }

    @Operation(summary = "获取绑定 GitHub 授权地址", description = "登录用户绑定 GitHub，state 与当前用户绑定")
    @SecurityRequirement(name = "sa-token")
    @GetMapping("/bind/authorize")
    public Result<OAuthAuthorizeVO> bindAuthorize() {
        return Result.success(gitHubOAuthService.beginBind(UserContext.requireUserId()));
    }

    @Operation(summary = "读取 GitHub 绑定状态", description = "只返回绑定展示字段和状态，不返回 token")
    @SecurityRequirement(name = "sa-token")
    @GetMapping("/binding")
    public Result<GitHubBindingStatusVO> binding() {
        return Result.success(gitHubBindingService.getStatus(UserContext.requireUserId()));
    }

    @Operation(summary = "解绑 GitHub", description = "未设置密码的 GitHub 账号需先设置密码")
    @SecurityRequirement(name = "sa-token")
    @DeleteMapping("/binding")
    public Result<Void> unbind() {
        gitHubBindingService.unbind(UserContext.requireUserId());
        return Result.success();
    }
}
