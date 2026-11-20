package com.teaclub.cloud.marketing.service;

import com.teaclub.cloud.marketing.entity.MktLevelBenefit;

import java.util.List;
import java.util.Map;

/**
 * 会员等级营销权益。
 *
 * <p><b>等级定义不在这里</b>：等级名称、升级门槛、折扣率仍在 member-service
 * （{@code mem_member_level}），本服务只维护「营销侧权益」：
 * 生日券、积分倍率、专属券等。
 *
 * <p>这样避免两个服务同时管一份等级数据导致对不上。
 */
public interface LevelBenefitService {

    /** 某等级的权益列表。 */
    List<MktLevelBenefit> listByLevel(Long levelId);

    /** 全品牌权益配置（按等级分组）。 */
    Map<Long, List<MktLevelBenefit>> listAll();

    /** 新增或更新一条权益（同等级同编码唯一）。 */
    Long save(MktLevelBenefit benefit);

    void delete(Long id);

    /**
     * 取某等级的积分倍率。
     *
     * @return 倍率 ×100（100 = 1 倍，150 = 1.5 倍）；无配置时返回 100
     */
    int pointRateOf(Long brandId, Long levelId);

    /** 取某等级配置的生日券模板 ID 列表。 */
    List<Long> birthdayCouponTemplates(Long brandId, Long levelId);
}
