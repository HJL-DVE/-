<template>
  <div class="rs-wrap" :style="{ minHeight: height }">
    <div v-if="state === 'loading'" class="rs-center">
      <el-icon class="rs-spin"><Loading /></el-icon>
      <span class="rs-text">加载中…</span>
    </div>
    <div v-else-if="state === 'error'" class="rs-center">
      <el-icon class="rs-err"><WarningFilled /></el-icon>
      <span class="rs-text">加载失败{{ detail ? '：' + detail : '' }}</span>
      <el-button v-if="retryable" size="small" type="primary" plain @click="$emit('retry')">重试</el-button>
    </div>
    <div v-else class="rs-center">
      <span class="rs-text rs-empty">{{ emptyText }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
/** 报表图表的统一三态：loading / error(可重试) / empty。由父页面根据数据长度切换。 */
import { Loading, WarningFilled } from '@element-plus/icons-vue'

defineProps<{
  state: 'loading' | 'error' | 'empty'
  detail?: string
  emptyText?: string
  height?: string
  retryable?: boolean
}>()
defineEmits<{ retry: [] }>()
</script>

<style scoped>
.rs-wrap { display: flex; align-items: center; justify-content: center; }
.rs-center { display: flex; flex-direction: column; align-items: center; gap: 8px; color: #909399; font-size: 13px; }
.rs-spin { font-size: 26px; animation: spin 1s linear infinite; }
.rs-err { font-size: 26px; color: #f56c6c; }
.rs-empty { color: #c0c4cc; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>