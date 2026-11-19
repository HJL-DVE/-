package com.teaclub.cloud.web.annotation;

import java.lang.annotation.*;

/**
 * 标记需要记录操作日志的方法。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 所属模块 */
    String module();

    /** 操作描述 */
    String value();

    /** 是否记录请求参数 */
    boolean saveParam() default true;
}
