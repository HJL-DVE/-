<template>
  <div class="page">
    <div class="page-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="支付记录" name="orders">
          <div class="toolbar">
            <el-select v-model="orderQuery.status" placeholder="支付状态" clearable style="width: 150px">
              <el-option v-for="(label, value) in PAY_STATUS_LABEL" :key="value" :label="label" :value="value" />
            </el-select>
            <el-select v-model="orderQuery.bizType" placeholder="业务类型" clearable style="width: 140px">
              <el-option label="订单" value="ORDER" />
              <el-option label="充值" value="RECHARGE" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="searchOrders">查询</el-button>
            <el-button :icon="Refresh" @click="loadOrders">刷新</el-button>
            <div class="toolbar-spacer" />
            <span class="text-muted">GET /api/admin/payment/orders · 共 {{ orderTotal }} 条</span>
          </div>

          <el-table :data="orders" v-loading="orderLoading" border stripe>
            <el-table-column prop="paymentNo" label="支付单号" min-width="190" show-overflow-tooltip />
            <el-table-column label="业务" width="130">
              <template #default="{ row }">
                {{ BIZ_TYPE_LABEL[row.bizType] || row.bizType }}
                <div class="text-muted mono">{{ row.bizNo }}</div>
              </template>
            </el-table-column>
            <el-table-column label="金额" width="110" align="right">
              <template #default="{ row }"><span class="money">{{ fenToYuan(row.amount) }}</span></template>
            </el-table-column>
            <el-table-column label="渠道" width="130">
              <template #default="{ row }">{{ PAY_CHANNEL_LABEL[row.channel] || row.channel }}</template>
            </el-table-column>
            <el-table-column prop="provider" label="实现" width="110" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="tagTypeByStatus(row.status)">
                  {{ PAY_STATUS_LABEL[row.status] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="memberId" label="会员 ID" width="100" />
            <el-table-column label="成功时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.successTime) }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="orderQuery.pageNo"
              v-model:page-size="orderQuery.pageSize"
              :total="orderTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="loadOrders"
              @size-change="searchOrders"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="通知日志" name="notify">
          <NotifyTable />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getPaymentPage } from '@/api/payment'
import NotifyTable from './notify.vue'
import {
  BIZ_TYPE_LABEL,
  PAY_CHANNEL_LABEL,
  PAY_STATUS_LABEL,
  fenToYuan,
  formatDateTime,
  tagTypeByStatus,
} from '@/utils/format'
import type { PaymentVO } from '@/api/types'

const activeTab = ref('orders')
const orderLoading = ref(false)
const orders = ref<PaymentVO[]>([])
const orderTotal = ref(0)

const orderQuery = reactive<{ pageNo: number; pageSize: number; status: string; bizType: string }>({
  pageNo: 1,
  pageSize: 20,
  status: '',
  bizType: '',
})

async function loadOrders(): Promise<void> {
  orderLoading.value = true
  try {
    const page = await getPaymentPage({
      pageNo: orderQuery.pageNo,
      pageSize: orderQuery.pageSize,
      status: orderQuery.status || undefined,
      bizType: orderQuery.bizType || undefined,
    })
    orders.value = page.records || []
    orderTotal.value = page.total || 0
  } finally {
    orderLoading.value = false
  }
}

function searchOrders(): void {
  orderQuery.pageNo = 1
  loadOrders()
}

onMounted(loadOrders)
</script>
