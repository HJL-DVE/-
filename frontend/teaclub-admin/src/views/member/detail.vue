<template>
  <div class="page">
    <el-page-header :content="member?.nickname || member?.phone || '会员详情'" @back="$router.back()" />

    <el-card v-loading="loading" shadow="never" style="margin-top: 14px">
      <el-descriptions v-if="member" :column="3" border size="small">
        <el-descriptions-item label="会员号">{{ member.memberNo }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ member.phone }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ member.nickname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="等级">{{ member.levelName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="积分">{{ member.points ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="tagTypeByStatus(member.status)">
            {{ MEMBER_STATUS_LABEL[member.status] || member.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="累计消费">
          <span class="money">{{ fenToYuan(member.totalConsumeAmount) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="累计充值">
          <span class="money">{{ fenToYuan(member.totalRechargeAmount) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="订单数">{{ member.totalOrderCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="注册门店">{{ member.registerStoreName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="注册渠道">{{ REGISTER_CHANNEL_LABEL[member.registerChannel || ''] || member.registerChannel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ formatDateTime(member.createTime) }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="未取到会员信息" />
    </el-card>

    <el-card shadow="never" style="margin-top: 14px">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="钱包" name="wallet">
          <ApiPending
            v-if="walletError"
            title="钱包接口未就绪"
            url="GET /api/admin/wallet/accounts/member/{memberId}"
            :code="walletError.code"
            :message="walletError.message"
            :trace-id="walletError.traceId"
          />
          <el-descriptions v-else-if="wallet" :column="3" border size="small">
            <el-descriptions-item label="实充余额">
              <span class="money">{{ fenToYuan(wallet.principalBalance) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="赠送余额">
              <span class="money">{{ fenToYuan(wallet.giftBalance) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="合计余额">
              <span class="money">{{ fenToYuan(wallet.totalBalance) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="钱包状态">{{ MEMBER_STATUS_LABEL[wallet.status] || wallet.status }}</el-descriptions-item>
            <el-descriptions-item label="开通时间">{{ formatDateTime(wallet.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="钱包 ID">{{ wallet.id }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="未开通钱包或接口返回空" />
        </el-tab-pane>

        <el-tab-pane label="近期流水" name="transactions">
          <ApiPending
            v-if="txnError"
            title="流水接口未就绪"
            url="GET /api/admin/wallet/transactions/member/{memberId}"
            :code="txnError.code"
            :message="txnError.message"
            :trace-id="txnError.traceId"
          />
          <el-table v-else :data="transactions" size="small" border stripe>
            <el-table-column prop="txnNo" label="流水号" min-width="170" show-overflow-tooltip />
            <el-table-column label="业务" width="120">
              <template #default="{ row }">{{ row.bizTypeName || BIZ_TYPE_LABEL[row.bizType] || row.bizType }}</template>
            </el-table-column>
            <el-table-column label="实充变动" width="110" align="right">
              <template #default="{ row }">{{ signedFen(row.principalChange) }}</template>
            </el-table-column>
            <el-table-column label="赠送变动" width="110" align="right">
              <template #default="{ row }">{{ signedFen(row.giftChange) }}</template>
            </el-table-column>
            <el-table-column label="变动后余额" width="120" align="right">
              <template #default="{ row }">{{ fenToYuan(row.principalAfter) }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="订单记录" name="orders">
          <el-table :data="orders" size="small" border stripe v-loading="orderLoading">
            <el-table-column prop="orderNo" label="订单号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="storeName" label="门店" min-width="140" show-overflow-tooltip />
            <el-table-column label="应付" width="100" align="right">
              <template #default="{ row }">{{ fenToYuan(row.payableAmount) }}</template>
            </el-table-column>
            <el-table-column label="实付" width="100" align="right">
              <template #default="{ row }"><span class="money">{{ fenToYuan(row.paidAmount) }}</span></template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="tagTypeByStatus(row.status)">
                  {{ row.statusLabel || ORDER_STATUS_LABEL[row.status] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="下单时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getMemberDetail } from '@/api/member'
import { getMemberTransactions, getWalletByMember } from '@/api/wallet'
import { getOrderPage } from '@/api/order'
import { ApiError, isApiError } from '@/api/request'
import ApiPending from '@/components/ApiPending.vue'
import {
  BIZ_TYPE_LABEL,
  MEMBER_STATUS_LABEL,
  ORDER_STATUS_LABEL,
  fenToYuan,
  formatDateTime,
  signedFen,
  tagTypeByStatus,
} from '@/utils/format'
import type { MemberVO, OrderVO, WalletTransactionVO, WalletVO } from '@/api/types'

const REGISTER_CHANNEL_LABEL: Record<string, string> = {
  MINIAPP: '微信小程序',
  WECHAT: '微信',
  POS: '门店收银',
  CASHIER: '收银台',
  ADMIN: '后台建档',
}

const route = useRoute()
const memberId = Number(route.params.id)

const loading = ref(false)
const orderLoading = ref(false)
const activeTab = ref('wallet')

const member = ref<MemberVO | null>(null)
const wallet = ref<WalletVO | null>(null)
const walletError = ref<ApiError | null>(null)
const transactions = ref<WalletTransactionVO[]>([])
const txnError = ref<ApiError | null>(null)
const orders = ref<OrderVO[]>([])

function toApiError(error: unknown): ApiError {
  return isApiError(error) ? error : new ApiError(-1, (error as Error)?.message || '未知错误')
}

async function loadWallet(): Promise<void> {
  try {
    wallet.value = await getWalletByMember(memberId)
    walletError.value = null
  } catch (error) {
    wallet.value = null
    walletError.value = toApiError(error)
  }
}

async function loadTransactions(): Promise<void> {
  try {
    transactions.value = await getMemberTransactions(memberId, 20)
    txnError.value = null
  } catch (error) {
    transactions.value = []
    txnError.value = toApiError(error)
  }
}

async function loadOrders(): Promise<void> {
  orderLoading.value = true
  try {
    const page = await getOrderPage({ pageNo: 1, pageSize: 20, memberId })
    orders.value = page.records || []
  } finally {
    orderLoading.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await load()
  } finally {
    loading.value = false
  }
})

async function load(): Promise<void> {
  await Promise.all([
    (async () => {
      try {
        member.value = await getMemberDetail(memberId)
      } catch {
        member.value = null
      }
    })(),
    loadWallet(),
    loadTransactions(),
    loadOrders(),
  ])
}
</script>
