<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">销售分析</h2>
      <span class="text-muted">销售=订单实付金额（元）</span>
      <div class="toolbar-spacer" />
      <el-radio-group v-model="days" size="small" @change="load">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon style="margin-bottom: 12px"
              :title="'报表加载失败：' + loadError">
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <el-row :gutter="14">
      <el-col :span="24">
        <el-card shadow="never"><template #header>销售趋势</template><EChart :option="trendOption" /></el-card>
      </el-col>
    </el-row>
    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            商品销量排行
            <el-radio-group v-model="metric" size="small" style="margin-left: 12px" @change="loadRank">
              <el-radio-button value="qty">按销量</el-radio-button>
              <el-radio-button value="amount">按金额</el-radio-button>
            </el-radio-group>
          </template>
          <EChart :option="rankOption" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never"><template #header>门店销售对比</template><EChart :option="compareOption" /></el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import EChart from './components/EChart.vue'
import { getDashboardTrend, getProductRanking, getStoreCompare } from '@/api/report'

const days = ref(7)
const metric = ref<'qty' | 'amount'>('qty')
const loading = ref(false)
const trendOption = ref<Record<string, unknown>>({})
const rankOption = ref<Record<string, unknown>>({})
const compareOption = ref<Record<string, unknown>>({})
const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const t = await getDashboardTrend(days.value)
    trendOption.value = {
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: (t.salesTrend || []).map((p) => p.date.slice(5)) },
      yAxis: { type: 'value', name: '元' },
      series: [{ name: '销售额', type: 'line', smooth: true, areaStyle: {}, data: (t.salesTrend || []).map((p) => p.value / 100) }],
      grid: { left: 60, right: 30, bottom: 30, top: 30 },
    }
    await loadRank()
    const cmp = await getStoreCompare(days.value)
    compareOption.value = {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      // 图例固定顶部并留出足够空间，避免与 x 轴门店名标签重叠
      legend: { data: ['销售额(元)', '订单数'], top: 4, left: 'center', itemGap: 24 },
      xAxis: {
        type: 'category',
        data: (cmp.list || []).map((x) => x.storeName || '未命名门店'),
        // 门店名过长时换行/倾斜，避免相邻标签叠在一起
        axisLabel: {
          interval: 0,
          rotate: (cmp.list || []).length > 4 ? 20 : 0,
          formatter: (name: string) => (name.length > 10 ? name.slice(0, 9) + '…' : name),
        },
      },
      yAxis: [
        { type: 'value', name: '销售额(元)', nameTextStyle: { padding: [0, 0, 0, 20] } },
        { type: 'value', name: '订单数(单)' },
      ],
      series: [
        { name: '销售额(元)', type: 'bar', barMaxWidth: 60, data: (cmp.list || []).map((x) => x.sales / 100) },
        { name: '订单数', type: 'bar', yAxisIndex: 1, barMaxWidth: 60, data: (cmp.list || []).map((x) => x.orders) },
      ],
      // 底部留足空间给（可能倾斜的）门店名，顶部留足给图例
      grid: { left: 70, right: 60, bottom: 64, top: 44, containLabel: false },
    }
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadRank() {
  const r = await getProductRanking(days.value, metric.value, 10)
  const list = r.list || []
  const isQty = metric.value === 'qty'
  rankOption.value = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', name: isQty ? '件' : '元' },
    yAxis: {
      type: 'category',
      data: list.map((x) => x.name).reverse(),
      // 商品名过长时截断，避免与柱子和刻度挤在一起
      axisLabel: {
        width: 120,
        overflow: 'truncate',
        formatter: (name: string) => (name.length > 10 ? name.slice(0, 9) + '…' : name),
      },
    },
    series: [{ type: 'bar', barMaxWidth: 24, data: list.map((x) => (isQty ? x.qty : x.amount / 100)).reverse() }],
    grid: { left: 130, right: 40, top: 20, bottom: 30 },
  }
}

onMounted(load)
</script>