<template>
  <view class="page">
    <view v-if="loading" class="state">加载中…</view>
    <view v-else-if="error" class="state">
      {{ error }}
      <button class="btn-plain retry" size="mini" @tap="load">重试</button>
    </view>
    <view v-else-if="!spu" class="state">商品不存在（接口返回空）</view>

    <view v-else>
      <image v-if="spuImage(spu)" class="hero-img" :src="spuImage(spu)" mode="aspectFill" />
      <view v-else class="hero-img placeholder">暂无图片</view>

      <view class="card">
        <view class="name">{{ spu.spuName }}</view>
        <view v-if="spu.description" class="muted sub">{{ spu.description }}</view>
        <view class="row-between price-row">
          <text class="price big">¥{{ fen2yuan(currentPrice) }}</text>
          <text class="muted">{{ spu.unit || '' }}</text>
        </view>
      </view>

      <view class="card">
        <view class="section-title">规格</view>
        <view v-if="skus.length === 0" class="state">该商品未配置 SKU</view>
        <view v-else class="chips">
          <view
            v-for="sku in skus"
            :key="skuIdOf(sku)"
            class="chip"
            :class="{ active: selectedSkuId === skuIdOf(sku) }"
            @tap="selectSku(skuIdOf(sku))"
          >
            {{ sku.skuName }}
          </view>
        </view>

        <!-- 规格分组来自后端 specJson（实测为中文键：杯型 / 温度 …），按分组渲染 -->
        <view v-for="group in groups" :key="group.key" class="spec-block">
          <view class="section-title">{{ group.label }}</view>
          <view v-if="group.kind === 'other'" class="muted spec-info">
            {{ group.options.join(' / ') }}
          </view>
          <view v-else class="chips">
            <view
              v-for="(opt, i) in group.options"
              :key="group.key + i"
              class="chip"
              :class="{ active: isActive(group, opt) }"
              @tap="pickOption(group, opt)"
            >
              {{ opt }}
            </view>
          </view>
        </view>
        <view v-if="groups.length === 0" class="muted spec-info">该规格未配置可选糖度/温度/加料</view>

        <view class="row-between qty-row">
          <text class="section-title">数量</text>
          <view class="qty">
            <text class="qty-btn" @tap="changeQty(-1)">-</text>
            <text class="qty-num">{{ quantity }}</text>
            <text class="qty-btn" @tap="changeQty(1)">+</text>
          </view>
        </view>
      </view>

      <view class="footer">
        <view class="footer-total">
          合计 <text class="price">¥{{ fen2yuan(currentPrice * quantity) }}</text>
        </view>
        <button class="btn-primary add-btn" @tap="addCart">加入购物车</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import * as productApi from '@/api/product'
import type { SkuItem, SpuDetail, SpuItem } from '@/api/types'
import { useStoreStore } from '@/stores/store'
import { useCartStore } from '@/stores/cart'
import { fen2yuan } from '@/utils/money'
import { skuIdOf, skuPrice, specGroups, spuImage } from '@/utils/normalize'
import type { SpecGroup } from '@/utils/normalize'
import { toast } from '@/utils/request'

const storeStore = useStoreStore()
const cartStore = useCartStore()

const spuId = ref<string>('')
const spu = ref<SpuItem | null>(null)
const skus = ref<SkuItem[]>([])
const selectedSkuId = ref<number>(0)
const picks = ref<Record<string, string | string[]>>({})
const quantity = ref(1)
const loading = ref(false)
const error = ref('')

const selectedSku = computed(() => skus.value.find((s) => skuIdOf(s) === selectedSkuId.value) || null)
const currentPrice = computed(() => (selectedSku.value ? skuPrice(selectedSku.value) : 0))
const groups = computed<SpecGroup[]>(() => specGroups(selectedSku.value ? selectedSku.value.specJson : null))

async function load() {
  if (storeStore.currentStoreId === null) {
    try {
      await storeStore.fetchStores()
    } catch (e) {
      console.error('[auth/stores]', e)
    }
  }
  if (storeStore.currentStoreId === null) {
    error.value = '未选择门店'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const data: SpuDetail = await productApi.getSpuDetail(storeStore.currentStoreId, spuId.value)
    spu.value = data && data.spu ? data.spu : null
    const list = data && Array.isArray(data.skus) ? data.skus : []
    skus.value = list
    if (list.length > 0) selectSku(skuIdOf(list[0]))
  } catch (e) {
    error.value = e instanceof Error ? e.message : '商品详情加载失败'
  } finally {
    loading.value = false
  }
}

onLoad((query) => {
  spuId.value = query && query.spuId ? String(query.spuId) : ''
  if (!spuId.value) {
    error.value = '缺少商品参数 spuId'
    return
  }
  load()
})

function isActive(group: SpecGroup, option: string): boolean {
  const picked = picks.value[group.key]
  if (Array.isArray(picked)) return picked.indexOf(option) >= 0
  return picked === option
}

function pickOption(group: SpecGroup, option: string) {
  if (group.kind === 'topping') {
    const cur = picks.value[group.key]
    const list = Array.isArray(cur) ? cur.slice() : []
    const idx = list.indexOf(option)
    if (idx >= 0) list.splice(idx, 1)
    else list.push(option)
    picks.value = { ...picks.value, [group.key]: list }
    return
  }
  picks.value = { ...picks.value, [group.key]: option }
}

/** 按分组类型归并成购物车的糖度 / 温度 / 加料 */
function collectSpec() {
  let sugar = ''
  let ice = ''
  const toppings: string[] = []
  groups.value.forEach((g) => {
    const picked = picks.value[g.key]
    if (g.kind === 'sugar' && typeof picked === 'string') sugar = picked
    else if (g.kind === 'ice' && typeof picked === 'string') ice = picked
    else if (g.kind === 'topping' && Array.isArray(picked)) picked.forEach((p) => toppings.push(p))
  })
  return { sugar, ice, toppings }
}

function selectSku(id: number) {
  selectedSkuId.value = id
  const sku = skus.value.find((s) => skuIdOf(s) === id) || null
  const next: Record<string, string | string[]> = {}
  specGroups(sku ? sku.specJson : null).forEach((g) => {
    if (g.kind === 'other') return
    next[g.key] = g.kind === 'topping' ? [] : g.options[0]
  })
  picks.value = next
}

function changeQty(delta: number) {
  const next = quantity.value + delta
  if (next < 1 || next > 99) return
  quantity.value = next
}

function addCart() {
  if (!spu.value) return
  if (!selectedSku.value) {
    toast('请选择规格')
    return
  }
  if (storeStore.currentStoreId === null) {
    toast('请先选择门店')
    return
  }
  const spec = collectSpec()
  const toppingText = spec.toppings.length > 0 ? ' 加' + spec.toppings.join('+') : ''
  cartStore.add({
    storeId: Number(storeStore.currentStoreId),
    spuId: Number(spu.value.spuId),
    skuId: skuIdOf(selectedSku.value),
    spuName: spu.value.spuName,
    skuName: selectedSku.value.skuName + toppingText,
    imageUrl: spuImage(spu.value),
    unitPrice: currentPrice.value,
    quantity: quantity.value,
    sugar: spec.sugar,
    ice: spec.ice,
    toppings: spec.toppings,
  })
  toast('已加入购物车')
}
</script>

<style scoped>
.page {
  padding-bottom: 180rpx;
}
.hero-img {
  width: 100%;
  height: 480rpx;
  background: #f3f4f6;
}
.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
}
.name {
  font-size: 36rpx;
  font-weight: 700;
}
.sub {
  margin-top: 8rpx;
}
.price-row {
  margin-top: 20rpx;
}
.price.big {
  font-size: 40rpx;
}
.section-title {
  font-size: 28rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
}
.spec-block {
  margin-top: 28rpx;
}
.spec-info {
  font-size: 26rpx;
  color: #6b7280;
}
.chips {
  display: flex;
  flex-wrap: wrap;
}
.chip {
  padding: 12rpx 28rpx;
  border-radius: 30rpx;
  background: #f3f4f6;
  font-size: 26rpx;
  margin: 0 16rpx 16rpx 0;
  color: #374151;
}
.chip.active {
  background: #dcfce7;
  color: #15803d;
  border: 1rpx solid #16a34a;
}
.qty-row {
  margin-top: 28rpx;
}
.qty {
  display: flex;
  align-items: center;
}
.qty-btn {
  width: 56rpx;
  height: 56rpx;
  line-height: 52rpx;
  text-align: center;
  border-radius: 50%;
  background: #f3f4f6;
  font-size: 32rpx;
}
.qty-num {
  width: 80rpx;
  text-align: center;
  font-size: 30rpx;
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
.footer-total {
  flex: 1;
  font-size: 26rpx;
}
.add-btn {
  width: 300rpx;
}
.retry {
  margin-top: 20rpx;
}
</style>
