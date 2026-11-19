package com.teaclub.cloud.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "新增员工请求")
public class UserCreateRequest {

    @NotBlank(message = "登录账号不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{3,31}$",
            message = "账号需以字母开头，由 4-32 位字母、数字或下划线组成")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 之间")
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 32)
    private String realName;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "账号类型不能为空")
    @Pattern(regexp = "^(HQ|STORE)$", message = "账号类型只能是 HQ 或 STORE")
    private String userType;

    @Schema(description = "角色 ID 列表")
    private List<Long> roleIds;

    @Schema(description = "门店授权：仅 userType=STORE 时有效")
    private List<Long> storeIds;

    @Schema(description = "默认门店 ID")
    private Long defaultStoreId;
}
