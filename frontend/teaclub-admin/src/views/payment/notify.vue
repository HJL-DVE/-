<template>
  <div>
    <div class="toolbar">
      <el-input v-model="status" placeholder="处理状态（如 PROCESSED）" clearable style="width: 200px" @keyup.enter="search" />
      <el-button type="primary" :icon="Search" @click="search">查询</el-button>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <div class="toolbar-spacer" />
      <span class="text-muted">共 {{ total }} 条通知记录</span>
    </div>

    <el-table :data="records" v-loading="loading" border stripe size="small">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="渠道" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.provider === 'MOCK' ? 'warning' : 'success'">
            {{ PROVIDER_LABEL[row.provider] || row.provider }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="notifyId" label="通知单号" min-width="220" show-overflow-tooltip />
      <el-table-column label="通知类型" width="110">
        <template #default="{ row }">{{ NOTIFY_TYPE_LABEL[row.notifyType] || row.notifyType }}</template>
      </el-table-column>
      <el-table-column label="事件" width="130">
        <template #default="{ row }">{{ EVENT_TYPE_LABEL[row.eventType] || row.eventType }}</template>
      </el-table-column>
      <el-table-column prop="outTradeNo" label="业务单号" min-width="200" show-overflow-tooltip />
      <el-table-column label="验签结果" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="String(row.verifyResult) === '1' ? 'success' : 'danger'">
            {{ VERIFY_RESULT_LABEL[String(row.verifyResult)] || row.verifyResult }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="处理状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="row.processStatus === 'PROCESSED' ? 'success' : 'warning'">
            {{ PROCESS_STATUS_LABEL[row.processStatus] || row.processStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="processMsg" label="处理结果" min-width="180" show-overflow-tooltip />
      <el-table-column label="通知时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
      </el-table-column>
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
import { onMounted, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getNotifyLogPage } from '@/api/payment'
import {
  NOTIFY_TYPE_LABEL,
  EVENT_TYPE_LABEL,
  PROCESS_STATUS_LABEL,
  VERIFY_RESULT_LABEL,
  formatDateTime,
} from '@/utils/format'

const PROVIDER_LABEL: Record<string, string> = { MOCK: '模拟支付', WECHAT: '微信支付', WECHAT_V3: '微信支付' }

const loading = ref(false)
const records = ref<Record<string, unknown>[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const status = ref('')

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

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.toolbar-spacer {
  flex: 1;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
