package com.teaclub.cloud.wallet.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p><b>乐观锁插件必须显式注册</b>：`@Version` 注解本身不会生效，
 * 需要 `OptimisticLockerInnerInterceptor` 才能在 UPDATE 时带上 version 条件。
 *
 * <p>注意：资金写入的主并发控制是数据库行锁（SELECT ... FOR UPDATE），
 * 乐观锁在此作为最后一道防线。
 *
 * <p>分页插件在 MyBatis-Plus 3.5.9+ 已拆到独立的 jsqlparser 模块，
 * 此处不引入，避免与核心插件产生版本耦合；分页查询用 MyBatis-Plus 的
 * `Page` 参数时由业务模块按需引入。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
