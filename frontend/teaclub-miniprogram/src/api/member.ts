import { http } from '@/utils/request'
import type { MemberLevel, MemberProfile } from './types'

/** GET /api/member/profile */
export function getProfile() {
  return http.get<MemberProfile>('/api/member/profile')
}

/** PUT /api/member/profile —— 仅 nickname / avatarUrl / gender */
export function updateProfile(payload: { nickname?: string; birthday?: string }) {
  return http.put<MemberProfile>('/api/member/profile', payload)
}

/** GET /api/member/levels */
export function getLevels() {
  return http.get<MemberLevel[]>('/api/member/levels')
}
