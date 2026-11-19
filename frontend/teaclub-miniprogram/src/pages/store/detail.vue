<template>
  <view class="page">
    <view v-if="loading" class="state">加载中…</view>
    <view v-else-if="error" class="state">
      {{ error }}
      <button class="btn-plain retry" size="mini" @tap="load">重试</button>
    </view>
    <view v-else-if="!store" class="state">门店不存在（接口返回空）</view>

    <view v-else>
      <view class="hero">
        <view class="hero-name">{{ store.storeName }}</view>
        <view class="hero-status">{{ statusText }}</view>
      </view>

      <view class="card">
        <view class="row"><text class="label">地址</text><text class="value">{{ store.address || '—' }}</text></view>
        <view class="row"><text class="label">电话</text><text class="value">{{ store.phone || '—' }}</text></view>
        <view class="row"><text class="label">营业时间</text><text class="value">{{ hours || '—' }}</text></view>
        <view class="row"><text class="label">门店编码</text><text class="value">{{ store.storeCode || '—' }}</text></view>
        <view class="row" v-if="store.city"><text class="label">城市</text><text class="value">{{ store.city }}</text></view>
      </view>

      <view class="card">
        <button class="btn-primary" @tap="selectThis">切换为当前门店</button>
        <button v-if="store.phone" class="btn-plain call" @tap="callPhone">拨打电话</button>
        <button v-if="hasLocation" class="btn-plain call" @tap="openLocation">查看地图位置</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import * as authApi from '@/api/auth'
import type { StoreItem } from '@/api/types'
import { useStoreStore } from '@/stores/store'
import { isStoreOpen, storeBusinessHours, storeStatusText } from '@/utils/normalize'

const storeStore = useStoreStore()
const store = ref<StoreItem | null>(null)
const loading = ref(false)
const error = ref('')
const storeId = ref<string>('')

const statusText = computed(() => (store.value ? storeStatusText(store.value) : ''))
const hours = computed(() => (store.value ? storeBusinessHours(store.value) : ''))
const hasLocation = computed(() => !!(store.value && store.value.latitude && store.value.longitude))

async function load() {
  if (!storeId.value) {
    error.value = '缺少门店参数 id'
    return
  }
  loading.value = true
  error.value = ''
  try {
    store.value = await authApi.getStore(storeId.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '门店详情加载失败'
  } finally {
    loading.value = false
  }
}

onLoad((query) => {
  storeId.value = (query && query.id ? String(query.id) : '') || ''
  load()
})

function selectThis() {
  if (!store.value) return
  storeStore.setCurrentStore(store.value.id)
  uni.showToast({ title: '已切换门店', icon: 'none' })
}

function callPhone() {
  if (!store.value || !store.value.phone) return
  uni.makePhoneCall({ phoneNumber: store.value.phone, fail: () => undefined })
}

function openLocation() {
  if (!store.value || !store.value.latitude || !store.value.longitude) return
  uni.openLocation({
    latitude: Number(store.value.latitude),
    longitude: Number(store.value.longitude),
    name: store.value.storeName,
    address: store.value.address || '',
  })
}
</script>

<style scoped>
.hero {
  background: linear-gradient(135deg, #16a34a, #4ade80);
  color: #ffffff;
  padding: 48rpx 32rpx;
}
.hero-name {
  font-size: 40rpx;
  font-weight: 700;
}
.hero-status {
  margin-top: 12rpx;
  font-size: 24rpx;
  opacity: 0.92;
}
.row {
  display: flex;
  padding: 22rpx 0;
  border-bottom: 1rpx solid #f3f4f6;
}
.label {
  width: 180rpx;
  color: #6b7280;
  font-size: 26rpx;
}
.value {
  flex: 1;
  font-size: 28rpx;
}
.call {
  margin-top: 20rpx;
}
.retry {
  margin-top: 20rpx;
}
</style>
