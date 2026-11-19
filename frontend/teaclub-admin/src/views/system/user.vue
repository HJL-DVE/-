<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="账号/姓名" clearable style="width: 180px" @keyup.enter="search" />
        <el-input v-model="query.phone" placeholder="手机号" clearable style="width: 150px" @keyup.enter="search" />
        <el-select v-model="query.userType" placeholder="类型" clearable style="width: 130px">
          <el-option label="总部" value="HQ" />
          <el-option label="门店" value="STORE" />
        </el-select>
        <el-select v-model="query.storeId" placeholder="所属门店" clearable filterable style="width: 180px">
          <el-option v-for="store in appStore.stores" :key="store.id" :label="store.storeName" :value="store.id" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
        <div class="toolbar-spacer" />
        <el-button type="primary" :icon="Plus" @click="openCreate">新增员工</el-button>
      </div>

      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column prop="username" label="账号" width="130" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ row.userType === 'HQ' ? '总部' : '门店' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="150">
          <template #default="{ row }">
            <el-tag v-for="name in row.roleNames || []" :key="name" size="small" style="margin-right: 4px">
              {{ name }}
            </el-tag>
            <span v-if="!(row.roleNames || []).length" class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="授权门店" min-width="180">
          <template #default="{ row }">
            <span v-if="(row.stores || []).length">
              {{ (row.stores || []).map((s: StoreBrief) => s.storeName).join('、') }}
            </span>
            <span v-else class="text-muted">全部/未授权</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近登录" width="170">
          <template #default="{ row }">{{ formatDateTime(row.lastLoginTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openGrant(row)">门店授权</el-button>
            <el-button link type="warning" @click="onResetPassword(row)">重置密码</el-button>
            <el-button link :type="row.status === 'ENABLED' ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑员工' : '新增员工'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="账号" prop="username">
              <el-input v-model="form.username" :disabled="!!editingId" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="!editingId" label="初始密码" prop="password">
              <el-input v-model="form.password" type="password" show-password />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型">
              <el-select v-model="form.userType" style="width: 100%" :disabled="!!editingId">
                <el-option label="总部" value="HQ" />
                <el-option label="门店" value="STORE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="角色">
              <el-select v-model="form.roleIds" multiple style="width: 100%" placeholder="选择角色">
                <el-option v-for="role in roles" :key="role.id" :label="role.roleName" :value="role.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="授权门店">
              <el-select v-model="form.storeIds" multiple filterable style="width: 100%" placeholder="选择门店">
                <el-option v-for="store in appStore.stores" :key="store.id" :label="store.storeName" :value="store.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认门店">
              <el-select v-model="form.defaultStoreId" clearable style="width: 100%">
                <el-option
                  v-for="store in appStore.stores.filter((s) => form.storeIds.includes(s.id))"
                  :key="store.id"
                  :label="store.storeName"
                  :value="store.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="grantVisible" title="门店授权" width="460px">
      <el-form label-width="90px">
        <el-form-item label="员工">{{ grantForm.username }}</el-form-item>
        <el-form-item label="授权门店">
          <el-select v-model="grantForm.storeIds" multiple filterable style="width: 100%">
            <el-option v-for="store in appStore.stores" :key="store.id" :label="store.storeName" :value="store.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认门店">
          <el-select v-model="grantForm.defaultStoreId" clearable style="width: 100%">
            <el-option
              v-for="store in appStore.stores.filter((s) => grantForm.storeIds.includes(s.id))"
              :key="store.id"
              :label="store.storeName"
              :value="store.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="grantVisible = false">取消</el-button>
        <el-button type="primary" :loading="grantSubmitting" @click="submitGrant">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createUser,
  getAllRoles,
  getUserPage,
  grantUserStores,
  resetUserPassword,
  updateUser,
  updateUserStatus,
} from '@/api/system'
import { useAppStore } from '@/stores/app'
import { formatDateTime } from '@/utils/format'
import type { RoleVO, StoreBrief, UserVO } from '@/api/types'

const appStore = useAppStore()

const loading = ref(false)
const submitting = ref(false)
const records = ref<UserVO[]>([])
const total = ref(0)
const roles = ref<RoleVO[]>([])

const query = reactive<{
  pageNo: number
  pageSize: number
  keyword: string
  phone: string
  userType: string
  storeId?: number
}>({ pageNo: 1, pageSize: 20, keyword: '', phone: '', userType: '', storeId: undefined })

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await getUserPage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      phone: query.phone || undefined,
      userType: query.userType || undefined,
      storeId: query.storeId || undefined,
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
  query.phone = ''
  query.userType = ''
  query.storeId = undefined
  search()
}

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const emptyForm = () => ({
  username: '',
  password: '',
  realName: '',
  phone: '',
  email: '',
  userType: 'STORE',
  roleIds: [] as number[],
  storeIds: [] as number[],
  defaultStoreId: undefined as number | undefined,
})

const form = reactive(emptyForm())

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }],
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row: UserVO): void {
  editingId.value = row.id
  Object.assign(form, {
    username: row.username,
    password: '',
    realName: row.realName || '',
    phone: row.phone || '',
    email: row.email || '',
    userType: row.userType,
    roleIds: roles.value.filter((role) => (row.roleCodes || []).includes(role.roleCode)).map((r) => r.id),
    storeIds: (row.stores || []).map((s) => s.storeId),
    defaultStoreId: (row.stores || []).find((s) => s.isDefault)?.storeId,
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
      await updateUser(editingId.value, {
        realName: form.realName || undefined,
        phone: form.phone || undefined,
        email: form.email || undefined,
        roleIds: form.roleIds,
        storeIds: form.storeIds,
        defaultStoreId: form.defaultStoreId,
      })
      ElMessage.success('员工已更新')
    } else {
      await createUser({
        username: form.username,
        password: form.password,
        realName: form.realName || undefined,
        phone: form.phone || undefined,
        email: form.email || undefined,
        userType: form.userType,
        roleIds: form.roleIds,
        storeIds: form.storeIds,
        defaultStoreId: form.defaultStoreId,
      })
      ElMessage.success('员工已创建')
    }
    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

const grantVisible = ref(false)
const grantSubmitting = ref(false)
const grantForm = reactive({
  id: 0,
  username: '',
  storeIds: [] as number[],
  defaultStoreId: undefined as number | undefined,
})

function openGrant(row: UserVO): void {
  grantForm.id = row.id
  grantForm.username = row.username
  grantForm.storeIds = (row.stores || []).map((s) => s.storeId)
  grantForm.defaultStoreId = (row.stores || []).find((s) => s.isDefault)?.storeId
  grantVisible.value = true
}

async function submitGrant(): Promise<void> {
  grantSubmitting.value = true
  try {
    await grantUserStores(grantForm.id, grantForm.storeIds, grantForm.defaultStoreId)
    ElMessage.success('门店授权已保存')
    grantVisible.value = false
    await load()
  } finally {
    grantSubmitting.value = false
  }
}

function onResetPassword(row: UserVO): void {
  ElMessageBox.prompt('请输入新密码（8-32 位）', '重置密码：' + row.username, {
    inputPlaceholder: '新密码',
    inputValidator: (value) => (!!value && value.length >= 8 && value.length <= 32) || '长度需为 8-32 位',
  })
    .then(async ({ value }) => {
      await resetUserPassword(row.id, value)
      ElMessage.success('密码已重置')
    })
    .catch(() => undefined)
}

function toggleStatus(row: UserVO): void {
  const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  ElMessageBox.confirm('确认' + (next === 'ENABLED' ? '启用' : '停用') + '员工「' + row.username + '」？', '提示', {
    type: 'warning',
  })
    .then(async () => {
      await updateUserStatus(row.id, next)
      ElMessage.success('操作成功')
      await load()
    })
    .catch(() => undefined)
}

onMounted(async () => {
  await appStore.loadStores().catch(() => undefined)
  await load()
  try {
    roles.value = await getAllRoles()
  } catch {
    roles.value = []
  }
})
</script>
