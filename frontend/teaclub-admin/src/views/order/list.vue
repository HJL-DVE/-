<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.orderNo" placeholder="订单号" clearable style="width: 190px" @keyup.enter="search" />
        <el-select v-model="query.status" placeholder="订单状态" clearable style="width: 140px">
          <el-option v-for="(label, value) in ORDER_STATUS_LABEL" :key="value" :label="label" :value="value" />
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
        <el-button :icon="Refresh" @click="reset">重置</el-button>
        <div class="toolbar-spacer" />
        <span class="text-muted">
          门店上下文：{{ appStore.currentStoreName }} · 共 {{ total }} 单
        </span>
      </div>

      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="storeName" label="门店" min-width="150" show-overflow-tooltip />
        <el-table-column label="会员" min-width="140">
          <template #default="{ row }">
            <span v-if="row.memberId">{{ row.memberName || row.memberPhone }}</span>
            <el-tag v-else size="small" type="info">散客</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="商品额" width="100" align="right">
          <template #default="{ row }">{{ fenToYuan(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="优惠" width="100" align="right">
          <template #default="{ row }">{{ fenToYuan(row.discountAmount) }}</template>
        </el-table-column>
        <el-table-column label="应付" width="100" align="right">
          <template #default="{ row }">{{ fenToYuan(row.payableAmount) }}</template>
        </el-table-column>
        <el-table-column label="实付" width="100" align="right">
          <template #default="{ row }"><span class="money">{{ fenToYuan(row.paidAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="ORDER_STATUS_TAG[row.status] || 'info'">
              {{ row.statusLabel || ORDER_STATUS_LABEL[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付渠道" width="110">
          <template #default="{ row }">{{ PAY_CHANNEL_LABEL[row.payChannel] || row.payChannel || '-' }}</template>
        </el-table-column>
        <el-table-column label="下单时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'UNPAID'" link type="warning" @click="onCancel(row)">取消</el-button>
            <el-button
              v-if="['PAID', 'COMPLETED', 'REFUNDING'].includes(row.status) && row.refundedAmount < row.paidAmount"
              link
              type="danger"
              @click="openRefund(row)"
            >
              退款
            </el-button>
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

    <el-drawer v-model="drawerVisible" size="48%" :title="detail?.orderNo || '订单详情'">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="门店">{{ detail.storeName }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ ORDER_SOURCE_LABEL[detail.orderSource || ''] || detail.orderSource || '-' }}</el-descriptions-item>
          <el-descriptions-item label="会员">{{ detail.memberName || detail.memberPhone || '散客' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="ORDER_STATUS_TAG[detail.status] || 'info'">
              {{ detail.statusLabel || ORDER_STATUS_LABEL[detail.status] }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="商品额">{{ fenToYuan(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="优惠合计">{{ fenToYuan(detail.discountAmount) }}</el-descriptions-item>
          <el-descriptions-item label="等级优惠">{{ fenToYuan(detail.levelDiscountAmount) }}</el-descriptions-item>
          <el-descriptions-item label="券优惠">{{ fenToYuan(detail.couponAmount) }}</el-descriptions-item>
          <el-descriptions-item label="应付">{{ fenToYuan(detail.payableAmount) }}</el-descriptions-item>
          <el-descriptions-item label="实付">{{ fenToYuan(detail.paidAmount) }}</el-descriptions-item>
          <el-descriptions-item label="已退款">{{ fenToYuan(detail.refundedAmount) }}</el-descriptions-item>
          <el-descriptions-item label="支付单号">{{ detail.paymentNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ formatDateTime(detail.payTime) }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">商品明细</el-divider>
        <el-table :data="detail.items || []" size="small" border>
          <el-table-column prop="spuName" label="商品" min-width="140" show-overflow-tooltip />
          <el-table-column prop="skuName" label="规格" min-width="130" show-overflow-tooltip />
          <el-table-column label="单价" width="100" align="right">
            <template #default="{ row }">{{ fenToYuan(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="80" align="center" />
          <el-table-column label="小计" width="100" align="right">
            <template #default="{ row }">{{ fenToYuan(row.actualAmount ?? row.totalAmount) }}</template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else description="加载中" />
    </el-drawer>

    <el-dialog v-model="refundVisible" title="申请退款" width="440px">
      <el-form label-width="110px">
        <el-form-item label="订单号">{{ refundForm.orderNo }}</el-form-item>
        <el-form-item label="可退金额">
          {{ fenToYuan(refundForm.maxRefund) }} 元
        </el-form-item>
        <el-form-item label="退款金额（元）">
          <el-input v-model="refundForm.amountYuan" />
        </el-form-item>
        <el-form-item label="退款原因">
          <el-input v-model="refundForm.reason" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button type="danger" :loading="refundSubmitting" @click="submitRefund">提交退款</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { cancelOrder, getOrderDetail, getOrderPage } from '@/api/order'
import { applyRefund } from '@/api/payment'
import { useAppStore } from '@/stores/app'
import {
  ORDER_STATUS_LABEL,
  ORDER_STATUS_TAG,
  PAY_CHANNEL_LABEL,
  fenToYuan,
  formatDateTime,
  yuanToFen,
} from '@/utils/format'
import type { OrderVO } from '@/api/types'

const ORDER_SOURCE_LABEL: Record<string, string> = {
  MINIAPP: '微信小程序',
  WECHAT: '微信小程序',
  POS: '门店收银',
  CASHIER: '收银台',
  ADMIN: '后台代下单',
}

const appStore = useAppStore()

const loading = ref(false)
const records = ref<OrderVO[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)

const query = reactive<{ pageNo: number; pageSize: number; orderNo: string; status: string }>({
  pageNo: 1,
  pageSize: 20,
  orderNo: '',
  status: '',
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await getOrderPage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      orderNo: query.orderNo || undefined,
      status: query.status || undefined,
      storeId: appStore.currentStoreId || undefined,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
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
  query.orderNo = ''
  query.status = ''
  dateRange.value = null
  search()
}

const drawerVisible = ref(false)
const detail = ref<OrderVO | null>(null)

async function openDetail(row: OrderVO): Promise<void> {
  drawerVisible.value = true
  detail.value = null
  detail.value = await getOrderDetail(row.orderNo)
}

function onCancel(row: OrderVO): void {
  ElMessageBox.prompt('取消原因', '取消订单 ' + row.orderNo, {
    inputPlaceholder: '可选',
    confirmButtonText: '确认取消',
    cancelButtonText: '返回',
  })
    .then(async ({ value }) => {
      await cancelOrder(row.orderNo, value || undefined)
      ElMessage.success('订单已取消')
      await load()
    })
    .catch(() => undefined)
}

const refundVisible = ref(false)
const refundSubmitting = ref(false)
const refundForm = reactive({ orderNo: '', maxRefund: 0, amountYuan: '', reason: '' })

function openRefund(row: OrderVO): void {
  refundForm.orderNo = row.orderNo
  refundForm.maxRefund = Math.max(0, Number(row.paidAmount || 0) - Number(row.refundedAmount || 0))
  refundForm.amountYuan = fenToYuan(refundForm.maxRefund)
  refundForm.reason = ''
  refundVisible.value = true
}

async function submitRefund(): Promise<void> {
  const fen = yuanToFen(refundForm.amountYuan)
  if (Number.isNaN(fen) || fen <= 0) {
    ElMessage.error('请输入合法退款金额')
    return
  }
  if (fen > refundForm.maxRefund) {
    ElMessage.error('退款金额不能超过可退金额')
    return
  }
  refundSubmitting.value = true
  try {
    await applyRefund({ orderNo: refundForm.orderNo, refundAmount: fen, reason: refundForm.reason || undefined })
    ElMessage.success('退款申请已提交')
    refundVisible.value = false
    await load()
  } finally {
    refundSubmitting.value = false
  }
}

onMounted(load)
watch(() => appStore.currentStoreId, load)
</script>
