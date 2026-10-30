package com.asuka.backend.mapper;

import com.asuka.backend.pojo.dto.UserQueryDTO;
import com.asuka.backend.pojo.entity.User;
import com.asuka.backend.pojo.vo.RoleCountVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户 Mapper。
 *
 * <p>单表增删改查直接继承 BaseMapper；
 * 需要多条件模糊搜索、聚合统计等场景使用 XML 原生 SQL。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 登录专用查询：显式带出 password 字段。
     * 普通 MP 查询因为实体上 @TableField(select = false) 不会返回密码。
     */
    User selectByUsernameForLogin(@Param("username") String username);

    /**
     * 按 ID 查询登录所需字段，显式带出 password。
     */
    User selectByIdForLogin(@Param("id") Long id);

    /**
     * 复杂条件分页查询：关键字同时匹配用户名、昵称、邮箱。
     * 使用 XML + MP 分页插件，SQL 可控且可读。
     */
    IPage<User> selectUserPage(Page<User> page, @Param("query") UserQueryDTO query);

    /**
     * 按角色聚合统计：单表 GROUP BY，使用原生 SQL 更直观。
     */
    List<RoleCountVO> countByRole();
}
