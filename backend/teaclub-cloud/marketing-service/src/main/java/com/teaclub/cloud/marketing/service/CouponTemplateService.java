package com.teaclub.cloud.marketing.service;

import com.teaclub.cloud.marketing.dto.CouponTemplateSaveRequest;
import com.teaclub.cloud.marketing.vo.CouponTemplateVO;

/**
 * 优惠券模板管理。
 *
 * <p>模板是「规则」，会员领取后生成 {@code mkt_coupon} 实例。
 * 已发行的模板<b>不允许修改面额/门槛/有效期</b>，
 * 否则会导致同模板下的券实际权益不一致（历史数据无法追溯）。
 */
public interface CouponTemplateService {

    Long create(CouponTemplateSaveRequest request);

    /** 更新模板。已发行过的模板只允许改名称、发行量、状态、说明。 */
    void update(Long id, CouponTemplateSaveRequest request);

    /** 逻辑删除。已发券的模板不允许删除。 */
    void delete(Long id);

    /** 启用 / 停用。 */
    void changeStatus(Long id, String status);

    CouponTemplateVO detail(Long id);
}
