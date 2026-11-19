<template>
  <view class="page">
    <view class="head">
      <view class="logo">TeaClub</view>
      <view class="sub">登录后可查询会员权益、储值与订单</view>
    </view>

    <view class="card">
      <view class="tabs">
        <view class="tab" :class="{ active: tab === 'wx' }" @tap="tab = 'wx'">微信登录</view>
        <view class="tab" :class="{ active: tab === 'pwd' }" @tap="tab = 'pwd'">账号登录</view>
        <view class="tab" :class="{ active: tab === 'reg' }" @tap="tab = 'reg'">注册</view>
      </view>

      <view v-if="tab === 'wx'" class="pane">
        <view class="tip">点击下方按钮调用 wx.login 获取 code，由后端 /api/auth/wx-login 换取 token。</view>
        <button class="btn-primary" :loading="loading" @tap="doWxLogin">微信一键登录</button>
      </view>

      <view v-else class="pane">
        <input v-model="phone" class="input" type="number" placeholder="手机号" maxlength="11" />
        <input v-model="password" class="input" password placeholder="密码" />
        <input v-if="tab === 'reg'" v-model="nickname" class="input" placeholder="昵称（可选）" />
        <button v-if="tab === 'pwd'" class="btn-primary" :loading="loading" @tap="doPasswordLogin">登录</button>
        <button v-else class="btn-primary" :loading="loading" @tap="doRegister">注册并登录</button>
      </view>

      <view class="agreement">登录即代表同意 TeaClub 会员服务条款</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/utils/request'

const authStore = useAuthStore()
const tab = ref<'wx' | 'pwd' | 'reg'>('wx')
const phone = ref('')
const password = ref('')
const nickname = ref('')
const loading = ref(false)

function back() {
  const pages = getCurrentPages()
  if (pages.length > 1) uni.navigateBack()
  else uni.switchTab({ url: '/pages/index/index' })
}

async function doWxLogin() {
  loading.value = true
  try {
    await authStore.loginByWechat({ nickname: nickname.value || undefined })
    toast('登录成功')
    setTimeout(back, 400)
  } catch (e) {
    // request 层已 toast，这里只在控制台留痕
    console.error('[wx-login]', e)
  } finally {
    loading.value = false
  }
}

async function doPasswordLogin() {
  if (!phone.value || !password.value) {
    toast('请输入手机号与密码')
    return
  }
  loading.value = true
  try {
    await authStore.loginByPassword(phone.value, password.value)
    toast('登录成功')
    setTimeout(back, 400)
  } catch (e) {
    console.error('[member-login]', e)
  } finally {
    loading.value = false
  }
}

async function doRegister() {
  if (!phone.value || !password.value) {
    toast('请输入手机号与密码')
    return
  }
  loading.value = true
  try {
    await authStore.register(phone.value, password.value, nickname.value || undefined)
    toast('注册成功')
    setTimeout(back, 400)
  } catch (e) {
    console.error('[member-register]', e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
}
.head {
  padding: 80rpx 40rpx 40rpx;
  background: linear-gradient(135deg, #16a34a, #4ade80);
  color: #ffffff;
}
.logo {
  font-size: 48rpx;
  font-weight: 700;
}
.sub {
  margin-top: 12rpx;
  font-size: 24rpx;
  opacity: 0.9;
}
.tabs {
  display: flex;
  border-bottom: 1rpx solid #e5e7eb;
  margin-bottom: 28rpx;
}
.tab {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  font-size: 28rpx;
  color: #6b7280;
}
.tab.active {
  color: #16a34a;
  font-weight: 600;
  border-bottom: 4rpx solid #16a34a;
}
.input {
  border: 1rpx solid #e5e7eb;
  border-radius: 12rpx;
  padding: 20rpx;
  margin-bottom: 20rpx;
  font-size: 28rpx;
}
.tip {
  font-size: 24rpx;
  color: #6b7280;
  margin-bottom: 28rpx;
  line-height: 1.6;
}
.agreement {
  margin-top: 32rpx;
  text-align: center;
  font-size: 22rpx;
  color: #9ca3af;
}
</style>
