package com.teaclub.cloud.marketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 营销（预留）：优惠券/积分/会员权益/活动
 *
 * <p>独立数据库：{@code marketing_db}。
 * <p>本服务只管理自己的数据，禁止直接访问其他服务的库。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.teaclub.cloud.marketing")
public class MarketingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketingApplication.class, args);
    }
}
