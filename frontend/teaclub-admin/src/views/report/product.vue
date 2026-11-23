<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">商品销售分析</h2>
      <div class="toolbar-spacer" />
      <el-select v-model="storeId" placeholder="全部门店" clearable size="small" style="width: 170px" @change="load">
        <el-option v-for="s in stores" :key="s.id" :label="s.storeName" :value="s.id" />
      </el-select>
      <el-radio-group v-model="days" size="small" @change="load">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="state === 'error'" type="error" :closable="false" show-icon style="margin-bottom: 12px"
              :title="'报表加载失败：' + errMsg">
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <el-row :gutter="14">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>商品销售趋势（近 {{ days }} 天，件 / 元）</template>
          <EChart v-if="trend.length" :option="optTrend" />
          <ReportState v-else :state="chartState" :detail="errMsg" retryable @retry="load" empty-text="暂无商品销售数据" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            商品销量排行（件）
          </template>
          <EChart v-if="qtyRank.length" :option="optQty" height="320px" />
          <ReportState v-else :state="chartState" empty-text="暂无数据" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>商品销售金额排行（元）</template>
          <EChart v-if="amtRank.length" :option="optAmt" height="320px" />
          <ReportState v-else :state="chartState" empty-text="暂无数据" />
        </el-card>
      </el-col>
    </el-row>

    <el-row style="margin-top: 14px">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>商品分类销售排行</template>
          <EChart v-if="cat.length" :option="optCat" height="300px" />
          <ReportState v-else :state="chartState" empty-text="暂无分类数据" />
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
  getCategoryRanking,
  getProductRanking,
  getProductTrend,
  type LoadState,
} from '@/api/report'
import { getAccessibleStores } from '@/api/store'

const stores = ref<{ id: number; storeName: string }[]>([])
const storeId = ref<number | undefined>()
const days = ref(30)
const loading = ref(false)
const state = ref<LoadState>('loading')
const errMsg = ref('')

const trend = ref<{ date: string; qty: number; amount: number }[]>([])
const qtyRank = ref<{ name: string; qty: number }[]>([])
const amtRank = ref<{ name: string; amount: number }[]>([])
const cat = ref<{ name: string; qty: number; amount: number }[]>([])

const chartState = computed<'loading' | 'error' | 'empty'>(() =>
  state.value === 'loading' ? 'loading' : state.value === 'error' ? 'error' : 'empty',
)

const optTrend = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['销量(件)', '销售额(元)'] },
  xAxis: { type: 'category', data: trend.value.map((x) => x.date.slice(5)), name: '日期' },
  yAxis: [{ type: 'value', name: '件' }, { type: 'value', name: '元' }],
  series: [
    { name: '销量(件)', type: 'line', smooth: true, data: trend.value.map((x) => x.qty) },
    { name: '销售额(元)', type: 'line', yAxisIndex: 1, smooth: true, data: trend.value.map((x) => x.amount / 100) },
  ],
  grid: { left: 54, right: 60, bottom: 28, top: 40 },
}))
const optQty = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  xAxis: { type: 'value', name: '件' },
  yAxis: {
    type: 'category', data: qtyRank.value.map((x) => x.name).reverse(),
    axisLabel: { width: 120, overflow: 'truncate', formatter: (n: string) => (n.length > 10 ? n.slice(0, 9) + '…' : n) },
  },
  series: [{ name: '销量', type: 'bar', barMaxWidth: 24, data: qtyRank.value.map((x) => x.qty).reverse() }],
  legend: { data: ['销量'] },
  grid: { left: 130, right: 30, top: 30, bottom: 28 },
}))
const optAmt = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  xAxis: { type: 'value', name: '元' },
  yAxis: {
    type: 'category', data: amtRank.value.map((x) => x.name).reverse(),
    axisLabel: { width: 120, overflow: 'truncate', formatter: (n: string) => (n.length > 10 ? n.slice(0, 9) + '…' : n) },
  },
  series: [{ name: '销售额', type: 'bar', barMaxWidth: 24, data: amtRank.value.map((x) => x.amount / 100).reverse() }],
  legend: { data: ['销售额'] },
  grid: { left: 130, right: 30, top: 30, bottom: 28 },
}))
const optCat = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  legend: { data: ['销量(件)', '销售额(元)'] },
  xAxis: {
    type: 'category', data: cat.value.map((x) => x.name || '未分类'),
    axisLabel: { interval: 0, rotate: cat.value.length > 5 ? 20 : 0, width: 80, overflow: 'truncate' },
  },
  yAxis: [{ type: 'value', name: '件' }, { type: 'value', name: '元' }],
  series: [
    { name: '销量(件)', type: 'bar', barMaxWidth: 40, data: cat.value.map((x) => x.qty) },
    { name: '销售额(元)', type: 'bar', yAxisIndex: 1, barMaxWidth: 40, data: cat.value.map((x) => x.amount / 100) },
  ],
  grid: { left: 54, right: 60, bottom: 48, top: 40 },
}))

async function load() {
  loading.value = true
  state.value = 'loading'
  errMsg.value = ''
  try {
    const [t, qr, ar, cr] = await Promise.all([
      getProductTrend(days.value, storeId.value),
      getProductRanking(days.value, 'qty', storeId.value),
      getProductRanking(days.value, 'amount', storeId.value),
      getCategoryRanking(days.value, 'qty', storeId.value),
    ])
    trend.value = (t.trend || []).map((p, i) => ({
      date: p.date,
      qty: p.value,
      amount: (t.trendAmount || [])[i]?.value ?? 0,
    }))
    qtyRank.value = (qr.list || []).map((x) => ({ name: x.name, qty: x.qty }))
    amtRank.value = (ar.list || []).map((x) => ({ name: x.name, amount: x.amount }))
    cat.value = cr.list || []
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
  } catch { stores.value = [] }
  await load()
})
</script>