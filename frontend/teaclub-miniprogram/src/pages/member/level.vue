<template>
  <view class="page">
    <view class="hero" v-if="profile">
      <text class="hero-label">当前等级</text>
      <text class="hero-value">{{ profile.levelName || "普通会员" }}</text>
      <text class="hero-sub" v-if="profile.totalConsumeAmount">
        累计消费 {{ fen2yuan(profile.totalConsumeAmount) }} 元
      </text>
    </view>

    <view v-if="loading" class="state">加载中...</view>
    <view v-else-if="error" class="state err">{{ error }}</view>

    <view v-else>
      <view class="section-title">等级权益</view>
      <view v-if="benefits.length === 0" class="state small">当前等级暂无专属权益</view>
      <view v-else class="list">
        <view v-for="b in benefits" :key="b.id || b.benefitCode" class="row">
          <text class="row-name">{{ b.benefitName }}</text>
          <text class="row-value">{{ benefitText(b) }}</text>
        </view>
      </view>

      <view class="section-title">全部等级</view>
      <view class="list">
        <view v-for="l in levels" :key="l.id" :class="['row', { current: l.id === currentLevelId }]">
          <text class="row-name">
            {{ l.levelName }}
            <text v-if="l.id === currentLevelId" class="badge">当前</text>
          </text>
          <text class="row-value">消费满 {{ fen2yuan(l.upgradeThreshold || 0) }} 元</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { getLevelBenefits, type LevelBenefit } from '@/api/marketing'
import { getProfile, getLevels } from '@/api/member'
import { fen2yuan } from '@/utils/money'

interface MemberLevelLite {
  id: number
  levelName: string
  upgradeThreshold?: number | null
}

interface ProfileLite {
  levelId?: number | null
  levelName?: string | null
  totalConsumeAmount?: number | null
}

const profile = ref<ProfileLite | null>(null)
const levels = ref<MemberLevelLite[]>([])
const benefits = ref<LevelBenefit[]>([])
const loading = ref(false)
const error = ref('')

const currentLevelId = computed(() => (profile.value ? profile.value.levelId : undefined))

function benefitText(b: LevelBenefit): string {
  const v = b.benefitValue || ''
  if (b.benefitCode === 'POINT_RATE') {
    const n = Number(v)
    return n > 0 ? n / 100 + ' 倍积分' : '—'
  }
  if (b.benefitCode === 'BIRTHDAY_POINTS') return v + ' 积分'
  if (b.benefitCode === 'BIRTHDAY_COUPON') return '生日专属券'
  if (b.benefitCode === 'EXCLUSIVE_COUPON') return '专属券'
  return v || '—'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    profile.value = (await getProfile()) as unknown as ProfileLite
    levels.value = (await getLevels()) as unknown as MemberLevelLite[]
    if (profile.value && profile.value.levelId) {
      benefits.value = await getLevelBenefits(profile.value.levelId)
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onShow(load)
</script>

<style scoped>
.page { min-height: 100vh; background: #f6f7f9; padding-bottom: 40rpx; }
.hero { background: linear-gradient(135deg, #d97706, #f59e0b); padding: 44rpx 32rpx; color: #fff; }
.hero-label { font-size: 26rpx; opacity: 0.9; }
.hero-value { display: block; font-size: 56rpx; font-weight: 700; margin: 8rpx 0; }
.hero-sub { font-size: 24rpx; opacity: 0.9; }
.section-title { font-size: 28rpx; font-weight: 600; padding: 28rpx 32rpx 12rpx; }
.state { text-align: center; color: #9ca3af; padding: 60rpx 0; font-size: 28rpx; }
.state.err { color: #dc2626; }
.state.small { padding: 24rpx 0; font-size: 26rpx; }
.list { padding: 0 20rpx; }
.row { display: flex; justify-content: space-between; align-items: center; background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 12rpx; }
.row.current { border: 2rpx solid #f59e0b; }
.row-name { font-size: 28rpx; font-weight: 600; }
.row-value { font-size: 24rpx; color: #6b7280; }
.badge { font-size: 20rpx; color: #fff; background: #f59e0b; border-radius: 6rpx; padding: 2rpx 8rpx; margin-left: 8rpx; }
</style>
