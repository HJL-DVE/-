<template>
  <view class="page">
    <view class="header">
      <text class="title">优惠券中心</text>
      <text class="sub" @tap="goMine">我的优惠券 ></text>
    </view>

    <view v-if="loading" class="state">加载中...</view>
    <view v-else-if="error" class="state err">
      <text>{{ error }}</text>
      <button class="btn-retry" size="mini" @tap="load">重试</button>
    </view>
    <view v-else-if="list.length === 0" class="state">暂无可领取的优惠券</view>

    <view v-else class="list">
      <view v-for="t in list" :key="t.id" class="card">
        <view class="card-left">
          <text class="amount" v-if="t.couponType === 'DISCOUNT'">{{ discountLabel(t) }}</text>
          <text class="amount" v-else>{{ fen2yuan(t.faceValue || 0) }}<text class="unit">元</text></text>
          <text class="cond">{{ condText(t) }}</text>
        </view>
        <view class="card-mid">
          <text class="name">{{ t.couponName }}</text>
          <text class="tag">{{ t.couponTypeLabel || t.couponType }}</text>
          <text class="meta">有效期 {{ t.validDays || 30 }} 天</text>
          <text class="meta" v-if="t.remark">{{ t.remark }}</text>
        </view>
        <view class="card-right">
          <button
            class="btn-get"
            size="mini"
            :disabled="!canReceive(t)"
            @tap="receive(t)"
          >
            {{ canReceive(t) ? '领取' : '已领满' }}
          </button>
          <text class="recv" v-if="(t.receivedCount || 0) > 0">已领 {{ t.receivedCount }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getAvailableCoupons, receiveCoupon, type CouponTemplateItem } from '@/api/marketing'
import { toast } from '@/utils/request'
import { fen2yuan } from '@/utils/money'

const list = ref<CouponTemplateItem[]>([])
const loading = ref(false)
const error = ref('')

function condText(t: CouponTemplateItem): string {
  const th = t.thresholdAmount || 0
  if (th <= 0) return '无门槛'
  return '满 ' + fen2yuan(th) + ' 元可用'
}

function discountLabel(t: CouponTemplateItem): string {
  const rate = t.discountRate || 10000
  const tenth = Math.round(rate / 100) / 10
  return tenth + ' 折'
}

function canReceive(t: CouponTemplateItem): boolean {
  const limit = t.perMemberLimit || 1
  return (t.receivedCount || 0) < limit
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    list.value = await getAvailableCoupons()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function receive(t: CouponTemplateItem) {
  try {
    await receiveCoupon(t.id, 1)
    toast('领取成功')
    await load()
  } catch (e) {
    // request 层已弹提示，这里不再重复
  }
}

function goMine() {
  uni.navigateTo({ url: '/pages/coupon/mine' })
}

onShow(load)
</script>

<style scoped>
.page { min-height: 100vh; background: #f6f7f9; padding: 24rpx; }
.header { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 20rpx; }
.title { font-size: 36rpx; font-weight: 600; }
.sub { font-size: 26rpx; color: #16a34a; }
.state { text-align: center; color: #9ca3af; padding: 80rpx 0; font-size: 28rpx; }
.state.err { color: #dc2626; }
.btn-retry { margin-top: 20rpx; }
.list { display: flex; flex-direction: column; gap: 20rpx; }
.card { display: flex; background: #fff; border-radius: 16rpx; padding: 24rpx; align-items: center; }
.card-left { width: 180rpx; text-align: center; border-right: 1rpx dashed #e5e7eb; }
.amount { font-size: 40rpx; font-weight: 700; color: #ef4444; }
.unit { font-size: 24rpx; margin-left: 4rpx; }
.cond { display: block; font-size: 22rpx; color: #9ca3af; margin-top: 6rpx; }
.card-mid { flex: 1; padding: 0 20rpx; }
.name { font-size: 30rpx; font-weight: 600; }
.tag { display: inline-block; font-size: 20rpx; color: #16a34a; background: #dcfce7; border-radius: 6rpx; padding: 2rpx 10rpx; margin-left: 8rpx; }
.meta { display: block; font-size: 22rpx; color: #9ca3af; margin-top: 6rpx; }
.card-right { width: 140rpx; text-align: center; }
.btn-get { background: #16a34a; color: #fff; font-size: 24rpx; }
.recv { display: block; font-size: 20rpx; color: #9ca3af; margin-top: 6rpx; }
</style>
