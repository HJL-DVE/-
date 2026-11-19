import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import type { LoginResult, MenuNode, UserInfo } from '@/api/types'
import { clearToken, getToken, setToken } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken())
  const userInfo = ref<UserInfo | null>(null)
  const menus = ref<MenuNode[]>([])
  const loaded = ref(false)

  const permissionSet = computed(() => new Set(userInfo.value?.permissions ?? []))

  const displayName = computed(() => userInfo.value?.realName || userInfo.value?.username || '未登录')

  function hasPerm(code?: string | string[]): boolean {
    if (!code) return true
    const list = Array.isArray(code) ? code : [code]
    return list.some((item) => permissionSet.value.has(item))
  }

  async function login(payload: { username: string; password: string }): Promise<LoginResult> {
    const result = await authApi.login(payload)
    token.value = result.accessToken
    setToken(result.accessToken)
    userInfo.value = result.userInfo
    return result
  }

  async function loadProfile(): Promise<UserInfo> {
    const info = await authApi.getMe()
    userInfo.value = info
    return info
  }

  async function loadMenus(): Promise<MenuNode[]> {
    const tree = await authApi.getMenus()
    menus.value = tree || []
    return menus.value
  }

  async function bootstrap(): Promise<void> {
    await Promise.all([loadProfile(), loadMenus()])
    loaded.value = true
  }

  function reset(): void {
    token.value = ''
    userInfo.value = null
    menus.value = []
    loaded.value = false
    clearToken()
  }

  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } catch {
      // 登出接口失败也要清理本地状态
    } finally {
      reset()
    }
  }

  return {
    token,
    userInfo,
    menus,
    loaded,
    permissionSet,
    displayName,
    hasPerm,
    login,
    loadProfile,
    loadMenus,
    bootstrap,
    logout,
    reset,
  }
})
