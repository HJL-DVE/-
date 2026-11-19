package com.teaclub.cloud.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 报表（预留）：经营数据统计
 *
 * <p>独立数据库：{@code report_db}。
 * <p>本服务只管理自己的数据，禁止直接访问其他服务的库。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.teaclub.cloud.report")
public class ReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportApplication.class, args);
    }
}
