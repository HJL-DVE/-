package com.teaclub.cloud.payment.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置（支付服务）。
 *
 * <p><b>分页插件必须显式注册</b>：不注册时 {@code selectPage} 不会拼 LIMIT/OFFSET，
 * 也不会回填 {@code total}，接口会表现为「一次返回全表 + total=0」。
 * 本类缺失曾导致支付记录 / 退款 / 通知日志三个管理端列表分页失效。
 *
 * <p>乐观锁插件同样需要显式注册，{@code @Version} 注解本身不生效。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
