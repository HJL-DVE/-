<template>
  <view class="page">
    <view v-if="!authStore.isLoggedIn" class="state">
      请先登录
      <button class="btn-primary retry" @tap="goLogin">去登录</button>
    </view>

    <view v-else class="card">
      <view v-if="loading" class="state">加载中…</view>
      <view v-else-if="error" class="state">
        {{ error }}
        <button class="btn-plain retry" size="mini" @tap="load">重试</button>
      </view>
      <view v-else>
        <view class="form-row">
          <text class="label">会员号</text>
          <text class="value">{{ profile.memberNo || '—' }}</text>
        </view>
        <view class="form-row">
          <text class="label">手机号</text>
          <text class="value">{{ profile.phone || '—' }}</text>
        </view>
        <view class="form-row">
          <text class="label">等级</text>
          <text class="value">{{ profile.levelName || '—' }}</text>
        </view>
        <view class="form-row">
          <text class="label">积分</text>
          <text class="value">{{ profile.points ?? '—' }}</text>
        </view>
        <view class="form-row">
          <text class="label">累计消费</text>
          <text class="value">¥{{ fen2yuan(profile.totalConsumeAmount || 0) }}</text>
        </view>

        <view class="form-row">
          <text class="label">昵称</text>
          <input v-model="form.nickname" class="input" placeholder="请输入昵称" />
        </view>
        <view class="form-row">
          <text class="label">性别</text>
          <picker mode="selector" :range="genderLabels" :value="genderIndex" @change="onGenderChange">
            <view class="picker">{{ genderLabels[genderIndex] }}</view>
          </picker>
        </view>
        <view class="form-row">
          <text class="label">头像地址</text>
          <input v-model="form.avatarUrl" class="input" placeholder="https://…" />
        </view>

        <button class="btn-primary save" :loading="saving" @tap="save">保存</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import * as memberApi from '@/api/member'
import type { MemberProfile } from '@/api/types'
import { fen2yuan } from '@/utils/money'
import { toast } from '@/utils/request'

const authStore = useAuthStore()
const profile = ref<MemberProfile>({})
const loading = ref(false)
const error = ref('')
const saving = ref(false)

const genderLabels = ['未知', '男', '女']
const form = reactive({ nickname: '', avatarUrl: '', gender: 'UNKNOWN' })

const genderIndex = computed(() => {
  const map: Record<string, number> = { UNKNOWN: 0, MALE: 1, FEMALE: 2 }
  return map[form.gender] ?? 0
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await memberApi.getProfile()
    profile.value = data || {}
    form.nickname = data?.nickname || ''
    form.avatarUrl = data?.avatarUrl || ''
    form.gender = data?.gender || 'UNKNOWN'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '资料加载失败'
  } finally {
    loading.value = false
  }
}

onShow(() => {
  if (authStore.isLoggedIn) load()
})

function onGenderChange(e: { detail: { value: number | string } }) {
  const idx = Number(e.detail.value)
  form.gender = ['UNKNOWN', 'MALE', 'FEMALE'][idx] || 'UNKNOWN'
}

async function save() {
  saving.value = true
  try {
    await memberApi.updateProfile({
      nickname: form.nickname,
      avatarUrl: form.avatarUrl,
      gender: form.gender,
    })
    toast('保存成功')
    load()
  } catch (e) {
    console.error('[member/profile PUT]', e)
  } finally {
    saving.value = false
  }
}

function goLogin() {
  uni.navigateTo({ url: '/pages/user/login' })
}
</script>

<style scoped>
.form-row {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f3f4f6;
}
.label {
  width: 180rpx;
  font-size: 26rpx;
  color: #6b7280;
}
.value {
  font-size: 28rpx;
}
.input {
  flex: 1;
  font-size: 28rpx;
  border: 1rpx solid #e5e7eb;
  border-radius: 10rpx;
  padding: 12rpx 16rpx;
}
.picker {
  font-size: 28rpx;
  border: 1rpx solid #e5e7eb;
  border-radius: 10rpx;
  padding: 12rpx 40rpx 12rpx 16rpx;
}
.save {
  margin-top: 32rpx;
}
.retry {
  margin-top: 20rpx;
}
</style>
