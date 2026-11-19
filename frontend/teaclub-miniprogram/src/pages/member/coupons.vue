<template>
  <view class="page">
    <view class="card placeholder">
      <view class="icon">券</view>
      <view class="title">敬请期待</view>
      <view class="muted desc">
        优惠券由营销服务（marketing-service）提供，本阶段未开放。
        当前页面不展示任何伪造优惠券数据。
      </view>
      <view class="muted small">契约 docs/33 第六节：优惠券页面为「预留」，无后端接口。</view>
      <button class="btn-primary back" @tap="goMember">返回会员中心</button>
    </view>
  </view>
</template>

<script setup lang="ts">
function goMember() {
  uni.navigateTo({ url: '/pages/member/index' })
}
</script>

<style scoped>
.placeholder {
  margin-top: 120rpx;
  text-align: center;
  padding: 60rpx 40rpx;
}
.icon {
  width: 120rpx;
  height: 120rpx;
  line-height: 120rpx;
  border-radius: 50%;
  background: #ffedd5;
  color: #b45309;
  font-size: 44rpx;
  margin: 0 auto 24rpx;
}
.title {
  font-size: 34rpx;
  font-weight: 700;
}
.desc {
  margin-top: 20rpx;
  line-height: 1.7;
  font-size: 26rpx;
}
.small {
  margin-top: 16rpx;
  font-size: 22rpx;
}
.back {
  margin-top: 48rpx;
}
</style>
