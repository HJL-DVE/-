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

    /** 是否旗舰店：0 否 1 是（品牌内唯一，作为后台默认门店） */
    private Integer isFlagship;

    /** 门店排序号，越小越靠前；旗舰店恒排最前 */
    private Integer storeSort;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
