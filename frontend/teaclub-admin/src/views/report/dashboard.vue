<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">总部经营驾驶舱</h2>
      <span class="text-muted">数据口径：销售=订单实付（分转元）；充值金额不含赠送（赠送单列）；支付金额均为模拟数据</span>
      <div class="toolbar-spacer" />
      <el-select v-model="storeId" placeholder="全部门店" clearable size="small" style="width: 180px" @change="loadAll">
        <el-option v-for="s in stores" :key="s.id" :label="s.storeName" :value="s.id" />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
    </div>

    <el-row :gutter="14">
      <el-col :span="4"><div class="stat-card"><div class="stat-label">门店总数</div><div class="stat-value">{{ ov?.storeCount ?? '-' }}</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">会员总数</div><div class="stat-value">{{ ov?.memberCount ?? '-' }}</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">今日销售(元)</div><div class="stat-value">{{ ov ? yuan(ov.todaySales) : '-' }}</div><div class="stat-extra">{{ ov?.todayOrders ?? '-' }} 单</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">今日充值(元)</div><div class="stat-value">{{ ov ? yuan(ov.todayRechargeAmount) : '-' }}</div><div class="stat-extra">赠送 {{ ov ? yuan(ov.todayRechargeGiftAmount) : '-' }}（另计）</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">今日退款(元)</div><div class="stat-value">{{ ov ? yuan(ov.todayRefundAmount) : '-' }}</div><div class="stat-extra">{{ ov?.todayRefundCount ?? '-' }} 笔</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">今日新增会员</div><div class="stat-value">{{ ov?.todayMemberCount ?? '-' }}</div></div></el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>近 7 天销售 / 订单趋势</template>
          <EChart :option="trendOption" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>各门店销售排行（今日）</template>
          <EChart :option="rankOption" height="300px" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import EChart from './components/EChart.vue'
import {
  getDashboardOverview,
  getDashboardTrend,
  yuan,
  type ReportOverview,
} from '@/api/report'
import { getAccessibleStores } from '@/api/store'

const ov = ref<ReportOverview | null>(null)
const stores = ref<{ id: number; storeName: string }[]>([])
const storeId = ref<number | undefined>()
const loading = ref(false)
const trend = ref<{ date: string; sales: number; orders: number }[]>([])

const trendOption = ref<Record<string, unknown>>({})
const rankOption = ref<Record<string, unknown>>({})

async function loadAll() {
  loading.value = true
  try {
    ov.value = await getDashboardOverview(storeId.value)
    const t = await getDashboardTrend(7, storeId.value)
    trend.value = (t.salesTrend || []).map((p, i) => ({
      date: p.date,
      sales: p.value / 100,
      orders: t.ordersTrend?.[i]?.value ?? 0,
    }))
    trendOption.value = {
      tooltip: { trigger: 'axis' },
      legend: { data: ['销售额(元)', '订单数'] },
      xAxis: { type: 'category', data: trend.value.map((x) => x.date.slice(5)) },
      yAxis: [{ type: 'value', name: '元' }, { type: 'value', name: '单' }],
      series: [
        { name: '销售额(元)', type: 'line', smooth: true, data: trend.value.map((x) => x.sales) },
        { name: '订单数', type: 'bar', yAxisIndex: 1, data: trend.value.map((x) => x.orders) },
      ],
      grid: { left: 50, right: 50, bottom: 30, top: 40 },
    }
    const list = ov.value?.storeRanking?.list || []
    rankOption.value = {
      tooltip: {},
      xAxis: { type: 'value', name: '元' },
      yAxis: { type: 'category', data: list.map((x) => x.storeName).reverse() },
      series: [{ type: 'bar', data: list.map((x) => x.sales / 100).reverse() }],
      grid: { left: 100, right: 30, top: 20, bottom: 30 },
    }
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const s = await getAccessibleStores()
    stores.value = (Array.isArray(s) ? s : []).map((x: { id: number; storeName?: string }) => ({
      id: x.id,
      storeName: x.storeName || String(x.id),
    }))
  } catch {
    stores.value = []
  }
  await loadAll()
})
</script>

<style scoped>
.stat-card { background: #fff; border-radius: 6px; padding: 14px 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.stat-label { font-size: 13px; color: #6b7280; }
.stat-value { font-size: 24px; font-weight: 700; margin-top: 6px; }
.stat-extra { font-size: 12px; color: #9ca3af; margin-top: 4px; }
</style>
