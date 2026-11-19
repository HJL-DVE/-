<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-select
          :model-value="appStore.currentStoreId"
          placeholder="选择门店（全局上下文）"
          filterable
          style="width: 240px"
          @update:model-value="onChangeStore"
        >
          <el-option v-for="store in appStore.stores" :key="store.id" :label="store.storeName" :value="store.id" />
        </el-select>
        <el-input v-model="keyword" placeholder="商品名称/编码" clearable style="width: 200px" @keyup.enter="search" />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <div class="toolbar-spacer" />
        <span class="text-muted">
          GET /api/admin/product/store-products?storeId={{ appStore.currentStoreId }} · 共 {{ total }} 条
        </span>
      </div>

      <el-table :data="records" v-loading="loading" border stripe row-key="id">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding: 8px 24px">
              <el-table :data="row.skus || []" size="small" border>
                <el-table-column prop="skuId" label="SKU ID" width="100" />
                <el-table-column prop="skuName" label="规格" min-width="160" />
                <el-table-column label="基础价" width="110" align="right">
                  <template #default="scope">{{ fenToYuan(scope.row.basePrice) }}</template>
                </el-table-column>
                <el-table-column label="门店价" width="110" align="right">
                  <template #default="scope">
                    <span class="money">{{ fenToYuan(scope.row.storePrice ?? scope.row.basePrice) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="在售" width="90">
                  <template #default="scope">
                    <el-tag size="small" :type="scope.row.isOnSale === 1 ? 'success' : 'info'">
                      {{ scope.row.isOnSale === 1 ? '是' : '否' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120">
                  <template #default="scope">
                    <el-button link type="primary" @click="openPrice(row, scope.row)">改价</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="spuCode" label="商品编码" width="130" />
        <el-table-column prop="spuName" label="商品名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="基础价" width="110" align="right">
          <template #default="{ row }">{{ fenToYuan(row.basePrice) }}</template>
        </el-table-column>
        <el-table-column label="门店最低价" width="120" align="right">
          <template #default="{ row }"><span class="money">{{ fenToYuan(row.minPrice) }}</span></template>
        </el-table-column>
        <el-table-column label="门店在售" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isOnSale === 1 ? 'success' : 'info'">
              {{ row.isOnSale === 1 ? '在售' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="toggleShelf(row)">
              {{ row.isOnSale === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button link type="danger" @click="onRemove(row)">从门店移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="search"
        />
      </div>
    </div>

    <el-dialog v-model="priceVisible" title="修改门店售价" width="420px">
      <el-form label-width="100px">
        <el-form-item label="商品">{{ priceForm.spuName }}</el-form-item>
        <el-form-item label="规格">{{ priceForm.skuName }}</el-form-item>
        <el-form-item label="门店价（元）">
          <el-input v-model="priceForm.storePriceYuan" placeholder="例如 18.00" />
        </el-form-item>
        <el-form-item label="在售">
          <el-switch v-model="priceForm.isOnSale" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="priceVisible = false">取消</el-button>
        <el-button type="primary" :loading="priceSubmitting" @click="submitPrice">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getStoreProducts, removeStoreSpu, setStoreSkuPrice, shelfStoreSpu } from '@/api/product'
import { useAppStore } from '@/stores/app'
import { fenToYuan, yuanToFen } from '@/utils/format'
import type { StoreSkuVO, StoreSpuVO } from '@/api/types'

const appStore = useAppStore()

const loading = ref(false)
const records = ref<StoreSpuVO[]>([])
const total = ref(0)
const keyword = ref('')
const pageNo = ref(1)
const pageSize = ref(20)

async function load(): Promise<void> {
  if (!appStore.currentStoreId) {
    records.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const page = await getStoreProducts({
      storeId: appStore.currentStoreId,
      keyword: keyword.value || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    })
    records.value = page.records || []
    total.value = page.total || 0
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNo.value = 1
  load()
}

function onChangeStore(id: number): void {
  appStore.setStore(id)
  search()
}

const priceVisible = ref(false)
const priceSubmitting = ref(false)
const priceForm = reactive({
  storeId: 0,
  skuId: 0,
  spuName: '',
  skuName: '',
  storePriceYuan: '',
  isOnSale: 1,
})

function openPrice(spu: StoreSpuVO, sku: StoreSkuVO): void {
  priceForm.storeId = appStore.currentStoreId
  priceForm.skuId = sku.skuId
  priceForm.spuName = spu.spuName
  priceForm.skuName = sku.skuName
  priceForm.storePriceYuan = fenToYuan(sku.storePrice ?? sku.basePrice)
  priceForm.isOnSale = sku.isOnSale ?? 1
  priceVisible.value = true
}

async function submitPrice(): Promise<void> {
  const fen = yuanToFen(priceForm.storePriceYuan)
  if (Number.isNaN(fen) || fen < 0) {
    ElMessage.error('请输入合法金额（最多两位小数）')
    return
  }
  priceSubmitting.value = true
  try {
    await setStoreSkuPrice(priceForm.storeId, priceForm.skuId, fen, priceForm.isOnSale)
    ElMessage.success('门店售价已更新')
    priceVisible.value = false
    await load()
  } finally {
    priceSubmitting.value = false
  }
}

function toggleShelf(row: StoreSpuVO): void {
  const next = row.isOnSale === 1 ? 0 : 1
  shelfStoreSpu(appStore.currentStoreId, row.spuId, next)
    .then(async () => {
      ElMessage.success('门店上下架已更新')
      await load()
    })
    .catch(() => undefined)
}

function onRemove(row: StoreSpuVO): void {
  ElMessageBox.confirm('确认将「' + row.spuName + '」从本门店移除？', '提示', { type: 'warning' })
    .then(async () => {
      await removeStoreSpu(appStore.currentStoreId, row.spuId)
      ElMessage.success('已移除')
      await load()
    })
    .catch(() => undefined)
}

onMounted(load)
watch(() => appStore.currentStoreId, load)
</script>
