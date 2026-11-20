package com.teaclub.cloud.marketing.service;

import com.teaclub.cloud.marketing.dto.ActivitySaveRequest;
import com.teaclub.cloud.marketing.vo.ActivityVO;

import java.util.List;

/**
 * 营销活动服务。
 *
 * <h3>8 类活动的触发时机</h3>
 * <table>
 *   <tr><th>类型</th><th>触发点</th></tr>
 *   <tr><td>NEW_MEMBER_GIFT</td><td>会员注册/首次登录后调用 {@link #triggerNewMember}</td></tr>
 *   <tr><td>BIRTHDAY</td><td>会员生日当天调用 {@link #triggerBirthday}</td></tr>
 *   <tr><td>FESTIVAL</td><td>时间窗口内会员主动领取 {@link #join}</td></tr>
 *   <tr><td>CONTINUOUS_SIGN</td><td>签到后调用 {@link #triggerContinuousSign}</td></tr>
 *   <tr><td>FULL_GIFT</td><td>ORDER_PAID 事件调用 {@link #triggerOrderActivity}</td></tr>
 *   <tr><td>POINTS_MALL</td><td>长期开放，走积分商城兑换</td></tr>
 *   <tr><td>STORE_SPECIAL</td><td>ORDER_PAID 事件 + 门店匹配</td></tr>
 *   <tr><td>SLEEPING_RECALL</td><td>定时任务扫描后调用 {@link #triggerSleepingRecall}</td></tr>
 * </table>
 *
 * <h3>幂等</h3>
 * 每次发放都会写 {@code mkt_activity_record}，
 * 唯一键 {@code uk_activity_member_biz(activity_id, member_id, biz_no)} 保证
 * 同一活动同一会员同一业务单只发一次 —— 消息重放不会重复发奖。
 */
public interface ActivityService {

    // ===== 管理端 =====

    Long create(ActivitySaveRequest request);

    void update(Long id, ActivitySaveRequest request);

    void delete(Long id);

    void changeStatus(Long id, String status);

    ActivityVO detail(Long id);

    /** 管理端列表（不限状态）。 */
    List<ActivityVO> listAll(String activityType, String status);

    // ===== 会员端 =====

    /** 当前会员可见的进行中活动。 */
    List<ActivityVO> listActiveForMember(Long memberId, Long storeId);

    /** 会员主动参与活动（节日活动等）。 */
    boolean join(Long activityId, Long memberId, Long storeId);

    // ===== 业务触发 =====

    /** 新人礼包：会员首次注册/登录后触发。 */
    int triggerNewMember(Long brandId, Long memberId);

    /** 生日福利：会员生日当天触发。 */
    int triggerBirthday(Long brandId, Long memberId);

    /** 连续签到奖励：签到成功后触发。 */
    int triggerContinuousSign(Long brandId, Long memberId, int continuousDays);

    /** 满赠 / 门店活动：订单支付成功后触发。 */
    int triggerOrderActivity(Long brandId, Long memberId, Long storeId,
                            String orderNo, long orderAmount);

    /** 沉睡召回：定时任务扫描后触发。 */
    int triggerSleepingRecall(Long brandId, Long memberId);

    /** 把已过期的活动置为 EXPIRED，返回处理条数。 */
    int expireBatch(int limit);
}
