import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getAccessibleStores } from '@/api/store'
import type { StoreVO } from '@/api/types'

const STORE_KEY = 'teaclub-admin-current-store'

/**
 * 全局门店上下文：切换门店后列表/统计随之变化。
 * 门店来源固定为 /api/admin/store/stores/accessible（当前用户可访问门店）。
 */
export const useAppStore = defineStore('app', () => {
  const stores = ref<StoreVO[]>([])
  const currentStoreId = ref<number>(Number(localStorage.getItem(STORE_KEY) || 0))
  const storesLoaded = ref(false)
  const collapsed = ref(false)

  const currentStore = computed<StoreVO | null>(
    () => stores.value.find((item) => item.id === currentStoreId.value) ?? null,
  )

  const currentStoreName = computed(() => currentStore.value?.storeName ?? '未选择门店')

  function setStore(id: number): void {
    currentStoreId.value = id
    if (id) localStorage.setItem(STORE_KEY, String(id))
    else localStorage.removeItem(STORE_KEY)
  }

  async function loadStores(force = false): Promise<StoreVO[]> {
    if (storesLoaded.value && !force) return stores.value
    const list = await getAccessibleStores()
    stores.value = list || []
    storesLoaded.value = true
    if (!stores.value.some((item) => item.id === currentStoreId.value)) {
      setStore(stores.value[0]?.id ?? 0)
    }
    return stores.value
  }

  function toggleCollapsed(): void {
    collapsed.value = !collapsed.value
  }

  return {
    stores,
    currentStoreId,
    currentStore,
    currentStoreName,
    storesLoaded,
    collapsed,
    setStore,
    loadStores,
    toggleCollapsed,
  }
})
