package com.teaclub.cloud.member;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 会员：档案/注册/微信绑定/资料/标签/等级/状态
 *
 * <p>独立数据库：{@code member_db}。
 * <p>本服务只管理自己的数据，禁止直接访问其他服务的库。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.teaclub.cloud.member")
public class MemberApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemberApplication.class, args);
    }
}
