<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="退款状态" clearable style="width: 150px">
          <el-option v-for="(label, value) in REFUND_STATUS_LABEL" :key="value" :label="label" :value="value" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <div class="toolbar-spacer" />
        <span class="text-muted">GET /api/admin/payment/refunds · 共 {{ total }} 条</span>
        <el-button type="danger" :icon="Plus" @click="openApply">申请退款</el-button>
      </div>

      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column prop="refundNo" label="退款单号" min-width="185" show-overflow-tooltip />
        <el-table-column prop="paymentNo" label="支付单号" min-width="185" show-overflow-tooltip />
        <el-table-column label="业务" width="130">
          <template #default="{ row }">
            {{ BIZ_TYPE_LABEL[row.bizType] || row.bizType }}
            <div class="text-muted mono">{{ row.bizNo }}</div>
          </template>
        </el-table-column>
        <el-table-column label="退款额" width="110" align="right">
          <template #default="{ row }"><span class="money">{{ fenToYuan(row.refundAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="退实充" width="110" align="right">
          <template #default="{ row }">{{ fenToYuan(row.refundPrincipalAmount) }}</template>
        </el-table-column>
        <el-table-column label="退赠送" width="110" align="right">
          <template #default="{ row }">{{ fenToYuan(row.refundGiftAmount) }}</template>
        </el-table-column>
        <el-table-column label="退现金" width="110" align="right">
          <template #default="{ row }">{{ fenToYuan(row.refundCashAmount) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="tagTypeByStatus(row.status)">
              {{ REFUND_STATUS_LABEL[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成功时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.successTime) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
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

    <el-dialog v-model="applyVisible" title="申请退款" width="460px">
      <el-form label-width="110px">
        <el-form-item label="订单号">
          <el-input v-model="applyForm.orderNo" placeholder="输入订单号" />
        </el-form-item>
        <el-form-item label="退款金额（元）">
          <el-input v-model="applyForm.amountYuan" placeholder="例如 12.00" />
        </el-form-item>
        <el-form-item label="退款原因">
          <el-input v-model="applyForm.reason" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <div class="text-muted" style="padding-left: 12px">
        服务端会校验退款金额不超过订单实付与已退差额。
      </div>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="danger" :loading="applying" @click="submitApply">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { applyRefund, getRefundPage } from '@/api/payment'
import { BIZ_TYPE_LABEL, REFUND_STATUS_LABEL, fenToYuan, formatDateTime, tagTypeByStatus, yuanToFen } from '@/utils/format'
import type { RefundVO } from '@/api/types'

const loading = ref(false)
const records = ref<RefundVO[]>([])
const total = ref(0)

const query = reactive<{ pageNo: number; pageSize: number; status: string }>({
  pageNo: 1,
  pageSize: 20,
  status: '',
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await getRefundPage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
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

const applyVisible = ref(false)
const applying = ref(false)
const applyForm = reactive({ orderNo: '', amountYuan: '', reason: '' })

function openApply(): void {
  applyForm.orderNo = ''
  applyForm.amountYuan = ''
  applyForm.reason = ''
  applyVisible.value = true
}

async function submitApply(): Promise<void> {
  if (!applyForm.orderNo) {
    ElMessage.warning('请输入订单号')
    return
  }
  const fen = yuanToFen(applyForm.amountYuan)
  if (Number.isNaN(fen) || fen <= 0) {
    ElMessage.error('请输入合法退款金额')
    return
  }
  applying.value = true
  try {
    const refund = await applyRefund({
      orderNo: applyForm.orderNo,
      refundAmount: fen,
      reason: applyForm.reason || undefined,
    })
    ElMessage.success('退款申请已提交：' + refund.refundNo)
    applyVisible.value = false
    await load()
  } finally {
    applying.value = false
  }
}

onMounted(load)
</script>
