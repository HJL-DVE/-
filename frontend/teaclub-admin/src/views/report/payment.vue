<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">支付统计</h2>
      <el-tag type="warning" size="small">{{ ov?.mockPaymentMark || '模拟支付数据' }}</el-tag>
      <div class="toolbar-spacer" />
      <el-radio-group v-model="days" size="small" @change="load">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
    </div>

    <el-row :gutter="14">
      <el-col :span="12">
        <el-card shadow="never"><template #header>各支付方式占比（模拟支付）</template><EChart :option="pieOption" /></el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>金额构成</template>
          <div class="stat-card" style="margin-bottom: 12px">
            <div class="stat-label">模拟支付总额(元)</div>
            <div class="stat-value">{{ ov ? yuan(ov.totalMockPaidAmount) : '-' }}</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">钱包消费(元) —— 余额抵扣，不计入模拟支付</div>
            <div class="stat-value">{{ ov ? yuan(ov.walletConsumeAmount) : '-' }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import EChart from './components/EChart.vue'
import { getPaymentOverview, yuan } from '@/api/report'

const days = ref(30)
const ov = ref<Awaited<ReturnType<typeof getPaymentOverview>> | null>(null)
const pieOption = ref<Record<string, unknown>>({})

async function load() {
  ov.value = await getPaymentOverview(days.value)
  const ch = ov.value?.byChannel || []
  pieOption.value = {
    tooltip: { trigger: 'item', formatter: '{b}: {c}元 ({d}%)' },
    legend: { orient: 'vertical', left: 'left' },
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      data: ch.map((x) => ({ name: x.channel, value: x.amount / 100 })),
    }],
  }
}

onMounted(load)
</script>

<style scoped>
.stat-card { background: #fff; border-radius: 6px; padding: 14px 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.stat-label { font-size: 13px; color: #6b7280; }
.stat-value { font-size: 24px; font-weight: 700; margin-top: 6px; }
</style>