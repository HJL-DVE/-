<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">营销数据分析</h2>
      <div class="toolbar-spacer" />
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
      <el-col :span="6"><div class="stat-card"><div class="stat-label">优惠券领取量</div>
        <div class="stat-value">{{ ov?.couponReceived ?? '-' }}</div></div></el-col>
      <el-col :span="6"><div class="stat-card"><div class="stat-label">优惠券使用量</div>
        <div class="stat-value">{{ ov?.couponUsed ?? '-' }}</div>
        <div class="stat-extra">使用率 {{ ov?.couponUseRate ?? '-' }}%</div></div></el-col>
      <el-col :span="6"><div class="stat-card"><div class="stat-label">积分发放量</div>
        <div class="stat-value">{{ ov?.pointsEarned ?? '-' }}</div></div></el-col>
      <el-col :span="6"><div class="stat-card"><div class="stat-label">积分兑换量</div>
        <div class="stat-value">{{ ov?.pointsConsumed ?? '-' }}</div></div></el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>优惠券领取 vs 使用</template>
          <EChart v-if="hasCoupon" :option="optCoupon" />
          <ReportState v-else :state="chartState" empty-text="统计期内暂无优惠券数据" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>会员等级分布（人）</template>
          <EChart v-if="levels.length" :option="optLevel" />
          <ReportState v-else :state="chartState" empty-text="暂无会员数据" />
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
import { getMarketingOverview, type LoadState } from '@/api/report'

type MktOv = Awaited<ReturnType<typeof getMarketingOverview>>
const ov = ref<MktOv | null>(null)
const days = ref(30)
const loading = ref(false)
const state = ref<LoadState>('loading')
const errMsg = ref('')
const levels = ref<{ name: string; value: number }[]>([])

const chartState = computed<'loading' | 'error' | 'empty'>(() =>
  state.value === 'loading' ? 'loading' : state.value === 'error' ? 'error' : 'empty',
)
const hasCoupon = computed(() =>
  (ov.value?.couponReceived ?? 0) > 0 || (ov.value?.couponUsed ?? 0) > 0,
)

const optCoupon = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  legend: { data: ['领取量', '使用量'] },
  xAxis: { type: 'category', data: ['优惠券'] },
  yAxis: { type: 'value', minInterval: 1, name: '张' },
  series: [
    { name: '领取量', type: 'bar', barWidth: 80, data: [ov.value?.couponReceived ?? 0] },
    { name: '使用量', type: 'bar', barWidth: 80, data: [ov.value?.couponUsed ?? 0] },
  ],
  grid: { left: 54, right: 24, bottom: 28, top: 40 },
}))
const optLevel = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} 人 ({d}%)' },
  legend: { orient: 'vertical', left: 'left', top: 'middle', itemGap: 10 },
  series: [{
    name: '会员等级',
    type: 'pie', radius: ['40%', '68%'], center: ['62%', '55%'],
    label: { formatter: '{d}%' },
    data: levels.value.map((x) => ({ name: x.name || '未知', value: x.value })),
  }],
}))

async function load() {
  loading.value = true
  state.value = 'loading'
  errMsg.value = ''
  try {
    ov.value = await getMarketingOverview(days.value)
    levels.value = ov.value?.memberLevelDistribution || []
    state.value = 'ready'
  } catch (e) {
    state.value = 'error'
    errMsg.value = e instanceof Error ? e.message : '未知错误'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.stat-card { background: #fff; border-radius: 6px; padding: 14px 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06); min-height: 84px; }
.stat-label { font-size: 13px; color: #6b7280; }
.stat-value { font-size: 22px; font-weight: 700; margin-top: 6px; }
.stat-extra { font-size: 12px; color: #9ca3af; margin-top: 4px; }
</style>