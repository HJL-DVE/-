<template>
  <el-container class="layout">
    <el-aside :width="appStore.collapsed ? '64px' : '224px'" class="layout-aside">
      <div class="logo">
        <span class="logo-mark">TC</span>
        <span v-show="!appStore.collapsed" class="logo-text">TeaClub 管理后台</span>
      </div>
      <el-scrollbar>
        <el-menu
          :default-active="activeMenu"
          :collapse="appStore.collapsed"
          :collapse-transition="false"
          router
          background-color="#1f2d3d"
          text-color="#c0c4cc"
          active-text-color="#ffffff"
        >
          <template v-for="node in userStore.menus" :key="node.id">
            <el-sub-menu v-if="hasChildren(node)" :index="node.routePath || String(node.id)">
              <template #title>
                <el-icon><component :is="iconOf(node.icon)" /></el-icon>
                <span>{{ node.permName }}</span>
              </template>
              <el-menu-item
                v-for="child in node.children"
                :key="child.id"
                :index="child.routePath"
              >
                <el-icon><component :is="iconOf(child.icon)" /></el-icon>
                <template #title>{{ child.permName }}</template>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="node.routePath">
              <el-icon><component :is="iconOf(node.icon)" /></el-icon>
              <template #title>{{ node.permName }}</template>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-button text @click="appStore.toggleCollapsed()">
            <el-icon><Fold v-if="!appStore.collapsed" /><Expand v-else /></el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title">{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <span class="store-label">门店</span>
          <el-select
            :model-value="appStore.currentStoreId"
            class="store-select"
            placeholder="选择门店"
            filterable
            @update:model-value="onChangeStore"
          >
            <el-option
              v-for="store in appStore.stores"
              :key="store.id"
              :label="store.storeName"
              :value="store.id"
            />
          </el-select>

          <el-button type="primary" plain size="small" @click="router.push('/cashier')">
            <el-icon><ShoppingCartFull /></el-icon>
            <span style="margin-left: 4px">收银工作台</span>
          </el-button>

          <el-dropdown trigger="click" @command="onCommand">
            <span class="user-trigger">
              <el-avatar :size="26">{{ userStore.displayName.slice(0, 1) }}</el-avatar>
              <span class="user-name">{{ userStore.displayName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  角色：{{ (userStore.userInfo?.roles || []).join(' / ') || '-' }}
                </el-dropdown-item>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <keep-alive :max="6">
            <component :is="Component" :key="route.path" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>

    <el-dialog v-model="passwordVisible" title="修改密码" width="420px">
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="96px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSubmitting" @click="submitPassword">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { changePassword } from '@/api/auth'
import type { MenuNode } from '@/api/types'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const iconNames = new Set(Object.keys(ElementPlusIcons))

function iconOf(name?: string | null): string {
  return name && iconNames.has(name) ? name : 'Menu'
}

function hasChildren(node: MenuNode): boolean {
  return Array.isArray(node.children) && node.children.length > 0
}

const activeMenu = computed(() => route.path)

function onChangeStore(id: number): void {
  appStore.setStore(id)
  ElMessage.success('已切换到门店：' + (appStore.stores.find((s) => s.id === id)?.storeName ?? id))
}

const passwordVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({ oldPassword: '', newPassword: '' })
const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 32, message: '长度 8-32 位', trigger: 'blur' },
  ],
}

function onCommand(command: string): void {
  if (command === 'password') {
    passwordVisible.value = true
    return
  }
  if (command === 'logout') {
    ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
      .then(async () => {
        await userStore.logout()
        router.replace('/login')
      })
      .catch(() => undefined)
  }
}

async function submitPassword(): Promise<void> {
  if (!passwordFormRef.value) return
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return
  passwordSubmitting.value = true
  try {
    await changePassword({ ...passwordForm })
    ElMessage.success('密码修改成功，请重新登录')
    passwordVisible.value = false
    await userStore.logout()
    router.replace('/login')
  } finally {
    passwordSubmitting.value = false
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}

.layout-aside {
  background: #1f2d3d;
  transition: width 0.2s;
  overflow: hidden;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  white-space: nowrap;
}

.logo-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: #67c23a;
  font-weight: 700;
  font-size: 13px;
}

.logo-text {
  font-size: 15px;
  font-weight: 600;
}

.layout-aside :deep(.el-menu) {
  border-right: none;
}

.layout-header {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  gap: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.store-label {
  color: #909399;
  font-size: 13px;
}

.store-select {
  width: 200px;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #303133;
}

.user-name {
  font-size: 13px;
}

.layout-main {
  padding: 0;
  overflow-y: auto;
}

:deep(.el-breadcrumb) {
  font-size: 13px;
}
</style>
