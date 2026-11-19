<template>
  <view class="page">
    <view v-if="!authStore.isLoggedIn" class="state">
      请先登录
      <button class="btn-primary retry" @tap="goLogin">去登录</button>
    </view>

    <block v-else>
      <view v-if="loading && list.length === 0" class="state">加载中…</view>
      <view v-else-if="error" class="state">
        {{ error }}
        <button class="btn-plain retry" size="mini" @tap="loadFirst">重试</button>
      </view>
      <view v-else-if="list.length === 0" class="state">暂无充值记录</view>

      <view v-else>
        <view v-for="item in list" :key="item.rechargeNo" class="card">
          <view class="row-between">
            <text class="no">{{ item.rechargeNo }}</text>
            <text class="status">{{ item.status || '' }}</text>
          </view>
          <view class="row-between kv"><text class="muted">套餐</text><text>{{ item.planName || '—' }}</text></view>
          <view class="row-between kv"><text class="muted">充值金额</text><text>¥{{ fen2yuan(item.rechargeAmount) }}</text></view>
          <view class="row-between kv"><text class="muted">赠送金额</text><text>¥{{ fen2yuan(item.giftAmount) }}</text></view>
          <view class="row-between kv"><text class="muted">实付金额</text><text class="price">¥{{ fen2yuan(item.actualAmount) }}</text></view>
          <view class="row-between kv"><text class="muted">创建时间</text><text>{{ formatTime(item.createTime) || '—' }}</text></view>
          <view class="row-between kv" v-if="item.payTime"><text class="muted">支付时间</text><text>{{ formatTime(item.payTime) }}</text></view>
          <view class="actions">
            <text class="action" @tap="goRecharge">再充一次</text>
            <text class="action" @tap="checkStatus(item.rechargeNo)">刷新状态</text>
          </view>
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
import type { RechargeOrder } from '@/api/types'
import { fen2yuan } from '@/utils/money'
import { formatTime } from '@/utils/normalize'
import { toast } from '@/utils/request'

const authStore = useAuthStore()
const list = ref<RechargeOrder[]>([])
const pageNo = ref(1)
const pageSize = 10
const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')
const hasMore = ref(true)

async function loadFirst() {
  if (!authStore.isLoggedIn) return
  loading.value = true
  error.value = ''
  pageNo.value = 1
  hasMore.value = true
  try {
    const res = await walletApi.getRechargeOrders({ pageNo: 1, pageSize })
    const records = res && Array.isArray(res.records) ? res.records : []
    list.value = records
    hasMore.value = records.length >= pageSize
  } catch (e) {
    error.value = e instanceof Error ? e.message : '充值记录加载失败'
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
    const res = await walletApi.getRechargeOrders({ pageNo: next, pageSize })
    const records = res && Array.isArray(res.records) ? res.records : []
    if (records.length === 0) hasMore.value = false
    else {
      list.value = list.value.concat(records)
      pageNo.value = next
      hasMore.value = records.length >= pageSize
    }
  } catch (e) {
    console.error('[recharges loadMore]', e)
  } finally {
    loadingMore.value = false
  }
}

onShow(() => {
  loadFirst()
})
onReachBottom(() => loadMore())

async function checkStatus(rechargeNo: string) {
  try {
    const data = await walletApi.getRechargeOrder(rechargeNo)
    toast('当前状态：' + (data && data.status ? data.status : '未知'))
    loadFirst()
  } catch (e) {
    console.error('[recharges/{no}]', e)
  }
}

function goRecharge() {
  uni.navigateTo({ url: '/pages/recharge/index' })
}
function goLogin() {
  uni.navigateTo({ url: '/pages/user/login' })
}
</script>

<style scoped>
.no {
  font-size: 24rpx;
  color: #6b7280;
}
.status {
  color: #16a34a;
  font-size: 26rpx;
}
.kv {
  padding: 8rpx 0;
  font-size: 26rpx;
}
.actions {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f3f4f6;
}
.action {
  color: #16a34a;
  font-size: 24rpx;
  margin-right: 32rpx;
}
.retry {
  margin-top: 20rpx;
}
</style>
