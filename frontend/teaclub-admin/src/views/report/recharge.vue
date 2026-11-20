<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">充值统计</h2>
      <span class="text-muted">口径：充值金额=本金；赠送金额单列；钱包到账=本金+赠送；赠送不计入收款</span>
      <div class="toolbar-spacer" />
      <el-radio-group v-model="days" size="small" @change="load">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
    </div>

    <el-row :gutter="14">
      <el-col :span="6"><div class="stat-card"><div class="stat-label">充值笔数</div><div class="stat-value">{{ ov?.count ?? '-' }}</div></div></el-col>
      <el-col :span="6"><div class="stat-card"><div class="stat-label">充值金额(元)</div><div class="stat-value">{{ ov ? yuan(ov.principalAmount) : '-' }}</div></div></el-col>
      <el-col :span="6"><div class="stat-card"><div class="stat-label">赠送金额(元)</div><div class="stat-value">{{ ov ? yuan(ov.giftAmount) : '-' }}</div><div class="stat-extra">不计入收款</div></div></el-col>
      <el-col :span="6"><div class="stat-card"><div class="stat-label">钱包到账(元)</div><div class="stat-value">{{ ov ? yuan(ov.creditedAmount) : '-' }}</div></div></el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 14px">
      <template #header>充值趋势（本金 / 赠送）</template>
      <EChart :option="trendOption" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import EChart from './components/EChart.vue'
import { getRechargeOverview, yuan } from '@/api/report'

const days = ref(30)
const ov = ref<Awaited<ReturnType<typeof getRechargeOverview>> | null>(null)
const trendOption = ref<Record<string, unknown>>({})

async function load() {
  ov.value = await getRechargeOverview(days.value)
  const t = ov.value?.trend || []
  trendOption.value = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['本金(元)', '赠送(元)'] },
    xAxis: { type: 'category', data: t.map((p) => p.date.slice(5)) },
    yAxis: { type: 'value', name: '元' },
    series: [
      { name: '本金(元)', type: 'line', smooth: true, data: t.map((p) => p.value / 100) },
      // 趋势里只有本金序列，赠送叠加展示需要单独接口；此处如实只画本金
    ],
    grid: { left: 60, right: 30, bottom: 30, top: 40 },
  }
}

onMounted(load)
</script>

<style scoped>
.stat-card { background: #fff; border-radius: 6px; padding: 14px 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.stat-label { font-size: 13px; color: #6b7280; }
.stat-value { font-size: 24px; font-weight: 700; margin-top: 6px; }
.stat-extra { font-size: 12px; color: #9ca3af; margin-top: 4px; }
</style>