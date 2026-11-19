import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

const CART_KEY = 'teaclub_cart'

export interface CartItem {
  key: string
  storeId: number
  spuId: number
  skuId: number
  spuName: string
  skuName: string
  imageUrl: string
  /** 分（整数） */
  unitPrice: number
  quantity: number
  sugar: string
  ice: string
  toppings: string[]
}

export interface AddCartPayload {
  storeId: number
  spuId: number
  skuId: number
  spuName: string
  skuName: string
  imageUrl?: string
  unitPrice: number
  quantity: number
  sugar?: string
  ice?: string
  toppings?: string[]
}

function buildKey(payload: { skuId: number; sugar?: string; ice?: string; toppings?: string[] }): string {
  const toppings = (payload.toppings || []).slice().sort().join('+')
  return payload.skuId + '|' + (payload.sugar || '') + '|' + (payload.ice || '') + '|' + toppings
}

/** 规格合并成备注文案（糖度 / 温度 / 加料） */
export function specRemark(item: CartItem): string {
  const parts: string[] = []
  if (item.sugar) parts.push('糖度:' + item.sugar)
  if (item.ice) parts.push('温度:' + item.ice)
  if (item.toppings && item.toppings.length > 0) parts.push('加料:' + item.toppings.join('+'))
  return parts.join(' / ')
}

export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItem[]>(readCart())
  const currentStoreId = ref<number | null>(null)

  function readCart(): CartItem[] {
    try {
      const raw = uni.getStorageSync(CART_KEY)
      if (!raw) return []
      const parsed = JSON.parse(raw as string)
      return Array.isArray(parsed) ? (parsed as CartItem[]) : []
    } catch (e) {
      return []
    }
  }

  function persist() {
    uni.setStorageSync(CART_KEY, JSON.stringify(items.value))
  }

  function setStore(storeId: number | null) {
    currentStoreId.value = storeId
  }

  /** 当前门店的购物车行 */
  const currentItems = computed<CartItem[]>(() => {
    if (currentStoreId.value === null) return items.value
    return items.value.filter((i) => Number(i.storeId) === Number(currentStoreId.value))
  })

  const totalQuantity = computed(() => currentItems.value.reduce((acc, i) => acc + i.quantity, 0))

  /** 金额一律整数分求和 */
  const totalAmount = computed(() =>
    currentItems.value.reduce((acc, i) => acc + Math.trunc(i.unitPrice) * i.quantity, 0),
  )

  function add(payload: AddCartPayload) {
    const key = buildKey(payload)
    const exist = items.value.find((i) => i.key === key && Number(i.storeId) === Number(payload.storeId))
    if (exist) {
      exist.quantity += payload.quantity
    } else {
      items.value.push({
        key,
        storeId: Number(payload.storeId),
        spuId: Number(payload.spuId),
        skuId: Number(payload.skuId),
        spuName: payload.spuName,
        skuName: payload.skuName,
        imageUrl: payload.imageUrl || '',
        unitPrice: Math.trunc(payload.unitPrice),
        quantity: payload.quantity,
        sugar: payload.sugar || '',
        ice: payload.ice || '',
        toppings: payload.toppings || [],
      })
    }
    persist()
  }

  function increase(key: string) {
    const hit = items.value.find((i) => i.key === key)
    if (hit) {
      hit.quantity += 1
      persist()
    }
  }

  function decrease(key: string) {
    const idx = items.value.findIndex((i) => i.key === key)
    if (idx < 0) return
    const item = items.value[idx]
    if (item.quantity <= 1) items.value.splice(idx, 1)
    else item.quantity -= 1
    persist()
  }

  function remove(key: string) {
    const idx = items.value.findIndex((i) => i.key === key)
    if (idx >= 0) items.value.splice(idx, 1)
    persist()
  }

  function clear() {
    if (currentStoreId.value === null) items.value = []
    else items.value = items.value.filter((i) => Number(i.storeId) !== Number(currentStoreId.value))
    persist()
  }

  /** 下单请求体：仅 skuId + quantity（OrderItemRequest 不含价格字段） */
  function toOrderItems(): Array<{ skuId: number; quantity: number }> {
    return currentItems.value.map((i) => ({ skuId: i.skuId, quantity: i.quantity }))
  }

  /** 规格备注汇总，写入订单级 remark（OrderItemRequest 无法承载 remark） */
  function toRemark(): string {
    return currentItems.value
      .map((i) => {
        const spec = specRemark(i)
        return spec ? i.spuName + '(' + spec + ')×' + i.quantity : ''
      })
      .filter((s) => s.length > 0)
      .join('；')
  }

  return {
    items,
    currentStoreId,
    currentItems,
    totalQuantity,
    totalAmount,
    setStore,
    add,
    increase,
    decrease,
    remove,
    clear,
    toOrderItems,
    toRemark,
  }
})
