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
          <el-table-column prop="usedBy" label="生效场景" min-width="220" show-overflow-tooltip />
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getPointsRules, type PointsRuleVO } from '@/api/marketing'
import { fenToYuan, tagTypeByStatus } from '@/utils/format'

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
.mono {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
}
</style>
