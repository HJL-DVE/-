package com.teaclub.common.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.teaclub.common.annotation.AuditLog;
import com.teaclub.common.api.TraceIdHolder;
import com.teaclub.common.context.LoginUser;
import com.teaclub.common.context.UserContext;
import com.teaclub.system.entity.SysOperationLog;
import com.teaclub.system.mapper.SysOperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 操作日志切面。记录关键写操作的操作人、入参、耗时与结果。
 *
 * <p>日志写入失败不影响主流程。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    /** 需要脱敏的字段名 */
    private static final Set<String> SENSITIVE = Set.of(
            "password", "oldPassword", "newPassword", "privateKey", "apiV3Key", "secret", "token");

    private final SysOperationLogMapper logMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                record(pjp, auditLog, start, error);
            } catch (Exception ex) {
                log.warn("操作日志记录失败: {}", ex.getMessage());
            }
        }
    }

    private void record(ProceedingJoinPoint pjp, AuditLog auditLog, long start, Throwable error) {
        LoginUser user = UserContext.get();
        HttpServletRequest req = currentRequest();

        SysOperationLog entity = new SysOperationLog();
        entity.setBrandId(user != null ? user.getBrandId() : 1L);
        entity.setOperatorId(user != null ? user.getUserId() : null);
        entity.setOperatorName(user != null ? user.getRealName() : "ANONYMOUS");
        entity.setModule(auditLog.module());
        entity.setOperation(auditLog.value());
        entity.setSuccess(error == null ? 1 : 0);
        entity.setErrorMsg(error == null ? null : truncate(error.getMessage(), 1000));
        entity.setCostMs((int) (System.currentTimeMillis() - start));
        entity.setTraceId(TraceIdHolder.get());

        if (req != null) {
            entity.setRequestUri(req.getRequestURI());
            entity.setRequestMethod(req.getMethod());
            entity.setIp(clientIp(req));
            entity.setUserAgent(truncate(req.getHeader("User-Agent"), 250));
            if (auditLog.saveParam()) {
                entity.setRequestParam(truncate(safeArgs(pjp), 2000));
            }
        }
        logMapper.insert(entity);
    }

    /**
     * 序列化入参并做递归脱敏。
     *
     * <p>脱敏发生在 JSON 树的任意层级：不仅顶层的 password 参数会被遮蔽，
     * 嵌套 DTO 内的 password / secret / token 等字段同样会被遮蔽，避免敏感
     * 信息随操作日志落库。
     */
    private String safeArgs(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        List<String> names = Arrays.asList(sig.getParameterNames());
        Object[] args = pjp.getArgs();
        ObjectNode root = objectMapper.createObjectNode();
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a == null || a instanceof MultipartFile || a instanceof HttpServletRequest) {
                continue;
            }
            String name = i < names.size() ? names.get(i) : ("arg" + i);
            JsonNode node = objectMapper.valueToTree(a);
            root.set(name, redact(node));
        }
        return root.toString();
    }

    /**
     * 递归遮蔽敏感字段。
     */
    private JsonNode redact(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> fieldNames = new ArrayList<>();
            obj.fieldNames().forEachRemaining(fieldNames::add);
            for (String field : fieldNames) {
                if (SENSITIVE.contains(field)) {
                    obj.put(field, "***");
                } else {
                    obj.set(field, redact(obj.get(field)));
                }
            }
            return obj;
        }
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, redact(arr.get(i)));
            }
            return arr;
        }
        return node;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}