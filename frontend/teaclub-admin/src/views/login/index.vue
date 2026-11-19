<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-head">
        <div class="login-logo">TC</div>
        <h1>TeaClub 管理后台</h1>
        <p class="text-muted">第六阶段 · 统一经网关 http://127.0.0.1:9000</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" size="large" placeholder="请输入账号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            show-password
            placeholder="请输入密码"
            :prefix-icon="Lock"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="submit">
          登录
        </el-button>
      </el-form>

      <div class="login-foot text-muted">
        登录成功后由 GET /api/admin/auth/menus 下发动态菜单
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { registerDynamicRoutes } from '@/router'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login({ username: form.username, password: form.password })
    await userStore.loadMenus()
    userStore.loaded = true
    registerDynamicRoutes(router, userStore.menus)
    await appStore.loadStores().catch(() => undefined)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
    ElMessage.success('欢迎回来，' + userStore.displayName)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2d3d 0%, #2c4a63 55%, #3d7a5a 100%);
}

.login-card {
  width: 400px;
  background: #fff;
  border-radius: 12px;
  padding: 32px 32px 24px;
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.22);
}

.login-head {
  text-align: center;
  margin-bottom: 22px;
}

.login-logo {
  width: 52px;
  height: 52px;
  margin: 0 auto 12px;
  border-radius: 14px;
  background: #67c23a;
  color: #fff;
  font-weight: 700;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-head h1 {
  font-size: 20px;
  margin: 0 0 6px;
}

.login-btn {
  width: 100%;
  margin-top: 6px;
}

.login-foot {
  margin-top: 16px;
  text-align: center;
}
</style>
