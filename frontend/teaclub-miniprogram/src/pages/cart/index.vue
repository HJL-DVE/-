<template>
  <view class="page">
    <view class="card">
      <view class="row-between">
        <text class="title">当前门店：{{ storeStore.currentStoreName }}</text>
        <text class="muted link" @tap="goStoreList">切换</text>
      </view>
    </view>

    <view v-if="cartStore.currentItems.length === 0" class="state">
      购物车是空的
      <button class="btn-primary retry" size="mini" @tap="goProductList">去选购</button>
    </view>

    <view v-else>
      <view v-for="item in cartStore.currentItems" :key="item.key" class="card line">
        <image v-if="item.imageUrl" class="line-img" :src="item.imageUrl" mode="aspectFill" />
        <view v-else class="line-img placeholder">暂无图</view>
        <view class="line-info">
          <view class="line-name">{{ item.spuName }}</view>
          <view class="muted line-spec">{{ item.skuName }}</view>
          <view class="muted line-spec" v-if="specRemark(item)">{{ specRemark(item) }}</view>
          <view class="row-between line-bottom">
            <text class="price">¥{{ fen2yuan(item.unitPrice) }}</text>
            <view class="qty">
              <text class="qty-btn" @tap="cartStore.decrease(item.key)">-</text>
              <text class="qty-num">{{ item.quantity }}</text>
              <text class="qty-btn" @tap="cartStore.increase(item.key)">+</text>
            </view>
          </view>
        </view>
        <text class="remove" @tap="cartStore.remove(item.key)">×</text>
      </view>

      <view class="card">
        <view class="row-between">
          <text class="muted">共 {{ cartStore.totalQuantity }} 件，本地预估</text>
          <text class="price">¥{{ fen2yuan(cartStore.totalAmount) }}</text>
        </view>
        <view class="muted note">最终金额以 POST /api/order/quote 服务端试算为准。</view>
      </view>

      <view class="footer">
        <button class="btn-plain clear" @tap="clearCart">清空</button>
        <button class="btn-primary checkout" @tap="goConfirm">去结算</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { useStoreStore } from '@/stores/store'
import { specRemark, useCartStore } from '@/stores/cart'
import { fen2yuan } from '@/utils/money'

const storeStore = useStoreStore()
const cartStore = useCartStore()

onShow(() => {
  cartStore.setStore(storeStore.currentStoreId)
})

function goStoreList() {
  uni.switchTab({ url: '/pages/store/list' })
}
function goProductList() {
  uni.navigateTo({ url: '/pages/product/list' })
}
function clearCart() {
  cartStore.clear()
}
function goConfirm() {
  if (cartStore.currentItems.length === 0) return
  uni.navigateTo({ url: '/pages/order/confirm' })
}
</script>

<style scoped>
.page {
  padding-bottom: 200rpx;
}
.title {
  font-size: 28rpx;
  font-weight: 600;
}
.link {
  color: #16a34a;
}
.line {
  display: flex;
  position: relative;
}
.line-img {
  width: 150rpx;
  height: 150rpx;
  border-radius: 16rpx;
  background: #f3f4f6;
}
.line-info {
  flex: 1;
  margin-left: 20rpx;
}
.line-name {
  font-size: 30rpx;
  font-weight: 600;
}
.line-spec {
  margin-top: 6rpx;
  font-size: 22rpx;
}
.line-bottom {
  margin-top: 16rpx;
}
.qty {
  display: flex;
  align-items: center;
}
.qty-btn {
  width: 48rpx;
  height: 48rpx;
  line-height: 44rpx;
  text-align: center;
  border-radius: 50%;
  background: #f3f4f6;
}
.qty-num {
  width: 64rpx;
  text-align: center;
}
.remove {
  position: absolute;
  right: 20rpx;
  top: 12rpx;
  color: #9ca3af;
  font-size: 34rpx;
}
.note {
  margin-top: 12rpx;
  font-size: 22rpx;
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
.clear {
  width: 200rpx;
  margin-right: 20rpx;
}
.checkout {
  flex: 1;
}
.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  font-size: 22rpx;
}
.retry {
  margin-top: 20rpx;
}
</style>
