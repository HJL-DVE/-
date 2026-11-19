import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import type { StoreItem } from '@/api/types'
import { storeStatusText } from '@/utils/normalize'

const CURRENT_STORE_KEY = 'teaclub_current_store'

export const useStoreStore = defineStore('store', () => {
  const stores = ref<StoreItem[]>([])
  const currentStoreId = ref<number | null>(readStoredStoreId())
  const loading = ref(false)
  const error = ref('')

  function readStoredStoreId(): number | null {
    try {
      const raw = uni.getStorageSync(CURRENT_STORE_KEY)
      const n = raw ? Number(raw) : NaN
      return isNaN(n) ? null : n
    } catch (e) {
      return null
    }
  }

  const currentStore = computed<StoreItem | null>(() => {
    if (currentStoreId.value === null) return null
    const hit = stores.value.find((s) => Number(s.id) === Number(currentStoreId.value))
    return hit || null
  })

  const currentStoreName = computed(() => (currentStore.value ? currentStore.value.storeName : '未选择门店'))
  const currentStoreStatusText = computed(() => (currentStore.value ? storeStatusText(currentStore.value) : ''))

  async function fetchStores() {
    loading.value = true
    error.value = ''
    try {
      const list = await authApi.getStores()
      stores.value = Array.isArray(list) ? list : []
      if (currentStoreId.value === null && stores.value.length > 0) {
        setCurrentStore(stores.value[0].id)
      } else if (
        currentStoreId.value !== null &&
        stores.value.length > 0 &&
        !stores.value.some((s) => Number(s.id) === Number(currentStoreId.value))
      ) {
        setCurrentStore(stores.value[0].id)
      }
      return stores.value
    } catch (e) {
      error.value = e instanceof Error ? e.message : '门店加载失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  function setCurrentStore(id: number | string) {
    currentStoreId.value = Number(id)
    uni.setStorageSync(CURRENT_STORE_KEY, String(id))
  }

  return {
    stores,
    currentStoreId,
    currentStore,
    currentStoreName,
    currentStoreStatusText,
    loading,
    error,
    fetchStores,
    setCurrentStore,
  }
})
