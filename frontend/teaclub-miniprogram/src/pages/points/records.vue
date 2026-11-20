<template>
  <view class="page">
    <view v-if="loading" class="state">加载中...</view>
    <view v-else-if="list.length === 0" class="state">暂无兑换记录</view>
    <view v-else class="list">
      <view v-for="r in list" :key="r.exchangeNo" class="row">
        <view class="row-left">
          <text class="row-name">{{ r.goodsName }}</text>
          <text class="row-meta">{{ formatTime(r.createTime) }}</text>
          <text class="row-meta" v-if="r.couponNo">券码 {{ r.couponNo }}</text>
        </view>
        <text class="row-cost">-{{ r.pointsCost }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getMyExchanges, type ExchangeResult } from '@/api/marketing'
import { formatTime } from '@/utils/normalize'

const list = ref<ExchangeResult[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await getMyExchanges(50)
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

onShow(load)
</script>

<style scoped>
.page { min-height: 100vh; background: #f6f7f9; padding: 20rpx; }
.state { text-align: center; color: #9ca3af; padding: 80rpx 0; font-size: 28rpx; }
.row { display: flex; justify-content: space-between; background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx; }
.row-name { font-size: 28rpx; font-weight: 600; }
.row-meta { display: block; font-size: 22rpx; color: #9ca3af; margin-top: 6rpx; }
.row-cost { font-size: 30rpx; font-weight: 700; color: #ef4444; }
</style>
