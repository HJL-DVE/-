<template>
  <view class="page">
    <view class="entry" @tap="goCenter">
      <view class="entry-left">
        <text class="entry-title">优惠券中心</text>
        <text class="entry-sub">领取门店发放的优惠券</text>
      </view>
      <text class="entry-arrow">></text>
    </view>

    <view class="entry" @tap="goMine">
      <view class="entry-left">
        <text class="entry-title">我的优惠券</text>
        <text class="entry-sub">查看未使用 / 已使用 / 已过期</text>
      </view>
      <text class="entry-arrow">></text>
    </view>

    <view class="entry" @tap="goBirthday">
      <view class="entry-left">
        <text class="entry-title">生日福利</text>
        <text class="entry-sub">生日当月专属好礼</text>
      </view>
      <text class="entry-arrow">></text>
    </view>
  </view>
</template>

<script setup lang="ts">
function goCenter() {
  uni.navigateTo({ url: '/pages/coupon/center' })
}
function goMine() {
  uni.navigateTo({ url: '/pages/coupon/mine' })
}
function goBirthday() {
  uni.navigateTo({ url: '/pages/member/birthday' })
}
</script>

<style scoped>
.page { min-height: 100vh; background: #f6f7f9; padding: 20rpx; }
.entry { display: flex; align-items: center; justify-content: space-between; background: #fff; border-radius: 16rpx; padding: 28rpx 24rpx; margin-bottom: 20rpx; }
.entry-title { display: block; font-size: 30rpx; font-weight: 600; }
.entry-sub { display: block; font-size: 22rpx; color: #9ca3af; margin-top: 6rpx; }
.entry-arrow { font-size: 28rpx; color: #d1d5db; }
</style>
