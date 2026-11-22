<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">权限管理</h2>
      <span class="text-muted">菜单与按钮权限树；授权请到「角色管理 → 授权」</span>
      <div class="toolbar-spacer" />
      <el-button type="primary" size="small" @click="openDlg(null)">新增权限</el-button>
      <el-button :icon="Refresh" size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="state === 'error'" type="error" :closable="false" show-icon style="margin-bottom: 12px" :title="'加载失败：' + errMsg">
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>
    <ReportState v-if="state === 'loading'" state="loading" height="420px" />

    <el-card v-else shadow="never">
      <el-table :data="tree" size="small" row-key="id" default-expand-all stripe>
        <el-table-column prop="permName" label="名称" min-width="180" />
        <el-table-column prop="permCode" label="编码" min-width="180" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.permType === 'MENU' ? 'success' : row.permType === 'BUTTON' ? 'warning' : 'info'" size="small">{{ PERM_TYPE_LABEL[row.permType] || row.permType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="routePath" label="路由" min-width="150" />
        <el-table-column prop="component" label="组件" min-width="150" />
        <el-table-column prop="sort" label="排序" width="70" align="center" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDlg(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="openDlg(null, row.id)">加子项</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlg" :title="form.id ? '编辑权限' : '新增权限'" width="520px">
      <el-form :model="form" label-width="90px" size="small">
        <el-form-item label="上级" v-if="!form.id">
          <el-tree-select v-model="form.parentId" :data="parentOptions" check-strictly style="width: 100%" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-radio-group v-model="form.permType">
            <el-radio-button value="MENU">菜单</el-radio-button>
            <el-radio-button value="BUTTON">按钮</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="编码" required><el-input v-model="form.permCode" maxlength="50" placeholder="如 member:list" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.permName" maxlength="30" /></el-form-item>
        <el-form-item label="路由" v-if="form.permType === 'MENU'"><el-input v-model="form.routePath" placeholder="/member/level" /></el-form-item>
        <el-form-item label="组件" v-if="form.permType === 'MENU'"><el-input v-model="form.component" placeholder="member/level" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" :max="999" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" active-value="ENABLED" inactive-value="DISABLED" active-text="启用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dlg = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import ReportState from '@/views/report/components/ReportState.vue'
import {
  getPermissionTree,
  createPermission,
  updatePermission,
  deletePermission,
  type PermissionNode,
} from '@/api/system'
import { PERM_TYPE_LABEL } from '@/utils/format'

const tree = ref<PermissionNode[]>([])
const state = ref<'loading' | 'error' | 'ready'>('loading')
const errMsg = ref('')
const loading = ref(false)
const saving = ref(false)
const dlg = ref(false)
const form = ref<Partial<PermissionNode> & { id?: number }>({})

const parentOptions = computed(() => {
  const out: { value: number; label: string }[] = [{ value: 0, label: '（顶级）' }]
  const walk = (nodes: PermissionNode[], prefix: string) => {
    for (const n of nodes) {
      out.push({ value: n.id, label: prefix + n.permName })
      if (n.children?.length) walk(n.children, prefix + n.permName + " / ")
    }
  }
  walk(tree.value, "")
  return out
})

async function load() {
  loading.value = true
  state.value = 'loading'
  errMsg.value = ''
  try {
    tree.value = await getPermissionTree()
    state.value = 'ready'
  } catch (e) {
    state.value = 'error'
    errMsg.value = e instanceof Error ? e.message : '未知错误'
  } finally { loading.value = false }
}

function openDlg(row: PermissionNode | null, parentId?: number) {
  form.value = row
    ? { ...row }
    : { parentId: parentId ?? 0, permType: 'MENU', permCode: '', permName: '', routePath: '', component: '', sort: 0, status: 'ENABLED' }
  dlg.value = true
}

async function save() {
  if (!form.value.permCode?.trim() || !form.value.permName?.trim()) { ElMessage.warning('编码与名称不能为空'); return }
  saving.value = true
  try {
    const payload = {
      permCode: form.value.permCode.trim(),
      permName: form.value.permName.trim(),
      permType: form.value.permType || 'MENU',
      parentId: form.value.parentId ?? 0,
      routePath: form.value.routePath || null,
      component: form.value.component || null,
      sort: form.value.sort ?? 0,
      status: form.value.status,
    }
    if (form.value.id) await updatePermission(form.value.id, payload)
    else await createPermission(payload)
    ElMessage.success('已保存')
    dlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally { saving.value = false }
}

async function remove(row: PermissionNode) {
  try { await ElMessageBox.confirm(`确定删除权限「${row.permName}」？（有子节点或已授权时后端会拒绝）`, '删除确认', { type: 'warning' }) }
  catch { return }
  try {
    await deletePermission(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

onMounted(load)
</script>