package com.asuka.backend.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.asuka.backend.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;

/**
 * 全局异常处理器。
 *
 * <p>统一把业务异常、参数校验异常、认证异常和未知异常转换为 {@link Result}。
 * 未知异常只在服务端日志中记录堆栈，返回给前端的内容不包含 SQL、堆栈等敏感信息。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：由业务代码主动抛出，code 和 message 都是可展示的。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex) {
        log.warn("业务异常：{}", ex.getMessage());
        return Result.error(ex.getCode(), ex.getMessage());
    }

    /**
     * @RequestBody 参数校验失败：取第一条错误信息返回，避免前端一次看到大量重复提示。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("参数校验失败");
        return Result.error(400, message);
    }

    /**
     * 查询参数绑定校验失败（GET 请求的 DTO、日期格式等）。
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("参数校验失败");
        return Result.error(400, message);
    }

    /**
     * 单个参数校验失败（@RequestParam、@PathVariable 等）。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("参数校验失败");
        return Result.error(400, message);
    }

    /**
     * 参数类型转换失败，例如分页参数传了非数字。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return Result.error(400, "参数格式错误");
    }

    /**
     * 请求体不是合法 JSON 或字段类型不匹配。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException ex) {
        return Result.error(400, "请求体格式错误");
    }

    /**
     * Sa-Token 未登录异常，统一返回 401，前端据此跳转登录页。
     */
    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLogin(NotLoginException ex) {
        return Result.error(401, "登录状态已失效，请重新登录");
    }

    /**
     * 角色或权限不足，统一返回 403。
     */
    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    public Result<Void> handleNoPermission(Exception ex) {
        return Result.error(403, "没有操作权限");
    }

    /**
     * 唯一索引冲突，通常是用户名、邮箱等重复。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException ex) {
        log.warn("唯一约束冲突：{}", ex.getMessage());
        return Result.error(400, "数据已存在，请勿重复提交");
    }

    /**
     * 静态资源不存在：favicon、浏览器扩展探测路径等。
     *
     * <p>这类请求不是业务异常，返回 404 即可，不记录 ERROR 堆栈。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("静态资源不存在：{}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "资源不存在"));
    }

    /**
     * 兜底异常：记录完整日志，只向前端返回通用提示。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("系统异常", ex);
        return Result.error(500, "系统繁忙，请稍后重试");
    }
}
