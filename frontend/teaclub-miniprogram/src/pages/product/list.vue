<template>
  <view class="page">
    <view class="search">
      <input v-model="keyword" class="search-input" placeholder="搜索商品" confirm-type="search" @confirm="loadSpus" />
      <text class="search-btn" @tap="loadSpus">搜索</text>
    </view>

    <view class="tabs">
      <scroll-view scroll-x class="tabs-scroll">
        <view class="tab" :class="{ active: activeCategory === null }" @tap="selectCategory(null)">全部</view>
        <view
          v-for="cat in categories"
          :key="categoryIdOf(cat)"
          class="tab"
          :class="{ active: activeCategory === categoryIdOf(cat) }"
          @tap="selectCategory(categoryIdOf(cat))"
        >
          {{ cat.categoryName }}
        </view>
      </scroll-view>
    </view>

    <view v-if="loading" class="state">加载中…</view>
    <view v-else-if="error" class="state">
      {{ error }}
      <button class="btn-plain retry" size="mini" @tap="loadAll">重试</button>
    </view>
    <view v-else-if="spus.length === 0" class="state">暂无可售商品</view>

    <view v-else class="list">
      <view v-for="spu in spus" :key="spuIdOf(spu)" class="card item" @tap="goDetail(spuIdOf(spu))">
        <image v-if="spuImage(spu)" class="item-img" :src="spuImage(spu)" mode="aspectFill" />
        <view v-else class="item-img placeholder">暂无图</view>
        <view class="item-info">
          <view class="item-name">{{ spu.spuName }}</view>
          <view class="muted item-sub">{{ spu.subtitle || '' }}</view>
          <view class="row-between item-bottom">
            <text class="price">¥{{ fen2yuan(spuPrice(spu)) }} 起</text>
            <text class="add">+</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import * as productApi from '@/api/product'
import type { CategoryItem, SpuItem } from '@/api/types'
import { useStoreStore } from '@/stores/store'
import { fen2yuan } from '@/utils/money'
import { categoryIdOf, spuIdOf, spuImage, spuPrice } from '@/utils/normalize'

const storeStore = useStoreStore()
const categories = ref<CategoryItem[]>([])
const spus = ref<SpuItem[]>([])
const activeCategory = ref<number | null>(null)
const keyword = ref('')
const loading = ref(false)
const error = ref('')

function normalizeList(res: SpuItem[] | { records: SpuItem[] } | null | undefined): SpuItem[] {
  if (!res) return []
  if (Array.isArray(res)) return res
  return Array.isArray(res.records) ? res.records : []
}

async function loadCategories() {
  if (storeStore.currentStoreId === null) return
  try {
    const list = await productApi.getCategories(storeStore.currentStoreId)
    categories.value = Array.isArray(list) ? list : []
  } catch (e) {
    console.error('[product/categories]', e)
  }
}

async function loadSpus() {
  if (storeStore.currentStoreId === null) {
    error.value = '未选择门店，请先选择门店'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const res = await productApi.getSpus(storeStore.currentStoreId, {
      categoryId: activeCategory.value === null ? undefined : activeCategory.value,
      keyword: keyword.value || undefined,
    })
    spus.value = normalizeList(res)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '商品加载失败'
  } finally {
    loading.value = false
  }
}

async function loadAll() {
  if (storeStore.currentStoreId === null) {
    try {
      await storeStore.fetchStores()
    } catch (e) {
      console.error('[auth/stores]', e)
    }
  }
  await loadCategories()
  await loadSpus()
}

onLoad((query) => {
  if (query && query.categoryId) activeCategory.value = Number(query.categoryId)
  loadAll()
})

function selectCategory(id: number | null) {
  activeCategory.value = id
  loadSpus()
}

function goDetail(spuId: number) {
  uni.navigateTo({ url: '/pages/product/detail?spuId=' + spuId })
}
</script>

<style scoped>
.search {
  display: flex;
  align-items: center;
  padding: 20rpx 24rpx;
  background: #ffffff;
}
.search-input {
  flex: 1;
  background: #f3f4f6;
  border-radius: 30rpx;
  padding: 14rpx 24rpx;
  font-size: 26rpx;
}
.search-btn {
  margin-left: 20rpx;
  color: #16a34a;
  font-size: 26rpx;
}
.tabs {
  background: #ffffff;
  padding: 12rpx 0;
  border-bottom: 1rpx solid #e5e7eb;
}
.tabs-scroll {
  white-space: nowrap;
}
.tab {
  display: inline-block;
  padding: 10rpx 28rpx;
  font-size: 26rpx;
  color: #6b7280;
}
.tab.active {
  color: #16a34a;
  font-weight: 600;
}
.item {
  display: flex;
}
.item-img {
  width: 180rpx;
  height: 180rpx;
  border-radius: 16rpx;
  background: #f3f4f6;
}
.item-info {
  flex: 1;
  margin-left: 24rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.item-name {
  font-size: 30rpx;
  font-weight: 600;
}
.item-sub {
  margin-top: 8rpx;
}
.item-bottom {
  margin-top: 20rpx;
}
.add {
  width: 52rpx;
  height: 52rpx;
  line-height: 48rpx;
  text-align: center;
  border-radius: 50%;
  background: #16a34a;
  color: #ffffff;
  font-size: 34rpx;
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
