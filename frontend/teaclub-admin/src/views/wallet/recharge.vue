<template>
  <div class="page">
    <ApiPending
      v-if="listError"
      title="充值订单接口未就绪"
      url="GET /api/admin/wallet/recharges"
      :code="listError.code"
      :message="listError.message"
      :trace-id="listError.traceId"
    />

    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.memberId" placeholder="会员 ID" clearable style="width: 130px" @keyup.enter="search" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
          <el-option v-for="(label, value) in RECHARGE_STATUS_LABEL" :key="value" :label="label" :value="value" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 240px"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="reload">刷新</el-button>
        <div class="toolbar-spacer" />
        <el-button type="primary" :icon="Plus" :disabled="!!listError" @click="openCreate">新建充值</el-button>
      </div>

      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column prop="rechargeNo" label="充值单号" min-width="190" show-overflow-tooltip />
        <el-table-column label="会员" min-width="130">
          <template #default="{ row }">
            {{ row.memberName || row.memberPhone || '-' }}
            <div class="text-muted">ID {{ row.memberId }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="storeName" label="门店" min-width="150" show-overflow-tooltip />
        <el-table-column prop="planName" label="方案" min-width="130" show-overflow-tooltip />
        <el-table-column label="充值额" width="110" align="right">
          <template #default="{ row }"><span class="money">{{ fenToYuan(row.rechargeAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="赠送额" width="110" align="right">
          <template #default="{ row }">{{ fenToYuan(row.giftAmount) }}</template>
        </el-table-column>
        <el-table-column label="实收" width="110" align="right">
          <template #default="{ row }">{{ fenToYuan(row.actualAmount) }}</template>
        </el-table-column>
        <el-table-column label="支付渠道" width="120">
          <template #default="{ row }">{{ PAY_CHANNEL_LABEL[row.payChannel] || row.payChannel || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="tagTypeByStatus(row.status)">
              {{ RECHARGE_STATUS_LABEL[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.payTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="danger" @click="onCancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="search"
        />
      </div>
    </div>

    <div class="page-card" style="margin-top: 14px">
      <div class="toolbar">
        <strong>充值方案（GET /api/admin/wallet/plans）</strong>
        <div class="toolbar-spacer" />
        <el-button :icon="Refresh" size="small" @click="loadPlans">刷新</el-button>
      </div>
      <ApiPending
        v-if="plansError"
        title="充值方案接口未就绪"
        url="GET /api/admin/wallet/plans"
        :code="plansError.code"
        :message="plansError.message"
        :trace-id="plansError.traceId"
      />
      <el-table v-else :data="plans" border size="small" v-loading="plansLoading">
        <el-table-column prop="planCode" label="编码" width="110" />
        <el-table-column prop="planName" label="方案名称" min-width="150" />
        <el-table-column label="充值金额" width="110" align="right">
          <template #default="{ row }">{{ fenToYuan(row.rechargeAmount) }}</template>
        </el-table-column>
        <el-table-column label="赠送金额" width="110" align="right">
          <template #default="{ row }">{{ fenToYuan(row.giftAmount) }}</template>
        </el-table-column>
        <el-table-column label="赠送有效期" width="110" align="right">
          <template #default="{ row }">{{ row.giftExpireDays ? row.giftExpireDays + ' 天' : '永久' }}</template>
        </el-table-column>
        <el-table-column label="等级门槛" width="110">
          <template #default="{ row }">{{ row.minLevelName || (row.minLevelId ? 'ID ' + row.minLevelId : '无') }}</template>
        </el-table-column>
        <el-table-column label="每人限次" width="90" align="center">
          <template #default="{ row }">{{ row.perMemberLimit || '不限' }}</template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效中" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.effective ? 'success' : 'info'">{{ row.effective ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="createVisible" title="新建充值订单" width="500px">
      <el-form label-width="110px">
        <el-form-item label="会员手机号">
          <el-input v-model="createForm.phone" placeholder="输入手机号后点击查询" style="width: 190px" />
          <el-button style="margin-left: 8px" :loading="memberLoading" @click="findMember">查询</el-button>
        </el-form-item>
        <el-form-item label="会员">
          <span v-if="createForm.member">
            {{ createForm.member.nickname || '-' }} / {{ createForm.member.phone }}（ID {{ createForm.member.id }}）
          </span>
          <span v-else class="text-muted">未选择</span>
        </el-form-item>
        <el-form-item label="门店">
          <span>{{ appStore.currentStoreName }}（ID {{ appStore.currentStoreId || '-' }}）</span>
        </el-form-item>
        <el-form-item label="充值方案">
          <el-select v-model="createForm.planId" style="width: 100%" placeholder="选择方案">
            <el-option
              v-for="plan in plans"
              :key="plan.id"
              :label="plan.planName + '（充 ' + fenToYuan(plan.rechargeAmount) + ' 送 ' + fenToYuan(plan.giftAmount) + '）'"
              :value="plan.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="自定义金额">
          <el-input v-model="createForm.customAmountYuan" placeholder="仅自定义方案需要，单位元" />
        </el-form-item>
        <el-form-item label="支付渠道">
          <el-select v-model="createForm.payChannel" style="width: 100%">
            <el-option label="模拟支付" value="MOCK" />
            <el-option label="微信 Native" value="WECHAT_NATIVE" />
            <el-option label="微信 JSAPI" value="WECHAT_JSAPI" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="充值订单详情" width="520px">
      <el-descriptions v-if="current" :column="1" border size="small">
        <el-descriptions-item label="充值单号">{{ current.rechargeNo }}</el-descriptions-item>
        <el-descriptions-item label="会员">{{ current.memberName || current.memberPhone }} (ID {{ current.memberId }})</el-descriptions-item>
        <el-descriptions-item label="门店">{{ current.storeName }}</el-descriptions-item>
        <el-descriptions-item label="方案">{{ current.planName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="充值金额">{{ fenToYuan(current.rechargeAmount) }}</el-descriptions-item>
        <el-descriptions-item label="赠送金额">{{ fenToYuan(current.giftAmount) }}</el-descriptions-item>
        <el-descriptions-item label="实收金额">{{ fenToYuan(current.actualAmount) }}</el-descriptions-item>
        <el-descriptions-item label="支付单号">{{ current.paymentNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ RECHARGE_STATUS_LABEL[current.status] || current.status }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ formatDateTime(current.payTime) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(current.createTime) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { cancelRecharge, createRecharge, getPlans, getRechargePage } from '@/api/wallet'
import { getMemberByPhone } from '@/api/member'
import { ApiError, isApiError } from '@/api/request'
import ApiPending from '@/components/ApiPending.vue'
import { useAppStore } from '@/stores/app'
import {
  PAY_CHANNEL_LABEL,
  RECHARGE_STATUS_LABEL,
  fenToYuan,
  formatDateTime,
  tagTypeByStatus,
  yuanToFen,
} from '@/utils/format'
import type { MemberVO, RechargeOrderVO, RechargePlanVO } from '@/api/types'

const appStore = useAppStore()

const loading = ref(false)
const records = ref<RechargeOrderVO[]>([])
const total = ref(0)
const listError = ref<ApiError | null>(null)
const dateRange = ref<[string, string] | null>(null)

const plansLoading = ref(false)
const plans = ref<RechargePlanVO[]>([])
const plansError = ref<ApiError | null>(null)

const query = reactive<{ pageNo: number; pageSize: number; memberId: string; status: string }>({
  pageNo: 1,
  pageSize: 20,
  memberId: '',
  status: '',
})

function toApiError(error: unknown): ApiError {
  return isApiError(error) ? error : new ApiError(-1, (error as Error)?.message || '未知错误')
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await getRechargePage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      memberId: query.memberId ? Number(query.memberId) : undefined,
      status: query.status || undefined,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
    })
    records.value = page.records || []
    total.value = page.total || 0
    listError.value = null
  } catch (error) {
    records.value = []
    total.value = 0
    listError.value = toApiError(error)
  } finally {
    loading.value = false
  }
}

function search(): void {
  query.pageNo = 1
  load()
}

async function loadPlans(): Promise<void> {
  plansLoading.value = true
  try {
    plans.value = await getPlans()
    plansError.value = null
  } catch (error) {
    plans.value = []
    plansError.value = toApiError(error)
  } finally {
    plansLoading.value = false
  }
}

function reload(): void {
  load()
  loadPlans()
}

const detailVisible = ref(false)
const current = ref<RechargeOrderVO | null>(null)

function openDetail(row: RechargeOrderVO): void {
  current.value = row
  detailVisible.value = true
}

function onCancel(row: RechargeOrderVO): void {
  ElMessageBox.confirm('确认取消充值单 ' + row.rechargeNo + '？', '提示', { type: 'warning' })
    .then(async () => {
      await cancelRecharge(row.rechargeNo)
      ElMessage.success('已取消')
      await load()
    })
    .catch(() => undefined)
}

const createVisible = ref(false)
const creating = ref(false)
const memberLoading = ref(false)
const createForm = reactive<{
  phone: string
  member: MemberVO | null
  planId?: number
  customAmountYuan: string
  payChannel: string
}>({ phone: '', member: null, planId: undefined, customAmountYuan: '', payChannel: 'MOCK' })

function openCreate(): void {
  createForm.phone = ''
  createForm.member = null
  createForm.planId = undefined
  createForm.customAmountYuan = ''
  createVisible.value = true
}

async function findMember(): Promise<void> {
  if (!createForm.phone) {
    ElMessage.warning('请输入会员手机号')
    return
  }
  memberLoading.value = true
  try {
    createForm.member = await getMemberByPhone(createForm.phone)
  } catch {
    createForm.member = null
  } finally {
    memberLoading.value = false
  }
}

async function submitCreate(): Promise<void> {
  if (!createForm.member) {
    ElMessage.warning('请先查询并确认会员')
    return
  }
  if (!appStore.currentStoreId) {
    ElMessage.warning('请先在顶部选择门店')
    return
  }
  if (!createForm.planId) {
    ElMessage.warning('请选择充值方案')
    return
  }
  let customAmount: number | undefined
  if (createForm.customAmountYuan) {
    const fen = yuanToFen(createForm.customAmountYuan)
    if (Number.isNaN(fen) || fen <= 0) {
      ElMessage.error('自定义金额不合法')
      return
    }
    customAmount = fen
  }
  creating.value = true
  try {
    const order = await createRecharge({
      memberId: createForm.member.id,
      storeId: appStore.currentStoreId,
      planId: createForm.planId,
      customAmount,
      payChannel: createForm.payChannel,
    })
    ElMessage.success('充值订单已创建：' + order.rechargeNo + '（状态 ' + order.status + '）')
    createVisible.value = false
    await load()
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  void appStore.loadStores().catch(() => undefined)
  reload()
})
</script>
