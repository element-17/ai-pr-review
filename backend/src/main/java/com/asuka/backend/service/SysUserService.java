package com.asuka.backend.service;

import com.asuka.backend.result.PageResult;
import com.asuka.backend.dto.UserCreateDTO;
import com.asuka.backend.dto.UserQueryDTO;
import com.asuka.backend.dto.UserUpdateDTO;
import com.asuka.backend.entity.SysUser;
import com.asuka.backend.vo.RoleCountVO;
import com.asuka.backend.vo.UserVO;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * 用户服务。
 *
 * <p>继承 IService 后，单表 CRUD 可以直接使用 save、updateById、
 * removeById、getById、page 等 MyBatis-Plus 方法。
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * MyBatis-Plus 分页：适合常规单表条件查询。
     */
    PageResult<UserVO> page(UserQueryDTO query);

    /**
     * 原生 XML 分页：适合多字段模糊搜索等复杂 SQL。
     */
    PageResult<UserVO> nativePage(UserQueryDTO query);

    UserVO getDetail(Long id);

    Long createUser(UserCreateDTO dto);

    void updateUser(Long id, UserUpdateDTO dto);

    void deleteUser(Long id);

    void changeStatus(Long id, Integer status);

    List<RoleCountVO> roleStats();
}
