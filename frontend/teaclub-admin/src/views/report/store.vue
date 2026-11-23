<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">门店经营驾驶舱</h2>
      <span class="text-muted">支付金额均为模拟数据</span>
      <div class="toolbar-spacer" />
      <el-select v-model="storeId" placeholder="选择门店" size="small" style="width: 170px" @change="load">
        <el-option v-for="s in stores" :key="s.id" :label="s.storeName" :value="s.id" />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="state === 'error'" type="error" :closable="false" show-icon style="margin-bottom: 12px"
              :title="'报表加载失败：' + errMsg">
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <el-row :gutter="14">
      <el-col :span="4"><div class="stat-card"><div class="stat-label">今日销售额(元)</div>
        <div class="stat-value">{{ ov ? yuan(ov.sales) : '-' }}</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">今日订单量</div>
        <div class="stat-value">{{ ov?.orders ?? '-' }}</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">今日客单价(元)</div>
        <div class="stat-value">{{ ov?.avgOrder ?? '-' }}</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">今日充值(元)</div>
        <div class="stat-value">{{ ov ? yuan(ov.rechargeAmount) : '-' }}</div>
        <div class="stat-extra">赠送 {{ ov ? yuan(ov.rechargeGiftAmount) : '-' }} 另计</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">今日退款(元)</div>
        <div class="stat-value">{{ ov ? yuan(ov.refundAmount) : '-' }}</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">今日新增会员</div>
        <div class="stat-value">{{ ov?.todayNewMembers ?? '-' }}</div></div></el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>每日销售趋势（近 7 日，元）</template>
          <EChart v-if="daily.length" :option="optDaily" />
          <ReportState v-else :state="chartState" empty-text="暂无销售数据" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>每小时订单趋势（今日，单）</template>
          <EChart v-if="hourly.length" :option="optHourly" />
          <ReportState v-else :state="chartState" empty-text="今日暂无订单" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>商品销量排行（近 30 天，件）</template>
          <EChart v-if="hot.length" :option="optHot" height="300px" />
          <ReportState v-else :state="chartState" empty-text="暂无商品销售数据" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>支付方式占比（近 30 天，模拟支付，元）</template>
          <EChart v-if="channels.length" :option="optPay" height="300px" />
          <ReportState v-else :state="chartState" empty-text="暂无支付数据" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import EChart from './components/EChart.vue'
import ReportState from './components/ReportState.vue'
import {
  getDashboardTrend,
  getHourlyOrders,
  getProductRanking,
  getPaymentOverview,
  getStoreOverview,
  yuan,
  type LoadState,
} from '@/api/report'
import { getAccessibleStores } from '@/api/store'

type StoreOv = Awaited<ReturnType<typeof getStoreOverview>>
const ov = ref<StoreOv | null>(null)
const stores = ref<{ id: number; storeName: string }[]>([])
const storeId = ref<number | undefined>()
const loading = ref(false)
const state = ref<LoadState>('loading')
const errMsg = ref('')

const daily = ref<{ date: string; value: number }[]>([])
const hourly = ref<{ hour: string; orders: number }[]>([])
const hot = ref<{ name: string; qty: number }[]>([])
const channels = ref<{ channel: string; amount: number }[]>([])

const chartState = computed<'loading' | 'error' | 'empty'>(() =>
  state.value === 'loading' ? 'loading' : state.value === 'error' ? 'error' : 'empty',
)

const optDaily = computed(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: daily.value.map((x) => x.date.slice(5)), name: '日期' },
  yAxis: { type: 'value', name: '元' },
  series: [{ name: '销售额', type: 'line', smooth: true, areaStyle: {}, data: daily.value.map((x) => x.value / 100) }],
  legend: { data: ['销售额'] },
  grid: { left: 60, right: 24, bottom: 28, top: 36 },
}))
const optHourly = computed(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: hourly.value.map((x) => x.hour), name: '时段' },
  yAxis: { type: 'value', minInterval: 1, name: '单' },
  series: [{ name: '订单量', type: 'bar', data: hourly.value.map((x) => x.orders) }],
  legend: { data: ['订单量'] },
  grid: { left: 44, right: 24, bottom: 28, top: 36 },
}))
const optHot = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  xAxis: { type: 'value', name: '件' },
  yAxis: {
    type: 'category', data: hot.value.map((x) => x.name).reverse(),
    axisLabel: { width: 120, overflow: 'truncate', formatter: (n: string) => (n.length > 10 ? n.slice(0, 9) + '…' : n) },
  },
  series: [{ name: '销量', type: 'bar', barMaxWidth: 24, data: hot.value.map((x) => x.qty).reverse() }],
  legend: { data: ['销量'] },
  grid: { left: 130, right: 30, top: 30, bottom: 28 },
}))
const optPay = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c}元 ({d}%)' },
  legend: { orient: 'vertical', left: 'left', top: 'middle', itemGap: 10 },
  series: [{
    name: '模拟支付',
    type: 'pie', radius: ['40%', '68%'], center: ['62%', '55%'],
    label: { formatter: '{b}: {d}%' },
    data: channels.value.map((x) => ({ name: x.channel, value: x.amount / 100 })),
  }],
}))

async function load() {
  loading.value = true
  state.value = 'loading'
  errMsg.value = ''
  try {
    ov.value = await getStoreOverview(storeId.value)
    const [t, h, pr, pay] = await Promise.all([
      getDashboardTrend(7, storeId.value),
      getHourlyOrders(storeId.value),
      getProductRanking(30, 'qty', storeId.value),
      getPaymentOverview(30, storeId.value),
    ])
    daily.value = t.salesTrend || []
    hourly.value = h.list || []
    hot.value = (pr.list || []).map((x) => ({ name: x.name, qty: x.qty }))
    channels.value = (pay.byChannel || []).map((x) => ({ channel: x.channel, amount: x.amount }))
    state.value = 'ready'
  } catch (e) {
    state.value = 'error'
    errMsg.value = e instanceof Error ? e.message : '未知错误'
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const s = await getAccessibleStores()
    stores.value = (Array.isArray(s) ? s : []).map((x) => ({ id: x.id, storeName: x.storeName || String(x.id) }))
    if (stores.value.length && storeId.value === undefined) storeId.value = stores.value[0].id
  } catch { stores.value = [] }
  await load()
})
</script>

<style scoped>
.stat-card { background: #fff; border-radius: 6px; padding: 14px 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06); min-height: 84px; }
.stat-label { font-size: 13px; color: #6b7280; }
.stat-value { font-size: 22px; font-weight: 700; margin-top: 6px; }
.stat-extra { font-size: 12px; color: #9ca3af; margin-top: 4px; }
</style>