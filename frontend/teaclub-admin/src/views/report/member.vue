<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">会员分析</h2>
      <div class="toolbar-spacer" />
      <el-radio-group v-model="days" size="small" @change="loadGrowth">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
        <el-radio-button :value="90">近 90 天</el-radio-button>
      </el-radio-group>
    </div>

    <el-row :gutter="14">
      <el-col :span="16">
        <el-card shadow="never"><template #header>新增会员趋势</template><EChart :option="growthOption" /></el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never"><template #header>会员等级分布</template><EChart :option="levelOption" /></el-card>
      </el-col>
    </el-row>
    <el-card shadow="never" style="margin-top: 14px">
      <template #header>会员消费排行 TOP10</template>
      <el-table :data="rank" size="small" stripe>
        <el-table-column prop="memberNo" label="会员号" width="180" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column label="累计消费(元)" align="right">
          <template #default="{ row }">{{ (row.totalConsume / 100).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="orderCount" label="订单数" width="100" align="right" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import EChart from './components/EChart.vue'
import { getMemberOverview, getMemberGrowth } from '@/api/report'

const days = ref(30)
const growthOption = ref<Record<string, unknown>>({})
const levelOption = ref<Record<string, unknown>>({})
const rank = ref<{ memberNo: string; nickname: string; totalConsume: number; orderCount: number }[]>([])

async function loadGrowth() {
  const g = await getMemberGrowth(days.value)
  growthOption.value = {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: (g.growth || []).map((p) => p.date.slice(5)) },
    yAxis: { type: 'value', minInterval: 1, name: '人' },
    series: [{ name: '新增会员', type: 'bar', data: (g.growth || []).map((p) => p.value) }],
    grid: { left: 50, right: 30, bottom: 30, top: 30 },
  }
}

async function load() {
  const o = await getMemberOverview()
  rank.value = o.consumeRank || []
  levelOption.value = {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 人 ({d}%)' },
    legend: { orient: 'vertical', left: 'left' },
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      label: { formatter: '{b}: {c}' },
      data: (o.levelDistribution || []).map((x) => ({ name: x.name || '未知', value: x.value })),
    }],
  }
  await loadGrowth()
}

onMounted(load)
</script>