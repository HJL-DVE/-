<template>
  <div class="page">
    <ApiPending
      v-if="listError"
      title="钱包账户接口未就绪"
      url="GET /api/admin/wallet/accounts"
      :code="listError.code"
      :message="listError.message"
      :trace-id="listError.traceId"
    />

    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.phone" placeholder="会员手机号" clearable style="width: 170px" @keyup.enter="search" />
        <el-input v-model="query.keyword" placeholder="会员号/昵称" clearable style="width: 170px" @keyup.enter="search" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="正常" value="NORMAL" />
          <el-option label="冻结" value="FROZEN" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button :icon="CircleCheck" @click="runReconcile">对账</el-button>
        <div class="toolbar-spacer" />
        <span class="text-muted">共 {{ total }} 个钱包账户</span>
      </div>

      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column prop="walletId" label="钱包 ID" width="100" />
        <el-table-column label="会员" min-width="150">
          <template #default="{ row }">
            {{ row.memberName || row.memberNickname || '-' }}
            <div class="text-muted">{{ row.memberNo || '-' }} · {{ row.memberPhone || row.phone || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="memberId" label="会员 ID" width="110" />
        <el-table-column label="实付余额" width="120" align="right">
          <template #default="{ row }"><span class="money">{{ fenToYuan(row.principalBalance) }}</span></template>
        </el-table-column>
        <el-table-column label="赠送余额" width="120" align="right">
          <template #default="{ row }"><span class="money">{{ fenToYuan(row.giftBalance) }}</span></template>
        </el-table-column>
        <el-table-column label="总余额" width="120" align="right">
          <template #default="{ row }"><span class="money">{{ fenToYuan(row.totalBalance) }}</span></template>
        </el-table-column>
        <el-table-column label="累计实付入" width="120" align="right">
          <template #default="{ row }">{{ fenToYuan(row.totalPrincipalIn) }}</template>
        </el-table-column>
        <el-table-column label="累计赠送入" width="120" align="right">
          <template #default="{ row }">{{ fenToYuan(row.totalGiftIn) }}</template>
        </el-table-column>
        <el-table-column label="累计实付出" width="120" align="right">
          <template #default="{ row }">{{ fenToYuan(row.totalPrincipalOut) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'NORMAL' ? 'success' : 'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开通时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="search"
        />
      </div>
    </div>

    <el-dialog v-model="reconcileVisible" title="资金对账结果" width="520px">
      <el-descriptions v-if="reconcile" :column="1" border size="small">
        <el-descriptions-item label="是否一致">
          <el-tag :type="reconcile.consistent ? 'success' : 'danger'">
            {{ reconcile.consistent ? '一致' : '存在差异' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="不一致数量">{{ reconcile.inconsistentCount ?? 0 }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="reconcileDetails" size="small" border style="margin-top: 10px">
        <el-table-column
          v-for="key in Object.keys(reconcileDetails[0] || {})"
          :key="key"
          :prop="key"
          :label="key"
          min-width="130"
          show-overflow-tooltip
        />
      </el-table>
      <div v-if="reconcileError" class="text-muted">{{ reconcileError.message }}</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { CircleCheck, Refresh, Search } from '@element-plus/icons-vue'
import { getReconcile, getWalletPage } from '@/api/wallet'
import { ApiError, isApiError } from '@/api/request'
import ApiPending from '@/components/ApiPending.vue'
import { fenToYuan, formatDateTime } from '@/utils/format'
import type { WalletVO } from '@/api/types'

const loading = ref(false)
const records = ref<WalletVO[]>([])
const total = ref(0)
const listError = ref<ApiError | null>(null)

const query = reactive<{ pageNo: number; pageSize: number; phone: string; keyword: string; status: string }>({
  pageNo: 1,
  pageSize: 20,
  phone: '',
  keyword: '',
  status: '',
})

function toApiError(error: unknown): ApiError {
  return isApiError(error) ? error : new ApiError(-1, (error as Error)?.message || '未知错误')
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await getWalletPage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      phone: query.phone || undefined,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
    })
    records.value = page.records || []
    total.value = page.total || 0
    listError.value = null
  } catch (error) {
    records.value = []
    total.value = 0
    listError.value = toApiError(error)
  } finally {
    loading.value = false
  }
}

function search(): void {
  query.pageNo = 1
  load()
}

const reconcileVisible = ref(false)
const reconcile = ref<Record<string, unknown> | null>(null)
const reconcileError = ref<ApiError | null>(null)

const reconcileDetails = computed<Record<string, unknown>[]>(() => {
  const details = reconcile.value?.details
  return Array.isArray(details) ? (details as Record<string, unknown>[]) : []
})

async function runReconcile(): Promise<void> {
  reconcileVisible.value = true
  reconcile.value = null
  reconcileError.value = null
  try {
    reconcile.value = await getReconcile()
  } catch (error) {
    reconcileError.value = toApiError(error)
  }
}

onMounted(load)
</script>
