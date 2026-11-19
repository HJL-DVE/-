<template>
  <view class="page">
    <view v-if="!authStore.isLoggedIn" class="state">
      请先登录后下单
      <button class="btn-primary retry" @tap="goLogin">去登录</button>
    </view>

    <block v-else>
      <view class="card">
        <view class="row-between">
          <text class="section-title">门店</text>
          <text>{{ storeStore.currentStoreName }}</text>
        </view>
      </view>

      <view class="card">
        <view class="section-title">商品</view>
        <view v-for="item in cartStore.currentItems" :key="item.key" class="item-line">
          <view class="item-name">{{ item.spuName }} <text class="muted">×{{ item.quantity }}</text></view>
          <view class="muted item-spec">{{ item.skuName }}<text v-if="specRemark(item)"> · {{ specRemark(item) }}</text></view>
        </view>
        <view v-if="cartStore.currentItems.length === 0" class="muted">购物车为空</view>
      </view>

      <view class="card">
        <view class="row-between">
          <text class="section-title">服务端试算</text>
          <text class="muted" @tap="loadQuote">重新试算</text>
        </view>
        <view v-if="quoteLoading" class="state">试算中…</view>
        <view v-else-if="quoteError" class="state">
          {{ quoteError }}
          <button class="btn-plain retry" size="mini" @tap="loadQuote">重试</button>
        </view>
        <view v-else-if="quote">
          <view class="row-between kv"><text class="muted">商品总额</text><text>¥{{ fen2yuan(quote.totalAmount) }}</text></view>
          <view class="row-between kv"><text class="muted">等级优惠</text><text>-¥{{ fen2yuan(quote.levelDiscountAmount) }}</text></view>
          <view class="row-between kv"><text class="muted">活动优惠</text><text>-¥{{ fen2yuan(quote.activityDiscountAmount) }}</text></view>
          <view class="row-between kv"><text class="muted">优惠合计</text><text>-¥{{ fen2yuan(quote.discountAmount) }}</text></view>
          <view class="row-between kv total"><text>应付金额</text><text class="price big">¥{{ fen2yuan(quote.payableAmount) }}</text></view>
          <view v-if="quote.memberLevelName" class="muted kv">会员等级：{{ quote.memberLevelName }}</view>
        </view>
      </view>

      <view class="card">
        <view class="section-title">支付方式</view>
        <view class="pay-option" :class="{ active: payMode === 'WALLET' }" @tap="payMode = 'WALLET'">
          <view>
            <view class="pay-name">储值钱包支付</view>
            <view class="muted">POST /api/order/orders/{orderNo}/pay-wallet</view>
          </view>
          <text v-if="payMode === 'WALLET'" class="check">✓</text>
        </view>
        <view class="pay-option" :class="{ active: payMode === 'WECHAT' }" @tap="payMode = 'WECHAT'">
          <view>
            <view class="pay-name">微信支付</view>
            <view class="muted">POST /api/payment/create（Mock 渠道）</view>
          </view>
          <text v-if="payMode === 'WECHAT'" class="check">✓</text>
        </view>
      </view>

      <view class="footer">
        <view class="footer-amount">
          应付 <text class="price">¥{{ fen2yuan(quote ? quote.payableAmount : 0) }}</text>
        </view>
        <button class="btn-primary submit" :loading="submitting" @tap="submit">提交订单</button>
      </view>
    </block>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { useStoreStore } from '@/stores/store'
import { specRemark, useCartStore } from '@/stores/cart'
import * as orderApi from '@/api/order'
import * as paymentApi from '@/api/payment'
import type { OrderQuote } from '@/api/types'
import { fen2yuan } from '@/utils/money'
import { toast } from '@/utils/request'

const authStore = useAuthStore()
const storeStore = useStoreStore()
const cartStore = useCartStore()

const quote = ref<OrderQuote | null>(null)
const quoteLoading = ref(false)
const quoteError = ref('')
const payMode = ref<'WALLET' | 'WECHAT'>('WALLET')
const submitting = ref(false)

onLoad(() => {
  cartStore.setStore(storeStore.currentStoreId)
  if (authStore.isLoggedIn) loadQuote()
})

async function loadQuote() {
  if (storeStore.currentStoreId === null) {
    quoteError.value = '未选择门店'
    return
  }
  const items = cartStore.toOrderItems()
  if (items.length === 0) {
    quoteError.value = '购物车为空'
    return
  }
  quoteLoading.value = true
  quoteError.value = ''
  try {
    quote.value = await orderApi.quoteOrder({ storeId: storeStore.currentStoreId, items })
  } catch (e) {
    quoteError.value = e instanceof Error ? e.message : '试算失败'
  } finally {
    quoteLoading.value = false
  }
}

async function submit() {
  if (!authStore.isLoggedIn) {
    goLogin()
    return
  }
  if (storeStore.currentStoreId === null) {
    toast('请先选择门店')
    return
  }
  const items = cartStore.toOrderItems()
  if (items.length === 0) {
    toast('购物车为空')
    return
  }
  submitting.value = true
  try {
    const order = await orderApi.createOrder({
      storeId: storeStore.currentStoreId,
      items,
      remark: cartStore.toRemark(),
      orderSource: 'MINIAPP',
    })
    const orderNo = order && order.orderNo ? order.orderNo : ''
    if (!orderNo) {
      toast('下单成功但未返回订单号')
      return
    }
    if (payMode.value === 'WALLET') {
      await orderApi.payOrderWithWallet(orderNo)
      cartStore.clear()
      toast('钱包支付成功')
      setTimeout(() => {
        uni.redirectTo({ url: '/pages/order/detail?orderNo=' + orderNo })
      }, 600)
      return
    }
    // 微信支付：创建支付单，金额与状态全部以后端为准
    const payment = await paymentApi.createPayment({ bizType: 'ORDER', bizNo: orderNo })
    cartStore.clear()
    const paymentNo = payment && payment.paymentNo ? payment.paymentNo : ''
    const invoke = payment ? payment.invokeParams : ''
    if (invoke && typeof invoke === 'object') {
      try {
        await requestPayment(invoke as Record<string, unknown>)
      } catch (e) {
        console.error('[wx.requestPayment]', e)
      }
    }
    uni.redirectTo({ url: '/pages/order/detail?orderNo=' + orderNo + (paymentNo ? '&paymentNo=' + paymentNo : '') })
  } catch (e) {
    console.error('[order submit]', e)
  } finally {
    submitting.value = false
  }
}

function requestPayment(params: Record<string, unknown>): Promise<void> {
  return new Promise((resolve, reject) => {
    const api = (uni as unknown as { requestPayment?: (options: Record<string, unknown>) => void }).requestPayment
    if (!api) {
      reject(new Error('当前环境不支持 wx.requestPayment'))
      return
    }
    api({
      ...params,
      success: () => resolve(),
      fail: (err: { errMsg?: string }) => reject(new Error(err.errMsg || '支付未完成')),
    })
  })
}

function goLogin() {
  uni.navigateTo({ url: '/pages/user/login' })
}
</script>

<style scoped>
.page {
  padding-bottom: 200rpx;
}
.section-title {
  font-size: 28rpx;
  font-weight: 600;
}
.item-line {
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f3f4f6;
}
.item-name {
  font-size: 28rpx;
}
.item-spec {
  margin-top: 6rpx;
  font-size: 22rpx;
}
.kv {
  padding: 12rpx 0;
  font-size: 26rpx;
}
.kv.total {
  border-top: 1rpx solid #f3f4f6;
  margin-top: 8rpx;
  padding-top: 20rpx;
  font-size: 28rpx;
  font-weight: 600;
}
.price.big {
  font-size: 36rpx;
}
.pay-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
  border: 1rpx solid #e5e7eb;
  border-radius: 16rpx;
  margin-top: 16rpx;
}
.pay-option.active {
  border-color: #16a34a;
  background: #f0fdf4;
}
.pay-name {
  font-size: 28rpx;
  font-weight: 600;
}
.check {
  color: #16a34a;
  font-size: 32rpx;
}
.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #ffffff;
  display: flex;
  align-items: center;
  padding: 20rpx 24rpx;
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.06);
}
.footer-amount {
  flex: 1;
  font-size: 26rpx;
}
.submit {
  width: 300rpx;
}
.retry {
  margin-top: 20rpx;
}
</style>
