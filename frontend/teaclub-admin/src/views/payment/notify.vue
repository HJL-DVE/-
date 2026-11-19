<template>
  <div>
    <div class="toolbar">
      <el-input v-model="status" placeholder="通知状态" clearable style="width: 160px" @keyup.enter="search" />
      <el-button type="primary" :icon="Search" @click="search">查询</el-button>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <div class="toolbar-spacer" />
      <span class="text-muted">GET /api/admin/payment/notify-logs · 共 {{ total }} 条</span>
    </div>

    <el-table :data="records" v-loading="loading" border stripe size="small">
      <el-table-column
        v-for="column in columns"
        :key="column"
        :prop="column"
        :label="column"
        min-width="150"
        show-overflow-tooltip
      />
      <template #empty>
        <el-empty description="暂无通知记录" :image-size="70" />
      </template>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="search"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getNotifyLogPage } from '@/api/payment'

const loading = ref(false)
const records = ref<Record<string, unknown>[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const status = ref('')

/** 后端返回 Map<String,Object>，列名由真实返回决定，不臆造字段 */
const columns = computed(() => {
  const first = records.value[0]
  return first ? Object.keys(first) : []
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await getNotifyLogPage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      status: status.value || undefined,
    })
    records.value = page.records || []
    total.value = page.total || 0
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNo.value = 1
  load()
}

onMounted(load)
</script>
