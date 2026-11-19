package com.teaclub.cloud.security.context;

import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;

/**
 * 请求级登录用户上下文。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static LoginUser require() {
        LoginUser u = HOLDER.get();
        if (u == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return u;
    }

    public static Long currentUserId() {
        return require().getUserId();
    }

    public static Long currentBrandId() {
        return require().getBrandId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
