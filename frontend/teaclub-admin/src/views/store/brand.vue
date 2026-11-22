<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">品牌信息</h2>
      <span class="text-muted">品牌为全局唯一，字段变更影响扣款顺序等业务规则</span>
      <div class="toolbar-spacer" />
      <el-button v-if="!editing" type="primary" size="small" :disabled="!canEdit" @click="editing = true">
        编辑
      </el-button>
      <template v-else>
        <el-button size="small" @click="cancelEdit">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
      </template>
    </div>

    <ReportState v-if="state === 'loading'" state="loading" height="320px" />
    <ReportState v-else-if="state === 'error'" state="error" :detail="errMsg" retryable @retry="load" height="320px" />

    <el-card v-else shadow="never" style="max-width: 720px">
      <el-descriptions :column="1" border size="large">
        <el-descriptions-item label="品牌编码">{{ brand?.brandCode }}</el-descriptions-item>
        <el-descriptions-item label="品牌名称">
          <el-input v-if="editing" v-model="form.brandName" maxlength="50" show-word-limit style="width: 300px" />
          <span v-else>{{ brand?.brandName }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="客服电话">
          <el-input v-if="editing" v-model="form.contactPhone" maxlength="30" style="width: 300px" />
          <span v-else>{{ brand?.contactPhone || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="Logo 地址">
          <el-input v-if="editing" v-model="form.logoUrl" placeholder="https://..." style="width: 300px" />
          <span v-else class="text-muted">{{ brand?.logoUrl || '未设置' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="余额抵扣顺序">
          <el-select v-if="editing" v-model="form.walletDeductOrder" style="width: 200px">
            <el-option label="先赠后本（优先使用赠送金）" value="GIFT_FIRST" />
            <el-option label="先本后赠（优先使用充值本金）" value="PRINCIPAL_FIRST" />
          </el-select>
          <span v-else>{{ deductOrderText }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="brand?.status === 'ENABLED' ? 'success' : 'danger'" size="small">
            {{ brand?.status === 'ENABLED' ? '启用' : '停用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(brand?.updateTime) }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ReportState from '@/views/report/components/ReportState.vue'
import { getBrand, updateBrand, type BrandVO } from '@/api/brand'
import { useUserStore } from '@/stores/user'

const brand = ref<BrandVO | null>(null)
const state = ref<'loading' | 'error' | 'ready'>('loading')
const errMsg = ref('')
const editing = ref(false)
const saving = ref(false)
const form = ref<Partial<BrandVO>>({})

const userStore = useUserStore()
const canEdit = computed(() => {
  const perms = userStore.userInfo?.permissions || []
  return perms.includes('brand:update') || perms.includes('*')
})

const deductOrderText = computed(() => {
  const v = brand.value?.walletDeductOrder
  return v === 'PRINCIPAL_FIRST' ? '先本后赠（优先使用充值本金）'
    : v === 'GIFT_FIRST' ? '先赠后本（优先使用赠送金）' : (v || '-')
})

function formatDateTime(v?: string | null): string {
  if (!v) return '-'
  return v.replace('T', ' ').slice(0, 19)
}

async function load() {
  state.value = 'loading'
  errMsg.value = ''
  try {
    brand.value = await getBrand()
    state.value = 'ready'
  } catch (e) {
    state.value = 'error'
    errMsg.value = e instanceof Error ? e.message : '未知错误'
  }
}

function cancelEdit() {
  editing.value = false
  form.value = {}
}

async function save() {
  if (!form.value.brandName || !form.value.brandName.trim()) {
    ElMessage.warning('品牌名称不能为空')
    return
  }
  saving.value = true
  try {
    await updateBrand({
      brandName: form.value.brandName?.trim(),
      contactPhone: form.value.contactPhone,
      logoUrl: form.value.logoUrl,
      walletDeductOrder: form.value.walletDeductOrder,
    })
    ElMessage.success('品牌信息已更新')
    editing.value = false
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>