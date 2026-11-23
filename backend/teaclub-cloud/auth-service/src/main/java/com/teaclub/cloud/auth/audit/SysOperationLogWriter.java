package com.teaclub.cloud.auth.audit;

import com.teaclub.cloud.auth.entity.SysOperationLog;
import com.teaclub.cloud.auth.mapper.SysOperationLogMapper;
import com.teaclub.cloud.web.audit.AuditLogEntry;
import com.teaclub.cloud.web.audit.AuditLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * auth-service 的审计日志落库实现：写入 iam_db.sys_operation_log。
 *
 * <p>这是 {@code @AuditLog} 真正产生数据的最后一环 —— 此前注解已遍布 51 处，
 * 但没有 Writer/切面，表里只有历史数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysOperationLogWriter implements AuditLogWriter {

    private final SysOperationLogMapper logMapper;

    @Override
    public void write(AuditLogEntry entry) {
        SysOperationLog e = new SysOperationLog();
        e.setBrandId(entry.getBrandId() == null ? 1L : entry.getBrandId());
        e.setOperatorId(entry.getOperatorId());
        e.setOperatorName(entry.getOperatorName());
        e.setStoreId(entry.getStoreId());
        e.setModule(entry.getModule());
        e.setOperation(entry.getOperation());
        e.setRequestUri(entry.getRequestUri());
        e.setRequestMethod(entry.getRequestMethod());
        e.setRequestParam(entry.getRequestParam());
        e.setResponseCode(entry.getResponseCode());
        e.setSuccess(entry.getSuccess());
        e.setErrorMsg(entry.getErrorMsg());
        e.setIp(entry.getIp());
        e.setUserAgent(entry.getUserAgent());
        e.setTraceId(entry.getTraceId());
        e.setCostMs(entry.getCostMs());
        e.setCreateTime(entry.getCreateTime());
        logMapper.insert(e);
    }
}
