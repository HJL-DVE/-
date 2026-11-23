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
        <el-button v-if="canEditStoreProduct()" type="success" :icon="Plus" @click="openAdd">添加商品</el-button>
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
                <el-table-column v-if="canEditStoreProduct()" label="操作" width="120">
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
        <el-table-column v-if="canEditStoreProduct()" label="操作" width="220" fixed="right">
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

    <!-- 商品管理弹窗：可从总部选品上架，也可现场新建商品（含图片） -->
    <el-dialog v-model="addVisible" title="商品管理" width="820px" destroy-on-close body-class="store-product-dialog">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
        :title="'当前门店：' + (appStore.currentStoreName || '未选择') + '，添加后即可在本店收银台售卖'"
      />

      <el-tabs v-model="addTab">
        <!-- ============ 选现有商品 ============ -->
        <el-tab-pane label="从总部商品中选择" name="existing">
          <div style="display: flex; gap: 8px; margin-bottom: 10px">
            <el-input v-model="addKeyword" placeholder="商品名称/编码" clearable style="width: 220px" @keyup.enter="loadCandidates" />
            <el-select v-model="addCategoryId" placeholder="全部分类" clearable style="width: 160px" @change="loadCandidates">
              <el-option v-for="cat in categoryOptions" :key="cat.id" :label="cat.categoryName" :value="cat.id" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="loadCandidates">查询</el-button>
          </div>

          <el-table
            :data="candidates"
            v-loading="addLoading"
            border
            size="small"
            max-height="300"
            row-key="id"
            @selection-change="onCandidateSelect"
          >
            <el-table-column type="selection" width="46" :selectable="isCandidateSelectable" />
            <el-table-column label="图片" width="70" align="center">
              <template #default="{ row }">
                <el-image v-if="row.mainImage" :src="row.mainImage" fit="cover" style="width: 40px; height: 40px; border-radius: 4px" />
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="spuCode" label="编码" width="110" />
            <el-table-column prop="spuName" label="商品名称" min-width="150" show-overflow-tooltip />
            <el-table-column label="分类" width="110">
              <template #default="{ row }">{{ row.categoryName || '-' }}</template>
            </el-table-column>
            <el-table-column label="基础价" width="90" align="right">
              <template #default="{ row }">{{ fenToYuan(row.basePrice) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.added" size="small" type="info">已在本店</el-tag>
                <el-tag v-else size="small" type="success">可添加</el-tag>
              </template>
            </el-table-column>
          </el-table>

          <!-- 操作区：必须用普通 div，不能用 el-dialog 的 #footer slot
               （放在 tab-pane 内时 dialog 不会渲染 footer，按钮会消失） -->
          <div class="dialog-actions">
            <span class="text-muted">已选 {{ selectedCandidates.length }} 个商品</span>
            <div>
              <el-button @click="addVisible = false">取消</el-button>
              <el-button type="primary" :loading="addSubmitting" :disabled="!selectedCandidates.length" @click="submitAdd">
                添加到本门店({{ selectedCandidates.length }})
              </el-button>
            </div>
          </div>
        </el-tab-pane>

        <!-- ============ 新建商品 ============ -->
        <el-tab-pane label="新建商品" name="create">
          <el-form :model="newSpu" label-width="100px" size="default">
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="商品名称" required>
                  <el-input v-model="newSpu.spuName" placeholder="如：杨枝甘露" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="商品编码" required>
                  <el-input v-model="newSpu.spuCode" placeholder="留空自动生成" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="所属分类" required>
                  <el-select v-model="newSpu.categoryId" placeholder="选择分类" style="width: 100%">
                    <el-option v-for="cat in categoryOptions" :key="cat.id" :label="cat.categoryName" :value="cat.id" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="单位">
                  <el-input v-model="newSpu.unit" placeholder="杯" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="售价（元）" required>
                  <el-input-number v-model="newSpu.basePriceYuan" :min="0" :precision="2" :step="0.5" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="最低保护价">
                  <el-input-number v-model="newSpu.minPriceYuan" :min="0" :precision="2" :step="0.5" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="商品图片">
              <div class="upload-row">
                <el-image
                  v-if="newSpu.mainImage"
                  :src="newSpu.mainImage"
                  fit="cover"
                  style="width: 84px; height: 84px; border-radius: 6px; border: 1px solid #dcdfe6"
                />
                <div v-else class="upload-placeholder">暂无图片</div>
                <div>
                  <el-button :loading="uploading" @click="pickImage">上传图片</el-button>
                  <el-button v-if="newSpu.mainImage" link type="danger" @click="newSpu.mainImage = ''">移除</el-button>
                  <div class="text-muted" style="font-size: 12px; margin-top: 4px">支持 jpg/png/webp/gif，≤10MB（原图直传）</div>
                </div>
              </div>
              <input ref="fileInputRef" type="file" accept="image/*" style="display: none" @change="onFileChange" />
            </el-form-item>

            <el-form-item label="商品描述">
              <el-input v-model="newSpu.description" type="textarea" :rows="2" placeholder="可选" />
            </el-form-item>

            <el-form-item label="规格">
              <div style="width: 100%">
                <div v-for="(sku, idx) in newSpu.skus" :key="idx" class="sku-row">
                  <el-input v-model="sku.skuName" placeholder="规格名（如 大杯/中杯）" style="width: 180px" />
                  <el-input-number v-model="sku.priceYuan" :min="0" :precision="2" :step="0.5" style="width: 140px" />
                  <el-button link type="danger" :disabled="newSpu.skus.length <= 1" @click="removeSku(idx)">删除</el-button>
                </div>
                <el-button link type="primary" @click="addSku">+ 添加规格</el-button>
                <div class="text-muted" style="font-size: 12px">
                  至少一个规格；不填规格名时自动用「默认规格」
                </div>
              </div>
            </el-form-item>
          </el-form>

          <div class="dialog-actions">
            <span class="text-muted">新商品将直接创建并上架到当前门店</span>
            <div>
              <el-button @click="addVisible = false">取消</el-button>
              <el-button type="primary" :loading="creating" @click="submitCreate">
                创建并上架
              </el-button>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

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
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createSpu,
  getCategoryTree,
  getSpuPage,
  getStoreProducts,
  removeStoreSpu,
  setStoreSkuPrice,
  shelfStoreSpu,
  uploadProductImage,
} from '@/api/product'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { fenToYuan, yuanToFen } from '@/utils/format'
import type { CategoryVO, SpuVO, StoreSkuVO, StoreSpuVO } from '@/api/types'

const appStore = useAppStore()
const userStore = useUserStore()
/** 是否有门店商品写权限（收银员只有 list，全部写按钮隐藏） */
const canEditStoreProduct = () => userStore.hasPerm('product:store:update')

const loading = ref(false)
const records = ref<StoreSpuVO[]>([])
const total = ref(0)
const keyword = ref('')
const pageNo = ref(1)
const pageSize = ref(20)

// ---------- 添加商品到门店 ----------
const addVisible = ref(false)
const addLoading = ref(false)
const addSubmitting = ref(false)
const addKeyword = ref('')
const addCategoryId = ref<number | undefined>(undefined)
const candidates = ref<(SpuVO & { added?: boolean })[]>([])
const selectedCandidates = ref<(SpuVO & { added?: boolean })[]>([])
const categoryOptions = ref<CategoryVO[]>([])

function openAdd(): void {
  if (!appStore.currentStoreId) {
    ElMessage.warning('请先选择门店')
    return
  }
  addVisible.value = true
  addTab.value = 'existing'
  addKeyword.value = ''
  addCategoryId.value = undefined
  selectedCandidates.value = []
  resetNewSpu()
  loadCandidates()
  loadCategories()
}

async function loadCategories(): Promise<void> {
  if (categoryOptions.value.length) return
  try {
    categoryOptions.value = await getCategoryTree()
  } catch {
    categoryOptions.value = []
  }
}

/** 候选商品 = 总部在售 SPU，标记哪些已在本门店 */
async function loadCandidates(): Promise<void> {
  addLoading.value = true
  try {
    const page = await getSpuPage({
      keyword: addKeyword.value || undefined,
      categoryId: addCategoryId.value,
      pageNo: 1,
      pageSize: 200,
    })
    // 已在本门店的 SPU 集合
    const existing = new Set(records.value.map((r) => r.spuId))
    candidates.value = (page.records || []).map((s) => ({ ...s, added: existing.has(s.id) }))
  } catch (e) {
    ElMessage.error('商品列表加载失败')
  } finally {
    addLoading.value = false
  }
}

// ---------- 新建商品 ----------
const addTab = ref<'existing' | 'create'>('existing')
const creating = ref(false)
const uploading = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

interface NewSkuForm {
  skuName: string
  priceYuan: number
}
const newSpu = reactive({
  spuName: '',
  spuCode: '',
  categoryId: undefined as number | undefined,
  unit: '杯',
  basePriceYuan: 0,
  minPriceYuan: 0,
  mainImage: '',
  description: '',
  skus: [{ skuName: '', priceYuan: 0 }] as NewSkuForm[],
})

function resetNewSpu(): void {
  newSpu.spuName = ''
  newSpu.spuCode = ''
  newSpu.categoryId = undefined
  newSpu.unit = '杯'
  newSpu.basePriceYuan = 0
  newSpu.minPriceYuan = 0
  newSpu.mainImage = ''
  newSpu.description = ''
  newSpu.skus = [{ skuName: '', priceYuan: 0 }]
}

function addSku(): void {
  newSpu.skus.push({ skuName: '', priceYuan: 0 })
}

function removeSku(idx: number): void {
  if (newSpu.skus.length <= 1) return
  newSpu.skus.splice(idx, 1)
}

function pickImage(): void {
  fileInputRef.value?.click()
}

async function onFileChange(e: Event): Promise<void> {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('图片不能超过 10MB')
    input.value = ''
    return
  }
  uploading.value = true
  try {
    const res = await uploadProductImage(file)
    newSpu.mainImage = res.url
    ElMessage.success('图片已上传')
  } catch {
    ElMessage.error('图片上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

/** 创建商品（SPU + SKU）并上架到当前门店 */
async function submitCreate(): Promise<void> {
  if (!appStore.currentStoreId) {
    ElMessage.warning('请先选择门店')
    return
  }
  if (!newSpu.spuName.trim()) {
    ElMessage.warning('请填写商品名称')
    return
  }
  if (!newSpu.categoryId) {
    ElMessage.warning('请选择商品分类')
    return
  }
  if (!newSpu.basePriceYuan || newSpu.basePriceYuan <= 0) {
    ElMessage.warning('请填写商品售价')
    return
  }
  const validSkus = newSpu.skus.filter((s) => s.priceYuan > 0)
  if (!validSkus.length) {
    ElMessage.warning('至少填写一个规格的售价')
    return
  }

  creating.value = true
  try {
    const basePrice = yuanToFen(newSpu.basePriceYuan)
    // 编码留空时服务端会生成，这里给个稳定的默认值兜底
    const spuCode = newSpu.spuCode.trim() || 'SPU' + Date.now()
    const spuId = await createSpu({
      categoryId: newSpu.categoryId,
      spuCode,
      spuName: newSpu.spuName.trim(),
      unit: newSpu.unit || '杯',
      basePrice,
      minPrice: newSpu.minPriceYuan ? yuanToFen(newSpu.minPriceYuan) : basePrice,
      mainImage: newSpu.mainImage || null,
      description: newSpu.description || null,
      status: 'ON_SALE',
      sort: 0,
      skus: validSkus.map((s, i) => ({
        skuCode: spuCode + '-S' + (i + 1),
        skuName: s.skuName.trim() || '默认规格',
        price: yuanToFen(s.priceYuan),
        costPrice: 0,
        imageUrl: newSpu.mainImage || null,
        sort: i,
        status: 'ENABLED',
      })),
    })
    // 创建后立即上架到当前门店
    if (spuId) await shelfStoreSpu(appStore.currentStoreId, spuId, 1)
    ElMessage.success('商品已创建并上架到本店')
    resetNewSpu()
    addVisible.value = false
    await load()
  } catch (e: unknown) {
    const err = e as { message?: string }
    ElMessage.error(err?.message || '创建失败，请检查编码是否重复')
  } finally {
    creating.value = false
  }
}

function isCandidateSelectable(row: SpuVO & { added?: boolean }): boolean {
  return !row.added
}

function onCandidateSelect(rows: (SpuVO & { added?: boolean })[]): void {
  selectedCandidates.value = rows
}

async function submitAdd(): Promise<void> {
  if (!appStore.currentStoreId || !selectedCandidates.value.length) return
  addSubmitting.value = true
  try {
    // 逐个上架（shelf 在关系不存在时会自动创建门店商品关系）
    for (const spu of selectedCandidates.value) {
      await shelfStoreSpu(appStore.currentStoreId, spu.id, 1)
    }
    ElMessage.success('已添加 ' + selectedCandidates.value.length + ' 个商品到本门店')
    addVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error('添加失败，请重试')
  } finally {
    addSubmitting.value = false
  }
}

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

<!-- 弹窗内操作区 */
<style>
.store-product-dialog {
  max-height: 82vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.store-product-dialog .el-dialog__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.dialog-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}
</style>

<!-- 商品管理弹窗：内容区可滚动，底部「添加到本门店」按钮始终可见 -->
<style>
.store-product-dialog {
  max-height: 82vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.store-product-dialog .el-dialog__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.store-product-dialog .el-dialog__footer {
  flex-shrink: 0;
  border-top: 1px solid #ebeef5;
  padding-top: 12px;
}
</style>
