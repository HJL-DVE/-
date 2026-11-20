<template>
  <view class="page">
    <view class="hero">
      <text class="hero-label">已连续签到</text>
      <text class="hero-value">{{ continuousDays }} 天</text>
      <text class="hero-sub">累计积分 {{ totalPoints }}</text>
    </view>

    <view class="action">
      <button class="btn" :disabled="signedToday" @tap="doSignIn">
        {{ signedToday ? '今日已签到' : '立即签到' }}
      </button>
    </view>

    <view class="card">
      <view class="card-title">签到规则</view>
      <view class="rule">· 每日签到得基础积分</view>
      <view class="rule">· 连续签到有额外加成（上限 7 天）</view>
      <view class="rule">· 中断后连续天数从 1 重新计算</view>
      <view class="rule">· 同一自然日只能签到一次</view>
    </view>

    <view class="card">
      <view class="card-title">最近签到记录</view>
      <view v-if="records.length === 0" class="state small">暂无记录</view>
      <view v-for="r in records" :key="r.id" class="row">
        <text class="row-date">{{ formatTime(r.createTime) }}</text>
        <text class="row-pts">+{{ r.changePoints }}</text>
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
  type PointsTxn,
} from '@/api/marketing'
import { toast } from '@/utils/request'
import { formatTime } from '@/utils/normalize'

const continuousDays = ref(0)
const totalPoints = ref(0)
const signedToday = ref(false)
const records = ref<PointsTxn[]>([])

async function load() {
  try {
    const acc = await getPointsAccount()
    totalPoints.value = acc.totalPoints
    const list = await getPointsTransactions({ bizType: 'SIGN_IN', limit: 30 })
    records.value = list
    // 今天是否已签到：看有没有当天的 SIGN_IN 流水
    const today = new Date().toISOString().slice(0, 10)
    signedToday.value = list.some((t) => {
      const d = (t.createTime || '').slice(0, 10)
      return d === today
    })
    continuousDays.value = signedToday.value
      ? Math.max(1, countContinuous(list))
      : 0
  } catch (e) {
    // 忽略
  }
}

function countContinuous(list: PointsTxn[]): number {
  // 按日期降序统计连续天数
  const dates = list
    .map((t) => (t.createTime || '').slice(0, 10))
    .filter((d) => !!d)
    .sort()
    .reverse()
  const set = new Set(dates)
  let n = 0
  const cur = new Date()
  for (;;) {
    const key = cur.toISOString().slice(0, 10)
    if (!set.has(key)) break
    n++
    cur.setDate(cur.getDate() - 1)
  }
  return n
}

async function doSignIn() {
  try {
    const r = await signIn()
    if (r.firstTime) {
      toast('签到成功 +' + r.points + ' 积分')
    } else {
      toast(r.bonusRemark || '今日已签到')
    }
    signedToday.value = true
    await load()
  } catch (e) {
    // request 层已提示
  }
}

onShow(load)
</script>

<style scoped>
.page { min-height: 100vh; background: #f6f7f9; padding-bottom: 40rpx; }
.hero { background: linear-gradient(135deg, #16a34a, #22c55e); padding: 48rpx 32rpx; color: #fff; text-align: center; }
.hero-label { font-size: 26rpx; opacity: 0.9; }
.hero-value { display: block; font-size: 72rpx; font-weight: 700; margin: 8rpx 0; }
.hero-sub { font-size: 24rpx; opacity: 0.9; }
.action { padding: 32rpx; }
.btn { background: #16a34a; color: #fff; border-radius: 12rpx; font-size: 30rpx; }
.btn[disabled] { background: #d1d5db; color: #6b7280; }
.card { background: #fff; border-radius: 16rpx; margin: 0 20rpx 20rpx; padding: 24rpx; }
.card-title { font-size: 28rpx; font-weight: 600; margin-bottom: 12rpx; }
.rule { font-size: 24rpx; color: #6b7280; line-height: 1.8; }
.state.small { padding: 20rpx 0; font-size: 24rpx; color: #9ca3af; }
.row { display: flex; justify-content: space-between; padding: 12rpx 0; border-bottom: 1rpx solid #f3f4f6; }
.row-date { font-size: 24rpx; color: #6b7280; }
.row-pts { font-size: 24rpx; color: #16a34a; font-weight: 600; }
</style>
