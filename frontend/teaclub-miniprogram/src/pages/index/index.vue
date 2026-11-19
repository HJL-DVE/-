<template>
  <view class="page">
    <!-- 品牌区 -->
    <view class="brand">
      <view class="brand-title">TeaClub 奶茶</view>
      <view class="brand-sub">会员储值 · 充值有礼 · 积分兑换</view>
    </view>

    <!-- 门店选择 -->
    <view class="card store-card" @tap="goStoreList">
      <view class="row-between">
        <view>
          <view class="store-name">{{ storeStore.currentStoreName }}</view>
          <view class="muted">{{ storeStore.currentStore ? storeStore.currentStore.address || '' : '请选择门店后下单' }}</view>
        </view>
        <view class="switch-btn">切换门店</view>
      </view>
      <view v-if="storeStore.currentStore" class="store-meta">
        <text class="tag" :class="isOpen ? 'tag-open' : 'tag-close'">{{ storeStore.currentStoreStatusText }}</text>
        <text class="muted hours">{{ businessHours }}</text>
      </view>
    </view>

    <!-- 品牌轮播（静态宣传位，非接口数据） -->
    <swiper class="banner" :indicator-dots="true" :autoplay="true" :interval="4000" :circular="true">
      <swiper-item v-for="(b, i) in banners" :key="i">
        <view class="banner-item" :style="{ background: b.bg }">
          <view class="banner-title">{{ b.title }}</view>
          <view class="banner-desc">{{ b.desc }}</view>
        </view>
      </swiper-item>
    </swiper>

    <!-- 功能入口 -->
    <view class="quick">
      <view class="quick-item" @tap="goRecharge">
        <view class="quick-icon">充</view>
        <view class="quick-text">充值有礼</view>
      </view>
      <view class="quick-item" @tap="goProductList">
        <view class="quick-icon">点</view>
        <view class="quick-text">在线点单</view>
      </view>
      <view class="quick-item" @tap="goCart">
        <view class="quick-icon">车</view>
        <view class="quick-text">购物车</view>
      </view>
      <view class="quick-item" @tap="goMember">
        <view class="quick-icon">卡</view>
        <view class="quick-text">会员中心</view>
      </view>
    </view>

    <!-- 热门商品 -->
    <view class="section">
      <view class="row-between section-head">
        <text class="section-title">热门商品</text>
        <text class="muted" @tap="goProductList">全部 ›</text>
      </view>
      <view v-if="hotLoading" class="state">加载中…</view>
      <view v-else-if="hotError" class="state">
        <view>{{ hotError }}</view>
        <button class="btn-plain retry" size="mini" @tap="loadHot">重试</button>
      </view>
      <view v-else-if="hotList.length === 0" class="state">该门店暂无热门商品</view>
      <scroll-view v-else scroll-x class="hot-scroll">
        <view v-for="spu in hotList" :key="spuIdOf(spu)" class="hot-item" @tap="goProductDetail(spuIdOf(spu))">
          <image v-if="spuImage(spu)" class="hot-img" :src="spuImage(spu)" mode="aspectFill" />
          <view v-else class="hot-img placeholder">暂无图</view>
          <view class="hot-name">{{ spu.spuName }}</view>
          <view class="price">¥{{ fen2yuan(spuPrice(spu)) }}</view>
        </view>
      </scroll-view>
    </view>

    <!-- 分类 -->
    <view class="section">
      <view class="row-between section-head">
        <text class="section-title">商品分类</text>
        <text class="muted" @tap="goProductList">全部 ›</text>
      </view>
      <view v-if="categoryLoading" class="state">加载中…</view>
      <view v-else-if="categoryError" class="state">
        <view>{{ categoryError }}</view>
        <button class="btn-plain retry" size="mini" @tap="loadCategories">重试</button>
      </view>
      <view v-else-if="categories.length === 0" class="state">该门店暂无可售分类</view>
      <view v-else class="cat-grid">
        <view v-for="cat in categories" :key="categoryIdOf(cat)" class="cat-item" @tap="goCategory(categoryIdOf(cat))">
          <image v-if="cat.iconUrl" class="cat-icon" :src="cat.iconUrl" mode="aspectFill" />
          <view v-else class="cat-icon placeholder">类</view>
          <view class="cat-name">{{ cat.categoryName }}</view>
        </view>
      </view>
    </view>

    <!-- 充值入口 -->
    <view class="card recharge-entry" @tap="goRecharge">
      <view>
        <view class="recharge-title">储值充值 · 充得多送得多</view>
        <view class="muted">充值金额由后端套餐配置，赠送金额随套餐</view>
      </view>
      <view class="recharge-btn">去充值</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useStoreStore } from '@/stores/store'
import { useCartStore } from '@/stores/cart'
import * as productApi from '@/api/product'
import type { CategoryItem, SpuItem } from '@/api/types'
import { fen2yuan } from '@/utils/money'
import { categoryIdOf, isStoreOpen, spuIdOf, spuImage, spuPrice, storeBusinessHours } from '@/utils/normalize'

const storeStore = useStoreStore()
const cartStore = useCartStore()

const banners = [
  { title: '新鲜现制', desc: '当日鲜奶 · 4 小时鲜茶', bg: 'linear-gradient(135deg,#16a34a,#4ade80)' },
  { title: '会员储值', desc: '充值套餐 · 赠送金额', bg: 'linear-gradient(135deg,#f59e0b,#fbbf24)' },
  { title: '门店自提', desc: '线上下单 · 到店取茶', bg: 'linear-gradient(135deg,#0ea5e9,#38bdf8)' },
]

const hotList = ref<SpuItem[]>([])
const hotLoading = ref(false)
const hotError = ref('')

const categories = ref<CategoryItem[]>([])
const categoryLoading = ref(false)
const categoryError = ref('')

const isOpen = computed(() => isStoreOpen(storeStore.currentStore))
const businessHours = computed(() => storeBusinessHours(storeStore.currentStore))

async function ensureStore() {
  try {
    await storeStore.fetchStores()
  } catch (e) {
    // 门店接口失败：页面仍可展示，错误在门店卡片上体现
  }
  cartStore.setStore(storeStore.currentStoreId)
}

async function loadHot() {
  if (storeStore.currentStoreId === null) {
    hotList.value = []
    return
  }
  hotLoading.value = true
  hotError.value = ''
  try {
    const list = await productApi.getHotProducts(storeStore.currentStoreId, 6)
    hotList.value = Array.isArray(list) ? list : []
  } catch (e) {
    hotError.value = e instanceof Error ? e.message : '热门商品加载失败'
  } finally {
    hotLoading.value = false
  }
}

async function loadCategories() {
  if (storeStore.currentStoreId === null) {
    categories.value = []
    return
  }
  categoryLoading.value = true
  categoryError.value = ''
  try {
    const list = await productApi.getCategories(storeStore.currentStoreId)
    categories.value = Array.isArray(list) ? list : []
  } catch (e) {
    categoryError.value = e instanceof Error ? e.message : '分类加载失败'
  } finally {
    categoryLoading.value = false
  }
}

onShow(async () => {
  const before = storeStore.currentStoreId
  await ensureStore()
  if (before !== storeStore.currentStoreId || hotList.value.length === 0) {
    loadHot()
    loadCategories()
  }
})

function goStoreList() {
  uni.navigateTo({ url: '/pages/store/list' })
}
function goRecharge() {
  uni.navigateTo({ url: '/pages/recharge/index' })
}
function goProductList() {
  uni.navigateTo({ url: '/pages/product/list' })
}
function goCart() {
  uni.navigateTo({ url: '/pages/cart/index' })
}
function goMember() {
  uni.navigateTo({ url: '/pages/member/index' })
}
function goCategory(categoryId: number) {
  uni.navigateTo({ url: '/pages/product/list?categoryId=' + categoryId })
}
function goProductDetail(spuId: number) {
  uni.navigateTo({ url: '/pages/product/detail?spuId=' + spuId })
}
</script>

<style scoped>
.page {
  padding-bottom: 40rpx;
}
.brand {
  background: linear-gradient(135deg, #16a34a, #4ade80);
  padding: 40rpx 32rpx 56rpx;
  color: #ffffff;
}
.brand-title {
  font-size: 44rpx;
  font-weight: 700;
}
.brand-sub {
  margin-top: 10rpx;
  font-size: 24rpx;
  opacity: 0.92;
}
.store-card {
  margin-top: -32rpx;
}
.store-name {
  font-size: 32rpx;
  font-weight: 600;
}
.store-meta {
  margin-top: 16rpx;
  display: flex;
  align-items: center;
}
.hours {
  margin-left: 16rpx;
}
.switch-btn {
  font-size: 24rpx;
  color: #16a34a;
  border: 1rpx solid #16a34a;
  border-radius: 30rpx;
  padding: 6rpx 20rpx;
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
.banner {
  height: 220rpx;
  margin: 20rpx 24rpx;
  border-radius: 20rpx;
  overflow: hidden;
}
.banner-item {
  height: 220rpx;
  color: #ffffff;
  padding: 40rpx 32rpx;
  box-sizing: border-box;
}
.banner-title {
  font-size: 40rpx;
  font-weight: 700;
}
.banner-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  opacity: 0.92;
}
.quick {
  display: flex;
  background: #ffffff;
  margin: 20rpx 24rpx;
  border-radius: 20rpx;
  padding: 24rpx 0;
}
.quick-item {
  flex: 1;
  text-align: center;
}
.quick-icon {
  width: 72rpx;
  height: 72rpx;
  line-height: 72rpx;
  margin: 0 auto;
  border-radius: 50%;
  background: #dcfce7;
  color: #15803d;
  font-size: 30rpx;
}
.quick-text {
  margin-top: 10rpx;
  font-size: 24rpx;
}
.section {
  margin: 20rpx 24rpx;
  background: #ffffff;
  border-radius: 20rpx;
  padding: 24rpx;
}
.section-head {
  margin-bottom: 20rpx;
}
.section-title {
  font-size: 30rpx;
  font-weight: 600;
}
.hot-scroll {
  white-space: nowrap;
}
.hot-item {
  display: inline-block;
  width: 200rpx;
  margin-right: 20rpx;
}
.hot-img {
  width: 200rpx;
  height: 200rpx;
  border-radius: 16rpx;
  background: #f3f4f6;
}
.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  font-size: 22rpx;
}
.hot-name {
  margin-top: 10rpx;
  font-size: 26rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.cat-grid {
  display: flex;
  flex-wrap: wrap;
}
.cat-item {
  width: 25%;
  text-align: center;
  margin-bottom: 20rpx;
}
.cat-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: #f3f4f6;
}
.cat-name {
  margin-top: 10rpx;
  font-size: 24rpx;
}
.retry {
  margin-top: 16rpx;
}
.recharge-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #fff7ed, #ffedd5);
}
.recharge-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #b45309;
}
.recharge-btn {
  background: #f59e0b;
  color: #ffffff;
  font-size: 26rpx;
  padding: 12rpx 28rpx;
  border-radius: 30rpx;
}
</style>
