import { http } from './request'
import type { MemberLevelVO, MemberQuery, MemberStatsVO, MemberVO, PageResult } from './types'

export function getMemberPage(query: Partial<MemberQuery>): Promise<PageResult<MemberVO>> {
  return http.get<PageResult<MemberVO>>('/api/admin/member/members', query)
}

/** 后台新增会员（手工建档） */
export function createMember(data: {
  phone: string
  nickname?: string
  gender?: string
  birthday?: string
  levelId?: number
  registerStoreId?: number
  password?: string
}): Promise<number> {
  return http.post<number>('/api/admin/member/members', data)
}

export function getMemberDetail(id: number): Promise<MemberVO> {
  return http.get<MemberVO>('/api/admin/member/members/' + id)
}

export function getMemberByPhone(phone: string): Promise<MemberVO> {
  return http.get<MemberVO>('/api/admin/member/members/by-phone', { phone })
}

/** 契约标注为「新增」：后端未落地时返回 code=50000，调用方需按未就绪处理 */
export function getMemberByCode(code: string, silent = false): Promise<MemberVO> {
  return http.get<MemberVO>('/api/admin/member/members/by-code', { code }, { silent })
}

export function updateMember(
  id: number,
  data: { nickname?: string; avatarUrl?: string; gender?: string; birthday?: string; levelId?: number; remark?: string },
): Promise<void> {
  return http.put<void>('/api/admin/member/members/' + id, data)
}

export function updateMemberStatus(id: number, status: string, reason?: string): Promise<void> {
  return http.patch<void>('/api/admin/member/members/' + id + '/status', { status, reason })
}

export function deleteMember(id: number): Promise<void> {
  return http.del<void>('/api/admin/member/members/' + id)
}

export function getMemberLevels(): Promise<MemberLevelVO[]> {
  return http.get<MemberLevelVO[]>('/api/admin/member/levels')
}

/** 契约标注为「新增」（仪表盘会员统计） */
export function getMemberStats(silent = false): Promise<MemberStatsVO> {
  return http.get<MemberStatsVO>('/api/admin/member/members/stats', undefined, { silent })
}
