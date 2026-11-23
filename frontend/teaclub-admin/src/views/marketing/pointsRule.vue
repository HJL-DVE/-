<template>
  <div class="page">
    <div class="page-card">
      <div class="page-head">
        <div>
          <h2 class="title">积分规则</h2>
          <p class="subtitle">当前品牌生效的积分获取与有效期规则，数据来源于积分规则配置表</p>
        </div>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>

      <div v-if="loading" class="state">规则加载中…</div>
      <div v-else-if="error" class="state">
        {{ error }}
        <el-button size="small" style="margin-left: 12px" @click="load">重试</el-button>
      </div>

      <template v-else>
        <!-- 核心指标卡片 -->
        <div class="stat-grid">
          <div class="stat-card">
            <div class="stat-label">消费积分比例</div>
            <div class="stat-value">{{ earnRule ? `${fenToYuan(earnRule.amountPerPoint)} 元 / 1 分` : '—' }}</div>
            <div class="stat-extra">每消费 {{ earnRule ? fenToYuan(earnRule.amountPerPoint) : '—' }} 元累计 1 积分</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">签到基础积分</div>
            <div class="stat-value">{{ signInRule ? `${signInRule.signInPoints} 分` : '—' }}</div>
            <div class="stat-extra">会员每日签到可获得</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">连续签到加成</div>
            <div class="stat-value">{{ signInRule ? `${signInRule.signInBonus} 分 / 天` : '—' }}</div>
            <div class="stat-extra">连续签到上限 {{ signInRule ? signInRule.signInContinuousMax : '—' }} 天</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">积分有效期</div>
            <div class="stat-value">{{ signInRule ? (signInRule.expireDays === 0 ? '永久' : `${signInRule.expireDays} 天`) : '—' }}</div>
            <div class="stat-extra">按获得批次先进先出过期</div>
          </div>
        </div>

        <!-- 规则明细 -->
        <h3 class="section-title">规则明细</h3>
        <el-table :data="rules" border stripe size="small" v-loading="loading">
          <el-table-column prop="ruleName" label="规则名称" width="150" />
          <el-table-column prop="ruleCode" label="规则编码" width="160">
            <template #default="{ row }"><span class="mono">{{ row.ruleCode }}</span></template>
          </el-table-column>
          <el-table-column prop="amountPerPoint" label="消费积分比例" width="150" align="right">
            <template #default="{ row }">{{ fenToYuan(row.amountPerPoint) }} 元 / 1 分</template>
          </el-table-column>
          <el-table-column prop="signInPoints" label="签到积分" width="110" align="right" />
          <el-table-column prop="signInContinuousMax" label="连续上限（天）" width="130" align="right" />
          <el-table-column prop="signInBonus" label="连续加成（分/天）" width="150" align="right" />
          <el-table-column prop="expireDays" label="有效期（天）" width="120" align="right">
            <template #default="{ row }">{{ row.expireDays === 0 ? '永久' : row.expireDays }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="tagTypeByStatus(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="usedBy" label="生效场景" min-width="200" show-overflow-tooltip />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 积分流水业务类型字典 -->
        <h3 class="section-title">积分流水类型说明</h3>
        <p class="hint">会员积分流水中「类型」字段的含义，供对账与客服查询时参考。</p>
        <el-table :data="bizTypes" border stripe size="small">
          <el-table-column prop="label" label="类型" width="140" />
          <el-table-column prop="code" label="编码" width="200">
            <template #default="{ row }"><span class="mono">{{ row.code }}</span></template>
          </el-table-column>
          <el-table-column prop="bizNo" label="关联单号" min-width="200" show-overflow-tooltip />
          <el-table-column prop="direction" label="积分方向" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="row.direction.includes('扣') ? 'danger' : 'success'">{{ row.direction }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- 编辑规则弹窗 -->
      <el-dialog v-model="dialogVisible" title="编辑积分规则" width="520px" :close-on-click-modal="false">
        <el-form :model="form" label-width="150px" size="small">
          <el-form-item label="规则编码">
            <el-input :model-value="form.ruleCode" disabled />
          </el-form-item>
          <el-form-item label="规则名称">
            <el-input :model-value="form.ruleName" disabled />
          </el-form-item>
          <el-form-item label="消费积分比例">
            <el-input-number v-model="form.amountPerPoint" :min="1" :step="100" style="width: 160px" />
            <span class="unit">分 = {{ fenToYuan(form.amountPerPoint) }} 元得 1 积分</span>
          </el-form-item>
          <el-form-item label="签到基础积分">
            <el-input-number v-model="form.signInPoints" :min="0" :step="1" style="width: 160px" />
            <span class="unit">分</span>
          </el-form-item>
          <el-form-item label="连续签到上限">
            <el-input-number v-model="form.signInContinuousMax" :min="0" :step="1" style="width: 160px" />
            <span class="unit">天</span>
          </el-form-item>
          <el-form-item label="连续签到加成">
            <el-input-number v-model="form.signInBonus" :min="0" :step="1" style="width: 160px" />
            <span class="unit">分 / 天</span>
          </el-form-item>
          <el-form-item label="积分有效期">
            <el-input-number v-model="form.expireDays" :min="0" :step="30" style="width: 160px" />
            <span class="unit">天（0 = 永不过期）</span>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.status" style="width: 160px">
              <el-option label="已启用" value="ENABLED" />
              <el-option label="已停用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="dialog-tip">保存后立即生效，下一个订单/签到时按新规则计算积分。</div>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getPointsRules, updatePointsRule, type PointsRuleVO } from '@/api/marketing'
import { fenToYuan, tagTypeByStatus } from '@/utils/format'
import { ElMessage } from 'element-plus'

const rules = ref<PointsRuleVO[]>([])
const loading = ref(false)
const error = ref('')

const earnRule = computed(() => rules.value.find((r) => r.ruleCode === 'ORDER_EARN'))
const signInRule = computed(() => rules.value.find((r) => r.ruleCode === 'SIGN_IN'))

const STATUS_LABEL: Record<string, string> = { ENABLED: '已启用', DISABLED: '已停用' }
const statusLabel = (s: string) => STATUS_LABEL[s] || s

const bizTypes = [
  { code: 'ORDER_EARN', label: '消费得积分', bizNo: '订单号', direction: '增加' },
  { code: 'ORDER_REFUND_REVERSE', label: '退款冲正', bizNo: '原订单号', direction: '扣回' },
  { code: 'SIGN_IN', label: '签到奖励', bizNo: '签到日期', direction: '增加' },
  { code: 'ACTIVITY', label: '活动奖励', bizNo: '活动编号', direction: '增加' },
  { code: 'EXCHANGE', label: '积分兑换', bizNo: '兑换单号', direction: '扣减' },
  { code: 'DEDUCT', label: '人工扣减', bizNo: '操作单号', direction: '扣减' },
  { code: 'EXPIRE', label: '积分过期', bizNo: '批次号', direction: '扣减' },
  { code: 'ADJUST', label: '人工调整', bizNo: '操作单号', direction: '增加 / 扣减' },
]

const dialogVisible = ref(false)
const saving = ref(false)
const form = ref<PointsRuleVO & { id?: number }>({
  id: 0,
  ruleCode: '',
  ruleName: '',
  amountPerPoint: 100,
  signInPoints: 0,
  signInContinuousMax: 0,
  signInBonus: 0,
  expireDays: 0,
  status: 'ENABLED',
  usedBy: '',
})

function openEdit(row: PointsRuleVO) {
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (!form.value.id) {
    ElMessage.warning('规则 ID 缺失，无法保存')
    return
  }
  saving.value = true
  try {
    await updatePointsRule(form.value.id, {
      amountPerPoint: form.value.amountPerPoint,
      signInPoints: form.value.signInPoints,
      signInContinuousMax: form.value.signInContinuousMax,
      signInBonus: form.value.signInBonus,
      expireDays: form.value.expireDays,
      status: form.value.status,
    })
    ElMessage.success('积分规则已更新')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    rules.value = await getPointsRules()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '规则加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}
.title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: #909399;
}
.state {
  padding: 40px 0;
  text-align: center;
  color: #909399;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}
.stat-card {
  background: #f8fafc;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
}
.stat-label {
  font-size: 13px;
  color: #606266;
}
.stat-value {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 700;
  color: #16a34a;
}
.stat-extra {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
.section-title {
  margin: 22px 0 10px;
  font-size: 16px;
  font-weight: 600;
}
.hint {
  margin: -4px 0 10px;
  font-size: 12px;
  color: #909399;
}
.unit {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
.dialog-tip {
  margin-top: 10px;
  padding: 10px 12px;
  background: #f0fdf4;
  border-radius: 6px;
  font-size: 12px;
  color: #15803d;
}
.mono {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
}
</style>
