<template>
  <div class="page">
    <ApiPending
      v-if="listError"
      title="优惠券模板接口未就绪"
      url="GET /api/admin/marketing/coupons/templates"
      :code="listError.code"
      :message="listError.message"
      :trace-id="listError.traceId"
    />

    <div class="page-card">
      <div class="toolbar">
        <el-select v-model="query.couponType" placeholder="券类型" clearable style="width: 150px" @change="search">
          <el-option v-for="(label, value) in COUPON_TYPE_LABEL" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="search">
          <el-option v-for="(label, value) in TEMPLATE_STATUS_LABEL" :key="value" :label="label" :value="value" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="reload">刷新</el-button>
        <div class="toolbar-spacer" />
        <span class="text-muted">GET /api/admin/marketing/coupons/templates · 共 {{ total }} 条</span>
        <el-button type="primary" :icon="Plus" :disabled="!!listError" @click="openCreate">新建券模板</el-button>
      </div>

      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column prop="templateNo" label="模板编号" min-width="190" show-overflow-tooltip />
        <el-table-column prop="couponName" label="券名称" min-width="170" show-overflow-tooltip />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            {{ row.couponTypeLabel || COUPON_TYPE_LABEL[row.couponType] || row.couponType }}
          </template>
        </el-table-column>
        <el-table-column label="适用范围" width="100">
          <template #default="{ row }">{{ SCOPE_TYPE_LABEL[row.scopeType] || row.scopeType }}</template>
        </el-table-column>
        <el-table-column label="面额 / 折扣" width="130" align="right">
          <template #default="{ row }"><span class="money">{{ valueText(row) }}</span></template>
        </el-table-column>
        <el-table-column label="使用门槛" width="110" align="right">
          <template #default="{ row }">{{ thresholdText(row) }}</template>
        </el-table-column>
        <el-table-column label="发行 / 已发" width="110" align="center">
          <template #default="{ row }">
            {{ row.totalCount ? row.totalCount + ' / ' + (row.issuedCount || 0) : '不限 / ' + (row.issuedCount || 0) }}
          </template>
        </el-table-column>
        <el-table-column label="每人限领" width="90" align="center">
          <template #default="{ row }">{{ row.perMemberLimit || '不限' }}</template>
        </el-table-column>
        <el-table-column label="有效期" min-width="150">
          <template #default="{ row }">{{ validText(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="tagTypeByStatus(row.status)">
              {{ TEMPLATE_STATUS_LABEL[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openGrant(row)">发放</el-button>
            <el-button link type="warning" @click="onToggleStatus(row)">
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
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

    <el-dialog v-model="formVisible" :title="form.id ? '编辑券模板' : '新建券模板'" width="640px">
      <el-form label-width="120px">
        <el-form-item label="券名称" required>
          <el-input v-model="form.couponName" placeholder="如：满 30 减 5" />
        </el-form-item>
        <el-form-item label="券类型" required>
          <el-select v-model="form.couponType" style="width: 100%" @change="onTypeChange">
            <el-option v-for="(label, value) in COUPON_TYPE_LABEL" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用范围">
          <el-select v-model="form.scopeType" style="width: 100%">
            <el-option v-for="(label, value) in SCOPE_TYPE_LABEL" :key="value" :label="label" :value="value" />
          </el-select>
          <div v-if="form.scopeType !== 'ALL'" class="text-muted" style="margin-top: 4px">
            指定门店 / 商品的门店与 SPU 关联需后端接口支持，本表单暂不提交关联列表
          </div>
        </el-form-item>
        <el-form-item v-if="showFaceValue" label="面额（元）" required>
          <el-input v-model="form.faceValueYuan" placeholder="如 5.00" style="width: 180px" />
          <span class="text-muted" style="margin-left: 8px">提交时转换为分</span>
        </el-form-item>
        <el-form-item v-if="form.couponType === 'FULL_REDUCTION'" label="满减门槛（元）" required>
          <el-input v-model="form.thresholdYuan" placeholder="如 30.00" style="width: 180px" />
        </el-form-item>
        <el-form-item v-if="form.couponType === 'DISCOUNT'" label="折扣（折）" required>
          <el-input v-model="form.discountZhe" placeholder="如 9.5 表示 95 折" style="width: 180px" />
          <span class="text-muted" style="margin-left: 8px">提交时换算为万分数（9.5 → 9500）</span>
        </el-form-item>
        <el-form-item v-if="form.couponType === 'DISCOUNT'" label="折扣封顶（元）">
          <el-input v-model="form.maxDiscountYuan" placeholder="留空表示不封顶" style="width: 180px" />
        </el-form-item>
        <el-form-item label="发行总量">
          <el-input-number v-model="form.totalCount" :min="0" :step="10" />
          <span class="text-muted" style="margin-left: 8px">0 = 不限</span>
        </el-form-item>
        <el-form-item label="每人限领">
          <el-input-number v-model="form.perMemberLimit" :min="0" />
          <span class="text-muted" style="margin-left: 8px">0 = 不限</span>
        </el-form-item>
        <el-form-item label="有效期类型">
          <el-radio-group v-model="form.validType">
            <el-radio value="RELATIVE">领取后 N 天</el-radio>
            <el-radio value="FIXED">固定区间</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.validType === 'RELATIVE'" label="有效天数">
          <el-input-number v-model="form.validDays" :min="1" :max="3650" />
        </el-form-item>
        <template v-else>
          <el-form-item label="生效时间" required>
            <el-date-picker
              v-model="form.validStart"
              type="datetime"
              value-format="YYYY-MM-DD[T]HH:mm:ss"
              placeholder="开始时间"
              style="width: 220px"
            />
          </el-form-item>
          <el-form-item label="失效时间" required>
            <el-date-picker
              v-model="form.validEnd"
              type="datetime"
              value-format="YYYY-MM-DD[T]HH:mm:ss"
              placeholder="结束时间"
              style="width: 220px"
            />
          </el-form-item>
        </template>
        <el-form-item label="新人专享">
          <el-switch v-model="form.newMemberOnly" />
        </el-form-item>
        <el-form-item label="生日专享">
          <el-switch v-model="form.birthdayOnly" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 180px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="grantVisible" title="发放优惠券" width="440px">
      <el-form label-width="90px">
        <el-form-item label="券模板">
          <span>{{ grantForm.templateName }}</span>
        </el-form-item>
        <el-form-item label="会员 ID" required>
          <el-input v-model="grantForm.memberId" placeholder="输入会员 ID" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="grantForm.qty" :min="1" :max="100" />
        </el-form-item>
      </el-form>
      <div class="text-muted">POST /api/admin/marketing/coupons/templates/{id}/grant</div>
      <template #footer>
        <el-button @click="grantVisible = false">取消</el-button>
        <el-button type="primary" :loading="granting" @click="submitGrant">发放</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  changeCouponTemplateStatus,
  createCouponTemplate,
  deleteCouponTemplate,
  getCouponTemplate,
  getCouponTemplates,
  grantCoupon,
  updateCouponTemplate,
} from '@/api/marketing'
import { ApiError, isApiError } from '@/api/request'
import ApiPending from '@/components/ApiPending.vue'
import { fenToYuan, formatDate, formatDateTime, tagTypeByStatus, yuanToFen } from '@/utils/format'
import type { CouponTemplateVO } from '@/api/marketing'

const COUPON_TYPE_LABEL: Record<string, string> = {
  FULL_REDUCTION: '满减券',
  DISCOUNT: '折扣券',
  NO_THRESHOLD: '无门槛券',
  GOODS_EXCHANGE: '商品兑换券',
  NEW_MEMBER: '新人券',
  BIRTHDAY: '生日券',
}

const SCOPE_TYPE_LABEL: Record<string, string> = {
  ALL: '全品牌',
  STORE: '指定门店',
  GOODS: '指定商品',
}

const TEMPLATE_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  ENABLED: '启用',
  DISABLED: '停用',
}

const loading = ref(false)
const records = ref<CouponTemplateVO[]>([])
const total = ref(0)
const listError = ref<ApiError | null>(null)

const query = reactive<{ pageNo: number; pageSize: number; couponType: string; status: string }>({
  pageNo: 1,
  pageSize: 20,
  couponType: '',
  status: '',
})

function toApiError(error: unknown): ApiError {
  return isApiError(error) ? error : new ApiError(-1, (error as Error)?.message || '未知错误')
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await getCouponTemplates({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      couponType: query.couponType || undefined,
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
  void load()
}

function reload(): void {
  void load()
}

/** 折扣率万分数转「折」：9500 → 9.5 */
function valueText(row: CouponTemplateVO): string {
  if (row.couponType === 'DISCOUNT') {
    const rate = row.discountRate ?? 10000
    const zhe = (rate / 1000).toFixed(2).replace(/0+$/, '').replace(/\.$/, '')
    const cap = row.maxDiscountAmount ? '（封顶 ' + fenToYuan(row.maxDiscountAmount) + '）' : ''
    return zhe + ' 折' + cap
  }
  if (row.couponType === 'GOODS_EXCHANGE') return '兑换指定商品'
  return fenToYuan(row.faceValue)
}

function thresholdText(row: CouponTemplateVO): string {
  if (row.couponType !== 'FULL_REDUCTION') return '无门槛'
  return '满 ' + fenToYuan(row.thresholdAmount)
}

function validText(row: CouponTemplateVO): string {
  if (row.validType === 'FIXED') {
    return formatDate(row.validStart) + ' ~ ' + formatDate(row.validEnd)
  }
  return '领取后 ' + (row.validDays ?? '-') + ' 天'
}

/** 后端返回 2026-11-20T15:05:44.53，日期选择器需要 2026-11-20T15:05:44 */
function toPickerValue(value?: string | null): string {
  if (!value) return ''
  return String(value).slice(0, 19)
}

interface CouponForm {
  id?: number
  couponName: string
  couponType: string
  scopeType: string
  faceValueYuan: string
  thresholdYuan: string
  discountZhe: string
  maxDiscountYuan: string
  totalCount: number
  perMemberLimit: number
  validType: string
  validDays: number
  validStart: string
  validEnd: string
  newMemberOnly: boolean
  birthdayOnly: boolean
  status: string
  remark: string
}

const formVisible = ref(false)
const submitting = ref(false)
const form = reactive<CouponForm>(emptyForm())
const showFaceValue = ref(true)

function emptyForm(): CouponForm {
  return {
    id: undefined,
    couponName: '',
    couponType: 'FULL_REDUCTION',
    scopeType: 'ALL',
    faceValueYuan: '',
    thresholdYuan: '',
    discountZhe: '',
    maxDiscountYuan: '',
    totalCount: 0,
    perMemberLimit: 1,
    validType: 'RELATIVE',
    validDays: 30,
    validStart: '',
    validEnd: '',
    newMemberOnly: false,
    birthdayOnly: false,
    status: 'DRAFT',
    remark: '',
  }
}

function assignForm(next: CouponForm): void {
  Object.assign(form, next)
  showFaceValue.value = form.couponType !== 'DISCOUNT' && form.couponType !== 'GOODS_EXCHANGE'
}

function onTypeChange(): void {
  showFaceValue.value = form.couponType !== 'DISCOUNT' && form.couponType !== 'GOODS_EXCHANGE'
}

function openCreate(): void {
  assignForm(emptyForm())
  formVisible.value = true
}

async function openEdit(row: CouponTemplateVO): Promise<void> {
  try {
    const detail = await getCouponTemplate(row.id)
    assignForm({
      id: detail.id,
      couponName: detail.couponName,
      couponType: detail.couponType,
      scopeType: detail.scopeType || 'ALL',
      faceValueYuan: detail.faceValue ? fenToYuan(detail.faceValue) : '',
      thresholdYuan: detail.thresholdAmount ? fenToYuan(detail.thresholdAmount) : '',
      discountZhe: detail.discountRate ? (detail.discountRate / 1000).toFixed(2).replace(/0+$/, '').replace(/\.$/, '') : '',
      maxDiscountYuan: detail.maxDiscountAmount ? fenToYuan(detail.maxDiscountAmount) : '',
      totalCount: detail.totalCount ?? 0,
      perMemberLimit: detail.perMemberLimit ?? 1,
      validType: detail.validType || 'RELATIVE',
      validDays: detail.validDays ?? 30,
      validStart: toPickerValue(detail.validStart),
      validEnd: toPickerValue(detail.validEnd),
      newMemberOnly: detail.newMemberOnly === 1,
      birthdayOnly: detail.birthdayOnly === 1,
      status: detail.status,
      remark: detail.remark || '',
    })
    formVisible.value = true
  } catch {
    // 错误提示由 http 拦截器统一弹出
  }
}

function parseMoney(input: string, label: string): number | null {
  const fen = yuanToFen(input)
  if (Number.isNaN(fen) || fen <= 0) {
    ElMessage.warning(label + '不合法（单位元，最多两位小数）')
    return null
  }
  return fen
}

function buildPayload(): Partial<CouponTemplateVO> | null {
  if (!form.couponName.trim()) {
    ElMessage.warning('请填写券名称')
    return null
  }
  if (!form.couponType) {
    ElMessage.warning('请选择券类型')
    return null
  }
  const payload: Partial<CouponTemplateVO> = {
    couponName: form.couponName.trim(),
    couponType: form.couponType,
    scopeType: form.scopeType,
    totalCount: form.totalCount,
    perMemberLimit: form.perMemberLimit,
    validType: form.validType,
    newMemberOnly: form.newMemberOnly ? 1 : 0,
    birthdayOnly: form.birthdayOnly ? 1 : 0,
    status: form.status,
    remark: form.remark,
  }
  if (form.validType === 'FIXED') {
    if (!form.validStart || !form.validEnd) {
      ElMessage.warning('固定有效期需要填写生效与失效时间')
      return null
    }
    payload.validStart = form.validStart
    payload.validEnd = form.validEnd
  } else {
    payload.validDays = form.validDays
  }
  if (form.couponType === 'DISCOUNT') {
    const zhe = Number(form.discountZhe)
    if (!Number.isFinite(zhe) || zhe <= 0 || zhe >= 10) {
      ElMessage.warning('折扣需大于 0 且小于 10 折，如 9.5')
      return null
    }
    payload.discountRate = Math.round(zhe * 1000)
    payload.faceValue = 0
    if (form.maxDiscountYuan) {
      const cap = parseMoney(form.maxDiscountYuan, '折扣封顶金额')
      if (cap === null) return null
      payload.maxDiscountAmount = cap
    }
  } else if (form.couponType === 'GOODS_EXCHANGE') {
    payload.faceValue = 0
  } else {
    const face = parseMoney(form.faceValueYuan, '面额')
    if (face === null) return null
    payload.faceValue = face
  }
  if (form.couponType === 'FULL_REDUCTION') {
    const threshold = parseMoney(form.thresholdYuan, '满减门槛金额')
    if (threshold === null) return null
    payload.thresholdAmount = threshold
  }
  return payload
}

async function submitForm(): Promise<void> {
  const payload = buildPayload()
  if (!payload) return
  submitting.value = true
  try {
    if (form.id) {
      await updateCouponTemplate(form.id, payload)
      ElMessage.success('已保存')
    } else {
      await createCouponTemplate(payload)
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

function onToggleStatus(row: CouponTemplateVO): void {
  const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const action = next === 'ENABLED' ? '启用' : '停用'
  ElMessageBox.confirm('确认' + action + '券模板「' + row.couponName + '」？', '提示', { type: 'warning' })
    .then(async () => {
      await changeCouponTemplateStatus(row.id, next)
      ElMessage.success('已' + action)
      await load()
    })
    .catch(() => undefined)
}

function onDelete(row: CouponTemplateVO): void {
  ElMessageBox.confirm(
    '确认删除券模板「' + row.couponName + '」？已发行的模板后端会拒绝删除。',
    '提示',
    { type: 'warning' },
  )
    .then(async () => {
      await deleteCouponTemplate(row.id)
      ElMessage.success('已删除')
      await load()
    })
    .catch(() => undefined)
}

const grantVisible = ref(false)
const granting = ref(false)
const grantForm = reactive<{ templateId: number; templateName: string; memberId: string; qty: number }>({
  templateId: 0,
  templateName: '',
  memberId: '',
  qty: 1,
})

function openGrant(row: CouponTemplateVO): void {
  grantForm.templateId = row.id
  grantForm.templateName = row.couponName
  grantForm.memberId = ''
  grantForm.qty = 1
  grantVisible.value = true
}

async function submitGrant(): Promise<void> {
  const memberId = Number(grantForm.memberId)
  if (!Number.isInteger(memberId) || memberId <= 0) {
    ElMessage.warning('请输入合法会员 ID')
    return
  }
  if (!Number.isInteger(grantForm.qty) || grantForm.qty <= 0) {
    ElMessage.warning('发放数量需为正整数')
    return
  }
  granting.value = true
  try {
    const coupons = await grantCoupon(grantForm.templateId, memberId, grantForm.qty)
    ElMessage.success('已发放 ' + (coupons?.length || 0) + ' 张')
    grantVisible.value = false
    await load()
  } catch {
    // 错误提示由 http 拦截器统一弹出
  } finally {
    granting.value = false
  }
}

onMounted(load)
</script>
