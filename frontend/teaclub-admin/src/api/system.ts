import { http } from './request'
import type { PageResult, RoleVO, UserQuery, UserVO } from './types'

export interface UserCreatePayload {
  username: string
  password: string
  realName?: string
  phone?: string
  email?: string
  userType?: string
  roleIds?: number[]
  storeIds?: number[]
  defaultStoreId?: number
}

export interface UserUpdatePayload {
  realName?: string
  phone?: string
  email?: string
  avatarUrl?: string
  roleIds?: number[]
  storeIds?: number[]
  defaultStoreId?: number
}

export function getUserPage(query: Partial<UserQuery>): Promise<PageResult<UserVO>> {
  return http.get<PageResult<UserVO>>('/api/admin/system/users', query)
}

export function createUser(data: UserCreatePayload): Promise<number> {
  return http.post<number>('/api/admin/system/users', data)
}

export function updateUser(id: number, data: UserUpdatePayload): Promise<void> {
  return http.put<void>('/api/admin/system/users/' + id, data)
}

export function deleteUser(id: number): Promise<void> {
  return http.del<void>('/api/admin/system/users/' + id)
}

export function resetUserPassword(id: number, newPassword: string): Promise<void> {
  return http.post<void>('/api/admin/system/users/' + id + '/reset-password', { newPassword })
}

export function updateUserStatus(id: number, status: string, reason?: string): Promise<void> {
  return http.patch<void>('/api/admin/system/users/' + id + '/status', { status, reason })
}

export function grantUserStores(id: number, storeIds: number[], defaultStoreId?: number): Promise<void> {
  return http.put<void>('/api/admin/system/users/' + id + '/stores', { storeIds, defaultStoreId })
}

export function getUserRoleIds(id: number): Promise<number[]> {
  return http.get<number[]>('/api/admin/system/users/' + id + '/roles')
}

export function getUserStoreIds(id: number): Promise<number[]> {
  return http.get<number[]>('/api/admin/system/users/' + id + '/stores')
}

export function getAllRoles(): Promise<RoleVO[]> {
  return http.get<RoleVO[]>('/api/admin/system/roles/all')
}
