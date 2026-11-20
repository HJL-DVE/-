<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">充值与支付统计</h2>
      <el-tag type="warning" size="small">所有支付金额均为模拟支付数据，非真实收款</el-tag>
      <div class="toolbar-spacer" />
      <el-select v-model="storeId" placeholder="全部门店" clearable size="small" style="width: 170px" @change="load">
        <el-option v-for="s in stores" :key="s.id" :label="s.storeName" :value="s.id" />
      </el-select>
      <el-radio-group v-model="days" size="small" @change="load">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
    </div>

    <el-alert v-if="state === 'error'" type="error" :closable="false" show-icon style="margin-bottom: 12px"
              :title="'报表加载失败：' + errMsg">
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <el-row :gutter="14">
      <el-col :span="5"><div class="stat-card"><div class="stat-label">充值实收金额(元)</div>
        <div class="stat-value">{{ rc ? yuan(rc.principalAmount) : '-' }}</div>
        <div class="stat-extra">{{ rc?.count ?? '-' }} 笔</div></div></el-col>
      <el-col :span="5"><div class="stat-card"><div class="stat-label">赠送金额(元)</div>
        <div class="stat-value">{{ rc ? yuan(rc.giftAmount) : '-' }}</div>
        <div class="stat-extra">不计入收款</div></div></el-col>
      <el-col :span="5"><div class="stat-card"><div class="stat-label">模拟支付成功金额(元)</div>
        <div class="stat-value">{{ pay ? yuan(pay.totalMockPaidAmount) : '-' }}</div>
        <div class="stat-extra">模拟数据，非真实收款</div></div></el-col>
      <el-col :span="5"><div class="stat-card"><div class="stat-label">钱包消费金额(元)</div>
        <div class="stat-value">{{ pay ? yuan(pay.walletConsumeAmount) : '-' }}</div>
        <div class="stat-extra">余额抵扣，非现金收款</div></div></el-col>
      <el-col :span="4"><div class="stat-card"><div class="stat-label">退款金额(元)</div>
        <div class="stat-value">{{ rf ? yuan(rf.amount) : '-' }}</div>
        <div class="stat-extra">{{ rf?.count ?? '-' }} 笔</div></div></el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>充值实收趋势（元/日）</template>
          <EChart v-if="rt.length" :option="optRc" />
          <ReportState v-else :state="chartState" empty-text="统计期内无充值" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>支付方式占比（模拟支付）</template>
          <EChart v-if="channels.length" :option="optPay" />
          <ReportState v-else :state="chartState" empty-text="统计期内无模拟支付" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 14px">
      <template #header>金额构成说明（口径）</template>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="充值实收金额">会员实际支付的本金（充 100 送 20 → 实收 100）</el-descriptions-item>
        <el-descriptions-item label="赠送金额">营销赠送部分（送 20），计入钱包到账但<b>不计入收款</b></el-descriptions-item>
        <el-descriptions-item label="钱包到账">本金 + 赠送 = {{ rc ? yuan(rc.creditedAmount) : '-' }} 元</el-descriptions-item>
        <el-descriptions-item label="模拟支付成功金额">payment-service 模拟渠道收款（业务沙箱数据，非真实收款）</el-descriptions-item>
        <el-descriptions-item label="钱包消费金额">会员用余额支付订单（余额本金+赠送抵扣），不产生新的现金收款</el-descriptions-item>
        <el-descriptions-item label="退款金额">退款成功金额（含本金与赠送按比例退回）</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import EChart from './components/EChart.vue'
import ReportState from './components/ReportState.vue'
import {
  getPaymentOverview,
  getRechargeOverview,
  getRefundOverview,
  yuan,
  type LoadState,
} from '@/api/report'
import { getAccessibleStores } from '@/api/store'

type RcOv = Awaited<ReturnType<typeof getRechargeOverview>>
type PayOv = Awaited<ReturnType<typeof getPaymentOverview>>
type RfOv = Awaited<ReturnType<typeof getRefundOverview>>
const rc = ref<RcOv | null>(null)
const pay = ref<PayOv | null>(null)
const rf = ref<RfOv | null>(null)
const stores = ref<{ id: number; storeName: string }[]>([])
const storeId = ref<number | undefined>()
const days = ref(30)
const loading = ref(false)
const state = ref<LoadState>('loading')
const errMsg = ref('')
const rt = ref<{ date: string; value: number }[]>([])
const channels = ref<{ channel: string; amount: number }[]>([])

const chartState = computed<'loading' | 'error' | 'empty'>(() =>
  state.value === 'loading' ? 'loading' : state.value === 'error' ? 'error' : 'empty',
)

const optRc = computed(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: rt.value.map((x) => x.date.slice(5)), name: '日期' },
  yAxis: { type: 'value', name: '元' },
  series: [{ name: '充值实收', type: 'line', smooth: true, areaStyle: {}, data: rt.value.map((x) => x.value / 100) }],
  legend: { data: ['充值实收'] },
  grid: { left: 60, right: 24, bottom: 28, top: 36 },
}))
const optPay = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c}元 ({d}%)' },
  legend: { orient: 'vertical', left: 'left' },
  series: [{
    name: '模拟支付',
    type: 'pie', radius: ['40%', '70%'],
    label: { formatter: '{b}: {d}%' },
    data: channels.value.map((x) => ({ name: x.channel, value: x.amount / 100 })),
  }],
}))

async function load() {
  loading.value = true
  state.value = 'loading'
  errMsg.value = ''
  try {
    const [a, p, r] = await Promise.all([
      getRechargeOverview(days.value, storeId.value),
      getPaymentOverview(days.value, storeId.value),
      getRefundOverview(days.value, storeId.value),
    ])
    rc.value = a
    pay.value = p
    rf.value = r
    rt.value = a.trend || []
    channels.value = (p.byChannel || []).map((x) => ({ channel: x.channel, amount: x.amount }))
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

<style scoped>
.stat-card { background: #fff; border-radius: 6px; padding: 14px 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06); min-height: 84px; }
.stat-label { font-size: 13px; color: #6b7280; }
.stat-value { font-size: 22px; font-weight: 700; margin-top: 6px; }
.stat-extra { font-size: 12px; color: #9ca3af; margin-top: 4px; }
</style>