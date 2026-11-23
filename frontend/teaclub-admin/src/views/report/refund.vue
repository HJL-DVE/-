<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">退款统计</h2>
      <div class="toolbar-spacer" />
      <el-radio-group v-model="days" size="small" @change="load">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
    </div>

    <el-row :gutter="14" style="margin-bottom: 14px">
      <el-col :span="6"><div class="stat-card"><div class="stat-label">退款笔数</div><div class="stat-value">{{ ov?.count ?? '-' }}</div></div></el-col>
      <el-col :span="6"><div class="stat-card"><div class="stat-label">退款金额(元)</div><div class="stat-value">{{ ov ? yuan(ov.amount) : '-' }}</div></div></el-col>
    </el-row>
    <el-card shadow="never">
      <template #header>退款趋势</template>
      <EChart :option="trendOption" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import EChart from './components/EChart.vue'
import { getRefundOverview, yuan } from '@/api/report'

const days = ref(30)
const ov = ref<Awaited<ReturnType<typeof getRefundOverview>> | null>(null)
const trendOption = ref<Record<string, unknown>>({})

async function load() {
  try {
    ov.value = await getRefundOverview(days.value)
    const t = ov.value?.trend || []
    trendOption.value = {
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: t.map((p) => p.date.slice(5)) },
      yAxis: { type: 'value', name: '元' },
      series: [{ name: '退款金额', type: 'line', smooth: true, areaStyle: {}, data: t.map((p) => p.value / 100) }],
      grid: { left: 60, right: 30, bottom: 30, top: 30 },
    }
  } catch (e) {
    ElMessage.error('退款报表加载失败：' + (e instanceof Error ? e.message : '未知错误'))
  }
}

onMounted(load)
</script>

<style scoped>
.stat-card { background: #fff; border-radius: 6px; padding: 14px 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.stat-label { font-size: 13px; color: #6b7280; }
.stat-value { font-size: 24px; font-weight: 700; margin-top: 6px; }
</style>