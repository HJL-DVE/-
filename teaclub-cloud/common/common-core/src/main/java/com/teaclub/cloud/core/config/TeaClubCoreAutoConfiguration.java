package com.teaclub.cloud.core.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * common-core 自动配置。
 *
 * <p>注册 MyBatis-Plus 自动填充处理器（createTime / updateTime）。
 */
@AutoConfiguration
@ConditionalOnClass(MetaObjectHandler.class)
public class TeaClubCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MetaObjectHandler myMetaObjectHandler() {
        return new MyMetaObjectHandler();
    }
}
