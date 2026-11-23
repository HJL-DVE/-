<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="search">
          <el-option label="已退款" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="处理中" value="PENDING" />
        </el-select>
        <el-input v-model="query.memberId" placeholder="会员 ID" clearable style="width: 150px" @keyup.enter="search" />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <div class="toolbar-spacer" />
        <span class="text-muted">GET /api/admin/wallet/card-refunds · 共 {{ total }} 条</span>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
        title="会员自助退卡：提交即退款，实付本金原路退回，赠送金额作废不折现"
      />

      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column prop="refundNo" label="退卡单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="会员" width="140">
          <template #default="{ row }">
            {{ row.memberNickname || ('#' + row.memberId) }}
          </template>
        </el-table-column>
        <el-table-column label="退还本金" width="120" align="right">
          <template #default="{ row }"><span class="money">{{ fenToYuan(row.principalAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="作废赠送" width="120" align="right">
          <template #default="{ row }"><span class="text-muted">{{ fenToYuan(row.giftVoidAmount) }}</span></template>
        </el-table-column>
        <el-table-column prop="applyReason" label="退卡原因" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="资金流水号" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.refundTxnNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="办理时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
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
import { getCardRefundPage } from '@/api/wallet'
import type { CardRefundVO } from '@/api/wallet'
import { fenToYuan, formatDateTime } from '@/utils/format'

const loading = ref(false)
const records = ref<CardRefundVO[]>([])
const total = ref(0)

const query = reactive<{ pageNo: number; pageSize: number; status: string; memberId: string }>({
  pageNo: 1,
  pageSize: 20,
  status: '',
  memberId: '',
})

function statusLabel(s: string): string {
  return s === 'APPROVED' ? '已退款' : s === 'REJECTED' ? '已驳回' : '处理中'
}

function statusType(s: string): 'success' | 'danger' | 'warning' {
  return s === 'APPROVED' ? 'success' : s === 'REJECTED' ? 'danger' : 'warning'
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await getCardRefundPage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      status: query.status || undefined,
      memberId: query.memberId ? Number(query.memberId) : undefined,
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

onMounted(load)
</script>