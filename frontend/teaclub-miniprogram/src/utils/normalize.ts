import type { CategoryItem, SkuItem, SpuItem, StoreItem } from '@/api/types'

/** 实测字段为 spuId / categoryId，页面统一走这两个取值函数 */
export function spuIdOf(spu: SpuItem | null | undefined): number {
  if (!spu) return 0
  return Math.trunc(Number(spu.spuId) || 0)
}

export function categoryIdOf(cat: CategoryItem | null | undefined): number {
  if (!cat) return 0
  return Math.trunc(Number(cat.categoryId) || 0)
}

export function spuImage(spu: SpuItem | null | undefined): string {
  if (!spu) return ''
  return spu.imageUrl || ''
}

export function spuPrice(spu: SpuItem | null | undefined): number {
  if (!spu) return 0
  return Math.trunc(Number(spu.minPrice ?? 0) || 0)
}

export function skuIdOf(sku: SkuItem | null | undefined): number {
  if (!sku) return 0
  return Math.trunc(Number(sku.skuId) || 0)
}

export function skuPrice(sku: SkuItem | null | undefined): number {
  if (!sku) return 0
  return Math.trunc(Number(sku.price) || 0)
}

export function storeBusinessStatus(store: StoreItem | null | undefined): string {
  if (!store) return ''
  return store.businessStatus || ''
}

export function storeBusinessHours(store: StoreItem | null | undefined): string {
  if (!store) return ''
  if (store.openTime || store.closeTime) return (store.openTime || '') + '-' + (store.closeTime || '')
  return ''
}

const OPEN_VALUES = ['OPEN', 'OPENING', 'BUSINESS', 'NORMAL', '1', '营业中']

export function isStoreOpen(store: StoreItem | null | undefined): boolean {
  const status = storeBusinessStatus(store)
  if (!status) return false
  return OPEN_VALUES.indexOf(String(status).toUpperCase()) >= 0 || OPEN_VALUES.indexOf(String(status)) >= 0
}

export function storeStatusText(store: StoreItem | null | undefined): string {
  const status = storeBusinessStatus(store)
  if (!status) return '营业状态未知'
  if (isStoreOpen(store)) return '营业中'
  return status === 'CLOSED' ? '休息中' : status
}

/** 等级折扣：discountRate 为万分数，10000 = 无折扣，9500 = 9.5 折 */
export function discountLabel(discountRate: number | null | undefined): string {
  const rate = Math.trunc(Number(discountRate ?? 0))
  if (!rate || rate === 10000) return '无折扣'
  const tenth = Math.round(rate / 100) / 10
  return tenth + ' 折'
}

// ---- specJson 解析 ----
// 后端实测内容为中文键：{"杯型": "中杯", "温度": "正常冰"}；
// 契约样例写的是 {sugar, ice, topping}，两种都支持。

export type SpecKind = 'sugar' | 'ice' | 'topping' | 'cup' | 'other'

export interface SpecGroup {
  key: string
  label: string
  kind: SpecKind
  options: string[]
}

const SUGAR_HINTS = ['糖', 'sugar']
const ICE_HINTS = ['温度', '冰', 'ice', 'temperature']
const TOPPING_HINTS = ['加料', '小料', '佐料', 'topping']
const CUP_HINTS = ['杯型', '杯', '规格', 'cup', 'size']

export function classifySpecLabel(label: string): SpecKind {
  const key = label.toLowerCase()
  if (TOPPING_HINTS.some((h) => key.indexOf(h.toLowerCase()) >= 0)) return 'topping'
  if (SUGAR_HINTS.some((h) => key.indexOf(h.toLowerCase()) >= 0)) return 'sugar'
  if (ICE_HINTS.some((h) => key.indexOf(h.toLowerCase()) >= 0)) return 'ice'
  if (CUP_HINTS.some((h) => key.indexOf(h.toLowerCase()) >= 0)) return 'cup'
  return 'other'
}

function parseRaw(specJson: unknown): Record<string, unknown> {
  if (!specJson) return {}
  if (typeof specJson === 'string') {
    try {
      const parsed = JSON.parse(specJson)
      return parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : {}
    } catch (e) {
      return {}
    }
  }
  if (typeof specJson === 'object') return specJson as Record<string, unknown>
  return {}
}

/** 解析成「分组」结构，页面按分组渲染规格按钮 */
export function specGroups(specJson: unknown): SpecGroup[] {
  const raw = parseRaw(specJson)
  const groups: SpecGroup[] = []
  Object.keys(raw).forEach((label) => {
    const value = raw[label]
    const options = Array.isArray(value) ? value.map((v) => String(v)) : value === null || value === undefined ? [] : [String(value)]
    if (options.length === 0) return
    groups.push({ key: label, label, kind: classifySpecLabel(label), options })
  })
  return groups
}

export interface SpecOptions {
  sugar: string[]
  ice: string[]
  topping: string[]
  cup: string[]
}

/** 归并成购物车需要的糖度 / 温度 / 加料三类 */
export function parseSpec(specJson: unknown): SpecOptions {
  const result: SpecOptions = { sugar: [], ice: [], topping: [], cup: [] }
  specGroups(specJson).forEach((g) => {
    if (g.kind === 'sugar') result.sugar = g.options
    else if (g.kind === 'ice') result.ice = g.options
    else if (g.kind === 'topping') result.topping = g.options
    else if (g.kind === 'cup') result.cup = g.options
  })
  return result
}

export function formatTime(value: string | null | undefined): string {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 19)
}
