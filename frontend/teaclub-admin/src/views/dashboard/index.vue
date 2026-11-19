<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">总部仪表盘</h2>
      <span class="text-muted">
        前端并发调用 4 个真实接口拼装（不做聚合 BFF）： order/stats/summary · member/members/stats · wallet/stats ·
        store/stores
      </span>
      <div class="toolbar-spacer" />
      <el-radio-group v-model="days" size="small" @change="loadAll">
        <el-radio-button :label="7">近 7 天</el-radio-button>
        <el-radio-button :label="30">近 30 天</el-radio-button>
      </el-radio-group>
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
    </div>

    <el-alert v-if="pendingList.length" type="warning" :closable="false" show-icon style="margin-bottom: 14px">
      <template #title>以下数据块接口不可用（如实提示，未用假数据填充）</template>
      <ul style="margin: 6px 0 0; padding-left: 18px">
        <li v-for="item in pendingList" :key="item.url">
          {{ item.url }} → code={{ item.code }} {{ item.message }}
          <span class="text-muted">traceId={{ item.traceId || '-' }}</span>
        </li>
      </ul>
    </el-alert>

    <el-row :gutter="14">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label"><el-icon><TrendCharts /></el-icon> 营业额（GMV）</div>
          <div class="stat-value">{{ orderStats ? fenToYuan(orderStats.gmv) : placeholder(orderStatsError) }}</div>
          <div class="stat-extra">
            订单 {{ orderStats?.orderCount ?? '-' }} 笔 · 已支付 {{ orderStats?.paidOrderCount ?? '-' }} 笔 ·
            客单价 {{ orderStats ? fenToYuan(orderStats.avgOrderAmount) : '-' }}
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label"><el-icon><UserFilled /></el-icon> 会员总数</div>
          <div class="stat-value">{{ memberStats ? memberStats.totalMembers : placeholder(memberStatsError) }}</div>
          <div class="stat-extra">
            近 7 日新增 {{ memberStats?.newMembers7d ?? '-' }} · 30 日活跃 {{ memberStats?.activeMembers30d ?? '-' }}
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label"><el-icon><Wallet /></el-icon> 储值总余额</div>
          <div class="stat-value">{{ walletStats ? fenToYuan(walletStats.totalBalance) : placeholder(walletStatsError) }}</div>
          <div class="stat-extra">
            钱包 {{ walletStats?.walletCount ?? '-' }} 个 · 累计充值 {{ walletStats ? fenToYuan(walletStats.rechargeTotal) : '-' }} ·
            赠送 {{ walletStats ? fenToYuan(walletStats.giftGrantTotal) : '-' }}
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label"><el-icon><Shop /></el-icon> 门店数量</div>
          <div class="stat-value">{{ storeTotal ?? '-' }}</div>
          <div class="stat-extra">营业中 {{ openStoreCount }} / 可访问 {{ accessibleCount }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="12">
        <el-card shadow="never" header="销售趋势（dailyTrend，后端原样返回）">
          <el-table :data="orderStats?.dailyTrend || []" size="small" border v-loading="loading">
            <el-table-column prop="statDate" label="日期" width="140" />
            <el-table-column prop="orderCount" label="订单数" width="110" align="right" />
            <el-table-column label="GMV" align="right">
              <template #default="{ row }">
                <span class="money">{{ fenToYuan(row.gmv) }}</span>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无趋势数据" :image-size="60" />
            </template>
          </el-table>
          <el-descriptions v-if="orderStats" :column="2" border size="small" style="margin-top: 10px">
            <el-descriptions-item label="余额支付合计">
              {{ fenToYuan(orderStats.balancePayAmount) }}
            </el-descriptions-item>
            <el-descriptions-item label="微信支付合计">
              {{ fenToYuan(orderStats.wechatPayAmount) }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never" header="最近订单（GET /api/admin/order/orders，真实数据）">
          <el-table :data="recentOrders" size="small" v-loading="recentLoading" height="300">
            <el-table-column prop="orderNo" label="订单号" min-width="165" show-overflow-tooltip />
            <el-table-column prop="storeName" label="门店" min-width="130" show-overflow-tooltip />
            <el-table-column label="实付" width="95" align="right">
              <template #default="{ row }">
                <span class="money">{{ fenToYuan(row.paidAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="tagTypeByStatus(row.status)">
                  {{ row.statusLabel || ORDER_STATUS_LABEL[row.status] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { getAccessibleStores, getStorePage } from '@/api/store'
import { getMemberStats } from '@/api/member'
import { getOrderPage, getOrderStatsSummary } from '@/api/order'
import { getWalletStats } from '@/api/wallet'
import { ApiError, isApiError } from '@/api/request'
import { ORDER_STATUS_LABEL, fenToYuan, tagTypeByStatus } from '@/utils/format'
import type { MemberStatsVO, OrderStatsVO, OrderVO, WalletStatsVO } from '@/api/types'

const appStore = useAppStore()

const days = ref(7)
const loading = ref(false)
const recentLoading = ref(false)

const storeTotal = ref<number | null>(null)
const accessibleCount = ref(0)
const openStoreCount = ref(0)

const orderStats = ref<OrderStatsVO | null>(null)
const memberStats = ref<MemberStatsVO | null>(null)
const walletStats = ref<WalletStatsVO | null>(null)
const orderStatsError = ref<ApiError | null>(null)
const memberStatsError = ref<ApiError | null>(null)
const walletStatsError = ref<ApiError | null>(null)
const recentOrders = ref<OrderVO[]>([])

const pendingList = computed(() => {
  const list: { url: string; code: number; message: string; traceId?: string }[] = []
  if (orderStatsError.value) {
    list.push({
      url: 'GET /api/admin/order/stats/summary',
      code: orderStatsError.value.code,
      message: orderStatsError.value.message,
      traceId: orderStatsError.value.traceId,
    })
  }
  if (memberStatsError.value) {
    list.push({
      url: 'GET /api/admin/member/members/stats',
      code: memberStatsError.value.code,
      message: memberStatsError.value.message,
      traceId: memberStatsError.value.traceId,
    })
  }
  if (walletStatsError.value) {
    list.push({
      url: 'GET /api/admin/wallet/stats',
      code: walletStatsError.value.code,
      message: walletStatsError.value.message,
      traceId: walletStatsError.value.traceId,
    })
  }
  return list
})

function placeholder(error: ApiError | null): string {
  return error ? '未就绪(' + error.code + ')' : '-'
}

function toApiError(error: unknown): ApiError {
  return isApiError(error) ? error : new ApiError(-1, (error as Error)?.message || '未知错误')
}

async function loadStores(): Promise<void> {
  try {
    const [page, accessible] = await Promise.all([getStorePage({ pageNo: 1, pageSize: 1 }), getAccessibleStores()])
    storeTotal.value = page.total
    accessibleCount.value = accessible.length
    openStoreCount.value = accessible.filter((item) => item.status === 'OPEN').length
  } catch {
    storeTotal.value = null
  }
}

async function loadOrderStats(): Promise<void> {
  try {
    orderStats.value = await getOrderStatsSummary(
      { storeId: appStore.currentStoreId || undefined, days: days.value },
      true,
    )
    orderStatsError.value = null
  } catch (error) {
    orderStats.value = null
    orderStatsError.value = toApiError(error)
  }
}

async function loadMemberStats(): Promise<void> {
  try {
    memberStats.value = await getMemberStats(true)
    memberStatsError.value = null
  } catch (error) {
    memberStats.value = null
    memberStatsError.value = toApiError(error)
  }
}

async function loadWalletStats(): Promise<void> {
  try {
    walletStats.value = await getWalletStats(true)
    walletStatsError.value = null
  } catch (error) {
    walletStats.value = null
    walletStatsError.value = toApiError(error)
  }
}

async function loadRecentOrders(): Promise<void> {
  recentLoading.value = true
  try {
    const page = await getOrderPage({
      pageNo: 1,
      pageSize: 8,
      storeId: appStore.currentStoreId || undefined,
    })
    recentOrders.value = page.records || []
  } finally {
    recentLoading.value = false
  }
}

async function loadAll(): Promise<void> {
  loading.value = true
  try {
    await Promise.all([loadStores(), loadOrderStats(), loadMemberStats(), loadWalletStats(), loadRecentOrders()])
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
watch(() => appStore.currentStoreId, loadAll)
</script>

<style scoped>
.page-title {
  margin: 0;
  font-size: 18px;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 18px;
  border: 1px solid #ebeef5;
  height: 100%;
}

.stat-label {
  color: #909399;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.stat-value {
  margin-top: 10px;
  font-size: 26px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.stat-extra {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
</style>
