package com.teaclub.cloud.web.audit;

import com.teaclub.cloud.web.annotation.AuditLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 审计日志切面：拦截 {@link AuditLog} 标注的方法并落库。
 *
 * <p>此前项目已有 51 处 {@code @AuditLog} 标注但**没有切面**，导致操作日志从未自动产生
 * （表里只有历史单例阶段的数据）。本切面补齐这一环。
 *
 * <p><b>健壮性</b>：
 * <ul>
 *   <li>写日志失败只打 warn，绝不抛出——审计不能拖垮业务；</li>
 *   <li>服务未实现 {@link AuditLogWriter} 时降级为只打应用日志；</li>
 *   <li>记录耗时 costMs，便于发现慢操作。</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    private static final int MAX_PARAM_LEN = 2000;
    private static final int MAX_ERR_LEN = 1000;

    private final ObjectProvider<AuditLogWriter> writerProvider;

    public AuditLogAspect(ObjectProvider<AuditLogWriter> writerProvider) {
        this.writerProvider = writerProvider;
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        Throwable error = null;
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                AuditLogEntry entry = build(pjp, auditLog, error, System.currentTimeMillis() - start);
                AuditLogWriter writer = writerProvider.getIfAvailable();
                if (writer != null) {
                    writer.write(entry);
                }
                log.info("[AUDIT] {} - {} operator={} success={} cost={}ms",
                        entry.getModule(), entry.getOperation(), entry.getOperatorName(),
                        entry.getSuccess(), entry.getCostMs());
            } catch (Exception e) {
                log.warn("[AUDIT] 写日志失败 module={} op={} err={}",
                        auditLog.module(), auditLog.value(), e.getMessage());
            }
        }
    }

    private AuditLogEntry build(ProceedingJoinPoint pjp, AuditLog ann, Throwable error, long cost) {
        AuditLogEntry e = new AuditLogEntry();
        e.setModule(ann.module());
        e.setOperation(ann.value());
        e.setCostMs((int) Math.min(cost, Integer.MAX_VALUE));
        e.setSuccess(error == null ? 1 : 0);
        e.setResponseCode(error == null ? 0 : 500);
        e.setCreateTime(LocalDateTime.now());

        // common-web 不依赖 common-security（避免循环依赖），
        // 从 Gateway 转发的身份头取操作人，取不到则留空。
        HttpServletRequest req = currentRequest();
        if (req != null) {
            e.setOperatorId(parseLong(req.getHeader("X-User-Id")));
            e.setOperatorName(req.getHeader("X-Username"));
            e.setBrandId(parseLong(req.getHeader("X-Brand-Id")));
            String storeHeader = req.getHeader("X-Store-Ids");
            if (storeHeader != null && !storeHeader.isBlank()) {
                String first = storeHeader.split(",")[0].trim();
                e.setStoreId(parseLong(first));
            }
            e.setRequestUri(trunc(req.getRequestURI(), 250));
            e.setRequestMethod(req.getMethod());
            e.setIp(clientIp(req));
            e.setUserAgent(trunc(req.getHeader("User-Agent"), 250));
            if (ann.saveParam() && !"GET".equalsIgnoreCase(req.getMethod())) {
                e.setRequestParam(trunc(buildParams(pjp), MAX_PARAM_LEN));
            }
        }
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            e.setTraceId(traceId);
        }
        if (error != null) {
            e.setErrorMsg(trunc(error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage(), MAX_ERR_LEN));
        }
        return e;
    }

    private String buildParams(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        String[] names = sig.getParameterNames();
        Object[] args = pjp.getArgs();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            String name = names != null && i < names.length ? names[i] : ("arg" + i);
            sb.append(name).append('=').append(String.valueOf(args[i]));
        }
        return sb.toString();
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    private String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return trunc(ip, 60);
    }

    private Long parseLong(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String trunc(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
