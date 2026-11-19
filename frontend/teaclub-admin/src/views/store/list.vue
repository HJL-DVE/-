<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="门店名称/编码" clearable style="width: 200px" @keyup.enter="search" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="营业中" value="OPEN" />
          <el-option label="暂停营业" value="SUSPENDED" />
          <el-option label="已停业" value="CLOSED" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
        <div class="toolbar-spacer" />
        <el-button type="primary" :icon="Plus" @click="openCreate">新增门店</el-button>
      </div>

      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column prop="storeCode" label="门店编码" width="110" />
        <el-table-column prop="storeName" label="门店名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="city" label="城市" width="110" />
        <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="phone" label="电话" width="140" />
        <el-table-column prop="managerName" label="店长" width="100" />
        <el-table-column prop="businessHours" label="营业时间" width="130" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="tagTypeByStatus(row.status)">
              {{ STORE_STATUS_LABEL[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-dropdown trigger="click" @command="(cmd: string) => onStatus(row, cmd)">
              <el-button link type="primary">状态<el-icon><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="OPEN">营业中</el-dropdown-item>
                  <el-dropdown-item command="SUSPENDED">暂停营业</el-dropdown-item>
                  <el-dropdown-item command="CLOSED">已停业</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="search"
        />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑门店' : '新增门店'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="门店编码" prop="storeCode">
              <el-input v-model="form.storeCode" :disabled="!!editingId" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="门店名称" prop="storeName">
              <el-input v-model="form.storeName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="省份"><el-input v-model="form.province" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="城市"><el-input v-model="form.city" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="区县"><el-input v-model="form.district" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="店长"><el-input v-model="form.managerName" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="营业时间"><el-input v-model="form.businessHours" placeholder="09:00-22:00" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开业日期">
              <el-date-picker v-model="form.openDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="营业中" value="OPEN" />
                <el-option label="暂停营业" value="SUSPENDED" />
                <el-option label="已停业" value="CLOSED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ArrowDown, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { createStore, deleteStore, getStorePage, updateStore, updateStoreStatus } from '@/api/store'
import type { StoreSavePayload } from '@/api/store'
import { useAppStore } from '@/stores/app'
import { STORE_STATUS_LABEL, tagTypeByStatus } from '@/utils/format'
import type { StoreVO } from '@/api/types'

const appStore = useAppStore()

const loading = ref(false)
const records = ref<StoreVO[]>([])
const total = ref(0)

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  status: '',
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await getStorePage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
    })
    records.value = page.records || []
    total.value = page.total || 0
  } finally {
    loading.value = false
  }
}

function search(): void {
  query.pageNo = 1
  load()
}

function reset(): void {
  query.keyword = ''
  query.status = ''
  search()
}

const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const emptyForm = (): StoreSavePayload => ({
  storeCode: '',
  storeName: '',
  province: '',
  city: '',
  district: '',
  address: '',
  phone: '',
  managerName: '',
  businessHours: '',
  openDate: '',
  status: 'OPEN',
  remark: '',
})

const form = reactive<StoreSavePayload>(emptyForm())

const rules: FormRules = {
  storeCode: [{ required: true, message: '请输入门店编码', trigger: 'blur' }],
  storeName: [{ required: true, message: '请输入门店名称', trigger: 'blur' }],
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row: StoreVO): void {
  editingId.value = row.id
  Object.assign(form, {
    storeCode: row.storeCode,
    storeName: row.storeName,
    province: row.province || '',
    city: row.city || '',
    district: row.district || '',
    address: row.address || '',
    phone: row.phone || '',
    managerName: row.managerName || '',
    businessHours: row.businessHours || '',
    openDate: row.openDate || '',
    status: row.status,
    remark: row.remark || '',
  })
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingId.value) {
      await updateStore(editingId.value, { ...form })
      ElMessage.success('门店已更新')
    } else {
      await createStore({ ...form })
      ElMessage.success('门店已创建')
    }
    dialogVisible.value = false
    await load()
    await appStore.loadStores(true).catch(() => undefined)
  } finally {
    submitting.value = false
  }
}

async function onStatus(row: StoreVO, status: string): Promise<void> {
  await updateStoreStatus(row.id, status)
  ElMessage.success('状态已更新')
  await load()
}

function onDelete(row: StoreVO): void {
  ElMessageBox.confirm('确认删除门店「' + row.storeName + '」？', '提示', { type: 'warning' })
    .then(async () => {
      await deleteStore(row.id)
      ElMessage.success('已删除')
      await load()
      await appStore.loadStores(true).catch(() => undefined)
    })
    .catch(() => undefined)
}

onMounted(load)
</script>
