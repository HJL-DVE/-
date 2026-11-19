package com.teaclub.cloud.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("oms_order_item")
public class OmsOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long orderId;
    private String orderNo;
    private Long storeId;
    private Long spuId;
    private Long skuId;
    private String spuName;
    private String skuName;
    private String specJson;
    private String imageUrl;
    private Long unitPrice;
    private Integer quantity;
    private Long totalAmount;
    private Long discountAmount;
    private Long actualAmount;
    private Integer refundedQuantity;
    private LocalDateTime createTime;
}
