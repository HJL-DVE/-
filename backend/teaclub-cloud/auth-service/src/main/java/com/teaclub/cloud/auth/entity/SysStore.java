package com.teaclub.cloud.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_store")
public class SysStore {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String storeCode;
    private String storeName;
    private String province;
    private String city;
    private String district;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String phone;
    private String managerName;
    private String businessHours;
    private LocalDate openDate;
    private String status;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
