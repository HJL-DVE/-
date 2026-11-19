import { http } from '@/utils/request'
import type { StoreItem, WxLoginResult, MemberProfile } from './types'

/** POST /api/auth/wx-login —— code 由 wx.login 获取，后端 Mock code2session，前端不伪造 token */
export function wxLogin(payload: { code: string; nickname?: string; avatarUrl?: string; phone?: string }) {
  return http.post<WxLoginResult>('/api/auth/wx-login', payload, { auth: false })
}

/** POST /api/auth/member-login（白名单） */
export function memberLogin(payload: { phone: string; password: string }) {
  return http.post<WxLoginResult>('/api/auth/member-login', payload, { auth: false })
}

/** POST /api/auth/member-register（白名单） */
export function memberRegister(payload: { phone: string; password: string; nickname?: string }) {
  return http.post<WxLoginResult>('/api/auth/member-register', payload, { auth: false })
}

/** GET /api/auth/member/me —— 需会员端 Token */
export function getMemberMe() {
  return http.get<MemberProfile>('/api/auth/member/me')
}

/** GET /api/auth/stores（白名单） */
export function getStores() {
  return http.get<StoreItem[]>('/api/auth/stores', undefined, { auth: false })
}

/** GET /api/auth/stores/{id}（白名单） */
export function getStore(id: number | string) {
  return http.get<StoreItem>('/api/auth/stores/' + id, undefined, { auth: false })
}
