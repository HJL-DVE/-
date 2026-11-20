<template>
  <view class="page">
    <view class="bar">
      <text class="bar-label">我的积分</text>
      <text class="bar-value">{{ points }}</text>
      <text class="bar-link" @tap="goRecords">兑换记录 ></text>
    </view>

    <view v-if="loading" class="state">加载中...</view>
    <view v-else-if="list.length === 0" class="state">暂无可兑换商品</view>

    <view v-else class="grid">
      <view v-for="g in list" :key="g.id" class="card">
        <image v-if="g.imageUrl" class="img" :src="g.imageUrl" mode="aspectFill" />
        <view v-else class="img placeholder">礼</view>
        <text class="name">{{ g.goodsName }}</text>
        <text class="price">{{ g.pointsPrice }} 积分</text>
        <text class="stock">库存 {{ g.stock < 0 ? '不限' : g.stock }}</text>
        <button class="btn" size="mini" @tap="doExchange(g)">兑换</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import {
  exchangeGoods,
  getPointsAccount,
  getPointsGoods,
  type PointsGoods,
} from '@/api/marketing'
import { toast } from '@/utils/request'

const list = ref<PointsGoods[]>([])
const points = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await getPointsGoods()
    const acc = await getPointsAccount()
    points.value = acc.totalPoints
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function doExchange(g: PointsGoods) {
  if (points.value < g.pointsPrice) {
    toast('积分不足，还差 ' + (g.pointsPrice - points.value) + ' 分')
    return
  }
  uni.showModal({
    title: '确认兑换',
    content: '将消耗 ' + g.pointsPrice + ' 积分兑换「' + g.goodsName + '」',
    success: async (res) => {
      if (!res.confirm) return
      try {
        const r = await exchangeGoods(g.id)
        toast('兑换成功，剩余 ' + r.remainPoints + ' 积分')
        await load()
      } catch (e) {
        // request 层已提示
      }
    },
  })
}

function goRecords() {
  uni.navigateTo({ url: '/pages/points/records' })
}

onShow(load)
</script>

<style scoped>
.page { min-height: 100vh; background: #f6f7f9; padding-bottom: 40rpx; }
.bar { display: flex; align-items: baseline; gap: 16rpx; background: #fff; padding: 28rpx 32rpx; }
.bar-label { font-size: 26rpx; color: #6b7280; }
.bar-value { font-size: 40rpx; font-weight: 700; color: #16a34a; }
.bar-link { margin-left: auto; font-size: 24rpx; color: #16a34a; }
.state { text-align: center; color: #9ca3af; padding: 80rpx 0; font-size: 28rpx; }
.grid { display: flex; flex-wrap: wrap; padding: 20rpx; gap: 20rpx; }
.card { width: calc(50% - 10rpx); background: #fff; border-radius: 16rpx; padding: 20rpx; box-sizing: border-box; }
.img { width: 100%; height: 200rpx; border-radius: 12rpx; background: #f3f4f6; }
.img.placeholder { display: flex; align-items: center; justify-content: center; font-size: 48rpx; color: #d1d5db; }
.name { display: block; font-size: 28rpx; font-weight: 600; margin-top: 12rpx; }
.price { display: block; font-size: 30rpx; font-weight: 700; color: #ef4444; margin-top: 6rpx; }
.stock { display: block; font-size: 22rpx; color: #9ca3af; margin-top: 4rpx; }
.btn { margin-top: 12rpx; background: #16a34a; color: #fff; font-size: 24rpx; }
</style>
