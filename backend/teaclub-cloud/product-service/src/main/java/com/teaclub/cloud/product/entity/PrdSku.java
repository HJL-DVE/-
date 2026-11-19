package com.teaclub.cloud.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prd_sku")
public class PrdSku {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long spuId;
    private String skuCode;
    private String skuName;
    private String specJson;
    private Long price;
    private Long costPrice;
    private String imageUrl;
    private Integer sort;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
