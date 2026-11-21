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
        <view class="profile-block">
          <button class="avatar-btn" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
            <image v-if="avatarUrl" class="avatar-img" :src="avatarUrl" mode="aspectFill" />
            <text v-else class="avatar-ph">选头像</text>
          </button>
          <input v-model="nickname" class="input nick-input" type="nickname" placeholder="填写微信昵称" />
        </view>
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
import { toast, BASE_URL } from '@/utils/request'
import { getToken } from '@/utils/auth'

const authStore = useAuthStore()
const tab = ref<'wx' | 'pwd' | 'reg'>('wx')
const phone = ref('')
const password = ref('')
const nickname = ref('')
const loading = ref(false)
/** chooseAvatar 返回的微信临时头像路径（本地预览用） */
const avatarUrl = ref('')

/** 微信头像选择回调：chooseAvatar 只给临时路径，登录后上传服务器换成永久 URL */
function onChooseAvatar(e: any) {
  const url = e && e.detail && e.detail.avatarUrl
  if (url) avatarUrl.value = url
}

/** 把临时头像上传到服务器，返回可直接访问的永久 URL */
function uploadAvatar(tempPath: string): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: BASE_URL + '/api/product/upload/avatar',
      filePath: tempPath,
      name: 'file',
      header: { Authorization: 'Bearer ' + getToken() },
      success: (res) => {
        try {
          const body = JSON.parse(res.data)
          if (body && body.code === 0 && body.data && body.data.url) {
            resolve(body.data.url)
          } else {
            reject(new Error(body && body.message ? body.message : '头像上传失败'))
          }
        } catch (err) {
          reject(new Error('头像上传返回格式异常'))
        }
      },
      fail: (err) => reject(new Error(err && err.errMsg ? err.errMsg : '头像上传网络异常')),
    })
  })
}

function back() {
  const pages = getCurrentPages()
  if (pages.length > 1) uni.navigateBack()
  else uni.switchTab({ url: '/pages/index/index' })
}

async function doWxLogin() {
  loading.value = true
  try {
    // 1) 先用 code 登录（此时还没有 token，头像不能传）
    await authStore.loginByWechat({ nickname: nickname.value || undefined })

    // 2) 拿到 token 后再上传头像，写入会员档案
    if (avatarUrl.value) {
      try {
        const permanent = await uploadAvatar(avatarUrl.value)
        await authStore.updateProfile({ avatarUrl: permanent, nickname: nickname.value || undefined })
        avatarUrl.value = permanent
      } catch (e) {
        // 头像上传失败不阻断登录，仅提示
        console.error('[avatar-upload]', e)
        toast('头像上传失败，稍后可在个人资料设置')
      }
    }
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
.profile-block {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-bottom: 28rpx;
}
.avatar-btn {
  width: 120rpx;
  height: 120rpx;
  padding: 0;
  margin: 0;
  flex-shrink: 0;
  border-radius: 50%;
  overflow: hidden;
  background: #f3f4f6;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}
.avatar-btn::after {
  border: none;
}
.avatar-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
}
.avatar-ph {
  font-size: 22rpx;
  color: #9ca3af;
}
.nick-input {
  flex: 1;
  margin-bottom: 0;
}
.agreement {
  margin-top: 32rpx;
  text-align: center;
  font-size: 22rpx;
  color: #9ca3af;
}
</style>
