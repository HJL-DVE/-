<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="商品名称/编码" clearable style="width: 200px" @keyup.enter="search" />
        <el-tree-select
          v-model="query.categoryId"
          :data="categoryTree"
          :props="{ label: 'categoryName', value: 'id', children: 'children' }"
          node-key="id"
          placeholder="商品分类"
          clearable
          check-strictly
          style="width: 200px"
        />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="在售" value="ON_SALE" />
          <el-option label="下架" value="OFF_SALE" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
        <div class="toolbar-spacer" />
        <span class="text-muted">GET /api/admin/product/spus · 共 {{ total }} 条</span>
      </div>

      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column label="图片" width="80" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.mainImage"
              :src="row.mainImage"
              :preview-src-list="[row.mainImage]"
              preview-teleported
              fit="cover"
              style="width: 44px; height: 44px; border-radius: 6px"
            >
              <template #error><span class="img-fallback">无图</span></template>
            </el-image>
            <span v-else class="img-fallback">无图</span>
          </template>
        </el-table-column>
        <el-table-column prop="spuCode" label="商品编码" width="130" />
        <el-table-column prop="spuName" label="商品名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column label="基础价" width="110" align="right">
          <template #default="{ row }"><span class="money">{{ fenToYuan(row.basePrice) }}</span></template>
        </el-table-column>
        <el-table-column label="最低价" width="110" align="right">
          <template #default="{ row }">{{ fenToYuan(row.minPrice) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="tagTypeByStatus(row.status)">
              {{ row.status === 'ON_SALE' ? '在售' : row.status === 'OFF_SALE' ? '下架' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">SKU 明细</el-button>
            <el-button v-if="userStore.hasPerm('product:spu:update')" link type="primary" @click="toggleStatus(row)">
              {{ row.status === 'ON_SALE' ? '下架' : '上架' }}
            </el-button>
            <el-button v-if="userStore.hasPerm('product:spu:delete')" link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="search"
        />
      </div>
    </div>

    <el-drawer v-model="drawerVisible" size="46%" :title="detail?.spuName || '商品明细'">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="商品编码">{{ detail.spuCode }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ detail.categoryName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="基础价">{{ fenToYuan(detail.basePrice) }} 元</el-descriptions-item>
        <el-descriptions-item label="状态">{{ ENABLED_LABEL[detail.status] || detail.status }}</el-descriptions-item>
        <el-descriptions-item label="副标题" :span="2">{{ detail.subtitle || '-' }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ detail.description || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">SKU 列表（{{ detail?.skus?.length || 0 }}）</el-divider>
      <el-table :data="detail?.skus || []" size="small" border>
        <el-table-column prop="skuCode" label="SKU 编码" width="130" />
        <el-table-column prop="skuName" label="规格" min-width="150" show-overflow-tooltip />
        <el-table-column label="售价" width="100" align="right">
          <template #default="{ row }"><span class="money">{{ fenToYuan(row.price) }}</span></template>
        </el-table-column>
        <el-table-column v-if="userStore.hasPerm('product:spu:update')" label="成本" width="100" align="right">
          <template #default="{ row }">{{ fenToYuan(row.costPrice) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="tagTypeByStatus(row.status)">{{ ENABLED_LABEL[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getCategoryTree, getSpuDetail, getSpuPage, updateSpuStatus, deleteSpu } from '@/api/product'
import { useUserStore } from '@/stores/user'
import { fenToYuan, tagTypeByStatus, ENABLED_LABEL } from '@/utils/format'
import type { CategoryVO, SpuVO } from '@/api/types'

const userStore = useUserStore()
const loading = ref(false)
const records = ref<SpuVO[]>([])
const total = ref(0)
const categoryTree = ref<CategoryVO[]>([])

const query = reactive<{ pageNo: number; pageSize: number; keyword: string; categoryId?: number; status: string }>({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  categoryId: undefined,
  status: '',
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await getSpuPage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      categoryId: query.categoryId || undefined,
      status: query.status || undefined,
    })
    records.value = page.records || []
    total.value = page.total || 0
  } finally {
    loading.value = false
  }
}

function search(): void {
  query.pageNo = 1
  load()
}

function reset(): void {
  query.keyword = ''
  query.categoryId = undefined
  query.status = ''
  search()
}

const drawerVisible = ref(false)
const detail = ref<SpuVO | null>(null)

async function openDetail(row: SpuVO): Promise<void> {
  drawerVisible.value = true
  detail.value = null
  detail.value = await getSpuDetail(row.id)
}

function toggleStatus(row: SpuVO): void {
  const next = row.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
  updateSpuStatus(row.id, next)
    .then(async () => {
      ElMessage.success('状态已更新')
      await load()
    })
    .catch(() => undefined)
}

function onDelete(row: SpuVO): void {
  ElMessageBox.confirm('确认删除商品「' + row.spuName + '」？', '提示', { type: 'warning' })
    .then(async () => {
      await deleteSpu(row.id)
      ElMessage.success('已删除')
      await load()
    })
    .catch(() => undefined)
}

onMounted(async () => {
  await load()
  try {
    categoryTree.value = await getCategoryTree()
  } catch {
    categoryTree.value = []
  }
})
</script>

<style scoped>
.img-fallback {
  display: inline-block;
  font-size: 12px;
  color: #9ca3af;
  line-height: 44px;
}
</style>
