package com.teaclub.cloud.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 储值：钱包/双余额/充值/扣款/退款/资金流水
 *
 * <p>独立数据库：{@code wallet_db}。
 * <p>本服务只管理自己的数据，禁止直接访问其他服务的库。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.teaclub.cloud.wallet.client")
@MapperScan("com.teaclub.cloud.wallet.mapper")
public class WalletApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletApplication.class, args);
    }
}
