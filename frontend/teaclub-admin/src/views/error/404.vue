<template>
  <el-result icon="warning" title="404" sub-title="页面不存在或未在当前账号的菜单权限内">
    <template #extra>
      <el-button type="primary" @click="$router.replace('/')">返回首页</el-button>
    </template>
  </el-result>
</template>

<script setup lang="ts"></script>
