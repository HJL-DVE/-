import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import type { MemberProfile } from '@/api/types'
import {
  AUTH_EXPIRED_EVENT,
  clearToken,
  getStoredMember,
  getToken,
  setStoredMember,
  setToken,
} from '@/utils/auth'

function wxLoginCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (res) => {
        if (res.code) resolve(res.code)
        else reject(new Error('wx.login 未返回 code'))
      },
      fail: (err) => reject(new Error(err.errMsg || 'wx.login 失败')),
    })
  })
}

export const useAuthStore = defineStore('auth', () => {
  const stored = getStoredMember()
  const token = ref<string>(getToken())
  const memberId = ref<number | undefined>(stored && stored.memberId ? Number(stored.memberId) : undefined)
  const memberNo = ref<string>(stored && stored.memberNo ? stored.memberNo : '')
  const nickname = ref<string>(stored && stored.nickname ? stored.nickname : '')
  const avatarUrl = ref<string>(stored && stored.avatarUrl ? stored.avatarUrl : '')
  const profile = ref<MemberProfile | null>(null)
  const isLoggedIn = computed(() => token.value.length > 0)

  function applyLogin(res: {
    token: string
    memberId?: number
    memberNo?: string | null
    nickname?: string | null
    avatarUrl?: string | null
  }) {
    token.value = res.token
    memberId.value = res.memberId
    memberNo.value = res.memberNo || ''
    nickname.value = res.nickname || ''
    avatarUrl.value = res.avatarUrl || ''
    setToken(res.token)
    setStoredMember({
      memberId: res.memberId,
      memberNo: res.memberNo || '',
      nickname: res.nickname || '',
      avatarUrl: res.avatarUrl || '',
    })
  }

  /** 微信一键登录：code 由 wx.login 获取，token 完全由后端签发 */
  async function loginByWechat(extra?: { nickname?: string; avatarUrl?: string }) {
    const code = await wxLoginCode()
    const res = await authApi.wxLogin({ code, nickname: extra?.nickname, avatarUrl: extra?.avatarUrl })
    applyLogin(res)
    return res
  }

  async function loginByPassword(phone: string, password: string) {
    const res = await authApi.memberLogin({ phone, password })
    applyLogin(res)
    return res
  }

  async function register(phone: string, password: string, nick?: string) {
    const res = await authApi.memberRegister({ phone, password, nickname: nick })
    applyLogin(res)
    return res
  }

  async function fetchProfile() {
    if (!isLoggedIn.value) return null
    const data = await authApi.getMemberMe()
    profile.value = data
    if (data) {
      nickname.value = data.nickname || nickname.value
      avatarUrl.value = data.avatarUrl || avatarUrl.value
      memberNo.value = data.memberNo || memberNo.value
      if (data.id) memberId.value = data.id
    }
    return data
  }

  function logout() {
    token.value = ''
    memberId.value = undefined
    memberNo.value = ''
    nickname.value = ''
    avatarUrl.value = ''
    profile.value = null
    clearToken()
  }

  uni.$on(AUTH_EXPIRED_EVENT, () => {
    token.value = ''
    profile.value = null
  })

  return {
    token,
    memberId,
    memberNo,
    nickname,
    avatarUrl,
    profile,
    isLoggedIn,
    loginByWechat,
    loginByPassword,
    register,
    fetchProfile,
    logout,
  }
})
