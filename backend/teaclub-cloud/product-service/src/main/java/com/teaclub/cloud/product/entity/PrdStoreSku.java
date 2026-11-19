package com.teaclub.cloud.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prd_store_sku")
public class PrdStoreSku {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long storeId;
    private Long skuId;
    private Long storePrice;
    private Integer isOnSale;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
