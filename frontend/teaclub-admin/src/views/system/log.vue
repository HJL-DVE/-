<template>
  <div class="page">
    <div class="toolbar">
      <h2 class="page-title">操作日志</h2>
      <span class="text-muted">历史操作留痕（只读）；微服务化后新操作暂未接入写日志</span>
      <div class="toolbar-spacer" />
      <el-select v-model="success" placeholder="全部结果" clearable size="small" style="width: 130px" @change="load">
        <el-option label="成功" :value="true" />
        <el-option label="失败" :value="false" />
      </el-select>
      <el-input v-model="module" placeholder="模块" size="small" style="width: 140px" clearable @keyup.enter="load" />
      <el-input v-model="operator" placeholder="操作人" size="small" style="width: 130px" clearable @keyup.enter="load" />
      <el-button :icon="Refresh" size="small" :loading="loading" @click="load">查询</el-button>
    </div>

    <el-alert v-if="state === 'error'" type="error" :closable="false" show-icon style="margin-bottom: 12px" :title="'加载失败：' + errMsg">
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>
    <ReportState v-if="state === 'loading'" state="loading" height="360px" />
    <ReportState v-else-if="logs.length === 0" state="empty" empty-text="无匹配日志" height="360px" />

    <el-card v-else shadow="never">
      <el-table :data="logs" size="small" stripe>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ (row.createTime || '').replace('T', ' ').slice(0, 19) }}</template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="110" />
        <el-table-column prop="module" label="模块" width="110" />
        <el-table-column prop="operation" label="操作" min-width="140" />
        <el-table-column label="请求" min-width="220">
          <template #default="{ row }">
            <el-tag size="small" style="margin-right: 6px">{{ row.requestMethod }}</el-tag>
            <span class="mono">{{ row.requestUri }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.success === 1 ? 'success' : 'danger'" size="small">{{ row.success === 1 ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costMs" label="耗时(ms)" width="90" align="right" />
        <el-table-column prop="ip" label="IP" width="130" />
      </el-table>
      <el-pagination v-model:current-page="pageNo" :page-size="pageSize" :total="total"
                     layout="total, prev, pager, next" style="margin-top: 12px; justify-content: flex-end" @current-change="load" />
    </el-card>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import ReportState from '@/views/report/components/ReportState.vue'
import { getOperationLogs, type OperationLogVO } from '@/api/system'

const logs = ref<OperationLogVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 20
const module = ref('')
const operator = ref('')
const success = ref<boolean | undefined>(undefined)
const state = ref<'loading' | 'error' | 'ready'>('loading')
const errMsg = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  state.value = 'loading'
  errMsg.value = ''
  try {
    const r = await getOperationLogs({
      pageNo: pageNo.value,
      pageSize,
      module: module.value || undefined,
      operator: operator.value || undefined,
      success: success.value,
    })
    logs.value = r.records || []
    total.value = r.total || 0
    state.value = 'ready'
  } catch (e) {
    state.value = 'error'
    errMsg.value = e instanceof Error ? e.message : '未知错误'
  } finally { loading.value = false }
}

onMounted(load)
</script>

<style scoped>
.mono { font-family: Consolas, monospace; font-size: 12px; }
</style>