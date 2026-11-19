package com.teaclub.cloud.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prd_store_spu")
public class PrdStoreSpu {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long storeId;
    private Long spuId;
    private Integer isOnSale;
    private Integer sort;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
