<template>
  <view class="page">
    <view class="tabs">
      <scroll-view scroll-x class="tabs-scroll">
        <view
          v-for="tab in statusTabs"
          :key="tab.value"
          class="tab"
          :class="{ active: status === tab.value }"
          @tap="selectStatus(tab.value)"
        >
          {{ tab.label }}
        </view>
      </scroll-view>
    </view>

    <view v-if="!authStore.isLoggedIn" class="state">
      请先登录查看订单
      <button class="btn-primary retry" @tap="goLogin">去登录</button>
    </view>

    <block v-else>
      <view v-if="loading && orders.length === 0" class="state">加载中…</view>
      <view v-else-if="error" class="state">
        {{ error }}
        <button class="btn-plain retry" size="mini" @tap="loadFirst">重试</button>
      </view>
      <view v-else-if="orders.length === 0" class="state">暂无订单</view>

      <view v-else>
        <view v-for="order in orders" :key="order.orderNo" class="card order" @tap="goDetail(order.orderNo)">
          <view class="row-between">
            <text class="muted">{{ formatTime(order.createTime) }}</text>
            <text class="status">{{ order.statusLabel || order.status || '' }}</text>
          </view>
          <view class="store">{{ order.storeName || '门店' }}</view>
          <view v-for="(item, idx) in order.items || []" :key="idx" class="item-line">
            <text>{{ item.spuName || item.skuName }}</text>
            <text class="muted">×{{ item.quantity }}</text>
          </view>
          <view class="row-between bottom">
            <text class="muted">{{ order.orderNo }}</text>
            <text class="price">¥{{ fen2yuan(order.payableAmount) }}</text>
          </view>
        </view>
        <view v-if="loadingMore" class="state">加载更多…</view>
        <view v-else-if="!hasMore && orders.length > 0" class="state">没有更多了</view>
      </view>
    </block>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onReachBottom, onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import * as orderApi from '@/api/order'
import type { OrderInfo } from '@/api/types'
import { fen2yuan } from '@/utils/money'
import { formatTime } from '@/utils/normalize'

const authStore = useAuthStore()
const statusTabs = [
  { label: '全部', value: '' },
  { label: '待支付', value: 'UNPAID' },
  { label: '已支付', value: 'PAID' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '退款中', value: 'REFUNDING' },
  { label: '已退款', value: 'REFUNDED' },
  { label: '已取消', value: 'CANCELLED' },
]
const status = ref('')
const orders = ref<OrderInfo[]>([])
const pageNo = ref(1)
const pageSize = 10
const total = ref(0)
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
    const res = await orderApi.getOrders({ status: status.value || undefined, pageNo: 1, pageSize })
    const records = res && Array.isArray(res.records) ? res.records : []
    orders.value = records
    total.value = res && typeof res.total === 'number' ? res.total : records.length
    hasMore.value = records.length >= pageSize
  } catch (e) {
    error.value = e instanceof Error ? e.message : '订单加载失败'
    orders.value = []
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (!authStore.isLoggedIn || loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const next = pageNo.value + 1
    const res = await orderApi.getOrders({ status: status.value || undefined, pageNo: next, pageSize })
    const records = res && Array.isArray(res.records) ? res.records : []
    if (records.length === 0) hasMore.value = false
    else {
      orders.value = orders.value.concat(records)
      pageNo.value = next
      hasMore.value = records.length >= pageSize
    }
  } catch (e) {
    console.error('[order list loadMore]', e)
  } finally {
    loadingMore.value = false
  }
}

onShow(() => {
  loadFirst()
})

onReachBottom(() => {
  loadMore()
})

function selectStatus(value: string) {
  status.value = value
  loadFirst()
}

function goDetail(orderNo: string) {
  uni.navigateTo({ url: '/pages/order/detail?orderNo=' + orderNo })
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
.status {
  color: #16a34a;
  font-size: 26rpx;
}
.store {
  margin: 14rpx 0 8rpx;
  font-size: 28rpx;
  font-weight: 600;
}
.item-line {
  display: flex;
  justify-content: space-between;
  font-size: 26rpx;
  padding: 6rpx 0;
}
.bottom {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f3f4f6;
}
.retry {
  margin-top: 20rpx;
}
</style>
