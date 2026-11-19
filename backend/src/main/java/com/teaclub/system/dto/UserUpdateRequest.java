package com.teaclub.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "编辑员工请求")
public class UserUpdateRequest {

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 32)
    private String realName;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String avatarUrl;

    @Schema(description = "角色 ID 列表")
    private java.util.List<Long> roleIds;

    @Schema(description = "门店 ID 列表")
    private java.util.List<Long> storeIds;

    private Long defaultStoreId;
}
