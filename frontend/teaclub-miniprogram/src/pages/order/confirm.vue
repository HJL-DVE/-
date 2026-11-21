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
        <view v-for="item in cartStore.checkoutItems" :key="item.key" class="item-line">
          <view class="item-name">{{ item.spuName }} <text class="muted">×{{ item.quantity }}</text></view>
          <view class="muted item-spec">{{ item.skuName }}<text v-if="specRemark(item)"> · {{ specRemark(item) }}</text></view>
        </view>
        <view v-if="cartStore.checkoutItems.length === 0" class="muted">购物车为空</view>
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
        <view class="row-between" @tap="openCouponPicker">
          <text class="section-title">优惠券</text>
          <text :class="couponText === '不使用优惠券' ? 'muted' : 'coupon-picked'">
            {{ couponText }} >
          </text>
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
    <!-- 优惠券选择 -->
    <view v-if="couponPickerVisible" class="mask" @tap="closeCouponPicker">
      <view class="sheet" @tap.stop>
        <view class="sheet-title">选择优惠券</view>
        <scroll-view scroll-y class="sheet-body">
          <view class="coupon-row none" :class="{ active: !selectedCouponId }" @tap="pickCoupon(null)">
            <text>不使用优惠券</text>
            <text v-if="!selectedCouponId" class="check">✓</text>
          </view>
          <view v-if="couponsLoading" class="muted sheet-hint">加载中…</view>
          <view v-else-if="coupons.length === 0" class="muted sheet-hint">暂无可用优惠券</view>
          <view
            v-for="cp in coupons"
            :key="cp.id"
            class="coupon-row"
            :class="{ active: selectedCouponId === cp.id, disabled: !cp.usable }"
            @tap="cp.usable !== false && pickCoupon(cp)"
          >
            <view class="coupon-left">
              <view class="coupon-amount" v-if="cp.couponType === 'DISCOUNT'">
                {{ ((cp.discountRate || 10000) / 1000).toFixed(1) }}折
              </view>
              <view class="coupon-amount" v-else>¥{{ fen2yuan(cp.faceValue || 0) }}</view>
              <view class="muted coupon-cond">
                {{ (cp.thresholdAmount || 0) <= 0 ? '无门槛' : '满' + fen2yuan(cp.thresholdAmount || 0) + '可用' }}
              </view>
            </view>
            <view class="coupon-mid">
              <view class="coupon-name">{{ cp.couponName || cp.couponTypeLabel || cp.couponType }}</view>
              <view class="muted coupon-time" v-if="cp.validEnd">有效期至 {{ (cp.validEnd || '').slice(0, 10) }}</view>
            </view>
            <text v-if="selectedCouponId === cp.id" class="check">✓</text>
          </view>
        </scroll-view>
        <button class="btn-primary sheet-close" @tap="closeCouponPicker">完成</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { useStoreStore } from '@/stores/store'
import { specRemark, useCartStore } from '@/stores/cart'
import * as orderApi from '@/api/order'
import * as paymentApi from '@/api/payment'
import * as marketingApi from '@/api/marketing'
import type { CouponItem } from '@/api/marketing'
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

// ===== 优惠券 =====
const coupons = ref<CouponItem[]>([])
const couponsLoading = ref(false)
const couponPickerVisible = ref(false)
const selectedCouponId = ref<number | null>(null)
const couponText = computed(() => {
  if (!selectedCouponId.value) return '不使用优惠券'
  const cp = coupons.value.find((x) => x.id === selectedCouponId.value)
  if (!cp) return '已选优惠券'
  const label = cp.couponType === 'DISCOUNT'
    ? ((cp.discountRate || 10000) / 1000).toFixed(1) + '折'
    : '¥' + fen2yuan(cp.faceValue || 0)
  return (cp.couponName || cp.couponTypeLabel || '优惠券') + '（' + label + '）'
})

onLoad((opts) => {
  const isBuyNow = opts && opts.mode === 'buynow'
  if (isBuyNow) {
    // 立即下单：门店以商品页传入为准（商品页已把 storeStore.currentStoreId 用于规格价）
    cartStore.setStore(storeStore.currentStoreId)
  } else {
    cartStore.setStore(storeStore.currentStoreId)
  }
  if (authStore.isLoggedIn) {
    loadQuote()
    loadCoupons()
  }
})

/** 拉取会员可用券（后端按门槛/有效期过滤，usable 字段标明是否可用于本单） */
async function loadCoupons() {
  couponsLoading.value = true
  try {
    coupons.value = (await marketingApi.getUsableCoupons()) || []
  } catch (e) {
    console.error('[coupons/usable]', e)
    coupons.value = []
  } finally {
    couponsLoading.value = false
  }
}

function openCouponPicker() {
  couponPickerVisible.value = true
  if (coupons.value.length === 0) loadCoupons()
}

function closeCouponPicker() {
  couponPickerVisible.value = false
}

/** 选券后立即重新试算：抵扣金额由服务端算，前端不参与 */
async function pickCoupon(cp: CouponItem | null) {
  selectedCouponId.value = cp ? cp.id : null
  couponPickerVisible.value = false
  await loadQuote()
}

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
    quote.value = await orderApi.quoteOrder({
      storeId: storeStore.currentStoreId,
      items,
      memberCouponId: selectedCouponId.value || undefined,
    })
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
      memberCouponId: selectedCouponId.value || undefined,
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
.coupon-picked { color: #16a34a; font-size: 24rpx; }
.mask {
  position: fixed; left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.45); z-index: 100;
  display: flex; align-items: flex-end;
}
.sheet {
  width: 100%; background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 24rpx 24rpx 40rpx;
  max-height: 70vh; display: flex; flex-direction: column;
}
.sheet-title { font-size: 30rpx; font-weight: 600; text-align: center; margin-bottom: 16rpx; }
.sheet-body { flex: 1; max-height: 50vh; }
.sheet-hint { text-align: center; padding: 40rpx 0; font-size: 24rpx; }
.sheet-close { margin-top: 20rpx; }
.coupon-row {
  display: flex; align-items: center;
  padding: 20rpx 16rpx; border-bottom: 1rpx solid #f3f4f6;
}
.coupon-row.none { justify-content: space-between; font-size: 26rpx; }
.coupon-row.active { background: #f0fdf4; }
.coupon-row.disabled { opacity: 0.45; }
.coupon-left { width: 150rpx; }
.coupon-amount { font-size: 32rpx; font-weight: 700; color: #ef4444; }
.coupon-cond { font-size: 20rpx; margin-top: 4rpx; }
.coupon-mid { flex: 1; padding: 0 12rpx; }
.coupon-name { font-size: 26rpx; }
.coupon-time { font-size: 20rpx; margin-top: 4rpx; }
.check { color: #16a34a; font-size: 30rpx; font-weight: 700; }
.retry {
  margin-top: 20rpx;
}
</style>
