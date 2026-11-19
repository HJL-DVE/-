package com.teaclub.cloud.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 库存（预留）：原料/库存/盘点
 *
 * <p>独立数据库：{@code inventory_db}。
 * <p>本服务只管理自己的数据，禁止直接访问其他服务的库。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.teaclub.cloud.inventory")
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}
