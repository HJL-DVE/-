<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">充值套餐</h2>
      <span class="text-muted">充值金额=实收本金；赠送金额单列计入钱包（不计收款）</span>
      <div class="toolbar-spacer" />
      <el-button type="primary" size="small" @click="openDlg()">新增套餐</el-button>
      <el-button :icon="Refresh" size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="state === 'error'" type="error" :closable="false" show-icon style="margin-bottom: 12px" :title="'加载失败：' + errMsg">
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>
    <ReportState v-if="state === 'loading'" state="loading" height="320px" />

    <el-card v-else shadow="never">
      <el-table :data="plans" size="small" stripe>
        <el-table-column prop="planCode" label="编码" width="110" />
        <el-table-column prop="planName" label="名称" min-width="150" />
        <el-table-column label="充值(元)" width="100" align="right">
          <template #default="{ row }">{{ (row.rechargeAmount / 100).toFixed(0) }}</template>
        </el-table-column>
        <el-table-column label="赠送(元)" width="100" align="right">
          <template #default="{ row }">{{ (row.giftAmount / 100).toFixed(0) }}</template>
        </el-table-column>
        <el-table-column label="到账(元)" width="100" align="right">
          <template #default="{ row }">{{ ((row.rechargeAmount + row.giftAmount) / 100).toFixed(0) }}</template>
        </el-table-column>
        <el-table-column label="自定义金额" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.customAmountEnabled === 1" type="success" size="small">允许</el-tag>
            <el-tag v-else type="info" size="small">关闭</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" align="center" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDlg(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlg" :title="form.id ? '编辑套餐' : '新增套餐'" width="520px">
      <el-form :model="form" label-width="110px" size="small">
        <el-form-item label="套餐编码" required><el-input v-model="form.planCode" maxlength="30" placeholder="如 P100" /></el-form-item>
        <el-form-item label="套餐名称" required><el-input v-model="form.planName" maxlength="30" placeholder="如 充100送10" /></el-form-item>
        <el-form-item label="充值金额(元)" required>
          <el-input-number v-model="rechargeYuan" :min="1" :max="50000" :step="10" style="width: 200px" />
        </el-form-item>
        <el-form-item label="赠送金额(元)">
          <el-input-number v-model="giftYuan" :min="0" :max="50000" :step="5" style="width: 200px" />
          <span class="text-muted" style="margin-left: 10px">到账 {{ (rechargeYuan + giftYuan).toFixed(0) }} 元</span>
        </el-form-item>
        <el-form-item label="赠送有效期(天)">
          <el-input-number v-model="form.giftExpireDays" :min="0" :max="3650" style="width: 200px" />
          <span class="text-muted" style="margin-left: 10px">0 = 永久</span>
        </el-form-item>
        <el-form-item label="自定义金额">
          <el-switch v-model="customOn" active-value="1" inactive-value="0" />
          <template v-if="customOn === '1'">
            <el-input-number v-model="minCustomYuan" :min="1" :step="10" size="small" style="margin-left: 12px; width: 130px" />
            <span style="margin: 0 6px">-</span>
            <el-input-number v-model="maxCustomYuan" :min="1" :step="100" size="small" style="width: 130px" />
            <span class="text-muted" style="margin-left: 6px">元区间</span>
          </template>
        </el-form-item>
        <el-form-item label="每人限购">
          <el-input-number v-model="form.perMemberLimit" :min="0" :max="99" style="width: 200px" />
          <span class="text-muted" style="margin-left: 10px">0 = 不限</span>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" :max="999" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" active-value="ENABLED" inactive-value="DISABLED" active-text="启用" />
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="100" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dlg = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import ReportState from '@/views/report/components/ReportState.vue'
import { getPlans, createPlan, updatePlan, deletePlan } from '@/api/wallet'
import type { RechargePlanVO } from '@/api/types'

const plans = ref<RechargePlanVO[]>([])
const state = ref<'loading' | 'error' | 'ready'>('loading')
const errMsg = ref('')
const loading = ref(false)
const saving = ref(false)
const dlg = ref(false)
const form = ref<Partial<RechargePlanVO> & { id?: number }>({})

const rechargeYuan = computed({ get: () => (form.value.rechargeAmount ?? 10000) / 100, set: (v: number) => { form.value.rechargeAmount = Math.round(v * 100) } })
const giftYuan = computed({ get: () => (form.value.giftAmount ?? 0) / 100, set: (v: number) => { form.value.giftAmount = Math.round(v * 100) } })
const minCustomYuan = computed({ get: () => (form.value.minCustomAmount ?? 1000) / 100, set: (v: number) => { form.value.minCustomAmount = Math.round(v * 100) } })
const maxCustomYuan = computed({ get: () => (form.value.maxCustomAmount ?? 50000) / 100, set: (v: number) => { form.value.maxCustomAmount = Math.round(v * 100) } })
const customOn = computed({
  get: () => String(form.value.customAmountEnabled ?? 0),
  set: (v: string) => { form.value.customAmountEnabled = Number(v) },
})

async function load() {
  loading.value = true
  state.value = 'loading'
  errMsg.value = ''
  try {
    plans.value = await getPlans()
    state.value = 'ready'
  } catch (e) {
    state.value = 'error'
    errMsg.value = e instanceof Error ? e.message : '未知错误'
  } finally { loading.value = false }
}

function openDlg(row?: RechargePlanVO) {
  form.value = row ? { ...row } : { planCode: '', planName: '', rechargeAmount: 10000, giftAmount: 1000,
    giftExpireDays: 0, customAmountEnabled: 0, minCustomAmount: 1000, maxCustomAmount: 50000,
    perMemberLimit: 0, sort: 0, status: 'ENABLED', remark: '' }
  dlg.value = true
}

async function save() {
  if (!form.value.planCode?.trim() || !form.value.planName?.trim()) { ElMessage.warning('编码与名称不能为空'); return }
  saving.value = true
  try {
    if (form.value.id) await updatePlan(form.value.id, form.value)
    else await createPlan(form.value)
    ElMessage.success('已保存')
    dlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally { saving.value = false }
}

async function remove(row: RechargePlanVO) {
  try { await ElMessageBox.confirm(`确定删除套餐「${row.planName}」？`, '删除确认', { type: 'warning' }) }
  catch { return }
  try {
    await deletePlan(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

onMounted(load)
</script>

<style scoped>
.stat-card { background: #fff; border-radius: 6px; padding: 14px 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.text-muted { color: #9ca3af; font-size: 12px; }
</style>