<template>
  <view class="page">
    <view class="hero">
      <text class="hero-icon">🎂</text>
      <text class="hero-title">生日福利</text>
      <text class="hero-sub">生日当月在下方领取专属好礼</text>
    </view>

    <view v-if="loading" class="state">加载中...</view>
    <view v-else-if="error" class="state err">{{ error }}</view>

    <view v-else>
      <view v-if="benefits.length > 0" class="card">
        <view class="card-title">当前等级权益</view>
        <view v-for="b in benefits" :key="b.id || b.benefitCode" class="row">
          <text class="row-name">{{ b.benefitName }}</text>
          <text class="row-value">{{ b.benefitValue || '—' }}</text>
        </view>
      </view>

      <view class="card">
        <view class="card-title">生日专属券</view>
        <view v-if="coupons.length === 0" class="state small">
          暂无可领取的生日券（需门店配置生日券模板）
        </view>
        <view v-else class="list">
          <view v-for="c in coupons" :key="c.id" class="coupon">
            <view class="coupon-left">
              <text class="coupon-amount">{{ fen2yuan(c.faceValue || 0) }}<text class="unit">元</text></text>
              <text class="coupon-cond">{{ (c.thresholdAmount || 0) <= 0 ? '无门槛' : '满' + fen2yuan(c.thresholdAmount || 0) + '可用' }}</text>
            </view>
            <view class="coupon-mid">
              <text class="coupon-name">{{ c.couponName }}</text>
              <text class="coupon-meta">有效 {{ c.validDays || 30 }} 天</text>
            </view>
            <button class="btn-get" size="mini" @tap="receive(c)">领取</button>
          </view>
        </view>
      </view>

      <view class="card">
        <view class="card-title">温馨提示</view>
        <view class="rule">· 生日券由门店在会员生日当月定向发放</view>
        <view class="rule">· 若未展示，请联系门店工作人员</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import {
  getAvailableCoupons,
  getLevelBenefits,
  receiveCoupon,
  type CouponTemplateItem,
  type LevelBenefit,
} from '@/api/marketing'
import { getProfile } from '@/api/member'
import { toast } from '@/utils/request'
import { fen2yuan } from '@/utils/money'

const benefits = ref<LevelBenefit[]>([])
const coupons = ref<CouponTemplateItem[]>([])
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const p = (await getProfile()) as unknown as { levelId?: number | null }
    if (p && p.levelId) {
      benefits.value = await getLevelBenefits(p.levelId)
    }
    // 生日券：后端按 birthdayOnly 过滤
    coupons.value = await getAvailableCoupons({ birthdayOnly: true })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function receive(c: CouponTemplateItem) {
  try {
    await receiveCoupon(c.id, 1)
    toast('领取成功')
    await load()
  } catch (e) {
    // request 层已提示
  }
}

onShow(load)
</script>

<style scoped>
.page { min-height: 100vh; background: #f6f7f9; padding-bottom: 40rpx; }
.hero { background: linear-gradient(135deg, #db2777, #f472b6); padding: 56rpx 32rpx; color: #fff; text-align: center; }
.hero-icon { display: block; font-size: 72rpx; }
.hero-title { display: block; font-size: 40rpx; font-weight: 700; margin-top: 8rpx; }
.hero-sub { display: block; font-size: 24rpx; opacity: 0.9; margin-top: 8rpx; }
.state { text-align: center; color: #9ca3af; padding: 60rpx 0; font-size: 28rpx; }
.state.err { color: #dc2626; }
.state.small { padding: 20rpx 0; font-size: 24rpx; }
.card { background: #fff; border-radius: 16rpx; margin: 20rpx; padding: 24rpx; }
.card-title { font-size: 28rpx; font-weight: 600; margin-bottom: 12rpx; }
.row { display: flex; justify-content: space-between; padding: 12rpx 0; border-bottom: 1rpx solid #f3f4f6; }
.row-name { font-size: 26rpx; }
.row-value { font-size: 24rpx; color: #db2777; }
.coupon { display: flex; align-items: center; padding: 20rpx 0; border-bottom: 1rpx solid #f3f4f6; }
.coupon-left { width: 160rpx; text-align: center; }
.coupon-amount { font-size: 36rpx; font-weight: 700; color: #ef4444; }
.unit { font-size: 22rpx; margin-left: 2rpx; }
.coupon-cond { display: block; font-size: 20rpx; color: #9ca3af; }
.coupon-mid { flex: 1; padding: 0 16rpx; }
.coupon-name { font-size: 26rpx; font-weight: 600; }
.coupon-meta { display: block; font-size: 20rpx; color: #9ca3af; margin-top: 4rpx; }
.btn-get { background: #db2777; color: #fff; font-size: 22rpx; }
.rule { font-size: 24rpx; color: #6b7280; line-height: 1.8; }
</style>
