package com.teaclub.cloud.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

/**
 * 后台手工建档请求（门店办卡 / 纸质会员迁移场景）。
 *
 * <p>与小程序自助注册的区别：由店员在后台录入，可指定等级与注册门店，
 * 密码为空时系统给默认初始密码，会员首次登录后可自行修改。
 */
@Data
public class MemberCreateRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    /** 会员称呼，留空时由手机号生成默认昵称 */
    private String nickname;

    /** 性别：MALE / FEMALE / UNKNOWN */
    private String gender = "UNKNOWN";

    private LocalDate birthday;

    /** 指定等级 ID；留空时用品牌默认等级 */
    private Long levelId;

    /** 注册门店；留空时用当前用户可见的第一家门店 */
    private Long registerStoreId;

    /** 初始密码，留空时使用默认密码 TeaClub@123 */
    private String password;
}
