import { http } from './request'

export interface MemberLevelVO {
  id: number
  levelCode: string
  levelName: string
  levelNo: number
  upgradeType: string
  upgradeThreshold: number
  discountRate: number
  pointRate: number
  iconUrl?: string | null
  benefits?: string | null
  status: string
  createTime?: string | null
  memberCount?: number
}

export interface MemberLevelSave {
  levelCode: string
  levelName: string
  levelNo: number
  upgradeType: string
  upgradeThreshold: number
  discountRate: number
  pointRate: number
  iconUrl?: string | null
  benefits?: string | null
  status?: string
}

/** GET /api/admin/member/levels（member:level:list） */
export function getLevels() {
  return http.get<MemberLevelVO[]>('/api/admin/member/levels')
}

export function getLevel(id: number) {
  return http.get<MemberLevelVO>('/api/admin/member/levels/' + id)
}

export function createLevel(data: MemberLevelSave) {
  return http.post<number>('/api/admin/member/levels', data)
}

export function updateLevel(id: number, data: MemberLevelSave) {
  return http.put<void>('/api/admin/member/levels/' + id, data)
}

export function deleteLevel(id: number) {
  return http.del<void>('/api/admin/member/levels/' + id)
}

// ===== 营销权益（marketing-service，同页维护）=====

export interface LevelBenefitVO {
  id?: number
  levelId: number
  benefitCode: string
  benefitName: string
  benefitValue?: string | null
  status?: string
}

export function getBenefitsByLevel(levelId: number) {
  return http.get<LevelBenefitVO[]>('/api/admin/marketing/level-benefits/level/' + levelId)
}

export function saveBenefit(data: LevelBenefitVO) {
  return http.post<number>('/api/admin/marketing/level-benefits', data)
}

export function deleteBenefit(id: number) {
  return http.del<void>('/api/admin/marketing/level-benefits/' + id)
}