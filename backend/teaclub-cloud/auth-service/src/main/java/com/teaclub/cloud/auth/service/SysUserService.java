package com.teaclub.cloud.auth.service;

import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.auth.dto.*;
import com.teaclub.cloud.auth.vo.UserVO;

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
