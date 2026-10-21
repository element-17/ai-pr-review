package com.asuka.backend.config;

import com.asuka.backend.common.context.UserContext;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 公共字段自动填充。
 *
 * <p>实体继承 BaseEntity 后，createTime、updateTime、createUser、updateUser
 * 会在 insert / update 时自动写入，业务代码不需要手动 set。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();

        // strictInsertFill 只有在字段为空时才填充，避免覆盖业务显式设置的值
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);

        Long userId = UserContext.getUserId();
        if (userId != null) {
            this.strictInsertFill(metaObject, "createUser", Long.class, userId);
            this.strictInsertFill(metaObject, "updateUser", Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());

        Long userId = UserContext.getUserId();
        if (userId != null) {
            this.strictUpdateFill(metaObject, "updateUser", Long.class, userId);
        }
    }
}
