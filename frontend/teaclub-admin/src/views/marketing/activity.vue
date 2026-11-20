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
        <el-select v-model="query.activityType" placeholder="活动类型" clearable style="width: 170px" @change="load">
          <el-option v-for="(label, value) in ACTIVITY_TYPE_LABEL" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="load">
          <el-option v-for="(label, value) in ACTIVITY_STATUS_LABEL" :key="value" :label="label" :value="value" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <div class="toolbar-spacer" />
        <span class="text-muted">GET /api/admin/marketing/activities · 共 {{ records.length }} 条</span>
        <el-button type="primary" :icon="Plus" :disabled="!!listError" @click="openCreate">新建活动</el-button>
      </div>

      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column prop="activityNo" label="活动编号" min-width="190" show-overflow-tooltip />
        <el-table-column prop="activityName" label="活动名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            {{ row.activityTypeLabel || ACTIVITY_TYPE_LABEL[row.activityType] || row.activityType }}
          </template>
        </el-table-column>
        <el-table-column label="范围" width="100">
          <template #default="{ row }">{{ row.scopeType === 'STORE' ? '指定门店' : '全品牌' }}</template>
        </el-table-column>
        <el-table-column label="活动时间" min-width="200">
          <template #default="{ row }">
            {{ formatDateTime(row.startTime) }} ~ {{ formatDateTime(row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column label="名额（总 / 已用）" width="150" align="center">
          <template #default="{ row }">
            {{ row.totalQuota ? row.totalQuota + ' / ' + (row.usedQuota || 0) : '不限 / ' + (row.usedQuota || 0) }}
          </template>
        </el-table-column>
        <el-table-column label="每人限次" width="90" align="center">
          <template #default="{ row }">{{ row.perMemberLimit ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="优先级" width="80" align="center">
          <template #default="{ row }">{{ row.priority ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="奖励配置 configJson" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.configJson || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="tagTypeByStatus(row.status)">
              {{ row.statusLabel || ACTIVITY_STATUS_LABEL[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="onToggleStatus(row)">
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑活动' : '新建活动'" width="700px">
      <el-form label-width="120px">
        <el-form-item label="活动名称" required>
          <el-input v-model="form.activityName" placeholder="如：双十一满赠" />
        </el-form-item>
        <el-form-item label="活动类型" required>
          <el-select v-model="form.activityType" style="width: 100%" @change="onTypeChange">
            <el-option v-for="(label, value) in ACTIVITY_TYPE_LABEL" :key="value" :label="label + '（' + value + '）'" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用范围">
          <el-select v-model="form.scopeType" style="width: 100%">
            <el-option label="全品牌" value="ALL" />
            <el-option label="指定门店" value="STORE" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scopeType === 'STORE'" label="门店 ID">
          <el-input v-model="form.storeIds" placeholder="逗号分隔，如 1,2,3" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            value-format="YYYY-MM-DD[T]HH:mm:ss"
            placeholder="留空表示不限"
            style="width: 230px"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            value-format="YYYY-MM-DD[T]HH:mm:ss"
            placeholder="留空表示不限"
            style="width: 230px"
          />
        </el-form-item>
        <el-form-item label="总名额">
          <el-input-number v-model="form.totalQuota" :min="0" :step="10" />
          <span class="text-muted" style="margin-left: 8px">0 = 不限</span>
        </el-form-item>
        <el-form-item label="每人限次">
          <el-input-number v-model="form.perMemberLimit" :min="0" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" />
          <span class="text-muted" style="margin-left: 8px">数值越大越优先</span>
        </el-form-item>
        <el-form-item label="奖励配置">
          <div style="width: 100%">
            <el-input v-model="form.configJson" type="textarea" :rows="7" placeholder='{"thresholdAmount":5000,"couponTemplateIds":[],"points":0}' />
            <div style="margin-top: 8px">
              <el-button size="small" @click="fillTemplate">按类型生成模板</el-button>
              <el-button size="small" @click="formatConfig">格式化 JSON</el-button>
              <span class="text-muted" style="margin-left: 8px">
                结构见 ActivityRewardConfig：thresholdAmount / couponTemplateIds / points / continuousDays / sleepingDays / description
              </span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 200px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="进行中" value="ENABLED" />
            <el-option label="已停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="活动说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  changeActivityStatus,
  createActivity,
  deleteActivity,
  getActivities,
  getActivity,
  updateActivity,
} from '@/api/marketing'
import { ApiError, isApiError } from '@/api/request'
import ApiPending from '@/components/ApiPending.vue'
import { formatDateTime, tagTypeByStatus } from '@/utils/format'
import type { ActivityVO } from '@/api/marketing'

/** 与后端 ActivityType 枚举一致的 8 类活动 */
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

/** 与后端 ActivityStatus 枚举一致 */
const ACTIVITY_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  ENABLED: '进行中',
  DISABLED: '已停用',
  EXPIRED: '已过期',
}

/** 按活动类型生成 ActivityRewardConfig 骨架 */
const CONFIG_TEMPLATE: Record<string, string> = {
  NEW_MEMBER_GIFT: '{\n  "couponTemplateIds": [],\n  "points": 0,\n  "description": "新人注册赠券"\n}',
  BIRTHDAY: '{\n  "couponTemplateIds": [],\n  "points": 0,\n  "description": "生日当月赠券 + 积分"\n}',
  FESTIVAL: '{\n  "couponTemplateIds": [],\n  "points": 0,\n  "description": "节日活动奖励"\n}',
  CONTINUOUS_SIGN: '{\n  "continuousDays": 7,\n  "couponTemplateIds": [],\n  "points": 0,\n  "description": "连续签到 7 天奖励"\n}',
  FULL_GIFT: '{\n  "thresholdAmount": 5000,\n  "couponTemplateIds": [],\n  "points": 0,\n  "description": "满 50 元赠券 + 积分"\n}',
  POINTS_MALL: '{\n  "description": "积分商城长期开放"\n}',
  STORE_SPECIAL: '{\n  "couponTemplateIds": [],\n  "points": 0,\n  "description": "指定门店活动奖励"\n}',
  SLEEPING_RECALL: '{\n  "sleepingDays": 30,\n  "couponTemplateIds": [],\n  "points": 0,\n  "description": "沉睡 30 天召回奖励"\n}',
}

const loading = ref(false)
const records = ref<ActivityVO[]>([])
const listError = ref<ApiError | null>(null)
const query = reactive<{ activityType: string; status: string }>({ activityType: '', status: '' })

function toApiError(error: unknown): ApiError {
  return isApiError(error) ? error : new ApiError(-1, (error as Error)?.message || '未知错误')
}

async function load(): Promise<void> {
  loading.value = true
  try {
    records.value =
      (await getActivities({
        activityType: query.activityType || undefined,
        status: query.status || undefined,
      })) || []
    listError.value = null
  } catch (error) {
    records.value = []
    listError.value = toApiError(error)
  } finally {
    loading.value = false
  }
}

interface ActivityForm {
  id?: number
  activityName: string
  activityType: string
  scopeType: string
  storeIds: string
  startTime: string
  endTime: string
  totalQuota: number
  perMemberLimit: number
  priority: number
  status: string
  description: string
  configJson: string
}

const formVisible = ref(false)
const submitting = ref(false)
const form = reactive<ActivityForm>(emptyForm())

function emptyForm(): ActivityForm {
  return {
    id: undefined,
    activityName: '',
    activityType: 'FULL_GIFT',
    scopeType: 'ALL',
    storeIds: '',
    startTime: '',
    endTime: '',
    totalQuota: 0,
    perMemberLimit: 1,
    priority: 0,
    status: 'DRAFT',
    description: '',
    configJson: '',
  }
}

/** 后端返回 2026-11-20T15:05:44.53，日期选择器需要 2026-11-20T15:05:44 */
function toPickerValue(value?: string | null): string {
  if (!value) return ''
  return String(value).slice(0, 19)
}

function onTypeChange(): void {
  if (!form.configJson.trim()) form.configJson = CONFIG_TEMPLATE[form.activityType] || ''
}

function openCreate(): void {
  Object.assign(form, emptyForm())
  formVisible.value = true
}

async function openEdit(row: ActivityVO): Promise<void> {
  try {
    const detail = await getActivity(row.id)
    Object.assign(form, {
      id: detail.id,
      activityName: detail.activityName,
      activityType: detail.activityType,
      scopeType: detail.scopeType || 'ALL',
      storeIds: (detail.storeIds || []).join(','),
      startTime: toPickerValue(detail.startTime),
      endTime: toPickerValue(detail.endTime),
      totalQuota: detail.totalQuota ?? 0,
      perMemberLimit: detail.perMemberLimit ?? 1,
      priority: detail.priority ?? 0,
      status: detail.status,
      description: detail.description || '',
      configJson: detail.configJson || '',
    } as ActivityForm)
    formVisible.value = true
  } catch {
    // 错误提示由 http 拦截器统一弹出
  }
}

function fillTemplate(): void {
  if (!form.activityType) {
    ElMessage.warning('请先选择活动类型')
    return
  }
  const tpl = CONFIG_TEMPLATE[form.activityType]
  if (!tpl) {
    ElMessage.warning('该类型暂无模板')
    return
  }
  if (form.configJson.trim()) {
    ElMessageBox.confirm('将覆盖当前 JSON 内容，确认？', '提示', { type: 'warning' })
      .then(() => {
        form.configJson = tpl
      })
      .catch(() => undefined)
    return
  }
  form.configJson = tpl
}

function formatConfig(): void {
  if (!form.configJson.trim()) {
    ElMessage.warning('请先填写 JSON')
    return
  }
  try {
    form.configJson = JSON.stringify(JSON.parse(form.configJson), null, 2)
  } catch (error) {
    ElMessage.error('JSON 格式错误：' + (error as Error).message)
  }
}

function buildPayload(): Record<string, unknown> | null {
  if (!form.activityName.trim()) {
    ElMessage.warning('请填写活动名称')
    return null
  }
  if (!form.activityType) {
    ElMessage.warning('请选择活动类型')
    return null
  }
  let configJson = form.configJson.trim()
  if (configJson) {
    try {
      configJson = JSON.stringify(JSON.parse(configJson))
    } catch (error) {
      ElMessage.error('奖励配置 JSON 格式错误：' + (error as Error).message)
      return null
    }
  }
  const payload: Record<string, unknown> = {
    activityName: form.activityName.trim(),
    activityType: form.activityType,
    scopeType: form.scopeType,
    startTime: form.startTime || null,
    endTime: form.endTime || null,
    configJson: configJson || null,
    totalQuota: form.totalQuota,
    perMemberLimit: form.perMemberLimit,
    priority: form.priority,
    status: form.status,
    description: form.description,
  }
  if (form.scopeType === 'STORE') {
    const ids = form.storeIds
      .split(',')
      .map((item) => Number(item.trim()))
      .filter((item) => Number.isInteger(item) && item > 0)
    if (ids.length === 0) {
      ElMessage.warning('指定门店活动需要填写门店 ID')
      return null
    }
    payload.storeIds = ids
  }
  return payload
}

async function submitForm(): Promise<void> {
  const payload = buildPayload()
  if (!payload) return
  submitting.value = true
  try {
    if (form.id) {
      await updateActivity(form.id, payload)
      ElMessage.success('已保存')
    } else {
      await createActivity(payload)
      ElMessage.success('已创建')
    }
    formVisible.value = false
    await load()
  } catch {
    // 错误提示由 http 拦截器统一弹出
  } finally {
    submitting.value = false
  }
}

function onToggleStatus(row: ActivityVO): void {
  const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const action = next === 'ENABLED' ? '启用' : '停用'
  ElMessageBox.confirm('确认' + action + '活动「' + row.activityName + '」？', '提示', { type: 'warning' })
    .then(async () => {
      await changeActivityStatus(row.id, next)
      ElMessage.success('已' + action)
      await load()
    })
    .catch(() => undefined)
}

function onDelete(row: ActivityVO): void {
  ElMessageBox.confirm('确认删除活动「' + row.activityName + '」？', '提示', { type: 'warning' })
    .then(async () => {
      await deleteActivity(row.id)
      ElMessage.success('已删除')
      await load()
    })
    .catch(() => undefined)
}

onMounted(load)
</script>
