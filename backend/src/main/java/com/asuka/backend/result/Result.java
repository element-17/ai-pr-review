package com.asuka.backend.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 后端统一返回结果。
 *
 * <p>所有 Controller 统一返回该结构，前端只需要判断 code 即可：
 * <ul>
 *     <li>200：成功</li>
 *     <li>400：参数错误</li>
 *     <li>401：未登录或 token 失效</li>
 *     <li>403：无权限</li>
 *     <li>500：服务端异常</li>
 * </ul>
 *
 * @param <T> 业务数据类型
 */
@Data
@Schema(description = "统一响应结果")
public class Result<T> implements Serializable {

    @Schema(description = "状态码，200 表示成功", example = "200")
    private Integer code;

    @Schema(description = "提示信息", example = "操作成功")
    private String message;

    @Schema(description = "业务数据")
    private T data;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        return success("操作成功", data);
    }

    public static <T> Result<T> success(String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(String message) {
        return error(500, message);
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
