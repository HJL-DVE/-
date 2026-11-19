package com.teaclub.cloud.core.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 参数校验工具。
 *
 * <p>在 Service 层入口做一次显式校验，
 * 避免把非法参数传到 Mapper / 外部服务后才暴露问题。
 */
public final class ValidationUtils {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    private ValidationUtils() {
    }

    /**
     * 校验对象，失败时抛出 {@link jakarta.validation.ValidationException}。
     *
     * @param target 待校验对象
     */
    public static <T> void validate(T target) {
        validate(target, ValidationGroups.Default.class);
    }

    /**
     * 按分组校验对象。
     *
     * @param target 待校验对象
     * @param groups 校验分组
     */
    public static <T> void validate(T target, Class<?>... groups) {
        if (target == null) {
            throw new jakarta.validation.ValidationException("待校验对象不能为空");
        }
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(target, groups);
        if (!violations.isEmpty()) {
            String msg = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw new jakarta.validation.ValidationException(msg);
        }
    }

    /**
     * 只返回错误信息，不抛异常。
     */
    public static <T> String validateSilently(T target, Class<?>... groups) {
        if (target == null) {
            return "待校验对象不能为空";
        }
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(target, groups);
        return violations.isEmpty() ? null : violations.stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.joining("; "));
    }

    /**
     * 获取校验器（供高级用法）。
     */
    public static Validator validator() {
        return VALIDATOR;
    }
}
