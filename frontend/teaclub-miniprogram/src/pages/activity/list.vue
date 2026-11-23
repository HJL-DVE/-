<template>
  <view class="page">
    <view class="header">
      <text class="title">营销活动</text>
      <text class="sub">参与活动赢积分与优惠券</text>
    </view>

    <view v-if="loading" class="state">加载中...</view>
    <view v-else-if="error" class="state err">
      <text>{{ error }}</text>
      <button class="btn-retry" size="mini" @tap="load">重试</button>
    </view>
    <view v-else-if="list.length === 0" class="state">暂无可参与的活动</view>

    <view v-else class="list">
      <view v-for="a in list" :key="a.id" class="card">
        <view class="card-mid">
          <view class="name-row">
            <text class="name">{{ a.activityName }}</text>
            <text class="tag">{{ typeLabel(a.activityType) }}</text>
          </view>
          <text class="desc" v-if="a.description">{{ a.description }}</text>
          <view class="meta-row">
            <text class="meta">有效期 {{ dateText(a.startTime) }} ~ {{ dateText(a.endTime) }}</text>
          </view>
          <view class="meta-row" v-if="a.remainTimes != null">
            <text class="meta">剩余可参与 {{ a.remainTimes }} 次</text>
          </view>
        </view>
        <view class="card-right">
          <button
            class="btn-join"
            size="mini"
            :disabled="a.joined || joiningId === a.id"
            @tap="join(a)"
          >
            {{ a.joined ? '已参与' : (joiningId === a.id ? '参与中' : '参与') }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getActivities, joinActivity, type ActivityItem } from '@/api/marketing'
import { useStoreStore } from '@/stores/store'
import { toast } from '@/utils/request'

const storeStore = useStoreStore()
const list = ref<ActivityItem[]>([])
const loading = ref(false)
const error = ref('')
const joiningId = ref<number | null>(null)

const TYPE_LABEL: Record<string, string> = {
  NEW_MEMBER_GIFT: '新人礼',
  BIRTHDAY: '生日',
  FESTIVAL: '节日',
  CONTINUOUS_SIGN: '签到',
  FULL_GIFT: '满赠',
  POINTS_MALL: '积分',
  STORE_SPECIAL: '门店',
  SLEEPING_RECALL: '召回',
}

function typeLabel(t: string): string {
  return TYPE_LABEL[t] || t
}

function dateText(v?: string | null): string {
  if (!v) return '不限'
  return v.slice(0, 10)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const sid = storeStore.currentStoreId
    list.value = (await getActivities(sid ?? undefined)) || []
  } catch (e) {
    error.value = e instanceof Error ? e.message : '活动加载失败'
  } finally {
    loading.value = false
  }
}

async function join(a: ActivityItem) {
  if (a.joined || joiningId.value !== null) return
  joiningId.value = a.id
  try {
    const ok = await joinActivity(a.id, storeStore.currentStoreId ?? undefined)
    if (ok) {
      toast('参与成功，奖励已发放')
    } else {
      toast('参与失败，可能已达参与上限')
    }
    await load()
  } catch (e) {
    console.error('[activity/join]', e)
    toast('参与失败')
  } finally {
    joiningId.value = null
  }
}

onShow(() => {
  load()
})
</script>

<style scoped>
.header {
  padding: 24rpx 24rpx 8rpx;
}
.title {
  font-size: 36rpx;
  font-weight: 700;
  color: #16a34a;
}
.sub {
  display: block;
  font-size: 24rpx;
  color: #6b7280;
  margin-top: 6rpx;
}
.state {
  padding: 80rpx 24rpx;
  text-align: center;
  color: #9ca3af;
  font-size: 26rpx;
}
.state.err {
  color: #dc2626;
}
.btn-retry {
  margin-top: 16rpx;
}
.list {
  padding: 8rpx 24rpx 40rpx;
}
.card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx 20rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}
.card-mid {
  flex: 1;
}
.name-row {
  display: flex;
  align-items: center;
}
.name {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2937;
}
.tag {
  margin-left: 12rpx;
  font-size: 20rpx;
  color: #16a34a;
  background: #dcfce7;
  border-radius: 20rpx;
  padding: 2rpx 12rpx;
}
.desc {
  display: block;
  font-size: 24rpx;
  color: #6b7280;
  margin-top: 8rpx;
}
.meta-row {
  margin-top: 6rpx;
}
.meta {
  font-size: 22rpx;
  color: #9ca3af;
}
.card-right {
  margin-left: 16rpx;
}
.btn-join {
  background: #16a34a;
  color: #fff;
  font-size: 24rpx;
  border-radius: 999rpx;
  padding: 0 28rpx;
  line-height: 56rpx;
  height: 56rpx;
}
.btn-join[disabled] {
  background: #d1d5db;
  color: #fff;
}
</style>
