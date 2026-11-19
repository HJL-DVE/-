package com.teaclub.cloud.auth.service;

import com.teaclub.cloud.auth.dto.ChangePasswordRequest;
import com.teaclub.cloud.auth.dto.LoginRequest;
import com.teaclub.cloud.auth.vo.LoginVO;
import com.teaclub.cloud.auth.vo.MenuVO;
import com.teaclub.cloud.auth.vo.UserInfoVO;

import java.util.List;

public interface AuthService {

    /** 账号密码登录，成功后签发 JWT。 */
    LoginVO login(LoginRequest request, String clientIp);

    /** 获取当前登录用户信息（含权限与门店范围）。 */
    UserInfoVO currentUserInfo();

    /** 获取当前用户可见菜单树。 */
    List<MenuVO> currentMenuTree();

    /** 修改当前用户密码。 */
    void changePassword(ChangePasswordRequest request);

    /** 登出（令牌吊销）。 */
    void logout(String token);
}
