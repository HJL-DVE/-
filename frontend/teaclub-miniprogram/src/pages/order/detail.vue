<template>
  <view class="page">
    <view v-if="loading" class="state">加载中…</view>
    <view v-else-if="error" class="state">
      {{ error }}
      <button class="btn-plain retry" size="mini" @tap="load">重试</button>
    </view>
    <view v-else-if="!order" class="state">订单不存在</view>

    <block v-else>
      <view class="hero">
        <view class="hero-status">{{ order.statusLabel || order.status || '' }}</view>
        <view class="hero-no">订单号 {{ order.orderNo }}</view>
      </view>

      <view v-if="order.status === 'UNPAID' && payDeadline" class="countdown-bar">
        <text class="countdown-label">{{ remainSeconds > 0 ? '支付剩余时间' : '支付已超时' }}</text>
        <text class="countdown-value" :class="{ expired: remainSeconds <= 0 }">
          {{ remainSeconds > 0 ? remainText : '请重新支付' }}
        </text>
      </view>

      <view v-if="paymentNo" class="card">
        <view class="row-between">
          <text class="section-title">支付单</text>
          <text class="muted" @tap="pollPayment">刷新状态</text>
        </view>
        <view class="kv"><text class="muted">paymentNo</text><text>{{ paymentNo }}</text></view>
        <view class="kv"><text class="muted">状态</text><text>{{ paymentStatus || '未知' }}</text></view>
        <view class="kv" v-if="paymentAmount !== null"><text class="muted">金额</text><text>¥{{ fen2yuan(paymentAmount) }}</text></view>
        <view v-if="polling" class="muted">轮询中…</view>
      </view>

      <view class="card">
        <view class="section-title">商品</view>
        <view v-for="(item, idx) in order.items || []" :key="idx" class="item-line">
          <view>
            <view>{{ item.spuName || item.skuName }}</view>
            <view class="muted spec">{{ item.skuName }}<text v-if="item.specJson"> · {{ item.specJson }}</text></view>
          </view>
          <view class="item-right">
            <text class="muted">×{{ item.quantity }}</text>
            <text class="price">¥{{ fen2yuan(item.unitPrice) }}</text>
          </view>
        </view>
      </view>

      <view class="card">
        <view class="kv row-between"><text class="muted">商品总额</text><text>¥{{ fen2yuan(order.totalAmount) }}</text></view>
        <view class="kv row-between"><text class="muted">优惠合计</text><text>-¥{{ fen2yuan(order.discountAmount) }}</text></view>
        <view class="kv row-between"><text class="muted">实付金额</text><text class="price">¥{{ fen2yuan(order.paidAmount ?? order.payableAmount) }}</text></view>
        <view class="kv row-between"><text class="muted">支付渠道</text><text>{{ order.payChannel || '—' }}</text></view>
        <view class="kv row-between"><text class="muted">下单时间</text><text>{{ formatTime(order.createTime) || '—' }}</text></view>
        <view class="kv row-between" v-if="order.payTime"><text class="muted">支付时间</text><text>{{ formatTime(order.payTime) }}</text></view>
        <view class="kv row-between" v-if="order.cancelReason"><text class="muted">取消原因</text><text>{{ order.cancelReason }}</text></view>
        <view class="kv row-between" v-if="order.remark"><text class="muted">备注</text><text class="remark">{{ order.remark }}</text></view>
      </view>

      <view v-if="order.status === 'UNPAID'" class="card">
        <button class="btn-primary" :loading="payingWechat" @tap="payWechat">微信支付</button>
        <button class="btn-plain wallet" :loading="paying" @tap="payWallet">储值钱包支付</button>
        <button class="btn-plain cancel" @tap="cancel">取消订单</button>
      </view>
    </block>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import * as orderApi from '@/api/order'
import * as paymentApi from '@/api/payment'
import type { OrderInfo } from '@/api/types'
import { fen2yuan } from '@/utils/money'
import { formatTime } from '@/utils/normalize'
import { toast } from '@/utils/request'

/** 与后端 teaclub.payment.timeout-minutes 保持一致（支付单超时关闭时长） */
const PAY_TIMEOUT_MINUTES = 5

const order = ref<OrderInfo | null>(null)
const orderNo = ref('')
const paymentNo = ref('')
const loading = ref(false)
const error = ref('')
const paying = ref(false)
const payingWechat = ref(false)
const polling = ref(false)
const paymentStatus = ref('')
const paymentAmount = ref<number | null>(null)

// ===== 支付超时倒计时 =====
/** 支付单到期时间戳（毫秒）；未拿到创建时间前为 0，不显示倒计时 */
const payDeadline = ref(0)
/** 剩余秒数 */
const remainSeconds = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const remainText = computed(() => {
  const s = remainSeconds.value
  const mm = String(Math.floor(s / 60)).padStart(2, '0')
  const ss = String(s % 60).padStart(2, '0')
  return `${mm}:${ss}`
})

/** 解析后端时间：兼容 ISO（T 分隔 / 毫秒）与 iOS 对 '-' 的解析差异 */
function parseServerTime(value: string | null | undefined): number {
  if (!value) return 0
  const normalized = String(value).replace('T', ' ').replace(/\.\d+$/, '').replace(/-/g, '/')
  const t = new Date(normalized).getTime()
  return Number.isFinite(t) ? t : 0
}

/** 用支付单（或订单）创建时间启动倒计时 */
function startCountdown(createTime: string | null | undefined) {
  const created = parseServerTime(createTime)
  if (!created) return
  const deadline = created + PAY_TIMEOUT_MINUTES * 60 * 1000
  payDeadline.value = deadline
  const tick = () => {
    const left = Math.max(0, Math.floor((deadline - Date.now()) / 1000))
    remainSeconds.value = left
    if (left <= 0 && countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }
  if (countdownTimer) clearInterval(countdownTimer)
  tick()
  countdownTimer = setInterval(tick, 1000)
}

function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  payDeadline.value = 0
  remainSeconds.value = 0
}

onUnmounted(stopCountdown)

async function load() {
  if (!orderNo.value) {
    error.value = '缺少订单号参数 orderNo'
    return
  }
  loading.value = true
  error.value = ''
  try {
    order.value = await orderApi.getOrder(orderNo.value)
    if (order.value && order.value.status === 'UNPAID') {
      // 优先用支付单创建时间（pollPayment 会设置），这里兜底用订单创建时间
      if (!payDeadline.value && order.value.createTime) {
        startCountdown(order.value.createTime)
      }
    } else {
      stopCountdown()
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '订单加载失败'
  } finally {
    loading.value = false
  }
}

onLoad((query) => {
  orderNo.value = (query && query.orderNo ? String(query.orderNo) : '') || ''
  paymentNo.value = (query && query.paymentNo ? String(query.paymentNo) : '') || ''
  load()
  if (paymentNo.value) pollPayment()
})

async function pollPayment() {
  if (!paymentNo.value) return
  polling.value = true
  try {
    const data = await paymentApi.getPayment(paymentNo.value)
    paymentStatus.value = data && data.status ? data.status : ''
    paymentAmount.value = data && typeof data.amount === 'number' ? data.amount : null
    if (paymentStatus.value === 'SUCCESS') {
      stopCountdown()
      load()
    } else if (paymentStatus.value === 'CLOSED' || paymentStatus.value === 'FAILED') {
      // 支付单已关闭/失败：提示重新支付，不再走倒计时
      stopCountdown()
    } else if (data && data.createTime) {
      // 支付单创建时间更准确，重置倒计时以它为准
      startCountdown(data.createTime)
    }
  } catch (e) {
    console.error('[payment/orders]', e)
  } finally {
    polling.value = false
  }
}

/**
 * 微信支付（模拟）：本项目不接真实微信商户，
 * 点击后创建支付单 → 调 mock/notify 视为支付成功 → 刷新订单。
 * 金额与状态全部以后端为准，前端不参与金额计算。
 */
async function payWechat() {
  if (!order.value) return
  payingWechat.value = true
  try {
    let pn = ''
    let amt: number | undefined
    try {
      const payment = await paymentApi.createPayment({ bizType: 'ORDER', bizNo: order.value.orderNo })
      pn = payment && payment.paymentNo ? payment.paymentNo : ''
      amt = payment && typeof payment.amount === 'number' ? payment.amount : undefined
    } catch (e) {
      // 已存在待支付单时后端可能拒绝重复创建：回退用页面上已有的支付单号
      if (!paymentNo.value) throw e
      pn = paymentNo.value
      amt = paymentAmount.value ?? undefined
    }
    if (!pn) {
      toast('创建支付单失败')
      return
    }
    paymentNo.value = pn
    await paymentApi.mockPayNotify({ paymentNo: pn, amount: amt })
    toast('支付成功')
    await pollPayment()
    await load()
  } catch (e) {
    console.error('[pay-wechat]', e)
  } finally {
    payingWechat.value = false
  }
}

async function payWallet() {
  if (!order.value) return
  paying.value = true
  try {
    await orderApi.payOrderWithWallet(order.value.orderNo)
    toast('支付成功')
    await load()
  } catch (e) {
    console.error('[pay-wallet]', e)
  } finally {
    paying.value = false
  }
}

async function cancel() {
  if (!order.value) return
  uni.showModal({
    title: '确认取消订单？',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await orderApi.cancelOrder(order.value ? order.value.orderNo : '')
        toast('已取消')
        await load()
      } catch (e) {
        console.error('[cancel]', e)
      }
    },
  })
}
</script>

<style scoped>
.hero {
  background: linear-gradient(135deg, #16a34a, #4ade80);
  color: #ffffff;
  padding: 48rpx 32rpx;
}
.hero-status {
  font-size: 40rpx;
  font-weight: 700;
}
.hero-no {
  margin-top: 12rpx;
  font-size: 24rpx;
  opacity: 0.9;
}
.countdown-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 32rpx;
  background: #fff7ed;
  border-bottom: 1rpx solid #fed7aa;
}
.countdown-label {
  font-size: 26rpx;
  color: #9a3412;
}
.countdown-value {
  font-size: 32rpx;
  font-weight: 700;
  color: #ea580c;
  font-variant-numeric: tabular-nums;
}
.countdown-value.expired {
  color: #dc2626;
  font-size: 28rpx;
}
.section-title {
  font-size: 28rpx;
  font-weight: 600;
  margin-bottom: 12rpx;
}
.item-line {
  display: flex;
  justify-content: space-between;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #f3f4f6;
  font-size: 26rpx;
}
.item-right {
  text-align: right;
}
.spec {
  margin-top: 6rpx;
  font-size: 22rpx;
}
.kv {
  padding: 12rpx 0;
  font-size: 26rpx;
}
.remark {
  max-width: 420rpx;
  text-align: right;
}
.wallet {
  margin-top: 16rpx;
}
.cancel {
  margin-top: 20rpx;
}
.retry {
  margin-top: 20rpx;
}
</style>
