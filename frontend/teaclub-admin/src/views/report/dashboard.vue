<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">{{ isStoreScope ? '门店经营驾驶舱' : '总部经营驾驶舱' }}</h2>
      <span class="text-muted">销售=订单实付；充值金额不含赠送；支付金额均为模拟数据</span>
      <div class="toolbar-spacer" />
      <el-select
        v-model="storeId"
        :placeholder="isStoreScope ? '本店' : '全部门店'"
        :clearable="!isStoreScope"
        :disabled="isStoreScope"
        size="small"
        style="width: 170px"
        @change="loadAll"
      >
        <el-option v-for="s in stores" :key="s.id" :label="s.storeName" :value="s.id" />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
    </div>

    <el-alert v-if="state === 'error'" type="error" :closable="false" show-icon style="margin-bottom: 12px"
              :title="'报表加载失败：' + errMsg">
      <el-button size="small" @click="loadAll">重试</el-button>
    </el-alert>

    <el-row :gutter="14">
      <el-col :span="4"><div class="stat-card"><div class="stat-label">今日销售额(元)</div>
        <div class="stat-value">{{ ov ? yuan(ov.todaySales) : '-' }}</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">今日订单量</div>
        <div class="stat-value">{{ ov?.todayOrders ?? '-' }}</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">今日充值(元)</div>
        <div class="stat-value">{{ ov ? yuan(ov.todayRechargeAmount) : '-' }}</div>
        <div class="stat-extra">赠送 {{ ov ? yuan(ov.todayRechargeGiftAmount) : '-' }} 另计</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">今日退款(元)</div>
        <div class="stat-value">{{ ov ? yuan(ov.todayRefundAmount) : '-' }}</div>
        <div class="stat-extra">{{ ov?.todayRefundCount ?? '-' }} 笔</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">会员总数</div>
        <div class="stat-value">{{ ov?.memberCount ?? '-' }}</div>
        <div class="stat-extra">今日新增 {{ ov?.todayMemberCount ?? '-' }}</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">门店总数</div>
        <div class="stat-value">{{ ov?.storeCount ?? '-' }}</div></div></el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>近 7 日销售趋势（元）</template>
          <EChart v-if="trend7.length" :option="opt7" />
          <ReportState v-else :state="chartState" :detail="errMsg" retryable @retry="loadAll" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>近 30 日销售趋势（元）</template>
          <EChart v-if="trend30.length" :option="opt30" />
          <ReportState v-else :state="chartState" :detail="errMsg" retryable @retry="loadAll" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>门店销售排行榜（今日，元）</template>
          <EChart v-if="rank.length" :option="optRank" height="300px" />
          <ReportState v-else :state="chartState" empty-text="今日暂无门店销售数据" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>热销商品排行榜（近 30 天，销量）</template>
          <EChart v-if="hot.length" :option="optHot" height="300px" />
          <ReportState v-else :state="chartState" empty-text="暂无商品销售数据" />
        </el-card>
      </el-col>
    </el-row>

    <el-row style="margin-top: 14px">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>会员增长趋势（近 30 天，人）</template>
          <EChart v-if="growth.length" :option="optGrowth" />
          <ReportState v-else :state="chartState" empty-text="暂无新增会员数据" />
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
  getDashboardOverview,
  getDashboardTrend,
  getProductRanking,
  getMemberGrowth,
  yuan,
  type LoadState,
  type ReportOverview,
} from '@/api/report'
import { getAccessibleStores } from '@/api/store'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

/** 门店级账号（dataScope=STORE）强制只看本店，后端也会兜底校验 */
const isStoreScope = computed(() => userStore.userInfo?.dataScope === 'STORE')

const ov = ref<ReportOverview | null>(null)
const stores = ref<{ id: number; storeName: string }[]>([])
const storeId = ref<number | undefined>()
const loading = ref(false)
const state = ref<LoadState>('loading')
const errMsg = ref('')

const trend7 = ref<{ date: string; value: number }[]>([])
const trend30 = ref<{ date: string; value: number }[]>([])
const rank = ref<{ storeName: string; sales: number }[]>([])
const hot = ref<{ name: string; qty: number }[]>([])
const growth = ref<{ date: string; value: number }[]>([])

// 图表状态：整体 error 时图表区显示错误态；否则按各自数据有无显示 empty
const chartState = computed<'loading' | 'error' | 'empty'>(() =>
  state.value === 'loading' ? 'loading' : state.value === 'error' ? 'error' : 'empty',
)

function lineOption(dates: string[], values: number[], name: string, unit: string) {
  return {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: dates, name: '日期' },
    yAxis: { type: 'value', name: unit },
    series: [{ name, type: 'line', smooth: true, areaStyle: {}, data: values }],
    legend: { data: [name] },
    grid: { left: 60, right: 24, bottom: 28, top: 36 },
  }
}

const opt7 = computed(() => lineOption(trend7.value.map((x) => x.date.slice(5)),
  trend7.value.map((x) => x.value / 100), '销售额', '元'))
const opt30 = computed(() => lineOption(trend30.value.map((x) => x.date.slice(5)),
  trend30.value.map((x) => x.value / 100), '销售额', '元'))
const optGrowth = computed(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: growth.value.map((x) => x.date.slice(5)), name: '日期' },
  yAxis: { type: 'value', minInterval: 1, name: '人' },
  series: [{ name: '新增会员', type: 'bar', data: growth.value.map((x) => x.value) }],
  legend: { data: ['新增会员'] },
  grid: { left: 50, right: 24, bottom: 28, top: 36 },
}))
const optRank = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  xAxis: { type: 'value', name: '元' },
  yAxis: {
    type: 'category', data: rank.value.map((x) => x.storeName).reverse(),
    axisLabel: { width: 100, overflow: 'truncate', formatter: (n: string) => (n.length > 8 ? n.slice(0, 7) + '…' : n) },
  },
  series: [{ name: '销售额', type: 'bar', barMaxWidth: 24, data: rank.value.map((x) => x.sales / 100).reverse() }],
  legend: { data: ['销售额'] },
  grid: { left: 110, right: 30, top: 30, bottom: 28 },
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

async function loadAll() {
  loading.value = true
  state.value = 'loading'
  errMsg.value = ''
  try {
    ov.value = await getDashboardOverview(storeId.value)
    const [t7, t30, g] = await Promise.all([
      getDashboardTrend(7, storeId.value),
      getDashboardTrend(30, storeId.value),
      getMemberGrowth(30, storeId.value),
    ])
    trend7.value = t7.salesTrend || []
    trend30.value = t30.salesTrend || []
    growth.value = g.growth || []
    rank.value = (ov.value?.storeRanking?.list || []).map((x) => ({
      storeName: x.storeName, sales: x.sales,
    }))
    try {
      const pr = await getProductRanking(30, 'qty', storeId.value)
      hot.value = (pr.list || []).map((x) => ({ name: x.name, qty: x.qty }))
    } catch {
      hot.value = []
    }
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
    stores.value = (Array.isArray(s) ? s : []).map((x) => ({
      id: x.id, storeName: x.storeName || String(x.id),
    }))
    // 门店级账号默认锁定到授权门店（通常只有一家），避免走「全部门店」口径
    if (isStoreScope.value && stores.value.length && storeId.value == null) {
      storeId.value = stores.value[0].id
    }
  } catch { stores.value = [] }
  await loadAll()
})
</script>

<style scoped>
.stat-card { background: #fff; border-radius: 6px; padding: 14px 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06); min-height: 84px; }
.stat-label { font-size: 13px; color: #6b7280; }
.stat-value { font-size: 22px; font-weight: 700; margin-top: 6px; }
.stat-extra { font-size: 12px; color: #9ca3af; margin-top: 4px; }
</style>