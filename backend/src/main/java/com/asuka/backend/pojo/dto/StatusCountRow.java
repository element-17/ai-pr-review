package com.asuka.backend.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按状态分组的统计行。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusCountRow {

    private Integer status;

    private Long total;
}
