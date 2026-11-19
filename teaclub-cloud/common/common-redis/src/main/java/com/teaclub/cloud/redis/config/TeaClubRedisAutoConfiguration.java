package com.teaclub.cloud.redis.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import com.teaclub.cloud.redis.cache.CacheService;
import com.teaclub.cloud.redis.cache.CaptchaService;
import com.teaclub.cloud.redis.cache.HotDataCache;
import com.teaclub.cloud.redis.cache.LoginSessionCache;
import com.teaclub.cloud.redis.lock.RedisDistributedLock;

/**
 * Redis 组件自动配置。
 *
 * <p>统一使用 String key + JSON value 序列化，
 * 避免 JDK 序列化产生的乱码与跨语言不可读问题。
 */
@AutoConfiguration
@ConditionalOnClass({ RedisTemplate.class, RedisConnectionFactory.class })
public class TeaClubRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(mapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheService cacheService(RedisTemplate<String, Object> redisTemplate) {
        return new CacheService(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public CaptchaService captchaService(CacheService cacheService) {
        return new CaptchaService(cacheService);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoginSessionCache loginSessionCache(CacheService cacheService) {
        return new LoginSessionCache(cacheService);
    }

    @Bean
    @ConditionalOnMissingBean
    public HotDataCache hotDataCache(CacheService cacheService) {
        return new HotDataCache(cacheService);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisDistributedLock redisDistributedLock(RedisTemplate<String, Object> redisTemplate) {
        return new RedisDistributedLock(redisTemplate);
    }
}
