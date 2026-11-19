<template>
  <view class="page">
    <view v-if="storeStore.loading" class="state">加载中…</view>
    <view v-else-if="storeStore.error" class="state">
      {{ storeStore.error }}
      <button class="btn-plain retry" size="mini" @tap="reload">重试</button>
    </view>
    <view v-else-if="storeStore.stores.length === 0" class="state">暂无门店数据</view>

    <view v-else>
      <view
        v-for="store in storeStore.stores"
        :key="store.id"
        class="card store-item"
        :class="{ current: isCurrent(store.id) }"
      >
        <view class="row-between" @tap="selectStore(store.id)">
          <view class="store-main">
            <view class="store-name">
              {{ store.storeName }}
              <text v-if="isCurrent(store.id)" class="badge">当前门店</text>
            </view>
            <view class="muted addr">{{ store.address || '地址待维护' }}</view>
            <view class="meta">
              <text class="tag" :class="isOpen(store) ? 'tag-open' : 'tag-close'">{{ statusText(store) }}</text>
              <text class="muted hours">{{ hours(store) }}</text>
            </view>
          </view>
          <view class="chevron">›</view>
        </view>
        <view class="actions">
          <text class="action" @tap="selectStore(store.id)">设为当前门店</text>
          <text class="action" @tap="goDetail(store.id)">门店详情</text>
          <text v-if="store.phone" class="action" @tap="callPhone(store.phone)">电话</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { useStoreStore } from '@/stores/store'
import type { StoreItem } from '@/api/types'
import { isStoreOpen, storeBusinessHours, storeStatusText } from '@/utils/normalize'

const storeStore = useStoreStore()

async function reload() {
  try {
    await storeStore.fetchStores()
  } catch (e) {
    console.error('[auth/stores]', e)
  }
}

onShow(() => {
  if (storeStore.stores.length === 0 || storeStore.error) reload()
})

function isCurrent(id: number) {
  return storeStore.currentStoreId !== null && Number(storeStore.currentStoreId) === Number(id)
}
function isOpen(store: StoreItem) {
  return isStoreOpen(store)
}
function statusText(store: StoreItem) {
  return storeStatusText(store)
}
function hours(store: StoreItem) {
  return storeBusinessHours(store)
}

function selectStore(id: number) {
  storeStore.setCurrentStore(id)
  uni.showToast({ title: '已切换门店', icon: 'none' })
}

function goDetail(id: number) {
  uni.navigateTo({ url: '/pages/store/detail?id=' + id })
}

function callPhone(phone: string) {
  uni.makePhoneCall({ phoneNumber: phone, fail: () => undefined })
}
</script>

<style scoped>
.store-item.current {
  border: 2rpx solid #16a34a;
}
.store-name {
  font-size: 30rpx;
  font-weight: 600;
}
.badge {
  margin-left: 12rpx;
  font-size: 20rpx;
  background: #dcfce7;
  color: #15803d;
  border-radius: 20rpx;
  padding: 4rpx 12rpx;
}
.addr {
  margin-top: 10rpx;
}
.meta {
  margin-top: 12rpx;
}
.tag {
  font-size: 22rpx;
  padding: 4rpx 14rpx;
  border-radius: 20rpx;
}
.tag-open {
  background: #dcfce7;
  color: #15803d;
}
.tag-close {
  background: #fee2e2;
  color: #b91c1c;
}
.hours {
  margin-left: 16rpx;
}
.chevron {
  color: #9ca3af;
  font-size: 36rpx;
}
.actions {
  margin-top: 20rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f3f4f6;
  display: flex;
}
.action {
  font-size: 24rpx;
  color: #16a34a;
  margin-right: 32rpx;
}
.retry {
  margin-top: 20rpx;
}
</style>
