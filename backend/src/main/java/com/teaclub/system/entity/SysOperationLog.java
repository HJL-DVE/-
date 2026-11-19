package com.teaclub.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_operation_log")
public class SysOperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long operatorId;
    private String operatorName;
    private Long storeId;
    private String module;
    private String operation;
    private String requestUri;
    private String requestMethod;
    private String requestParam;
    private Integer responseCode;
    private Integer success;
    private String errorMsg;
    private String ip;
    private String userAgent;
    private String traceId;
    private Integer costMs;
    private LocalDateTime createTime;
}
