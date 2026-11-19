package com.teaclub.cloud.auth.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.auth.dto.ChangePasswordRequest;
import com.teaclub.cloud.auth.dto.LoginRequest;
import com.teaclub.cloud.auth.service.AuthService;
import com.teaclub.cloud.auth.vo.LoginVO;
import com.teaclub.cloud.auth.vo.MenuVO;
import com.teaclub.cloud.auth.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "认证管理", description = "登录、登出、当前用户信息、菜单")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "管理端登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request,
                                 HttpServletRequest servletRequest) {
        String ip = resolveIp(servletRequest);
        return Result.ok(authService.login(request, ip));
    }

    @Operation(summary = "当前登录用户信息")
    @GetMapping("/me")
    public Result<UserInfoVO> me() {
        return Result.ok(authService.currentUserInfo());
    }

    @Operation(summary = "当前用户菜单树")
    @GetMapping("/menus")
    public Result<List<MenuVO>> menus() {
        return Result.ok(authService.currentMenuTree());
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return Result.ok();
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        LoginUser user = UserContext.get();
        if (user != null) {
            authService.logout(token);
        }
        return Result.ok();
    }

    private String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
