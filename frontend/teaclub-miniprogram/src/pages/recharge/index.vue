<template>
  <view class="page">
    <view class="head">
      <view class="head-title">储值充值</view>
      <view class="head-sub">充值本金 + 赠送金额全部由后端入账</view>
      <view class="head-balance">
        实付余额 ¥{{ fen2yuan(wallet.principalBalance || 0) }} · 赠送余额 ¥{{ fen2yuan(wallet.giftBalance || 0) }}
      </view>
    </view>

    <view v-if="!authStore.isLoggedIn" class="state">
      请先登录后充值
      <button class="btn-primary retry" @tap="goLogin">去登录</button>
    </view>

    <block v-else>
      <view v-if="loading" class="state">套餐加载中…</view>
      <view v-else-if="error" class="state">
        {{ error }}
        <button class="btn-plain retry" size="mini" @tap="loadPlans">重试</button>
      </view>
      <view v-else-if="plans.length === 0" class="state">当前门店暂无可用充值套餐</view>

      <view v-else class="plans">
        <view
          v-for="plan in plans"
          :key="plan.id"
          class="card plan"
          :class="{ active: selectedPlanId === plan.id }"
          @tap="selectedPlanId = plan.id"
        >
          <view class="row-between">
            <view>
              <view class="plan-name">{{ plan.planName }}</view>
              <view class="muted plan-desc" v-if="plan.description">{{ plan.description }}</view>
            </view>
            <view class="plan-right">
              <view class="plan-amount">¥{{ fen2yuan(plan.rechargeAmount) }}</view>
              <view class="gift" v-if="plan.giftAmount && plan.giftAmount > 0">
                赠 ¥{{ fen2yuan(plan.giftAmount) }}
              </view>
            </view>
          </view>
          <view v-if="plan.startTime || plan.endTime" class="muted plan-time">
            活动时间：{{ formatTime(plan.startTime) || '—' }} ~ {{ formatTime(plan.endTime) || '—' }}
          </view>
        </view>
      </view>

      <view class="card" v-if="plans.length > 0">
        <view class="section-title">活动说明</view>
        <view class="muted rule">1. 充值金额与赠送金额以服务端套餐配置为准，本页仅展示接口返回值。</view>
        <view class="muted rule">2. 支付成功后**不会**在小程序本地加余额，入账由支付回调驱动。</view>
        <view class="muted rule">3. 本页会轮询 GET /api/wallet/recharges/{rechargeNo} 与 GET /api/wallet/account 确认到账。</view>
        <view class="muted rule">4. 赠送金额仅可用于消费抵扣，不可提现。</view>
      </view>

      <view class="footer" v-if="plans.length > 0">
        <view class="footer-amount">
          应付 <text class="price">¥{{ fen2yuan(selectedPlanAmount) }}</text>
        </view>
        <button class="btn-primary submit" :loading="submitting" @tap="submit">立即充值</button>
      </view>
    </block>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { useStoreStore } from '@/stores/store'
import * as walletApi from '@/api/wallet'
import * as paymentApi from '@/api/payment'
import type { RechargePlan, WalletAccount } from '@/api/types'
import { fen2yuan } from '@/utils/money'
import { formatTime } from '@/utils/normalize'
import { toast } from '@/utils/request'

const authStore = useAuthStore()
const storeStore = useStoreStore()
const plans = ref<RechargePlan[]>([])
const wallet = ref<WalletAccount>({})
const selectedPlanId = ref<number | null>(null)
const loading = ref(false)
const error = ref('')
const submitting = ref(false)

const selectedPlan = computed(() => plans.value.find((p) => p.id === selectedPlanId.value) || null)
const selectedPlanAmount = computed(() => (selectedPlan.value ? selectedPlan.value.rechargeAmount : 0))

async function loadPlans() {
  loading.value = true
  error.value = ''
  try {
    const list = await walletApi.getRechargePlans(storeStore.currentStoreId ?? undefined)
    plans.value = Array.isArray(list) ? list : []
    if (plans.value.length > 0 && selectedPlanId.value === null) {
      selectedPlanId.value = plans.value[0].id
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '充值套餐加载失败'
    plans.value = []
  } finally {
    loading.value = false
  }
}

async function loadWallet() {
  if (!authStore.isLoggedIn) return
  try {
    wallet.value = (await walletApi.getWalletAccount()) || {}
  } catch (e) {
    console.error('[wallet/account]', e)
  }
}

onShow(() => {
  if (!authStore.isLoggedIn) return
  loadWallet()
  loadPlans()
})

async function submit() {
  if (!selectedPlan.value) {
    toast('请选择充值套餐')
    return
  }
  submitting.value = true
  try {
    const storeId = storeStore.currentStoreId
    if (storeId === null) {
      toast('请先在首页选择门店')
      return
    }
    const recharge = await walletApi.createRecharge({ planId: selectedPlan.value.id, storeId })
    const rechargeNo = recharge && recharge.rechargeNo ? recharge.rechargeNo : ''
    if (!rechargeNo) {
      toast('充值单创建失败：未返回 rechargeNo')
      return
    }
    // 创建支付单，金额与支付状态全部以后端为准
    const payment = await paymentApi.createPayment({ bizType: 'RECHARGE', bizNo: rechargeNo })
    const paymentNo = payment && payment.paymentNo ? payment.paymentNo : ''
    const invoke = payment ? payment.invokeParams : ''
    if (invoke && typeof invoke === 'object') {
      await tryRequestPayment(invoke as Record<string, unknown>)
    }
    uni.navigateTo({
      url:
        '/pages/recharge/result?rechargeNo=' +
        rechargeNo +
        (paymentNo ? '&paymentNo=' + paymentNo : ''),
    })
  } catch (e) {
    console.error('[recharge submit]', e)
  } finally {
    submitting.value = false
  }
}

function tryRequestPayment(params: Record<string, unknown>): Promise<void> {
  return new Promise((resolve) => {
    const api = (uni as unknown as { requestPayment?: (options: Record<string, unknown>) => void }).requestPayment
    if (!api) {
      resolve()
      return
    }
    api({ ...params, success: () => resolve(), fail: () => resolve() })
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
.head {
  background: linear-gradient(135deg, #b45309, #f59e0b);
  color: #ffffff;
  padding: 44rpx 32rpx;
}
.head-title {
  font-size: 40rpx;
  font-weight: 700;
}
.head-sub {
  margin-top: 10rpx;
  font-size: 24rpx;
  opacity: 0.92;
}
.head-balance {
  margin-top: 20rpx;
  font-size: 28rpx;
}
.plan {
  border: 1rpx solid #e5e7eb;
}
.plan.active {
  border-color: #f59e0b;
  background: #fffbeb;
}
.plan-name {
  font-size: 30rpx;
  font-weight: 600;
}
.plan-desc {
  margin-top: 8rpx;
  font-size: 22rpx;
}
.plan-time {
  margin-top: 12rpx;
  font-size: 22rpx;
}
.plan-right {
  text-align: right;
}
.plan-amount {
  font-size: 34rpx;
  font-weight: 700;
  color: #dc2626;
}
.gift {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #b45309;
}
.section-title {
  font-size: 28rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
}
.rule {
  font-size: 23rpx;
  line-height: 1.8;
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
  background: #f59e0b;
}
.retry {
  margin-top: 20rpx;
}
</style>
