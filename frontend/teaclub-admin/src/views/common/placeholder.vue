<template>
  <div class="page">
    <div class="page-card">
      <el-result icon="info" :title="title">
        <template #sub-title>
          <p>
            该菜单来自后端 <code>GET /api/admin/auth/menus</code>（{{ permCode }}），
            但对应页面不在 <strong>docs/33 第四节 13 页清单</strong>内，本阶段未实现。
          </p>
          <p class="text-muted">如需实现，请先补充契约，不要由前端臆造接口。</p>
        </template>
        <template #extra>
          <el-button @click="$router.push('/dashboard')">返回仪表盘</el-button>
        </template>
      </el-result>
      <el-descriptions :column="2" border size="small" style="margin-top: 8px">
        <el-descriptions-item label="路由">{{ route.path }}</el-descriptions-item>
        <el-descriptions-item label="组件标识">{{ component }}</el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => (route.meta.title as string) || '未实现页面')
const permCode = computed(() => (route.meta.permCode as string) || '-')
const component = computed(() => (route.meta.title as string) ? '—' : '—')
</script>
