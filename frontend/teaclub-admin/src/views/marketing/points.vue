<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-input
          v-model="memberIdInput"
          placeholder="会员 ID"
          clearable
          style="width: 160px"
          @keyup.enter="search"
        />
        <el-select v-model="query.bizType" placeholder="流水类型" clearable style="width: 160px" @change="loadTransactions">
          <el-option v-for="(label, value) in BIZ_TYPE_LABEL" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="query.limit" placeholder="条数" style="width: 120px" @change="loadTransactions">
          <el-option :label="'20 条'" :value="20" />
          <el-option :label="'50 条'" :value="50" />
          <el-option :label="'100 条'" :value="100" />
          <el-option :label="'200 条'" :value="200" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" :disabled="!memberId" @click="reload">刷新</el-button>
        <div class="toolbar-spacer" />
        <span class="text-muted">GET /api/admin/marketing/points/account · GET /api/admin/marketing/points/transactions</span>
        <el-button type="primary" :icon="Plus" :disabled="!memberId || !account" @click="openAdjust">人工调整积分</el-button>
      </div>

      <el-empty v-if="!memberId" description="请输入会员 ID 后查询积分账户与流水" />

      <template v-else>
        <ApiPending
          v-if="accountError"
          title="积分账户接口未就绪"
          url="GET /api/admin/marketing/points/account"
          :code="accountError.code"
          :message="accountError.message"
          :trace-id="accountError.traceId"
        />

        <div v-else class="stat-grid" v-loading="accountLoading">
          <div class="stat-card">
            <div class="stat-label">可用积分</div>
            <div class="stat-value">{{ account ? account.totalPoints : '-' }}</div>
            <div class="stat-extra">会员 ID {{ memberId }}</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">累计获得</div>
            <div class="stat-value">{{ account ? account.totalEarned : '-' }}</div>
            <div class="stat-extra">含消费、签到、活动奖励</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">累计消耗</div>
            <div class="stat-value">{{ account ? account.totalConsumed : '-' }}</div>
            <div class="stat-extra">兑换 / 扣减 / 过期</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">累计冲正</div>
            <div class="stat-value">{{ account ? account.totalReversed : '-' }}</div>
            <div class="stat-extra">退款按原流水冲正</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">更新时间</div>
            <div class="stat-value" style="font-size: 16px">{{ formatDateTime(account ? account.updateTime : null) }}</div>
            <div class="stat-extra">账户唯一事实来源</div>
          </div>
        </div>

        <div style="margin-top: 16px">
          <ApiPending
            v-if="txnError"
            title="积分流水接口未就绪"
            url="GET /api/admin/marketing/points/transactions"
            :code="txnError.code"
            :message="txnError.message"
            :trace-id="txnError.traceId"
          />
          <el-table v-else :data="transactions" v-loading="txnLoading" border stripe>
            <el-table-column prop="txnNo" label="流水号" min-width="190" show-overflow-tooltip />
            <el-table-column label="类型" width="130">
              <template #default="{ row }">{{ row.bizTypeLabel || BIZ_TYPE_LABEL[row.bizType] || row.bizType }}</template>
            </el-table-column>
            <el-table-column prop="bizNo" label="业务单号" min-width="180" show-overflow-tooltip />
            <el-table-column label="变动" width="100" align="right">
              <template #default="{ row }">
                <span :class="row.changePoints >= 0 ? 'money-in' : 'money'">{{ signed(row.changePoints) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="beforePoints" label="变动前" width="100" align="right" />
            <el-table-column prop="afterPoints" label="变动后" width="100" align="right" />
            <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </template>
    </div>

    <el-dialog v-model="adjustVisible" title="人工调整积分" width="480px">
      <el-form label-width="100px">
        <el-form-item label="会员 ID">
          <span>{{ memberId }}（当前可用 {{ account ? account.totalPoints : '-' }} 分）</span>
        </el-form-item>
        <el-form-item label="调整积分" required>
          <el-input-number v-model="adjustForm.points" :min="-1000000" :max="1000000" />
          <span class="text-muted" style="margin-left: 8px">正数增加，负数扣减</span>
        </el-form-item>
        <el-form-item label="业务单号">
          <el-input v-model="adjustForm.bizNo" placeholder="留空由后端生成操作单号" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="adjustForm.remark" type="textarea" :rows="2" placeholder="调整原因，写入流水备注" />
        </el-form-item>
      </el-form>
      <div class="text-muted">POST /api/admin/marketing/points/adjust</div>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjusting" @click="submitAdjust">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { adjustPoints, getPointsAccount, getPointsTransactions } from '@/api/marketing'
import { ApiError, isApiError } from '@/api/request'
import ApiPending from '@/components/ApiPending.vue'
import { formatDateTime } from '@/utils/format'
import type { PointsAccountVO, PointsTransactionVO } from '@/api/marketing'

/** 与后端 PointsBizType 枚举一致 */
const BIZ_TYPE_LABEL: Record<string, string> = {
  ORDER_EARN: '消费得积分',
  ORDER_REFUND_REVERSE: '退款冲正',
  SIGN_IN: '签到',
  ACTIVITY: '活动奖励',
  EXCHANGE: '积分兑换',
  DEDUCT: '人工扣减',
  EXPIRE: '积分过期',
  ADJUST: '人工调整',
}

const memberIdInput = ref('')
const memberId = ref<number | null>(null)

const accountLoading = ref(false)
const account = ref<PointsAccountVO | null>(null)
const accountError = ref<ApiError | null>(null)

const txnLoading = ref(false)
const transactions = ref<PointsTransactionVO[]>([])
const txnError = ref<ApiError | null>(null)

const query = reactive<{ bizType: string; limit: number }>({ bizType: '', limit: 50 })

function toApiError(error: unknown): ApiError {
  return isApiError(error) ? error : new ApiError(-1, (error as Error)?.message || '未知错误')
}

function signed(points?: number | null): string {
  const n = Math.trunc(Number(points ?? 0))
  return n > 0 ? '+' + n : String(n)
}

async function loadAccount(): Promise<void> {
  if (!memberId.value) return
  accountLoading.value = true
  try {
    account.value = await getPointsAccount(memberId.value)
    accountError.value = null
  } catch (error) {
    account.value = null
    accountError.value = toApiError(error)
  } finally {
    accountLoading.value = false
  }
}

async function loadTransactions(): Promise<void> {
  if (!memberId.value) return
  txnLoading.value = true
  try {
    transactions.value =
      (await getPointsTransactions({
        memberId: memberId.value,
        bizType: query.bizType || undefined,
        limit: query.limit,
      })) || []
    txnError.value = null
  } catch (error) {
    transactions.value = []
    txnError.value = toApiError(error)
  } finally {
    txnLoading.value = false
  }
}

async function search(): Promise<void> {
  const id = Number(memberIdInput.value)
  if (!Number.isInteger(id) || id <= 0) {
    ElMessage.warning('请输入合法会员 ID')
    return
  }
  memberId.value = id
  await Promise.all([loadAccount(), loadTransactions()])
}

function reload(): void {
  void loadAccount()
  void loadTransactions()
}

const adjustVisible = ref(false)
const adjusting = ref(false)
const adjustForm = reactive<{ points: number; bizNo: string; remark: string }>({
  points: 0,
  bizNo: '',
  remark: '',
})

function openAdjust(): void {
  adjustForm.points = 0
  adjustForm.bizNo = ''
  adjustForm.remark = ''
  adjustVisible.value = true
}

async function submitAdjust(): Promise<void> {
  if (!memberId.value) return
  if (!Number.isInteger(adjustForm.points) || adjustForm.points === 0) {
    ElMessage.warning('调整积分需为非 0 整数')
    return
  }
  adjusting.value = true
  try {
    await adjustPoints({
      memberId: memberId.value,
      points: adjustForm.points,
      bizNo: adjustForm.bizNo || undefined,
      remark: adjustForm.remark || undefined,
    })
    ElMessage.success('已调整 ' + signed(adjustForm.points) + ' 分')
    adjustVisible.value = false
    await Promise.all([loadAccount(), loadTransactions()])
  } catch {
    // 错误提示由 http 拦截器统一弹出
  } finally {
    adjusting.value = false
  }
}
</script>
