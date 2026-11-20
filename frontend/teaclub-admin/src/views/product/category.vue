<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">商品分类</h2>
      <div class="toolbar-spacer" />
      <el-button type="primary" size="small" @click="openDlg()">新增分类</el-button>
      <el-button :icon="Refresh" size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="state === 'error'" type="error" :closable="false" show-icon style="margin-bottom: 12px" :title="'加载失败：' + errMsg">
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>
    <ReportState v-if="state === 'loading'" state="loading" height="300px" />

    <el-card v-else shadow="never" style="max-width: 760px">
      <el-table :data="tree" size="small" row-key="id" default-expand-all stripe>
        <el-table-column prop="categoryName" label="分类名称" min-width="200" />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="productCount" label="商品数" width="90" align="right" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDlg(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlg" :title="form.id ? '编辑分类' : '新增分类'" width="460px">
      <el-form :model="form" label-width="90px" size="small">
        <el-form-item label="分类名称" required><el-input v-model="form.categoryName" maxlength="20" /></el-form-item>
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
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import ReportState from '@/views/report/components/ReportState.vue'
import { getCategoryTree, createCategory, updateCategory, deleteCategory } from '@/api/product'
import type { CategoryVO } from '@/api/types'

const tree = ref<CategoryVO[]>([])
const state = ref<'loading' | 'error' | 'ready'>('loading')
const errMsg = ref('')
const loading = ref(false)
const saving = ref(false)
const dlg = ref(false)
const form = ref<{ id?: number; categoryName?: string; sort?: number; status?: string }>({})

async function load() {
  loading.value = true
  state.value = 'loading'
  errMsg.value = ''
  try {
    tree.value = await getCategoryTree()
    state.value = 'ready'
  } catch (e) {
    state.value = 'error'
    errMsg.value = e instanceof Error ? e.message : '未知错误'
  } finally { loading.value = false }
}

function openDlg(row?: CategoryVO) {
  form.value = row ? { id: row.id, categoryName: row.categoryName, sort: row.sort, status: row.status }
    : { categoryName: '', sort: 0, status: 'ENABLED' }
  dlg.value = true
}

async function save() {
  if (!form.value.categoryName?.trim()) { ElMessage.warning('分类名称不能为空'); return }
  saving.value = true
  try {
    const payload = { categoryName: String(form.value.categoryName || '').trim(), sort: form.value.sort, status: form.value.status }
    if (form.value.id) await updateCategory(form.value.id, payload)
    else await createCategory(payload)
    ElMessage.success('已保存')
    dlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally { saving.value = false }
}

async function remove(row: CategoryVO) {
  try { await ElMessageBox.confirm(`确定删除分类「${row.categoryName}」？（有商品挂靠时后端会拒绝）`, '删除确认', { type: 'warning' }) }
  catch { return }
  try {
    await deleteCategory(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

onMounted(load)
</script>