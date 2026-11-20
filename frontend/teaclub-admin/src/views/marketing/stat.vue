<template>
  <div class="page">
    <ApiPending
      v-if="listError"
      title="营销活动接口未就绪"
      url="GET /api/admin/marketing/activities"
      :code="listError.code"
      :message="listError.message"
      :trace-id="listError.traceId"
    />

    <div class="page-card">
      <div class="toolbar">
        <strong>活动统计</strong>
        <span class="text-muted">数据来源：GET /api/admin/marketing/activities（全量返回）</span>
        <div class="toolbar-spacer" />
        <span class="text-muted">共 {{ activities.length }} 个活动</span>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <div class="stat-grid" v-loading="loading">
        <div class="stat-card">
          <div class="stat-label">活动总数</div>
          <div class="stat-value">{{ activities.length }}</div>
          <div class="stat-extra">按类型 / 状态分布见下方明细</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">进行中</div>
          <div class="stat-value">{{ enabledCount }}</div>
          <div class="stat-extra">status = ENABLED</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">总名额（有限额活动）</div>
          <div class="stat-value">{{ quotaTotal }}</div>
          <div class="stat-extra">不限名额活动 {{ unlimitedTotal }} 个，不计入分母</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">已用名额（有限额活动）</div>
          <div class="stat-value">{{ usedLimited }}</div>
          <div class="stat-extra">全部活动已用合计 {{ usedTotal }} 个名额</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">整体参与率</div>
          <div class="stat-value">{{ overallRateText }}</div>
          <div class="stat-extra">已用 / 总名额（仅统计有限额活动）</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">不限名额活动</div>
          <div class="stat-value">{{ unlimitedTotal }}</div>
          <div class="stat-extra">totalQuota = 0，无法计算参与率</div>
        </div>
      </div>

      <h3 style="margin: 22px 0 10px">按活动类型汇总</h3>
      <el-table :data="typeRows" v-loading="loading" border stripe size="small">
        <el-table-column label="活动类型" min-width="170">
          <template #default="{ row }">
            {{ row.label }}
            <div class="text-muted mono">{{ row.type }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="count" label="活动数" width="90" align="right" />
        <el-table-column prop="enabled" label="进行中" width="90" align="right" />
        <el-table-column label="总名额" width="100" align="right">
          <template #default="{ row }">{{ row.quota }}</template>
        </el-table-column>
        <el-table-column label="已用名额" width="110" align="right">
          <template #default="{ row }">
            {{ row.used }}
            <div v-if="row.usedAll !== row.used" class="text-muted">全部 {{ row.usedAll }}</div>
          </template>
        </el-table-column>
        <el-table-column label="参与率" min-width="200">
          <template #default="{ row }">
            <div v-if="row.rate !== null" style="display: flex; align-items: center; gap: 8px">
              <el-progress :percentage="row.rate" :stroke-width="10" :show-text="false" style="flex: 1" />
              <span class="text-muted">{{ row.rateText }}</span>
            </div>
            <span v-else class="text-muted">不限名额，无法计算</span>
          </template>
        </el-table-column>
        <el-table-column label="名额结构" width="110" align="center">
          <template #default="{ row }">
            <span class="text-muted">{{ row.unlimited }} 个不限</span>
          </template>
        </el-table-column>
      </el-table>

      <h3 style="margin: 22px 0 10px">按状态汇总</h3>
      <el-table :data="statusTable" border stripe size="small">
        <el-table-column label="状态" width="180">
          <template #default="{ row }">
            <el-tag size="small" :type="tagTypeByStatus(row.status)">
              {{ ACTIVITY_STATUS_LABEL[row.status] || row.status }}
            </el-tag>
            <span class="text-muted mono" style="margin-left: 6px">{{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="count" label="活动数" width="110" align="right" />
        <el-table-column prop="quota" label="总名额（限额）" width="140" align="right" />
        <el-table-column prop="used" label="已用名额（限额）" width="150" align="right" />
        <el-table-column prop="usedAll" label="已用名额（全部）" width="150" align="right" />
      </el-table>

      <h3 style="margin: 22px 0 10px">活动明细（真实名额使用情况）</h3>
      <el-table :data="detailRows" v-loading="loading" border stripe size="small" max-height="420">
        <el-table-column prop="activityNo" label="活动编号" min-width="190" show-overflow-tooltip />
        <el-table-column prop="activityName" label="活动名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            {{ row.activityTypeLabel || ACTIVITY_TYPE_LABEL[row.activityType] || row.activityType }}
          </template>
        </el-table-column>
        <el-table-column label="总名额" width="90" align="right">
          <template #default="{ row }">{{ row.totalQuota && row.totalQuota > 0 ? row.totalQuota : '不限' }}</template>
        </el-table-column>
        <el-table-column prop="usedQuota" label="已用" width="80" align="right" />
        <el-table-column label="使用率" min-width="170">
          <template #default="{ row }">
            <div v-if="row.rate !== null" style="display: flex; align-items: center; gap: 8px">
              <el-progress :percentage="row.rate" :stroke-width="10" :show-text="false" style="flex: 1" />
              <span class="text-muted">{{ row.rateText }}</span>
            </div>
            <span v-else class="text-muted">不限名额</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="tagTypeByStatus(row.status)">
              {{ row.statusLabel || ACTIVITY_STATUS_LABEL[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="活动时间" min-width="200">
          <template #default="{ row }">
            {{ formatDateTime(row.startTime) }} ~ {{ formatDateTime(row.endTime) }}
          </template>
        </el-table-column>
      </el-table>

      <div class="text-muted" style="margin-top: 12px">
        全部数字均由接口返回的活动列表实时聚合，没有前端硬编码或随机值。
        参与率 = 已用名额 / 总名额，分母与分子都只统计 totalQuota &gt; 0 的活动；
        totalQuota = 0（不限名额）的活动单独计数，不参与比率计算，避免出现大于 100% 的失真结果。
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getActivities } from '@/api/marketing'
import { ApiError, isApiError } from '@/api/request'
import ApiPending from '@/components/ApiPending.vue'
import { formatDateTime, tagTypeByStatus } from '@/utils/format'
import type { ActivityVO } from '@/api/marketing'

const ACTIVITY_TYPE_LABEL: Record<string, string> = {
  NEW_MEMBER_GIFT: '新人礼包',
  BIRTHDAY: '生日福利',
  FESTIVAL: '节日活动',
  CONTINUOUS_SIGN: '连续签到',
  FULL_GIFT: '满赠活动',
  POINTS_MALL: '积分商城',
  STORE_SPECIAL: '指定门店活动',
  SLEEPING_RECALL: '沉睡会员召回',
}

const ACTIVITY_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  ENABLED: '进行中',
  DISABLED: '已停用',
  EXPIRED: '已过期',
}

interface TypeRow {
  type: string
  label: string
  count: number
  enabled: number
  quota: number
  used: number
  usedAll: number
  unlimited: number
  rate: number | null
  rateText: string
}

interface StatusRow {
  status: string
  count: number
  quota: number
  used: number
  usedAll: number
}

interface DetailRow extends ActivityVO {
  rate: number | null
  rateText: string
}

const loading = ref(false)
const activities = ref<ActivityVO[]>([])
const listError = ref<ApiError | null>(null)

function toApiError(error: unknown): ApiError {
  return isApiError(error) ? error : new ApiError(-1, (error as Error)?.message || '未知错误')
}

function isLimited(item: ActivityVO): boolean {
  return !!item.totalQuota && item.totalQuota > 0
}

function rateOf(quota: number, used: number): number | null {
  if (quota <= 0) return null
  return Math.min(100, Math.round((used / quota) * 10000) / 100)
}

const typeRows = computed<TypeRow[]>(() => {
  const map = new Map<string, TypeRow>()
  activities.value.forEach((item) => {
    const key = item.activityType || 'UNKNOWN'
    let row = map.get(key)
    if (!row) {
      row = {
        type: key,
        label: item.activityTypeLabel || ACTIVITY_TYPE_LABEL[key] || key,
        count: 0,
        enabled: 0,
        quota: 0,
        used: 0,
        usedAll: 0,
        unlimited: 0,
        rate: null,
        rateText: '',
      }
      map.set(key, row)
    }
    row.count += 1
    if (item.status === 'ENABLED') row.enabled += 1
    row.usedAll += item.usedQuota || 0
    if (isLimited(item)) {
      row.quota += item.totalQuota as number
      row.used += item.usedQuota || 0
    } else {
      row.unlimited += 1
    }
  })
  const rows = Array.from(map.values())
  rows.forEach((row) => {
    row.rate = rateOf(row.quota, row.used)
    row.rateText = row.used + ' / ' + row.quota
  })
  return rows.sort((a, b) => b.count - a.count)
})

const statusTable = computed<StatusRow[]>(() => {
  const map = new Map<string, StatusRow>()
  activities.value.forEach((item) => {
    const key = item.status || 'UNKNOWN'
    let row = map.get(key)
    if (!row) {
      row = { status: key, count: 0, quota: 0, used: 0, usedAll: 0 }
      map.set(key, row)
    }
    row.count += 1
    row.usedAll += item.usedQuota || 0
    if (isLimited(item)) {
      row.quota += item.totalQuota as number
      row.used += item.usedQuota || 0
    }
  })
  return Array.from(map.values()).sort((a, b) => b.count - a.count)
})

const enabledCount = computed(() => activities.value.filter((item) => item.status === 'ENABLED').length)

const quotaTotal = computed(() =>
  activities.value.reduce((sum, item) => sum + (isLimited(item) ? (item.totalQuota as number) : 0), 0),
)

const usedLimited = computed(() =>
  activities.value.reduce((sum, item) => sum + (isLimited(item) ? item.usedQuota || 0 : 0), 0),
)

const usedTotal = computed(() => activities.value.reduce((sum, item) => sum + (item.usedQuota || 0), 0))

const unlimitedTotal = computed(() => activities.value.filter((item) => !isLimited(item)).length)

const overallRateText = computed(() => {
  if (quotaTotal.value <= 0) return '-'
  return ((usedLimited.value / quotaTotal.value) * 100).toFixed(1) + '%'
})

const detailRows = computed<DetailRow[]>(() =>
  activities.value.map((item) => {
    const quota = isLimited(item) ? (item.totalQuota as number) : 0
    const used = item.usedQuota || 0
    return {
      ...item,
      rate: rateOf(quota, used),
      rateText: quota > 0 ? used + ' / ' + quota : '',
    }
  }),
)

async function load(): Promise<void> {
  loading.value = true
  try {
    activities.value = (await getActivities()) || []
    listError.value = null
  } catch (error) {
    activities.value = []
    listError.value = toApiError(error)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
