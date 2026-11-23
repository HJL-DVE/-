package com.teaclub.cloud.web.audit;

/**
 * 审计日志落库回调。
 *
 * <p>各服务实现本接口把自己的操作日志写到自己的库表：
 * <ul>
 *   <li>auth-service → sys_operation_log（iam_db）</li>
 *   <li>其它服务若无日志表，可不实现（切面自动跳过，只打应用日志）</li>
 * </ul>
 *
 * <p>设计原因：common-web 无法知道各服务的表结构，用回调解耦。
 */
public interface AuditLogWriter {

    /**
     * 记录一条操作日志。
     *
     * @param entry 已填充好的日志数据（模块/操作/操作人/结果/耗时等）
     */
    void write(AuditLogEntry entry);
}
