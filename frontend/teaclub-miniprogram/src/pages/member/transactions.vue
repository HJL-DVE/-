<template>
  <view class="page">
    <view v-if="!authStore.isLoggedIn" class="state">
      请先登录
      <button class="btn-primary retry" @tap="goLogin">去登录</button>
    </view>

    <block v-else>
      <view class="tabs">
        <scroll-view scroll-x class="tabs-scroll">
          <view
            v-for="tab in bizTabs"
            :key="tab.value"
            class="tab"
            :class="{ active: bizType === tab.value }"
            @tap="selectBiz(tab.value)"
          >
            {{ tab.label }}
          </view>
        </scroll-view>
      </view>

      <view v-if="loading && list.length === 0" class="state">加载中…</view>
      <view v-else-if="error" class="state">
        {{ error }}
        <button class="btn-plain retry" size="mini" @tap="loadFirst">重试</button>
      </view>
      <view v-else-if="list.length === 0" class="state">暂无流水</view>

      <view v-else>
        <view v-for="txn in list" :key="txn.id || txn.txnNo" class="card">
          <view class="row-between">
            <text class="biz">{{ txn.bizTypeName || bizLabel(txn.bizType) }}</text>
            <text class="muted">{{ formatTime(txn.createTime) }}</text>
          </view>
          <view class="row-between kv">
            <text class="muted">本金变动</text>
            <text :class="signedClass(txn.principalChange)">{{ signed(txn.principalChange) }}</text>
          </view>
          <view class="row-between kv">
            <text class="muted">赠送变动</text>
            <text :class="signedClass(txn.giftChange)">{{ signed(txn.giftChange) }}</text>
          </view>
          <view class="row-between kv">
            <text class="muted">变动后余额</text>
            <text>本金 ¥{{ fen2yuan(txn.principalAfter) }} · 赠送 ¥{{ fen2yuan(txn.giftAfter) }}</text>
          </view>
          <view class="row-between kv" v-if="txn.storeName"><text class="muted">门店</text><text>{{ txn.storeName }}</text></view>
          <view class="row-between kv" v-if="txn.bizNo"><text class="muted">业务单号</text><text class="small">{{ txn.bizNo }}</text></view>
          <view class="row-between kv" v-if="txn.remark"><text class="muted">备注</text><text class="small">{{ txn.remark }}</text></view>
        </view>
        <view v-if="loadingMore" class="state">加载更多…</view>
        <view v-else-if="!hasMore" class="state">没有更多了</view>
      </view>
    </block>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onReachBottom, onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import * as walletApi from '@/api/wallet'
import type { WalletTransaction } from '@/api/types'
import { fen2yuan } from '@/utils/money'
import { formatTime } from '@/utils/normalize'

const authStore = useAuthStore()
/** 与后端 WalletBizType 枚举一致：RECHARGE_IN / CONSUME_OUT / REFUND_IN / RECHARGE_REFUND_OUT / ADMIN_ADJUST */
const bizTabs = [
  { label: '全部', value: '' },
  { label: '充值入账', value: 'RECHARGE_IN' },
  { label: '消费扣款', value: 'CONSUME_OUT' },
  { label: '退款回冲', value: 'REFUND_IN' },
  { label: '充值退款', value: 'RECHARGE_REFUND_OUT' },
  { label: '人工调整', value: 'ADMIN_ADJUST' },
]
const bizType = ref('')
const list = ref<WalletTransaction[]>([])
const pageNo = ref(1)
const pageSize = 10
const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')
const hasMore = ref(true)

function bizLabel(value: string | undefined): string {
  const hit = bizTabs.find((t) => t.value === value)
  return hit ? hit.label : value || '—'
}

function signed(value: number | undefined): string {
  const fen = Math.trunc(Number(value || 0))
  if (fen === 0) return '¥0.00'
  return (fen > 0 ? '+' : '-') + '¥' + fen2yuan(Math.abs(fen))
}

function signedClass(value: number | undefined): string {
  const fen = Math.trunc(Number(value || 0))
  return fen < 0 ? 'minus' : 'plus'
}

async function loadFirst() {
  if (!authStore.isLoggedIn) return
  loading.value = true
  error.value = ''
  pageNo.value = 1
  hasMore.value = true
  try {
    const res = await walletApi.getTransactions({
      pageNo: 1,
      pageSize,
      bizType: bizType.value || undefined,
    })
    const records = res && Array.isArray(res.records) ? res.records : []
    list.value = records
    hasMore.value = records.length >= pageSize
  } catch (e) {
    error.value = e instanceof Error ? e.message : '流水加载失败'
    list.value = []
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (!authStore.isLoggedIn || loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const next = pageNo.value + 1
    const res = await walletApi.getTransactions({
      pageNo: next,
      pageSize,
      bizType: bizType.value || undefined,
    })
    const records = res && Array.isArray(res.records) ? res.records : []
    if (records.length === 0) hasMore.value = false
    else {
      list.value = list.value.concat(records)
      pageNo.value = next
      hasMore.value = records.length >= pageSize
    }
  } catch (e) {
    console.error('[transactions loadMore]', e)
  } finally {
    loadingMore.value = false
  }
}

onShow(() => loadFirst())
onReachBottom(() => loadMore())

function selectBiz(value: string) {
  bizType.value = value
  loadFirst()
}
function goLogin() {
  uni.navigateTo({ url: '/pages/user/login' })
}
</script>

<style scoped>
.tabs {
  background: #ffffff;
  border-bottom: 1rpx solid #e5e7eb;
}
.tabs-scroll {
  white-space: nowrap;
}
.tab {
  display: inline-block;
  padding: 22rpx 28rpx;
  font-size: 26rpx;
  color: #6b7280;
}
.tab.active {
  color: #16a34a;
  font-weight: 600;
}
.biz {
  font-size: 28rpx;
  font-weight: 600;
}
.kv {
  padding: 8rpx 0;
  font-size: 26rpx;
}
.plus {
  color: #16a34a;
}
.minus {
  color: #dc2626;
}
.small {
  font-size: 22rpx;
  color: #4b5563;
}
.retry {
  margin-top: 20rpx;
}
</style>
