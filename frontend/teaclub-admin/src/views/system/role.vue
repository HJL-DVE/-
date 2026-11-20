<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">角色管理</h2>
      <div class="toolbar-spacer" />
      <el-input v-model="keyword" placeholder="搜索编码/名称" size="small" style="width: 180px" clearable @keyup.enter="load" />
      <el-button type="primary" size="small" @click="openDlg()">新增角色</el-button>
      <el-button :icon="Refresh" size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="state === 'error'" type="error" :closable="false" show-icon style="margin-bottom: 12px" :title="'加载失败：' + errMsg">
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>
    <ReportState v-if="state === 'loading'" state="loading" height="320px" />

    <el-card v-else shadow="never">
      <el-table :data="rows" size="small" stripe>
        <el-table-column prop="roleCode" label="编码" width="160" />
        <el-table-column prop="roleName" label="名称" width="160" />
        <el-table-column label="数据范围" width="130">
          <template #default="{ row }">{{ scopeText(row.dataScope) }}</template>
        </el-table-column>
        <el-table-column label="内置" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.isBuiltin === 1" type="warning" size="small">内置</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="permissionCount" label="权限数" width="90" align="right" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDlg(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="openGrant(row)">授权</el-button>
            <el-button v-if="row.isBuiltin !== 1" link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlg" :title="form.id ? '编辑角色' : '新增角色'" width="480px">
      <el-form :model="form" label-width="90px" size="small">
        <el-form-item label="角色编码" required><el-input v-model="form.roleCode" maxlength="30" placeholder="如 STORE_MANAGER" /></el-form-item>
        <el-form-item label="角色名称" required><el-input v-model="form.roleName" maxlength="30" /></el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option label="全部门店（ALL）" value="ALL" />
            <el-option label="本门店（STORE）" value="STORE" />
            <el-option label="仅本人（SELF）" value="SELF" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" :max="999" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" active-value="ENABLED" inactive-value="DISABLED" active-text="启用" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="100" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dlg = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="grantDlg" :title="'角色授权 —— ' + (grantTarget?.roleName || '')" width="560px">
      <div v-if="grantLoading" style="text-align:center;padding:40px"><el-icon class="is-loading"><Loading /></el-icon></div>
      <el-tree v-else ref="treeRef" :data="permTree" show-checkbox node-key="id"
               :default-checked-keys="checkedIds" :props="{ label: 'permName', children: 'children' }"
               style="max-height: 420px; overflow: auto" />
      <template #footer>
        <el-button size="small" @click="grantDlg = false">取消</el-button>
        <el-button type="primary" size="small" :loading="grantSaving" @click="doGrant">保存授权</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Loading } from '@element-plus/icons-vue'
import type { ElTree } from 'element-plus'
import ReportState from '@/views/report/components/ReportState.vue'
import {
  getRolePage,
  getRoleDetail,
  createRole,
  updateRole,
  deleteRole,
  grantRolePermissions,
  getPermissionTree,
  type PermissionNode,
} from '@/api/system'
import type { RoleVO } from '@/api/types'

const rows = ref<RoleVO[]>([])
const keyword = ref('')
const state = ref<'loading' | 'error' | 'ready'>('loading')
const errMsg = ref('')
const loading = ref(false)
const saving = ref(false)
const dlg = ref(false)
// 表单字段与 RoleVO 部分重叠（types RoleVO 的 status 可选、含 createTime），用独立表单类型避免联合不兼容
const form = ref<{ id?: number; roleCode?: string; roleName?: string; dataScope?: string; sort?: number; remark?: string | null; status?: string }>({})

const grantDlg = ref(false)
const grantLoading = ref(false)
const grantSaving = ref(false)
const grantTarget = ref<RoleVO | null>(null)
const permTree = ref<PermissionNode[]>([])
const checkedIds = ref<number[]>([])
const treeRef = ref<InstanceType<typeof ElTree>>()

function scopeText(v?: string): string {
  return v === 'ALL' ? '全部门店' : v === 'STORE' ? '本门店' : v === 'SELF' ? '仅本人' : (v || '-')
}

async function load() {
  loading.value = true
  state.value = 'loading'
  errMsg.value = ''
  try {
    const r = await getRolePage({ pageNo: 1, pageSize: 50, keyword: keyword.value || undefined })
    rows.value = r.records || []
    state.value = 'ready'
  } catch (e) {
    state.value = 'error'
    errMsg.value = e instanceof Error ? e.message : '未知错误'
  } finally { loading.value = false }
}

function openDlg(row?: RoleVO) {
  form.value = row ? { ...row } : { roleCode: '', roleName: '', dataScope: 'STORE', sort: 0, status: 'ENABLED', remark: '' }
  dlg.value = true
}

async function save() {
  if (!form.value.roleCode?.trim() || !form.value.roleName?.trim()) { ElMessage.warning('编码与名称不能为空'); return }
  saving.value = true
  try {
    const payload = {
      roleCode: form.value.roleCode.trim().toUpperCase(),
      roleName: form.value.roleName.trim(),
      dataScope: form.value.dataScope,
      sort: form.value.sort,
      remark: form.value.remark,
    }
    if (form.value.id) await updateRole(form.value.id, payload)
    else await createRole(payload)
    ElMessage.success('已保存')
    dlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally { saving.value = false }
}

async function remove(row: RoleVO) {
  try { await ElMessageBox.confirm(`确定删除角色「${row.roleName}」？`, '删除确认', { type: 'warning' }) }
  catch { return }
  try {
    await deleteRole(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function openGrant(row: RoleVO) {
  grantTarget.value = row
  grantDlg.value = true
  grantLoading.value = true
  try {
    const [tree, detail] = await Promise.all([getPermissionTree(), getRoleDetail(row.id)])
    permTree.value = tree || []
    const ids = detail.permissionIds || []
    const leafIds: number[] = []
    const walk = (nodes: PermissionNode[]) => {
      for (const n of nodes) {
        if (n.children && n.children.length) walk(n.children)
        else if (ids.includes(n.id)) leafIds.push(n.id)
      }
    }
    walk(permTree.value)
    checkedIds.value = leafIds
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载权限树失败')
    grantDlg.value = false
  } finally { grantLoading.value = false }
}

async function doGrant() {
  if (!grantTarget.value) return
  const checked = treeRef.value?.getCheckedKeys() as number[] | undefined
  const half = treeRef.value?.getHalfCheckedKeys() as number[] | undefined
  const ids = [...(checked || []), ...(half || [])]
  grantSaving.value = true
  try {
    await grantRolePermissions(grantTarget.value.id, ids)
    ElMessage.success('授权已保存（相关用户需重新登录生效）')
    grantDlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '授权失败')
  } finally { grantSaving.value = false }
}

onMounted(load)
</script>