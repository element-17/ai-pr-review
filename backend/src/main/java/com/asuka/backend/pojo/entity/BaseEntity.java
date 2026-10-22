package com.asuka.backend.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体公共字段。
 *
 * <p>createTime、updateTime、createUser、updateUser 由 MyBatis-Plus
 * {@link com.asuka.backend.config.MyMetaObjectHandler} 自动填充；
 * deleted 由 MP 逻辑删除插件维护。
 */
@Data
public class BaseEntity implements Serializable {

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(value = "create_user", fill = FieldFill.INSERT)
    private Long createUser;

    @TableField(value = "update_user", fill = FieldFill.INSERT_UPDATE)
    private Long updateUser;

    /**
     * 逻辑删除标记：0 正常，1 已删除。
     * 查询时 MP 自动追加 deleted = 0，删除时自动改写为 deleted = 1。
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
