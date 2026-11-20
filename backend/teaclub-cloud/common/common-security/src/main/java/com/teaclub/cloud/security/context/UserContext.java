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

    /**
     * 当前品牌 ID；无上下文时返回 null（不抛异常）。
     *
     * <p>用于事件消费等<b>没有 HTTP 请求上下文</b>的场景：
     * 那里没有网关透传的品牌头，只能读事件载荷或回落到默认值。
     */
    public static Long currentBrandIdOrNull() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getBrandId();
    }

    /** 当前会员 ID；管理端请求返回 null（不抛异常，便于共用代码判断）。 */
    public static Long currentMemberIdOrNull() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getMemberId();
    }

    /** 当前会员 ID；会员端接口必须调用此方法，缺失即 401。 */
    public static Long requireMemberId() {
        Long memberId = currentMemberIdOrNull();
        if (memberId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return memberId;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
