package com.teaclub.cloud.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 商品：分类/SPU/SKU/门店商品/差异化定价/上下架
 *
 * <p>独立数据库：{@code product_db}。
 * <p>本服务只管理自己的数据，禁止直接访问其他服务的库。
 */
@SpringBootApplication
@MapperScan("com.teaclub.cloud.product.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.teaclub.cloud.product")
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
