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
          <text class="label">生日</text>
          <picker
            mode="date"
            :value="form.birthday"
            :start="'1920-01-01'"
            :end="today"
            :disabled="birthdayLocked"
            @change="onBirthdayChange"
          >
            <view class="picker" :class="{ locked: birthdayLocked }">
              {{ form.birthday || '请选择生日（填写后可享生日福利）' }}
            </view>
          </picker>
          <text v-if="birthdayLocked" class="lock-tip">已设置，不可修改</text>
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

const today = new Date().toISOString().slice(0, 10)
// 生日一旦设置后不可再改（防止反复修改薅生日福利），后端也会二次校验
const birthdayLocked = computed(() => !!profile.value?.birthday)
const form = reactive({ nickname: '', birthday: '' })

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await memberApi.getProfile()
    profile.value = data || {}
    form.nickname = data?.nickname || ''
    form.birthday = data?.birthday || ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : '资料加载失败'
  } finally {
    loading.value = false
  }
}

onShow(() => {
  if (authStore.isLoggedIn) load()
})

function onBirthdayChange(e: { detail: { value: string } }) {
  form.birthday = e.detail.value || ''
}

async function save() {
  saving.value = true
  try {
    await memberApi.updateProfile({
      nickname: form.nickname,
      birthday: form.birthday || undefined,
    })
    toast(form.birthday && !birthdayLocked.value ? '保存成功，生日福利将在生日当月自动发放' : '保存成功')
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
.lock-tip {
  margin-left: 12rpx;
  font-size: 20rpx;
  color: #9ca3af;
}
.picker.locked {
  color: #9ca3af;
  background: #f9fafb;
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
