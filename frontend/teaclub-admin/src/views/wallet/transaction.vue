<template>
  <div class="page">
    <ApiPending
      v-if="listError"
      title="资金流水接口未就绪"
      url="GET /api/admin/wallet/transactions"
      :code="listError.code"
      :message="listError.message"
      :trace-id="listError.traceId"
    />

    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.memberId" placeholder="会员 ID" clearable style="width: 130px" @keyup.enter="search" />
        <el-input v-model="query.bizNo" placeholder="业务单号" clearable style="width: 190px" @keyup.enter="search" />
        <el-select v-model="query.bizType" placeholder="业务类型" clearable style="width: 150px">
          <el-option v-for="(label, value) in BIZ_TYPE_LABEL" :key="value" :label="label" :value="value" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 240px"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <div class="toolbar-spacer" />
        <span class="text-muted">共 {{ total }} 条流水</span>
      </div>

      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column prop="txnNo" label="流水号" min-width="185" show-overflow-tooltip />
        <el-table-column label="会员" min-width="130">
          <template #default="{ row }">
            {{ row.memberName || '-' }}
            <div class="text-muted">ID {{ row.memberId }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="storeName" label="门店" min-width="150" show-overflow-tooltip />
        <el-table-column label="业务类型" width="120">
          <template #default="{ row }">{{ row.bizTypeName || BIZ_TYPE_LABEL[row.bizType] || row.bizType }}</template>
        </el-table-column>
        <el-table-column prop="bizNo" label="业务单号" min-width="180" show-overflow-tooltip />
        <el-table-column label="实付金变动" width="120" align="right">
          <template #default="{ row }">
            <span :class="Number(row.principalChange) < 0 ? 'money' : 'money-in'">{{ signedFen(row.principalChange) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="赠送金变动" width="120" align="right">
          <template #default="{ row }">
            <span :class="Number(row.giftChange) < 0 ? 'money' : 'money-in'">{{ signedFen(row.giftChange) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="变动后实付" width="120" align="right">
          <template #default="{ row }">{{ fenToYuan(row.principalAfter) }}</template>
        </el-table-column>
        <el-table-column label="变动后赠送" width="120" align="right">
          <template #default="{ row }">{{ fenToYuan(row.giftAfter) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getTransactionPage } from '@/api/wallet'
import { ApiError, isApiError } from '@/api/request'
import ApiPending from '@/components/ApiPending.vue'
import { BIZ_TYPE_LABEL, fenToYuan, formatDateTime, signedFen } from '@/utils/format'
import type { WalletTransactionVO } from '@/api/types'

const loading = ref(false)
const records = ref<WalletTransactionVO[]>([])
const total = ref(0)
const listError = ref<ApiError | null>(null)
const dateRange = ref<[string, string] | null>(null)

const query = reactive<{
  pageNo: number
  pageSize: number
  memberId: string
  bizNo: string
  bizType: string
}>({ pageNo: 1, pageSize: 20, memberId: '', bizNo: '', bizType: '' })

function toApiError(error: unknown): ApiError {
  return isApiError(error) ? error : new ApiError(-1, (error as Error)?.message || '未知错误')
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await getTransactionPage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      memberId: query.memberId ? Number(query.memberId) : undefined,
      bizNo: query.bizNo || undefined,
      bizType: query.bizType || undefined,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
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

onMounted(load)
</script>
