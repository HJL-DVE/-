<template>
  <el-result icon="error" title="403" sub-title="无权限访问该页面，请联系管理员分配权限">
    <template #extra>
      <el-button type="primary" @click="$router.replace('/')">返回首页</el-button>
    </template>
  </el-result>
</template>

<script setup lang="ts"></script>
