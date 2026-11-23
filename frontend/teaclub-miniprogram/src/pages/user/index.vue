<template>
  <view class="page">
    <view class="head">
      <view class="avatar">
        <image v-if="authStore.avatarUrl" class="avatar-img" :src="authStore.avatarUrl" mode="aspectFill" />
        <text v-else class="avatar-text">T</text>
      </view>
      <view class="head-info">
        <view class="nick">{{ authStore.isLoggedIn ? authStore.nickname || 'TeaClub 会员' : '未登录' }}</view>
        <view class="muted-light" v-if="authStore.isLoggedIn">
          {{ authStore.memberNo || '会员号未下发' }}
          <text v-if="authStore.profile && authStore.profile.levelName"> · {{ authStore.profile.levelName }}</text>
        </view>
        <view class="muted-light" v-else>登录后查看会员储值与订单</view>
      </view>
      <view v-if="!authStore.isLoggedIn" class="login-btn" @tap="goLogin">去登录</view>
    </view>

    <!-- 钱包余额 -->
    <view class="card wallet" @tap="goMember">
      <view class="row-between">
        <view>
          <view class="muted">账户余额（后端记账）</view>
          <view class="wallet-amount">{{ walletLoading ? '—' : '¥' + fen2yuan(wallet.totalBalance || 0) }}</view>
          <view class="muted wallet-detail">
            本金 ¥{{ fen2yuan(wallet.principalBalance || 0) }} · 赠送 ¥{{ fen2yuan(wallet.giftBalance || 0) }}
          </view>
        </view>
        <view class="wallet-btn">去充值</view>
      </view>
      <view v-if="walletError" class="err">{{ walletError }}</view>
    </view>

    <view class="card menu">
      <view v-for="item in menus" :key="item.url" class="menu-item" @tap="nav(item.url)">
        <text>{{ item.label }}</text>
        <text class="arrow">›</text>
      </view>
    </view>

    <view v-if="authStore.isLoggedIn" class="card">
      <button class="btn-plain" @tap="logout">退出登录</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import * as walletApi from '@/api/wallet'
import type { WalletAccount } from '@/api/types'
import { fen2yuan } from '@/utils/money'

const authStore = useAuthStore()
const wallet = ref<WalletAccount>({})
const walletLoading = ref(false)
const walletError = ref('')

const menus = [
  { label: '会员中心', url: '/pages/member/index' },
  { label: '我的订单', url: '/pages/order/list' },
  { label: '充值套餐', url: '/pages/recharge/index' },
  { label: '充值记录', url: '/pages/member/recharge-records' },
  { label: '消费记录', url: '/pages/member/transactions' },
  { label: '我的积分', url: '/pages/member/points' },
  { label: '我的优惠券', url: '/pages/member/coupons' },
  { label: '营销活动', url: '/pages/activity/list' },
  { label: '退卡申请', url: '/pages/member/card-refund' },
  { label: '门店列表', url: '/pages/store/list' },
  { label: '个人资料', url: '/pages/user/profile' },
]

async function loadAll() {
  if (!authStore.isLoggedIn) {
    wallet.value = {}
    return
  }
  try {
    await authStore.fetchProfile()
  } catch (e) {
    console.error('[auth/member/me]', e)
  }
  walletLoading.value = true
  walletError.value = ''
  try {
    wallet.value = (await walletApi.getWalletAccount()) || {}
  } catch (e) {
    walletError.value = e instanceof Error ? e.message : '钱包加载失败'
  } finally {
    walletLoading.value = false
  }
}

onShow(() => {
  loadAll()
})

// tabBar 页面必须用 switchTab，用 navigateTo 会静默失败（点了没反应）
const TAB_BAR_PAGES = [
  '/pages/index/index',
  '/pages/store/list',
  '/pages/order/list',
  '/pages/user/index',
]

function nav(url: string) {
  if (TAB_BAR_PAGES.includes(url)) {
    uni.switchTab({ url })
    return
  }
  if (!authStore.isLoggedIn && url !== '/pages/store/list') {
    uni.navigateTo({ url: '/pages/user/login' })
    return
  }
  uni.navigateTo({ url })
}

function goLogin() {
  uni.navigateTo({ url: '/pages/user/login' })
}

function goMember() {
  uni.navigateTo({ url: authStore.isLoggedIn ? '/pages/recharge/index' : '/pages/user/login' })
}

function logout() {
  authStore.logout()
  wallet.value = {}
  uni.showToast({ title: '已退出登录', icon: 'none' })
}
</script>

<style scoped>
.page {
  padding-bottom: 40rpx;
}
.head {
  display: flex;
  align-items: center;
  background: linear-gradient(135deg, #16a34a, #4ade80);
  padding: 56rpx 32rpx 48rpx;
  color: #ffffff;
}
.avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar-img {
  width: 112rpx;
  height: 112rpx;
}
.avatar-text {
  font-size: 44rpx;
  font-weight: 700;
}
.head-info {
  flex: 1;
  margin-left: 24rpx;
}
.nick {
  font-size: 34rpx;
  font-weight: 600;
}
.muted-light {
  margin-top: 8rpx;
  font-size: 24rpx;
  opacity: 0.9;
}
.login-btn {
  font-size: 26rpx;
  border: 1rpx solid #ffffff;
  border-radius: 30rpx;
  padding: 8rpx 24rpx;
}
.wallet {
  margin-top: -24rpx;
}
.wallet-amount {
  font-size: 48rpx;
  font-weight: 700;
  color: #dc2626;
  margin: 8rpx 0;
}
.wallet-detail {
  font-size: 22rpx;
}
.wallet-btn {
  background: #f59e0b;
  color: #ffffff;
  font-size: 26rpx;
  padding: 12rpx 28rpx;
  border-radius: 30rpx;
}
.err {
  margin-top: 12rpx;
  color: #dc2626;
  font-size: 22rpx;
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
</style>
