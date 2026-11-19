package com.teaclub.cloud.redis.cache;

import com.teaclub.cloud.redis.constant.RedisKeys;
import lombok.RequiredArgsConstructor;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 验证码服务。
 *
 * <p>图形验证码与短信验证码统一走 Redis，带 TTL 自动过期。
 */
@RequiredArgsConstructor
public class CaptchaService {

    private final CacheService cacheService;
    private final SecureRandom random = new SecureRandom();

    private static final char[] DIGITS = "0123456789".toCharArray();

    /**
     * 生成并存储图形验证码。
     *
     * @param scene 业务场景，隔离不同用途的验证码
     * @return 验证码明文（由调用方渲染成图片）
     */
    public String generateImageCaptcha(String scene) {
        String code = randomNumericCode(4);
        String key = RedisKeys.CAPTCHA + scene + ":" + code;
        // 校验时按 code 反查 key，因此以 code 为键片段
        cacheService.set(key, code, RedisKeys.TTL_CAPTCHA);
        return code;
    }

    /**
     * 校验图形验证码。校验通过即失效（一次性使用）。
     */
    public boolean verifyImageCaptcha(String scene, String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        String key = RedisKeys.CAPTCHA + scene + ":" + input.trim();
        if (!cacheService.hasKey(key)) {
            return false;
        }
        cacheService.delete(key);
        return true;
    }

    /**
     * 发送短信验证码。
     *
     * @param phone 手机号
     * @param code  验证码
     */
    public void saveSmsCode(String phone, String code) {
        cacheService.set(RedisKeys.SMS_CODE + phone, code, RedisKeys.TTL_SMS_CODE);
    }

    /**
     * 校验短信验证码。校验通过即失效。
     */
    public boolean verifySmsCode(String phone, String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        String key = RedisKeys.SMS_CODE + phone;
        Object cached = cacheService.get(key);
        if (cached == null) {
            return false;
        }
        if (!String.valueOf(cached).equals(input.trim())) {
            return false;
        }
        cacheService.delete(key);
        return true;
    }

    private String randomNumericCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(DIGITS[random.nextInt(DIGITS.length)]);
        }
        return sb.toString();
    }
}
