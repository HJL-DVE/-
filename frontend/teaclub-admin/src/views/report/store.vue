<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">门店经营驾驶舱</h2>
      <div class="toolbar-spacer" />
      <el-select v-model="storeId" placeholder="选择门店" size="small" style="width: 180px" @change="load">
        <el-option v-for="s in stores" :key="s.id" :label="s.storeName" :value="s.id" />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-row :gutter="14">
      <el-col :span="4"><div class="stat-card"><div class="stat-label">本店销售(元)</div><div class="stat-value">{{ ov ? yuan(ov.sales) : '-' }}</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">本店订单</div><div class="stat-value">{{ ov?.orders ?? '-' }}</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">客单价(元)</div><div class="stat-value">{{ ov?.avgOrder ?? '-' }}</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">本店充值(元)</div><div class="stat-value">{{ ov ? yuan(ov.rechargeAmount) : '-' }}</div><div class="stat-extra">赠送 {{ ov ? yuan(ov.rechargeGiftAmount) : '-' }} 另计</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">本店退款(元)</div><div class="stat-value">{{ ov ? yuan(ov.refundAmount) : '-' }}</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">今日新增会员</div><div class="stat-value">{{ ov?.todayNewMembers ?? '-' }}</div></div></el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 14px">
      <template #header>本店热销商品（近 30 天，按销量）</template>
      <EChart :option="hotOption" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import EChart from './components/EChart.vue'
import { getStoreOverview, yuan } from '@/api/report'
import { getAccessibleStores } from '@/api/store'

type StoreOv = Awaited<ReturnType<typeof getStoreOverview>>
const ov = ref<StoreOv | null>(null)
const stores = ref<{ id: number; storeName: string }[]>([])
const storeId = ref<number | undefined>()
const loading = ref(false)
const hotOption = ref<Record<string, unknown>>({})

async function load() {
  loading.value = true
  try {
    ov.value = await getStoreOverview(storeId.value)
    const hot = ov.value?.hotProducts || []
    hotOption.value = {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { data: ['销量', '销售额(元)'] },
      xAxis: { type: 'category', data: hot.map((x) => x.name) },
      yAxis: [{ type: 'value', name: '件' }, { type: 'value', name: '元' }],
      series: [
        { name: '销量', type: 'bar', data: hot.map((x) => x.qty) },
        { name: '销售额(元)', type: 'bar', yAxisIndex: 1, data: hot.map((x) => x.amount / 100) },
      ],
      grid: { left: 50, right: 60, bottom: 80, top: 40 },
    }
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
.stat-card { background: #fff; border-radius: 6px; padding: 14px 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.stat-label { font-size: 13px; color: #6b7280; }
.stat-value { font-size: 24px; font-weight: 700; margin-top: 6px; }
.stat-extra { font-size: 12px; color: #9ca3af; margin-top: 4px; }
</style>