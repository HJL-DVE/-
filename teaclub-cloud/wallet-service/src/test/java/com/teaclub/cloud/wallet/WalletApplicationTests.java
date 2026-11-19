package com.teaclub.cloud.wallet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.teaclub.cloud.wallet.mapper.WalWalletMapper;
import com.teaclub.cloud.wallet.service.WalletService;

/**
 * 上下文加载测试：验证服务可正常装配（含 Mapper 与资金服务）。
 *
 * <p>使用 `docker` profile 连真实 MySQL（wallet_db）。
 * <p><b>注意</b>：不能再排除 DataSourceAutoConfiguration ——
 * 排除后 MyBatis-Plus 拿不到 sqlSessionFactory，
 * Mapper 会报 "Property 'sqlSessionFactory' is required"。
 */
@SpringBootTest(
        classes = WalletApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.flyway.enabled=false"
        })
@ActiveProfiles("docker")
class WalletApplicationTests {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalWalletMapper walletMapper;

    @Test
    @DisplayName("上下文加载：资金服务与 Mapper 装配正常")
    void contextLoads() {
        org.assertj.core.api.Assertions.assertThat(walletService).isNotNull();
        org.assertj.core.api.Assertions.assertThat(walletMapper).isNotNull();
    }
}
