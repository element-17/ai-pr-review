package com.asuka.backend.mapper;

import com.asuka.backend.pojo.entity.UserSetting;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户设置 Mapper：单表 CRUD 由 MyBatis-Plus 完成。
 */
@Mapper
public interface UserSettingMapper extends BaseMapper<UserSetting> {
}
