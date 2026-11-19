package com.teaclub.cloud.tools;

import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.jwt.JwtProperties;
import com.teaclub.cloud.security.jwt.JwtProvider;

import java.util.Set;

public class TokenGen {
    public static void main(String[] args) {
        JwtProperties p = new JwtProperties();
        p.setAdminSecret("ZGV2LWFkbWluLXNlY3JldC1kby1ub3QtdXNlLWluLXByb2R1Y3Rpb24tMDE=");
        p.setMemberSecret("ZGV2LW1lbWJlci1zZWNyZXQtZG8tbm90LXVzZS1pbi1wcm9kdWN0aW9uLTAy");
        p.setAccessTokenExpireSeconds(7200);
        p.setIssuer("teaclub");

        LoginUser u = LoginUser.builder()
                .userId(1L).username("admin").realName("超级管理员")
                .brandId(1L).userType("HQ")
                .roles(Set.of("SUPER_ADMIN"))
                .permissions(Set.of("store:list", "member:list"))
                .allStoreAccess(true)
                .storeIds(Set.of())
                .build();

        System.out.println(new JwtProvider(p).issueAdminToken(u));
    }
}
