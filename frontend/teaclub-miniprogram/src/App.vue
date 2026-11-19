<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'

onLaunch(() => {
  // 应用启动：不做任何伪造数据，仅打印网关地址便于排查
  console.log('[TeaClub] app launch')
})
</script>

<style>
page {
  background-color: #f6f7f9;
  color: #1f2937;
  font-size: 28rpx;
  font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', Arial, sans-serif;
}
.card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 24rpx;
  margin: 20rpx 24rpx;
}
.row {
  display: flex;
  align-items: center;
}
.row-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.muted {
  color: #6b7280;
  font-size: 24rpx;
}
.price {
  color: #dc2626;
  font-weight: 600;
}
.btn-primary {
  background: #16a34a;
  color: #ffffff;
  border-radius: 44rpx;
  font-size: 30rpx;
}
.btn-plain {
  background: #ffffff;
  color: #16a34a;
  border: 1rpx solid #16a34a;
  border-radius: 44rpx;
  font-size: 28rpx;
}
.state {
  padding: 80rpx 24rpx;
  text-align: center;
  color: #6b7280;
  font-size: 26rpx;
}
</style>
