package com.asuka.backend.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 版本对比中的新增、仍存在和已消失问题。
 */
@Data
@Schema(description = "版本对比问题集合")
public class CompareIssuesVO {

    private List<CompareIssueVO> added;

    private List<CompareIssueVO> remaining;

    private List<CompareIssueVO> resolved;
}
