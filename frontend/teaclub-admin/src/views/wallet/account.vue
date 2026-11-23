<template>
  <div class="page">
    <ApiPending
      v-if="listError"
      title="钱包账户接口未就绪"
      url="GET /api/admin/wallet/accounts"
      :code="listError.code"
      :message="listError.message"
      :trace-id="listError.traceId"
    />

    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.phone" placeholder="会员手机号" clearable style="width: 170px" @keyup.enter="search" />
        <el-input v-model="query.keyword" placeholder="会员号/昵称" clearable style="width: 170px" @keyup.enter="search" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="正常" value="NORMAL" />
          <el-option label="冻结" value="FROZEN" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button v-if="userStore.hasPerm('payment:reconcile')" :icon="CircleCheck" @click="runReconcile">对账</el-button>
        <div class="toolbar-spacer" />
        <span class="text-muted">共 {{ total }} 个钱包账户</span>
      </div>

      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column prop="walletId" label="钱包 ID" width="100" />
        <el-table-column label="会员" min-width="150">
          <template #default="{ row }">
            {{ row.memberName || row.memberNickname || '-' }}
            <div class="text-muted">{{ row.memberNo || '-' }} · {{ row.memberPhone || row.phone || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="memberId" label="会员 ID" width="110" />
        <el-table-column label="实付余额" width="120" align="right">
          <template #default="{ row }"><span class="money">{{ fenToYuan(row.principalBalance) }}</span></template>
        </el-table-column>
        <el-table-column label="赠送余额" width="120" align="right">
          <template #default="{ row }"><span class="money">{{ fenToYuan(row.giftBalance) }}</span></template>
        </el-table-column>
        <el-table-column label="总余额" width="120" align="right">
          <template #default="{ row }"><span class="money">{{ fenToYuan(row.totalBalance) }}</span></template>
        </el-table-column>
        <el-table-column label="累计实付入" width="120" align="right">
          <template #default="{ row }">{{ fenToYuan(row.totalPrincipalIn) }}</template>
        </el-table-column>
        <el-table-column label="累计赠送入" width="120" align="right">
          <template #default="{ row }">{{ fenToYuan(row.totalGiftIn) }}</template>
        </el-table-column>
        <el-table-column label="累计实付出" width="120" align="right">
          <template #default="{ row }">{{ fenToYuan(row.totalPrincipalOut) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'NORMAL' ? 'success' : 'danger'">{{ MEMBER_STATUS_LABEL[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开通时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="userStore.hasPerm('wallet:adjust')"
              link type="warning"
              @click="openAdjust(row)"
            >调整余额</el-button>
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

    <el-dialog v-model="reconcileVisible" title="资金对账结果" width="520px">
      <el-descriptions v-if="reconcile" :column="1" border size="small">
        <el-descriptions-item label="是否一致">
          <el-tag :type="reconcile.consistent ? 'success' : 'danger'">
            {{ reconcile.consistent ? '一致' : '存在差异' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="不一致数量">{{ reconcile.inconsistentCount ?? 0 }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="reconcileDetails" size="small" border style="margin-top: 10px">
        <el-table-column
          v-for="key in Object.keys(reconcileDetails[0] || {})"
          :key="key"
          :prop="key"
          :label="key"
          min-width="130"
          show-overflow-tooltip
        />
      </el-table>
      <div v-if="reconcileError" class="text-muted">{{ reconcileError.message }}</div>
    </el-dialog>

    <el-dialog v-model="adjustVisible" title="余额人工调整" width="480px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px"
        title="高敏感操作：调整会直接改变会员钱包余额并生成资金流水，请确认已完成线下审批。" />
      <el-form :model="adjustForm" label-width="110px" size="small">
        <el-form-item label="会员">
          <span>{{ adjustForm.memberName }}（ID {{ adjustForm.memberId }}）</span>
        </el-form-item>
        <el-form-item label="审批单号" required>
          <el-input v-model="adjustForm.approvalNo" placeholder="线下审批单号（幂等键）" />
        </el-form-item>
        <el-form-item label="实付余额调整">
          <el-input-number v-model="adjustForm.principalYuan" :step="1" :precision="2" style="width: 200px" />
          <span class="text-muted" style="margin-left: 8px">元，负数=扣减</span>
        </el-form-item>
        <el-form-item label="赠送余额调整">
          <el-input-number v-model="adjustForm.giftYuan" :step="1" :precision="2" style="width: 200px" />
          <span class="text-muted" style="margin-left: 8px">元，负数=扣减</span>
        </el-form-item>
        <el-form-item label="调整原因" required>
          <el-input v-model="adjustForm.remark" type="textarea" :rows="2" placeholder="必填，留痕" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="warning" :loading="adjusting" @click="submitAdjust">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, Refresh, Search } from '@element-plus/icons-vue'
import { adjustWallet, getReconcile, getWalletPage } from '@/api/wallet'
import { ApiError, isApiError } from '@/api/request'
import ApiPending from '@/components/ApiPending.vue'
import { useUserStore } from '@/stores/user'
import { fenToYuan, formatDateTime, MEMBER_STATUS_LABEL, yuanToFen } from '@/utils/format'
import type { WalletVO } from '@/api/types'

const userStore = useUserStore()
const loading = ref(false)
const records = ref<WalletVO[]>([])
const total = ref(0)
const listError = ref<ApiError | null>(null)

const query = reactive<{ pageNo: number; pageSize: number; phone: string; keyword: string; status: string }>({
  pageNo: 1,
  pageSize: 20,
  phone: '',
  keyword: '',
  status: '',
})

function toApiError(error: unknown): ApiError {
  return isApiError(error) ? error : new ApiError(-1, (error as Error)?.message || '未知错误')
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await getWalletPage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      phone: query.phone || undefined,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
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

const reconcileVisible = ref(false)
const reconcile = ref<Record<string, unknown> | null>(null)
const reconcileError = ref<ApiError | null>(null)

const reconcileDetails = computed<Record<string, unknown>[]>(() => {
  const details = reconcile.value?.details
  return Array.isArray(details) ? (details as Record<string, unknown>[]) : []
})

async function runReconcile(): Promise<void> {
  reconcileVisible.value = true
  reconcile.value = null
  reconcileError.value = null
  try {
    reconcile.value = await getReconcile()
  } catch (error) {
    reconcileError.value = toApiError(error)
  }
}

const adjustVisible = ref(false)
const adjusting = ref(false)
const adjustForm = reactive({
  memberId: 0,
  memberName: '',
  approvalNo: '',
  principalYuan: 0,
  giftYuan: 0,
  remark: '',
})

function openAdjust(row: WalletVO): void {
  adjustForm.memberId = row.memberId ?? 0
  adjustForm.memberName = row.memberName || row.memberNickname || row.memberNo || ''
  adjustForm.approvalNo = ''
  adjustForm.principalYuan = 0
  adjustForm.giftYuan = 0
  adjustForm.remark = ''
  adjustVisible.value = true
}

async function submitAdjust(): Promise<void> {
  if (!adjustForm.approvalNo.trim()) {
    ElMessage.warning('请填写审批单号')
    return
  }
  const principal = yuanToFen(String(adjustForm.principalYuan))
  const gift = yuanToFen(String(adjustForm.giftYuan))
  if (principal === 0 && gift === 0) {
    ElMessage.warning('实付与赠送调整不能同时为 0')
    return
  }
  if (!adjustForm.remark.trim()) {
    ElMessage.warning('请填写调整原因')
    return
  }
  adjusting.value = true
  try {
    const r = await adjustWallet({
      memberId: adjustForm.memberId,
      approvalNo: adjustForm.approvalNo.trim(),
      principalChange: principal,
      giftChange: gift,
      remark: adjustForm.remark.trim(),
    })
    ElMessage.success(r.idempotentHit ? '该审批单已处理（幂等），未重复调整' : '余额调整成功，已生成流水')
    adjustVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(toApiError(error).message || '调整失败')
  } finally {
    adjusting.value = false
  }
}

onMounted(load)
</script>
