<template>
  <div class="page">
    <el-row :gutter="14">
      <el-col :span="9">
        <div class="page-card">
          <div class="toolbar">
            <strong>会员等级定义</strong>
            <span class="text-muted">GET /api/admin/member/levels</span>
            <div class="toolbar-spacer" />
            <el-button :icon="Refresh" size="small" @click="loadLevels">刷新</el-button>
          </div>

          <ApiPending
            v-if="levelsError"
            title="会员等级接口未就绪"
            url="GET /api/admin/member/levels"
            :code="levelsError.code"
            :message="levelsError.message"
            :trace-id="levelsError.traceId"
          />
          <el-empty v-else-if="!levelsLoading && levels.length === 0" description="暂无等级定义" />
          <div v-else v-loading="levelsLoading" class="level-list">
            <div
              v-for="item in levels"
              :key="item.id"
              class="level-item"
              :class="{ active: activeLevelId === item.id }"
              @click="selectLevel(item.id)"
            >
              <div class="level-head">
                <span class="level-name">{{ item.levelName }}</span>
                <el-tag size="small" :type="item.status === 'ENABLED' ? 'success' : 'info'">{{ item.status }}</el-tag>
              </div>
              <div class="text-muted mono">{{ item.levelCode }} · Lv.{{ item.levelNo }}</div>
              <div class="text-muted">
                升级门槛 {{ fenToYuan(item.upgradeThreshold) }} · 折扣 {{ discountText(item.discountRate) }} · 积分倍率
                {{ pointRateText(item.pointRate) }}
              </div>
              <div class="text-muted">
                会员数 {{ item.memberCount ?? '-' }} · 营销权益 {{ benefitCount(item.id) }} 项
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :span="15">
        <div class="page-card">
          <div class="toolbar">
            <strong>等级营销权益</strong>
            <span class="text-muted">GET /api/admin/marketing/level-benefits/level/{levelId}</span>
            <div class="toolbar-spacer" />
            <el-button :icon="Refresh" size="small" :disabled="!activeLevelId" @click="loadBenefits">刷新</el-button>
            <el-button type="primary" :icon="Plus" size="small" :disabled="!activeLevelId" @click="openCreate">
              新增权益
            </el-button>
          </div>

          <el-descriptions v-if="activeLevel" :column="2" border size="small" style="margin-bottom: 14px">
            <el-descriptions-item label="等级">{{ activeLevel.levelName }}（{{ activeLevel.levelCode }}）</el-descriptions-item>
            <el-descriptions-item label="等级序号">Lv.{{ activeLevel.levelNo }}</el-descriptions-item>
            <el-descriptions-item label="升级门槛">{{ fenToYuan(activeLevel.upgradeThreshold) }}</el-descriptions-item>
            <el-descriptions-item label="升级方式">{{ activeLevel.upgradeType }}</el-descriptions-item>
            <el-descriptions-item label="等级折扣">{{ discountText(activeLevel.discountRate) }}</el-descriptions-item>
            <el-descriptions-item label="积分倍率">{{ pointRateText(activeLevel.pointRate) }}</el-descriptions-item>
          </el-descriptions>

          <ApiPending
            v-if="benefitsError"
            title="等级权益接口未就绪"
            url="GET /api/admin/marketing/level-benefits/level/{levelId}"
            :code="benefitsError.code"
            :message="benefitsError.message"
            :trace-id="benefitsError.traceId"
          />
          <el-empty v-else-if="!activeLevelId" description="请选择左侧等级查看权益" />
          <el-table v-else :data="benefits" v-loading="benefitsLoading" border stripe>
            <el-table-column label="权益编码" width="180">
              <template #default="{ row }">
                <span class="mono">{{ row.benefitCode }}</span>
                <div class="text-muted">{{ BENEFIT_LABEL[row.benefitCode] || '-' }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="benefitName" label="权益名称" min-width="150" show-overflow-tooltip />
            <el-table-column label="权益值" min-width="150">
              <template #default="{ row }">
                <span>{{ row.benefitValue || '-' }}</span>
                <div class="text-muted">{{ benefitValueHint(row.benefitCode) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.updateTime || row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="onDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑权益' : '新增权益'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="所属等级">
          <span>{{ activeLevel ? activeLevel.levelName : '-' }}（ID {{ form.levelId }}）</span>
        </el-form-item>
        <el-form-item label="权益编码" required>
          <el-select v-model="form.benefitCode" style="width: 100%" @change="onCodeChange">
            <el-option v-for="(label, value) in BENEFIT_LABEL" :key="value" :label="label + '（' + value + '）'" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="权益名称" required>
          <el-input v-model="form.benefitName" placeholder="展示给会员的权益名称" />
        </el-form-item>
        <el-form-item label="权益值">
          <el-input v-model="form.benefitValue" placeholder="按右侧说明填写" />
          <div class="text-muted" style="margin-top: 4px">{{ benefitValueHint(form.benefitCode) }}</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="text-muted">POST /api/admin/marketing/level-benefits（同等级同编码唯一，重复提交即更新）</div>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { getMemberLevels } from '@/api/member'
import {
  deleteLevelBenefit,
  getLevelBenefits,
  getLevelBenefitsByLevel,
  saveLevelBenefit,
} from '@/api/marketing'
import { ApiError, isApiError } from '@/api/request'
import ApiPending from '@/components/ApiPending.vue'
import { fenToYuan, formatDateTime } from '@/utils/format'
import type { LevelBenefitVO } from '@/api/marketing'
import type { MemberLevelVO } from '@/api/types'

/** 与后端 BenefitCode 枚举一致 */
const BENEFIT_LABEL: Record<string, string> = {
  BIRTHDAY_COUPON: '生日券',
  POINT_RATE: '积分倍率',
  BIRTHDAY_POINTS: '生日积分',
  EXCLUSIVE_COUPON: '专属券',
  PRIORITY_SERVICE: '优先服务',
}

const BENEFIT_HINT: Record<string, string> = {
  BIRTHDAY_COUPON: '填券模板 ID，生日当天自动发放',
  POINT_RATE: '倍率 ×100，如 150 = 1.5 倍',
  BIRTHDAY_POINTS: '填积分数，如 200',
  EXCLUSIVE_COUPON: '券模板 ID，多个用英文逗号分隔',
  PRIORITY_SERVICE: '展示型权益文案，如「免排队」',
}

function benefitValueHint(code: string): string {
  return BENEFIT_HINT[code] || '按权益编码填写对应值'
}

const levelsLoading = ref(false)
const levels = ref<MemberLevelVO[]>([])
const levelsError = ref<ApiError | null>(null)

const allBenefits = ref<Record<string, LevelBenefitVO[]>>({})
const benefitsLoading = ref(false)
const benefits = ref<LevelBenefitVO[]>([])
const benefitsError = ref<ApiError | null>(null)
const activeLevelId = ref<number | null>(null)

const activeLevel = computed<MemberLevelVO | null>(
  () => levels.value.find((item) => item.id === activeLevelId.value) || null,
)

function toApiError(error: unknown): ApiError {
  return isApiError(error) ? error : new ApiError(-1, (error as Error)?.message || '未知错误')
}

/** 万分比转「折」：9500 → 9.5 折 */
function discountText(rate?: number | null): string {
  if (rate === null || rate === undefined) return '-'
  if (rate >= 10000) return '不打折'
  return (rate / 1000).toFixed(2).replace(/0+$/, '').replace(/\.$/, '') + ' 折'
}

/** 积分倍率 ×100：150 → 1.5 倍 */
function pointRateText(rate?: number | null): string {
  if (rate === null || rate === undefined) return '-'
  return (rate / 100).toFixed(2).replace(/0+$/, '').replace(/\.$/, '') + ' 倍'
}

function benefitCount(levelId: number): number {
  const list = allBenefits.value[String(levelId)]
  return list ? list.length : 0
}

async function loadAllBenefits(): Promise<void> {
  try {
    allBenefits.value = (await getLevelBenefits()) || {}
  } catch {
    allBenefits.value = {}
  }
}

async function loadLevels(): Promise<void> {
  levelsLoading.value = true
  try {
    levels.value = (await getMemberLevels()) || []
    levelsError.value = null
    if (!activeLevelId.value && levels.value.length > 0) {
      await selectLevel(levels.value[0].id)
    } else if (activeLevelId.value) {
      await loadBenefits()
    }
  } catch (error) {
    levels.value = []
    levelsError.value = toApiError(error)
  } finally {
    levelsLoading.value = false
  }
}

async function selectLevel(levelId: number): Promise<void> {
  activeLevelId.value = levelId
  await loadBenefits()
}

async function loadBenefits(): Promise<void> {
  if (!activeLevelId.value) {
    benefits.value = []
    return
  }
  benefitsLoading.value = true
  try {
    benefits.value = (await getLevelBenefitsByLevel(activeLevelId.value)) || []
    benefitsError.value = null
  } catch (error) {
    benefits.value = []
    benefitsError.value = toApiError(error)
  } finally {
    benefitsLoading.value = false
  }
  await loadAllBenefits()
}

interface BenefitForm {
  id?: number
  levelId: number
  benefitCode: string
  benefitName: string
  benefitValue: string
  status: string
}

const formVisible = ref(false)
const submitting = ref(false)
const form = reactive<BenefitForm>({
  id: undefined,
  levelId: 0,
  benefitCode: 'POINT_RATE',
  benefitName: '',
  benefitValue: '',
  status: 'ENABLED',
})

function onCodeChange(): void {
  if (!form.benefitName || Object.values(BENEFIT_LABEL).includes(form.benefitName)) {
    form.benefitName = BENEFIT_LABEL[form.benefitCode] || ''
  }
}

function openCreate(): void {
  if (!activeLevelId.value) return
  form.id = undefined
  form.levelId = activeLevelId.value
  form.benefitCode = 'POINT_RATE'
  form.benefitName = BENEFIT_LABEL.POINT_RATE
  form.benefitValue = ''
  form.status = 'ENABLED'
  formVisible.value = true
}

function openEdit(row: LevelBenefitVO): void {
  form.id = row.id
  form.levelId = row.levelId
  form.benefitCode = row.benefitCode
  form.benefitName = row.benefitName
  form.benefitValue = row.benefitValue || ''
  form.status = row.status || 'ENABLED'
  formVisible.value = true
}

async function submitForm(): Promise<void> {
  if (!form.benefitCode) {
    ElMessage.warning('请选择权益编码')
    return
  }
  if (!form.benefitName.trim()) {
    ElMessage.warning('请填写权益名称')
    return
  }
  submitting.value = true
  try {
    await saveLevelBenefit({
      id: form.id,
      levelId: form.levelId,
      benefitCode: form.benefitCode,
      benefitName: form.benefitName.trim(),
      benefitValue: form.benefitValue,
      status: form.status,
    })
    ElMessage.success('已保存')
    formVisible.value = false
    await loadBenefits()
  } catch {
    // 错误提示由 http 拦截器统一弹出
  } finally {
    submitting.value = false
  }
}

function onDelete(row: LevelBenefitVO): void {
  if (!row.id) return
  ElMessageBox.confirm('确认删除权益「' + row.benefitName + '」？', '提示', { type: 'warning' })
    .then(async () => {
      await deleteLevelBenefit(row.id as number)
      ElMessage.success('已删除')
      await loadBenefits()
    })
    .catch(() => undefined)
}

onMounted(() => {
  void loadAllBenefits()
  void loadLevels()
})
</script>

<style scoped>
.level-list {
  max-height: 620px;
  overflow-y: auto;
}

.level-item {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.15s;
}

.level-item:hover {
  border-color: #c6e2ff;
}

.level-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.level-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.level-name {
  font-weight: 600;
}
</style>
