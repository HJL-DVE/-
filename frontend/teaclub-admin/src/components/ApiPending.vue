<template>
  <el-alert type="warning" :closable="false" show-icon class="pending-box">
    <template #title>
      <strong>{{ title }}</strong>
    </template>
    <div>
      接口 <code>{{ url }}</code> 当前返回 <code>code={{ code }}</code>
      <span v-if="message">（{{ message }}）</span>，说明后端尚未落地该接口。
    </div>
    <div class="text-muted" style="margin-top: 4px">
      契约标注为「新增」，由后端并行补齐。本页面不会用静态/mock 数据替代真实返回。
      <span v-if="traceId">traceId={{ traceId }}</span>
    </div>
  </el-alert>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    url?: string
    code?: number | string
    message?: string
    traceId?: string
  }>(),
  {
    title: '接口未就绪',
    url: '-',
    code: '50000',
    message: '',
    traceId: '',
  },
)
</script>
