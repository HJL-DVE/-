<template>
  <view class="page">
    <view v-if="!authStore.isLoggedIn" class="state">
      请先登录查看会员信息
      <button class="btn-primary retry" @tap="goLogin">去登录</button>
    </view>

    <block v-else>
      <view class="hero">
        <view class="hero-level">{{ profile.levelName || '会员' }}</view>
        <view class="hero-no">{{ profile.memberNo || authStore.memberNo || '' }}</view>
        <view class="hero-balance">
          <view class="balance-item">
            <view class="balance-label">总余额</view>
            <view class="balance-value">¥{{ fen2yuan(wallet.totalBalance || 0) }}</view>
          </view>
          <view class="balance-item">
            <view class="balance-label">本金</view>
            <view class="balance-value">¥{{ fen2yuan(wallet.principalBalance || 0) }}</view>
          </view>
          <view class="balance-item">
            <view class="balance-label">赠送</view>
            <view class="balance-value">¥{{ fen2yuan(wallet.giftBalance || 0) }}</view>
          </view>
        </view>
      </view>

      <view v-if="loading" class="state">加载中…</view>
      <view v-else-if="error" class="state">
        {{ error }}
        <button class="btn-plain retry" size="mini" @tap="load">重试</button>
      </view>

      <view class="card">
        <view class="row-between kv"><text class="muted">积分</text><text>{{ profile.points ?? 0 }}</text></view>
        <view class="row-between kv"><text class="muted">累计消费</text><text>¥{{ fen2yuan(profile.totalConsumeAmount || 0) }}</text></view>
        <view class="row-between kv"><text class="muted">累计充值</text><text>¥{{ fen2yuan(profile.totalRechargeAmount || 0) }}</text></view>
        <view class="row-between kv"><text class="muted">订单数</text><text>{{ profile.totalOrderCount ?? 0 }}</text></view>
        <view class="row-between kv"><text class="muted">钱包状态</text><text>{{ wallet.status || '—' }}</text></view>
      </view>

      <view class="card">
        <view class="section-title">会员等级</view>
        <view v-if="levels.length === 0" class="muted">等级接口未返回数据</view>
        <view v-for="lv in levels" :key="lv.id" class="level-line">
          <text>{{ lv.levelName || lv.levelCode }}</text>
          <text class="muted">
            <text>{{ discountLabel(lv.discountRate) }}</text>
            <text v-if="lv.upgradeThreshold"> · 升级门槛 ¥{{ fen2yuan(lv.upgradeThreshold) }}</text>
            <text v-if="lv.pointRate"> · 积分 {{ lv.pointRate / 100 }} 倍</text>
          </text>
        </view>
      </view>

      <view class="card menu">
        <view class="menu-item" @tap="nav('/pages/recharge/index')"><text>充值套餐</text><text class="arrow">›</text></view>
        <view class="menu-item" @tap="nav('/pages/member/recharge-records')"><text>充值记录</text><text class="arrow">›</text></view>
        <view class="menu-item" @tap="nav('/pages/member/transactions')"><text>消费记录</text><text class="arrow">›</text></view>
        <view class="menu-item" @tap="nav('/pages/member/points')"><text>我的积分</text><text class="arrow">›</text></view>
        <view class="menu-item" @tap="nav('/pages/member/coupons')"><text>我的优惠券</text><text class="arrow">›</text></view>
      </view>

      <view class="muted tip">余额由后端记账，页面只展示 GET /api/wallet/account 的结果。</view>
    </block>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import * as memberApi from '@/api/member'
import * as walletApi from '@/api/wallet'
import type { MemberLevel, MemberProfile, WalletAccount } from '@/api/types'
import { fen2yuan } from '@/utils/money'
import { discountLabel } from '@/utils/normalize'

const authStore = useAuthStore()
const profile = ref<MemberProfile>({})
const wallet = ref<WalletAccount>({})
const levels = ref<MemberLevel[]>([])
const loading = ref(false)
const error = ref('')

async function load() {
  if (!authStore.isLoggedIn) return
  loading.value = true
  error.value = ''
  try {
    const [p, w] = await Promise.all([memberApi.getProfile(), walletApi.getWalletAccount()])
    profile.value = p || {}
    wallet.value = w || {}
  } catch (e) {
    error.value = e instanceof Error ? e.message : '会员信息加载失败'
  }
  try {
    const lv = await memberApi.getLevels()
    levels.value = Array.isArray(lv) ? lv : []
  } catch (e) {
    console.error('[member/levels]', e)
  }
  loading.value = false
}

onShow(() => {
  if (authStore.isLoggedIn) load()
})

function nav(url: string) {
  uni.navigateTo({ url })
}
function goLogin() {
  uni.navigateTo({ url: '/pages/user/login' })
}
</script>

<style scoped>
.hero {
  background: linear-gradient(135deg, #15803d, #4ade80);
  color: #ffffff;
  padding: 44rpx 32rpx 40rpx;
}
.hero-level {
  font-size: 36rpx;
  font-weight: 700;
}
.hero-no {
  margin-top: 8rpx;
  font-size: 22rpx;
  opacity: 0.9;
}
.hero-balance {
  display: flex;
  margin-top: 28rpx;
}
.balance-item {
  flex: 1;
}
.balance-label {
  font-size: 22rpx;
  opacity: 0.9;
}
.balance-value {
  margin-top: 8rpx;
  font-size: 32rpx;
  font-weight: 700;
}
.section-title {
  font-size: 28rpx;
  font-weight: 600;
  margin-bottom: 12rpx;
}
.kv {
  padding: 12rpx 0;
  font-size: 26rpx;
}
.level-line {
  display: flex;
  justify-content: space-between;
  padding: 12rpx 0;
  font-size: 26rpx;
  border-bottom: 1rpx solid #f3f4f6;
}
.menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 26rpx 0;
  border-bottom: 1rpx solid #f3f4f6;
  font-size: 28rpx;
}
.menu-item:last-child {
  border-bottom: none;
}
.arrow {
  color: #9ca3af;
}
.tip {
  text-align: center;
  font-size: 22rpx;
  padding: 10rpx 40rpx 40rpx;
}
.retry {
  margin-top: 20rpx;
}
</style>
