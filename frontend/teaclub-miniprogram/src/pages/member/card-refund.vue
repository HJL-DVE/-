<template>
  <view class="page">
    <view class="hero">
      <text class="hero-label">可退金额（实付本金）</text>
      <text class="hero-value">¥{{ fen2yuan(wallet.principalBalance || 0) }}</text>
      <view class="hero-row">
        <text class="hero-sub">赠送余额 ¥{{ fen2yuan(wallet.giftBalance || 0) }}（退卡作废）</text>
      </view>
    </view>

    <view class="card">
      <view class="section-title">退卡规则</view>
      <view class="rule">· 退还金额 = 当前实付本金余额，提交后立即到账</view>
      <view class="rule">· 赠送金额为营销权益，退卡时作废且不折现</view>
      <view class="rule">· 赠送金一经消费即不支持自助退卡，需联系门店人工处理</view>
      <view class="rule">· 退卡后余额清零，如需继续使用可重新充值</view>
      <view class="rule">· 同一会员每日限办理一次</view>
    </view>

    <view class="card">
      <view class="section-title">申请退卡</view>
      <textarea
        class="reason"
        v-model="reason"
        placeholder="请填写退卡原因（必填，便于门店跟进）"
        maxlength="100"
      />
      <button class="btn-danger" :loading="submitting" :disabled="!canRefund" @tap="submit">
        {{ canRefund ? '确认退卡 ¥' + fen2yuan(wallet.principalBalance || 0) : '当前无可退余额' }}
      </button>
    </view>

    <view v-if="list.length" class="card">
      <view class="section-title">退卡记录</view>
      <view v-for="r in list" :key="r.id" class="rec">
        <view class="rec-head">
          <text class="rec-no">{{ r.refundNo }}</text>
          <text :class="['tag', r.status === 'APPROVED' ? 'ok' : r.status === 'REJECTED' ? 'bad' : 'wait']">
            {{ label(r.status) }}
          </text>
        </view>
        <view class="rec-row">
          <text class="muted">退还本金</text>
          <text class="money">¥{{ fen2yuan(r.principalAmount || 0) }}</text>
        </view>
        <view class="rec-row">
          <text class="muted">作废赠送</text>
          <text class="muted">¥{{ fen2yuan(r.giftVoidAmount || 0) }}</text>
        </view>
        <view class="rec-row">
          <text class="muted">时间</text>
          <text class="muted">{{ r.createTime || '' }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import * as walletApi from '@/api/wallet'
import type { CardRefund } from '@/api/wallet'
import { fen2yuan } from '@/utils/money'

const wallet = ref<{ principalBalance?: number; giftBalance?: number }>({})
const list = ref<CardRefund[]>([])
const reason = ref('')
const submitting = ref(false)

const canRefund = computed(() => (wallet.value.principalBalance || 0) > 0)

function label(s?: string): string {
  return s === 'APPROVED' ? '已退款' : s === 'REJECTED' ? '已驳回' : '处理中'
}

async function loadAll(): Promise<void> {
  try {
    wallet.value = await walletApi.getWalletAccount()
  } catch {
    wallet.value = {}
  }
  try {
    list.value = (await walletApi.getMyCardRefunds()) || []
  } catch {
    list.value = []
  }
}

async function submit(): Promise<void> {
  if (!reason.value.trim()) {
    uni.showToast({ title: '请填写退卡原因', icon: 'none' })
    return
  }
  if (!canRefund.value) {
    uni.showToast({ title: '当前无实付余额可退', icon: 'none' })
    return
  }

  const principal = fen2yuan(wallet.value.principalBalance || 0)
  const gift = fen2yuan(wallet.value.giftBalance || 0)
  const confirmed = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '确认退卡',
      content: `将退还 ¥${principal}，赠送余额 ¥${gift} 作废且不折现。确定继续？`,
      confirmText: '确认退卡',
      confirmColor: '#dc2626',
      success: (r) => resolve(!!r.confirm),
      fail: () => resolve(false),
    })
  })
  if (!confirmed) return

  submitting.value = true
  try {
    await walletApi.applyCardRefund({ reason: reason.value.trim() })
    uni.showModal({
      title: '退卡成功',
      content: `已退还 ¥${principal}，赠送余额已作废`,
      showCancel: false,
    })
    reason.value = ''
    await loadAll()
  } catch (e) {
    const msg = (e as { message?: string })?.message || '退卡失败，请稍后重试'
    uni.showToast({ title: msg, icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onShow(loadAll)
</script>

<style scoped>
.page { min-height: 100vh; background: #f6f7f9; padding-bottom: 40rpx; }
.hero { background: linear-gradient(135deg, #16a34a, #22c55e); padding: 48rpx 32rpx; color: #fff; }
.hero-label { font-size: 26rpx; opacity: 0.9; }
.hero-value { display: block; font-size: 72rpx; font-weight: 700; margin: 8rpx 0; }
.hero-row { display: flex; gap: 32rpx; }
.hero-sub { font-size: 24rpx; opacity: 0.9; }
.card { background: #fff; border-radius: 16rpx; margin: 20rpx; padding: 28rpx; }
.section-title { font-size: 28rpx; font-weight: 600; margin-bottom: 16rpx; }
.rule { font-size: 24rpx; color: #6b7280; line-height: 2; }
.reason {
  width: 100%; height: 140rpx; border: 1rpx solid #e5e7eb; border-radius: 12rpx;
  padding: 20rpx; font-size: 26rpx; box-sizing: border-box; margin-bottom: 24rpx;
}
.btn-danger { background: #dc2626; color: #fff; border-radius: 12rpx; font-size: 30rpx; }
.btn-danger[disabled] { background: #d1d5db; color: #fff; }
.rec { border-top: 1rpx solid #f3f4f6; padding-top: 20rpx; margin-top: 20rpx; }
.rec-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10rpx; }
.rec-no { font-size: 24rpx; color: #374151; }
.rec-row { display: flex; justify-content: space-between; font-size: 24rpx; margin-top: 8rpx; }
.muted { color: #9ca3af; }
.money { color: #dc2626; font-weight: 600; }
.tag { font-size: 22rpx; padding: 4rpx 14rpx; border-radius: 8rpx; }
.tag.ok { background: #f0fdf4; color: #16a34a; }
.tag.bad { background: #fef2f2; color: #dc2626; }
.tag.wait { background: #fffbeb; color: #d97706; }
</style>