package com.teaclub.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prd_spu")
public class PrdSpu {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long categoryId;
    private String spuCode;
    private String spuName;
    private String subtitle;
    private String description;
    private String mainImage;
    private String imagesJson;
    private Long basePrice;
    private Long minPrice;
    private String unit;
    private Integer hasSku;
    private Integer sort;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
