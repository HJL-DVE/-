<template>
  <view class="page">
    <view class="hero">
      <text class="hero-label">我的积分</text>
      <text class="hero-value">{{ account ? account.totalPoints : 0 }}</text>
      <view class="hero-row">
        <text class="hero-sub">累计获得 {{ account ? account.totalEarned : 0 }}</text>
        <text class="hero-sub">累计消耗 {{ account ? account.totalConsumed : 0 }}</text>
      </view>
    </view>

    <view class="actions">
      <button class="btn-primary" size="mini" @tap="doSignIn">签到得积分</button>
      <button class="btn-plain" size="mini" @tap="goMall">积分商城</button>
      <button class="btn-plain" size="mini" @tap="goCoupon">优惠券</button>
    </view>

    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.value"
        :class="['tab', { active: bizType === t.value }]"
        @tap="switchType(t.value)"
      >{{ t.label }}</view>
    </view>

    <view v-if="loading" class="state">加载中...</view>
    <view v-else-if="txns.length === 0" class="state">暂无积分记录</view>
    <view v-else class="list">
      <view v-for="t in txns" :key="t.id" class="row">
        <view class="row-left">
          <text class="row-type">{{ t.bizTypeLabel || t.bizType }}</text>
          <text class="row-meta">{{ t.remark || t.bizNo || '' }}</text>
          <text class="row-meta">{{ formatTime(t.createTime) }}</text>
        </view>
        <view class="row-right">
          <text :class="['delta', t.changePoints >= 0 ? 'plus' : 'minus']">
            {{ t.changePoints >= 0 ? '+' : '' }}{{ t.changePoints }}
          </text>
          <text class="row-balance">余 {{ t.afterPoints }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import {
  getPointsAccount,
  getPointsTransactions,
  signIn,
  type PointsAccount,
  type PointsTxn,
} from '@/api/marketing'
import { toast } from '@/utils/request'
import { formatTime } from '@/utils/normalize'

const tabs = [
  { label: '全部', value: '' },
  { label: '消费', value: 'ORDER_EARN' },
  { label: '签到', value: 'SIGN_IN' },
  { label: '活动', value: 'ADJUST' },
  { label: '兑换', value: 'DEDUCT' },
  { label: '退款冲正', value: 'ORDER_REFUND_REVERSE' },
]

const account = ref<PointsAccount | null>(null)
const txns = ref<PointsTxn[]>([])
const bizType = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    account.value = await getPointsAccount()
    txns.value = await getPointsTransactions({
      bizType: bizType.value || undefined,
      limit: 50,
    })
  } catch (e) {
    txns.value = []
  } finally {
    loading.value = false
  }
}

function switchType(v: string) {
  bizType.value = v
  load()
}

async function doSignIn() {
  try {
    const r = await signIn()
    if (r.firstTime) {
      toast('签到成功 +' + r.points + ' 积分')
    } else {
      toast(r.bonusRemark || '今日已签到')
    }
    await load()
  } catch (e) {
    // request 层已提示
  }
}

function goMall() {
  uni.navigateTo({ url: '/pages/points/goods' })
}

function goCoupon() {
  uni.navigateTo({ url: '/pages/coupon/center' })
}

onShow(load)
</script>

<style scoped>
.page { min-height: 100vh; background: #f6f7f9; padding-bottom: 40rpx; }
.hero { background: linear-gradient(135deg, #16a34a, #22c55e); padding: 48rpx 32rpx; color: #fff; }
.hero-label { font-size: 26rpx; opacity: 0.9; }
.hero-value { display: block; font-size: 72rpx; font-weight: 700; margin: 8rpx 0; }
.hero-row { display: flex; gap: 32rpx; }
.hero-sub { font-size: 24rpx; opacity: 0.9; }
.actions { display: flex; gap: 16rpx; padding: 24rpx; }
.btn-primary { background: #16a34a; color: #fff; }
.btn-plain { background: #fff; color: #16a34a; border: 1rpx solid #16a34a; }
.tabs { display: flex; background: #fff; padding: 8rpx 0; white-space: nowrap; }
.tab { display: inline-block; padding: 12rpx 24rpx; font-size: 24rpx; color: #6b7280; }
.tab.active { color: #16a34a; font-weight: 600; }
.state { text-align: center; color: #9ca3af; padding: 80rpx 0; font-size: 28rpx; }
.list { padding: 20rpx; }
.row { display: flex; justify-content: space-between; background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx; }
.row-type { font-size: 28rpx; font-weight: 600; }
.row-meta { display: block; font-size: 22rpx; color: #9ca3af; margin-top: 6rpx; }
.row-right { text-align: right; }
.delta { display: block; font-size: 32rpx; font-weight: 700; }
.plus { color: #16a34a; }
.minus { color: #ef4444; }
.row-balance { display: block; font-size: 22rpx; color: #9ca3af; margin-top: 6rpx; }
</style>
