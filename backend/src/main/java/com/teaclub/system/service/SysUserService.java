package com.teaclub.system.service;

import com.teaclub.common.api.PageResult;
import com.teaclub.system.dto.*;
import com.teaclub.system.vo.UserVO;

import java.util.List;

public interface SysUserService {

    PageResult<UserVO> page(UserQuery query);

    UserVO detail(Long id);

    Long create(UserCreateRequest request);

    void update(Long id, UserUpdateRequest request);

    void delete(Long id);

    void resetPassword(Long id, ResetPasswordRequest request);

    void updateStatus(Long id, StatusUpdateRequest request);

    void grantStores(Long id, GrantStoresRequest request);

    List<Long> listRoleIds(Long userId);

    List<Long> listStoreIds(Long userId);
}
