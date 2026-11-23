package com.teaclub.cloud.web.audit;

import lombok.Data;

import java.time.LocalDateTime;

/** 审计日志数据载体（与具体表结构解耦，由各服务自行映射）。 */
@Data
public class AuditLogEntry {

    private Long brandId;
    private Long operatorId;
    private String operatorName;
    private Long storeId;

    /** 业务模块，如 员工管理 */
    private String module;

    /** 操作描述，如 新增员工 */
    private String operation;

    private String requestUri;
    private String requestMethod;
    private String requestParam;

    /** 0 = 成功（与项目响应码一致），500 = 异常 */
    private Integer responseCode;

    /** 1 成功 / 0 失败 */
    private Integer success;

    private String errorMsg;
    private String ip;
    private String userAgent;
    private String traceId;

    /** 耗时毫秒 */
    private Integer costMs;

    private LocalDateTime createTime;
}
