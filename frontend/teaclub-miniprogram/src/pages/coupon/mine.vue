<template>
  <view class="page">
    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.value"
        :class="['tab', { active: status === t.value }]"
        @tap="switchTab(t.value)"
      >{{ t.label }}</view>
    </view>

    <view v-if="loading" class="state">加载中...</view>
    <view v-else-if="list.length === 0" class="state">暂无优惠券</view>

    <view v-else class="list">
      <view v-for="c in list" :key="c.id" :class="['card', (c.status || '').toLowerCase()]">
        <view class="left">
          <text class="amount" v-if="c.couponType === 'DISCOUNT'">{{ discountLabel(c) }}</text>
          <text class="amount" v-else>{{ fen2yuan(c.faceValue || 0) }}<text class="unit">元</text></text>
          <text class="cond">{{ condText(c) }}</text>
        </view>
        <view class="mid">
          <text class="name">{{ c.couponName || c.couponTypeLabel || c.couponType }}</text>
          <text class="meta">{{ formatTime(c.validStart) }} ~ {{ formatTime(c.validEnd) }}</text>
          <text class="meta" v-if="c.lockedOrderNo">已锁定订单 {{ c.lockedOrderNo }}</text>
        </view>
        <view class="right">
          <text class="status">{{ c.statusLabel || c.status }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getMyCoupons, type CouponItem } from '@/api/marketing'
import { fen2yuan } from '@/utils/money'
import { formatTime } from '@/utils/normalize'

const tabs = [
  { label: '全部', value: '' },
  { label: '未使用', value: 'UNUSED' },
  { label: '已锁定', value: 'LOCKED' },
  { label: '已使用', value: 'USED' },
  { label: '已过期', value: 'EXPIRED' },
]

const status = ref('')
const list = ref<CouponItem[]>([])
const loading = ref(false)

function condText(c: CouponItem): string {
  const th = c.thresholdAmount || 0
  return th <= 0 ? '无门槛' : '满 ' + fen2yuan(th) + ' 元可用'
}

function discountLabel(c: CouponItem): string {
  const rate = c.discountRate || 10000
  return Math.round(rate / 100) / 10 + ' 折'
}

async function load() {
  loading.value = true
  try {
    list.value = await getMyCoupons({ status: status.value || undefined, limit: 100 })
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function switchTab(v: string) {
  status.value = v
  load()
}

onShow(load)
</script>

<style scoped>
.page { min-height: 100vh; background: #f6f7f9; }
.tabs { display: flex; background: #fff; padding: 16rpx 0; }
.tab { flex: 1; text-align: center; font-size: 26rpx; color: #6b7280; padding: 12rpx 0; }
.tab.active { color: #16a34a; font-weight: 600; border-bottom: 4rpx solid #16a34a; }
.state { text-align: center; color: #9ca3af; padding: 80rpx 0; font-size: 28rpx; }
.list { padding: 20rpx; display: flex; flex-direction: column; gap: 20rpx; }
.card { display: flex; background: #fff; border-radius: 16rpx; padding: 24rpx; align-items: center; }
.card.used, .card.expired { opacity: 0.5; }
.left { width: 180rpx; text-align: center; border-right: 1rpx dashed #e5e7eb; }
.amount { font-size: 40rpx; font-weight: 700; color: #ef4444; }
.unit { font-size: 24rpx; margin-left: 4rpx; }
.cond { display: block; font-size: 22rpx; color: #9ca3af; margin-top: 6rpx; }
.mid { flex: 1; padding: 0 20rpx; }
.name { font-size: 28rpx; font-weight: 600; }
.meta { display: block; font-size: 22rpx; color: #9ca3af; margin-top: 6rpx; }
.right { width: 110rpx; text-align: center; }
.status { font-size: 24rpx; color: #16a34a; }
.card.used .status, .card.expired .status { color: #9ca3af; }
</style>
