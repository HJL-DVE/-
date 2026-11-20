<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">订单统计</h2>
      <span class="text-muted">口径：成交额=订单实付（PAID/COMPLETED/REFUNDING/REFUNDED）</span>
      <div class="toolbar-spacer" />
      <el-select v-model="storeId" placeholder="全部门店" clearable size="small" style="width: 170px" @change="load">
        <el-option v-for="s in stores" :key="s.id" :label="s.storeName" :value="s.id" />
      </el-select>
      <el-radio-group v-model="days" size="small" @change="load">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
        <el-radio-button :value="90">近 90 天</el-radio-button>
      </el-radio-group>
      <el-button :icon="Refresh" size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="state === 'error'" type="error" :closable="false" show-icon style="margin-bottom: 12px" :title="'加载失败：' + errMsg">
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>
    <ReportState v-if="state === 'loading'" state="loading" height="320px" />

    <template v-else>
      <el-row :gutter="14">
        <el-col :span="6"><div class="stat-card"><div class="stat-label">成交额(元)</div><div class="stat-value">{{ s ? yuan(s.gmv) : '-' }}</div></div></el-col>
        <el-col :span="6"><div class="stat-card"><div class="stat-label">订单数</div><div class="stat-value">{{ s?.orderCount ?? '-' }}</div></div></el-col>
        <el-col :span="6"><div class="stat-card"><div class="stat-label">已支付订单</div><div class="stat-value">{{ s?.paidOrderCount ?? '-' }}</div></div></el-col>
        <el-col :span="6"><div class="stat-card"><div class="stat-label">客单价(元)</div><div class="stat-value">{{ s ? yuan(s.avgOrderAmount) : '-' }}</div></div></el-col>
      </el-row>

      <el-card shadow="never" style="margin-top: 14px">
        <template #header>余额支付金额（元）—— 钱包消费 + 微信收款（见下）</template>
        <div class="stat-value" style="font-size: 30px">{{ s ? yuan(s.balancePayAmount) : '-' }}</div>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import ReportState from '@/views/report/components/ReportState.vue'
import { getOrderStatsSummary } from '@/api/order'
import type { OrderStatsVO } from '@/api/types'
import { getAccessibleStores } from '@/api/store'

function yuan(fen: number | undefined | null): string { return ((fen ?? 0) / 100).toFixed(2) }

const s = ref<OrderStatsVO | null>(null)
const stores = ref<{ id: number; storeName: string }[]>([])
const storeId = ref<number | undefined>()
const days = ref(7)
const loading = ref(false)
const state = ref<'loading' | 'error' | 'ready'>('loading')
const errMsg = ref('')

async function load() {
  loading.value = true
  state.value = 'loading'
  errMsg.value = ''
  try {
    s.value = await getOrderStatsSummary({ days: days.value, storeId: storeId.value })
    state.value = 'ready'
  } catch (e) {
    state.value = 'error'
    errMsg.value = e instanceof Error ? e.message : '未知错误'
  } finally { loading.value = false }
}

onMounted(async () => {
  try {
    const st = await getAccessibleStores()
    stores.value = (Array.isArray(st) ? st : []).map((x) => ({ id: x.id, storeName: x.storeName || String(x.id) }))
  } catch { stores.value = [] }
  await load()
})
</script>

<style scoped>
.stat-card { background: #fff; border-radius: 6px; padding: 14px 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06); min-height: 84px; }
.stat-label { font-size: 13px; color: #6b7280; }
.stat-value { font-size: 24px; font-weight: 700; margin-top: 6px; }
</style>