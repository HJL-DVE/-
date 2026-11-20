<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">会员等级</h2>
      <span class="text-muted">等级定义与升级规则（member-service）＋ 营销权益（marketing-service）</span>
      <div class="toolbar-spacer" />
      <el-button v-if="hasPerm('member:level:create')" type="primary" size="small" @click="openLevelDialog()">新增等级</el-button>
      <el-button :icon="Refresh" size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="state === 'error'" type="error" :closable="false" show-icon style="margin-bottom: 12px"
              :title="'加载失败：' + errMsg">
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <ReportState v-if="state === 'loading'" state="loading" height="360px" />

    <template v-else>
      <el-card shadow="never">
        <template #header>等级定义（点击行查看/维护该等级营销权益）</template>
        <el-table :data="levels" size="small" stripe highlight-current-row
                  :row-class-name="(c: any) => (c.row.id === selectedId ? 'current-row' : '')"
                  @row-click="selectLevel" style="cursor: pointer">
          <el-table-column prop="levelNo" label="序号" width="64" />
          <el-table-column label="名称" width="140">
            <template #default="{ row }">{{ row.levelName }}
              <el-tag v-if="row.id === selectedId" size="small" style="margin-left: 6px">当前</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="levelCode" label="编码" width="130" />
          <el-table-column label="升级条件" width="170">
            <template #default="{ row }">{{ upgradeText(row) }}</template>
          </el-table-column>
          <el-table-column label="折扣率" width="90" align="right">
            <template #default="{ row }">{{ (row.discountRate / 100).toFixed(1) }} 折</template>
          </el-table-column>
          <el-table-column label="积分倍率" width="90" align="right">
            <template #default="{ row }">×{{ (row.pointRate / 100).toFixed(1) }}</template>
          </el-table-column>
          <el-table-column prop="memberCount" label="会员数" width="80" align="right" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button v-if="hasPerm('member:level:update')" link type="primary" size="small" @click.stop="openLevelDialog(row)">编辑</el-button>
              <el-button v-if="hasPerm('member:level:delete')" link type="danger" size="small" @click.stop="removeLevel(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never" style="margin-top: 14px">
        <template #header>
          营销权益{{ selected ? " —— " + selected.levelName : "" }}
          <el-button v-if="selected && hasPerm('member:level:update')" link type="primary" size="small" style="margin-left: 12px" @click="openBenefitDialog()">新增权益</el-button>
        </template>
        <el-table v-if="benefits.length" :data="benefits" size="small" stripe>
          <el-table-column prop="benefitName" label="权益名称" width="180" />
          <el-table-column prop="benefitCode" label="编码" width="180" />
          <el-table-column label="权益值" min-width="160">
            <template #default="{ row }">{{ benefitValueText(row) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openBenefitDialog(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeBenefit(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="empty-tip">{{ selected ? "该等级暂无营销权益配置" : "点击上方等级行查看权益" }}</div>
      </el-card>
    </template>

    <!-- 等级编辑弹窗 -->
    <el-dialog v-model="levelDlg" :title="levelForm.id ? '编辑等级' : '新增等级'" width="560px">
      <el-form :model="levelForm" label-width="110px" size="small">
        <el-form-item label="等级编码" required><el-input v-model="levelForm.levelCode" maxlength="30" placeholder="如 GOLD" /></el-form-item>
        <el-form-item label="等级名称" required><el-input v-model="levelForm.levelName" maxlength="20" /></el-form-item>
        <el-form-item label="等级序号" required><el-input-number v-model="levelForm.levelNo" :min="1" :max="20" /></el-form-item>
        <el-form-item label="升级方式" required>
          <el-select v-model="levelForm.upgradeType" style="width: 100%">
            <el-option label="按累计消费金额" value="CONSUME_AMOUNT" />
          </el-select>
        </el-form-item>
        <el-form-item label="升级门槛(元)" required>
          <el-input-number v-model="thresholdYuan" :min="0" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="折扣率" required>
          <el-slider v-model="discountZhe" :min="50" :max="100" :step="1" show-input input-size="small" />
          <span class="text-muted" style="margin-left: 12px">{{ discountZhe }} 折 = {{ discountZhe * 100 }} 万分数</span>
        </el-form-item>
        <el-form-item label="积分倍率" required>
          <el-input-number v-model="pointRateX" :min="0.5" :max="10" :step="0.1" />
          <span class="text-muted" style="margin-left: 12px">×{{ pointRateX }} = {{ Math.round(pointRateX * 100) }}</span>
        </el-form-item>
        <el-form-item label="权益说明"><el-input v-model="levelForm.benefits" type="textarea" :rows="2" maxlength="200" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="levelForm.status" active-value="ENABLED" inactive-value="DISABLED" active-text="启用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="levelDlg = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="saveLevel">保存</el-button>
      </template>
    </el-dialog>

    <!-- 权益编辑弹窗 -->
    <el-dialog v-model="benefitDlg" :title="benefitForm.id ? '编辑权益' : '新增权益'" width="520px">
      <el-form :model="benefitForm" label-width="110px" size="small">
        <el-form-item label="权益编码" required>
          <el-select v-model="benefitForm.benefitCode" style="width: 100%" @change="onBenefitCodeChange">
            <el-option label="生日券 BIRTHDAY_COUPON" value="BIRTHDAY_COUPON" />
            <el-option label="积分倍率 POINT_RATE" value="POINT_RATE" />
            <el-option label="生日积分 BIRTHDAY_POINTS" value="BIRTHDAY_POINTS" />
            <el-option label="专属券 EXCLUSIVE_COUPON" value="EXCLUSIVE_COUPON" />
            <el-option label="优先服务 PRIORITY_SERVICE" value="PRIORITY_SERVICE" />
          </el-select>
        </el-form-item>
        <el-form-item label="权益名称" required><el-input v-model="benefitForm.benefitName" maxlength="30" /></el-form-item>
        <el-form-item label="权益值" required>
          <el-input v-model="benefitForm.benefitValue" placeholder="POINT_RATE 填 150 = 1.5 倍；券模板 ID 逗号分隔" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="benefitForm.status" active-value="ENABLED" inactive-value="DISABLED" active-text="启用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="benefitDlg = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="saveBenefit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import ReportState from '@/views/report/components/ReportState.vue'
import {
  deleteBenefit,
  deleteLevel,
  getBenefitsByLevel,
  getLevels,
  saveBenefit as saveBenefitApi,
  createLevel,
  updateLevel,
  type LevelBenefitVO,
  type MemberLevelSave,
  type MemberLevelVO,
} from '@/api/memberLevel'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const levels = ref<MemberLevelVO[]>([])
const selectedId = ref<number | null>(null)
const benefits = ref<LevelBenefitVO[]>([])
const state = ref<'loading' | 'error' | 'ready'>('loading')
const errMsg = ref('')
const loading = ref(false)
const saving = ref(false)

const levelDlg = ref(false)
const benefitDlg = ref(false)
const levelForm = ref<Partial<MemberLevelSave> & { id?: number }>({})
const benefitForm = ref<Partial<LevelBenefitVO> & { id?: number }>({})

const selected = computed(() => levels.value.find((x) => x.id === selectedId.value) || null)
// 门槛：分->元；折扣率：万分数->折；倍率：存 ×100
const thresholdYuan = computed({
  get: () => (levelForm.value.upgradeThreshold ?? 0) / 100,
  set: (v: number) => { levelForm.value.upgradeThreshold = Math.round(v * 100) },
})
const discountZhe = computed({
  get: () => (levelForm.value.discountRate ?? 10000) / 100,
  set: (v: number) => { levelForm.value.discountRate = Math.round(v * 100) },
})
const pointRateX = computed({
  get: () => (levelForm.value.pointRate ?? 100) / 100,
  set: (v: number) => { levelForm.value.pointRate = Math.round(v * 100) },
})

function hasPerm(p: string): boolean {
  const perms = userStore.userInfo?.permissions || []
  return perms.includes(p) || perms.includes('*')
}

function upgradeText(row: MemberLevelVO): string {
  if (row.upgradeThreshold <= 0) return '注册即达'
  return '累计消费 ' + (row.upgradeThreshold / 100).toFixed(0) + ' 元'
}

function benefitValueText(row: LevelBenefitVO): string {
  const v = row.benefitValue || '-'
  if (row.benefitCode === 'POINT_RATE') {
    const n = Number(v)
    return isNaN(n) ? v : n / 100 + ' 倍积分'
  }
  return v
}

async function load() {
  loading.value = true
  state.value = 'loading'
  errMsg.value = ''
  try {
    levels.value = await getLevels()
    if (!selectedId.value && levels.value.length) {
      selectedId.value = levels.value[0].id
    }
    if (selectedId.value) await loadBenefits(selectedId.value)
    state.value = 'ready'
  } catch (e) {
    state.value = 'error'
    errMsg.value = e instanceof Error ? e.message : '未知错误'
  } finally {
    loading.value = false
  }
}

async function loadBenefits(levelId: number) {
  try {
    benefits.value = await getBenefitsByLevel(levelId)
  } catch {
    benefits.value = []
  }
}

function selectLevel(row: MemberLevelVO) {
  selectedId.value = row.id
  loadBenefits(row.id)
}

function openLevelDialog(row?: MemberLevelVO) {
  levelForm.value = row
    ? { ...row }
    : { levelCode: '', levelName: '', levelNo: (levels.value.length || 0) + 1,
        upgradeType: 'CONSUME_AMOUNT', upgradeThreshold: 0, discountRate: 10000,
        pointRate: 100, benefits: '', status: 'ENABLED' }
  levelDlg.value = true
}

async function saveLevel() {
  const f = levelForm.value
  if (!f.levelCode?.trim() || !f.levelName?.trim()) {
    ElMessage.warning('等级编码与名称不能为空')
    return
  }
  saving.value = true
  try {
    const payload: MemberLevelSave = {
      levelCode: f.levelCode.trim().toUpperCase(),
      levelName: f.levelName.trim(),
      levelNo: f.levelNo || 1,
      upgradeType: f.upgradeType || 'CONSUME_AMOUNT',
      upgradeThreshold: f.upgradeThreshold || 0,
      discountRate: f.discountRate || 10000,
      pointRate: f.pointRate || 100,
      iconUrl: f.iconUrl,
      benefits: f.benefits,
      status: f.status,
    }
    if (f.id) await updateLevel(f.id, payload)
    else await createLevel(payload)
    ElMessage.success('已保存')
    levelDlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeLevel(row: MemberLevelVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除等级「${row.levelName}」？（有会员挂在该等级时后端会拒绝）`,
      '删除确认', { type: 'warning' },
    )
  } catch { return }
  try {
    await deleteLevel(row.id)
    ElMessage.success('已删除')
    if (selectedId.value === row.id) selectedId.value = null
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

function openBenefitDialog(row?: LevelBenefitVO) {
  if (!selectedId.value) {
    ElMessage.warning('请先选择等级')
    return
  }
  benefitForm.value = row
    ? { ...row }
    : { levelId: selectedId.value, benefitCode: 'POINT_RATE', benefitName: '',
        benefitValue: '', status: 'ENABLED' }
  benefitDlg.value = true
}

function onBenefitCodeChange(code: string) {
  const map: Record<string, string> = {
    BIRTHDAY_COUPON: '生日券', POINT_RATE: '积分倍率', BIRTHDAY_POINTS: '生日积分',
    EXCLUSIVE_COUPON: '专属券', PRIORITY_SERVICE: '优先服务',
  }
  if (!benefitForm.value.id) benefitForm.value.benefitName = map[code] || ''
}

async function saveBenefit() {
  const f = benefitForm.value
  if (!f.benefitCode || !f.benefitName?.trim() || !f.benefitValue?.trim()) {
    ElMessage.warning('编码/名称/权益值均不能为空')
    return
  }
  saving.value = true
  try {
    await saveBenefitApi({
      id: f.id,
      levelId: f.levelId!,
      benefitCode: f.benefitCode,
      benefitName: f.benefitName.trim(),
      benefitValue: f.benefitValue.trim(),
      status: f.status,
    })
    ElMessage.success('已保存')
    benefitDlg.value = false
    if (selectedId.value) await loadBenefits(selectedId.value)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeBenefit(row: LevelBenefitVO) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`确定删除权益「${row.benefitName}」？`, '删除确认', { type: 'warning' })
  } catch { return }
  try {
    await deleteBenefit(row.id)
    ElMessage.success('已删除')
    if (selectedId.value) await loadBenefits(selectedId.value)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

onMounted(load)
</script>

<style scoped>
.empty-tip { text-align: center; color: #c0c4cc; padding: 32px 0; font-size: 13px; }
:deep(.current-row) { background: var(--el-color-primary-light-9) !important; }
</style>