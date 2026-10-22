package com.asuka.backend.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 分页查询统一返回结构。
 *
 * <p>与 MyBatis-Plus 的 Page 对象解耦，避免把 MP 的内部字段直接暴露给前端。
 *
 * @param <T> 当前页数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页结果")
public class PageResult<T> implements Serializable {

    @Schema(description = "总记录数", example = "100")
    private long total;

    @Schema(description = "当前页数据")
    private List<T> records;
}
