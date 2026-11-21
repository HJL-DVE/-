<template>
  <view class="page">
    <view class="status-box" :class="paid ? 'ok' : 'pending'">
      <view class="status-icon">{{ paid ? '✓' : '!' }}</view>
      <view class="status-text">{{ paid ? '充值已到账' : '等待支付到账' }}</view>
      <view class="status-sub">{{ rechargeNo }}</view>
      <view class="build-tag">build {{ BUILD_TAG }}</view>
    </view>

    <view class="card">
      <view class="row-between kv"><text class="muted">充值单号</text><text class="small">{{ rechargeNo || '—' }}</text></view>
      <view class="row-between kv"><text class="muted">支付单号</text><text class="small">{{ paymentNo || '—' }}</text></view>
      <view class="row-between kv"><text class="muted">充值单状态</text><text>{{ rechargeStatus || '查询中' }}</text></view>
      <view class="row-between kv" v-if="paymentStatus"><text class="muted">支付单状态</text><text>{{ paymentStatus }}</text></view>
      <view class="row-between kv"><text class="muted">充值金额</text><text>¥{{ fen2yuan(recharge.rechargeAmount) }}</text></view>
      <view class="row-between kv"><text class="muted">赠送金额</text><text>¥{{ fen2yuan(recharge.giftAmount) }}</text></view>
      <view class="row-between kv"><text class="muted">实付金额</text><text class="price">¥{{ fen2yuan(recharge.actualAmount) }}</text></view>
    </view>

    <view class="card">
      <view class="section-title">钱包余额（后端记账）</view>
      <view class="row-between kv"><text class="muted">总余额</text><text class="price big">¥{{ fen2yuan(wallet.totalBalance || 0) }}</text></view>
      <view class="row-between kv"><text class="muted">本金</text><text>¥{{ fen2yuan(wallet.principalBalance || 0) }}</text></view>
      <view class="row-between kv"><text class="muted">赠送</text><text>¥{{ fen2yuan(wallet.giftBalance || 0) }}</text></view>
      <view class="muted polling" v-if="polling">正在轮询到账状态（第 {{ pollCount }} 次）…</view>
      <view class="muted polling" v-else-if="!paid">轮询已停止：{{ stopReason }}</view>
    </view>

    <view class="card">
      <button v-if="!paid" class="btn-primary" :loading="payingWechat" @tap="payWechat">微信支付</button>
      <button class="btn-primary" v-else :loading="checking" @tap="refresh">手动刷新状态</button>
      <button v-if="!paid" class="btn-plain next" :loading="checking" @tap="refresh">手动刷新状态</button>
      <button class="btn-plain next" @tap="goMember">查看会员中心</button>
    </view>

    <view class="muted note">
      前端不做任何本地加余额操作；余额变化只来自 GET /api/wallet/account 的返回值。
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import * as walletApi from '@/api/wallet'
import * as paymentApi from '@/api/payment'
import type { RechargeOrder, WalletAccount } from '@/api/types'
import { fen2yuan } from '@/utils/money'

// 构建标记：用于确认开发者工具加载的是哪一份产物（排查缓存问题）
const BUILD_TAG = 'RCPAY-2'
const PAID_VALUES = ['SUCCESS', 'PAID', 'CREDITED', 'COMPLETED']
const MAX_POLL = 20
const INTERVAL = 2000

const rechargeNo = ref('')
const paymentNo = ref('')
const recharge = ref<RechargeOrder>({})
const wallet = ref<WalletAccount>({})
const rechargeStatus = ref('')
const paymentStatus = ref('')
const polling = ref(false)
const checking = ref(false)
const payingWechat = ref(false)
const pollCount = ref(0)
const stopReason = ref('')
let timer: ReturnType<typeof setTimeout> | null = null

const paid = computed(() => PAID_VALUES.indexOf(String(rechargeStatus.value).toUpperCase()) >= 0)

onLoad((query) => {
  rechargeNo.value = (query && query.rechargeNo ? String(query.rechargeNo) : '') || ''
  paymentNo.value = (query && query.paymentNo ? String(query.paymentNo) : '') || ''
  refresh()
  scheduleNext()
})

onUnload(() => {
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
})

function scheduleNext() {
  if (paid.value || pollCount.value >= MAX_POLL) return
  polling.value = true
  timer = setTimeout(async () => {
    pollCount.value += 1
    await refresh()
    if (paid.value) {
      polling.value = false
      return
    }
    if (pollCount.value >= MAX_POLL) {
      polling.value = false
      stopReason.value = '已达最大轮询次数，支付成功后余额仍由后端回调入账'
      return
    }
    scheduleNext()
  }, INTERVAL)
}

async function refresh() {
  if (!rechargeNo.value) {
    stopReason.value = '缺少充值单号参数 rechargeNo'
    polling.value = false
    return
  }
  checking.value = true
  try {
    const order = await walletApi.getRechargeOrder(rechargeNo.value)
    if (order) {
      recharge.value = order
      rechargeStatus.value = order.status || ''
    }
  } catch (e) {
    console.error('[wallet/recharges/{no}]', e)
  }
  try {
    wallet.value = (await walletApi.getWalletAccount()) || {}
  } catch (e) {
    console.error('[wallet/account]', e)
  }
  if (paymentNo.value) {
    try {
      const pay = await paymentApi.getPayment(paymentNo.value)
      paymentStatus.value = pay && pay.status ? pay.status : ''
    } catch (e) {
      console.error('[payment/orders]', e)
    }
  }
  checking.value = false
}

/**
 * 微信支付（模拟）：本项目不接真实微信商户，点击即视为支付成功。
 * 金额与状态全部以后端为准 —— 由 mock/notify 触发真实入账链路（支付单 SUCCESS → 充值单 PAID → 钱包入账）。
 */
async function payWechat() {
  if (!rechargeNo.value) {
    stopReason.value = '缺少充值单号，无法发起支付'
    return
  }
  payingWechat.value = true
  try {
    let pn = paymentNo.value
    let amt: number | undefined = recharge.value.actualAmount
    if (!pn) {
      const payment = await paymentApi.createPayment({ bizType: 'RECHARGE', bizNo: rechargeNo.value })
      pn = payment && payment.paymentNo ? payment.paymentNo : ''
      amt = payment && typeof payment.amount === 'number' ? payment.amount : amt
    }
    if (!pn) {
      stopReason.value = '创建支付单失败，请稍后重试'
      return
    }
    paymentNo.value = pn
    await paymentApi.mockPayNotify({ paymentNo: pn, amount: amt })
    await refresh()
    if (paid.value) {
      polling.value = false
    }
  } catch (e) {
    console.error('[recharge pay-wechat]', e)
  } finally {
    payingWechat.value = false
  }
}

function goMember() {
  uni.navigateTo({ url: '/pages/member/index' })
}
</script>

<style scoped>
.status-box {
  padding: 60rpx 32rpx;
  text-align: center;
  color: #ffffff;
}
.status-box.ok {
  background: linear-gradient(135deg, #15803d, #4ade80);
}
.status-box.pending {
  background: linear-gradient(135deg, #b45309, #f59e0b);
}
.status-icon {
  width: 100rpx;
  height: 100rpx;
  line-height: 96rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  margin: 0 auto 20rpx;
  font-size: 44rpx;
}
.status-text {
  font-size: 34rpx;
  font-weight: 700;
}
.status-sub {
  margin-top: 10rpx;
  font-size: 22rpx;
  opacity: 0.9;
}
.build-tag {
  margin-top: 6rpx;
  font-size: 18rpx;
  opacity: 0.6;
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
.small {
  font-size: 22rpx;
}
.price.big {
  font-size: 36rpx;
}
.polling {
  margin-top: 16rpx;
  font-size: 22rpx;
}
.next {
  margin-top: 20rpx;
}
.note {
  text-align: center;
  font-size: 22rpx;
  padding: 10rpx 40rpx 40rpx;
  line-height: 1.6;
}
</style>
