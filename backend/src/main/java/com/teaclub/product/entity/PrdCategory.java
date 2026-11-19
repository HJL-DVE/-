package com.teaclub.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prd_category")
public class PrdCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long parentId;
    private String categoryName;
    private String iconUrl;
    private Integer sort;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
