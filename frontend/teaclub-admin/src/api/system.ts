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

// ===== 角色（阶段九：system/role 页面）=====


export interface RoleSave {
  roleCode: string
  roleName: string
  dataScope?: string
  sort?: number | null
  remark?: string | null
  permissionIds?: number[]
}

export function getRolePage(query?: { pageNo?: number; pageSize?: number; keyword?: string }) {
  return http.get<{ records: RoleVO[]; total: number }>('/api/admin/system/roles', query)
}

export function getRoleDetail(id: number) {
  return http.get<RoleVO>('/api/admin/system/roles/' + id)
}

export function createRole(data: RoleSave) {
  return http.post<number>('/api/admin/system/roles', data)
}

export function updateRole(id: number, data: RoleSave) {
  return http.put<void>('/api/admin/system/roles/' + id, data)
}

export function deleteRole(id: number) {
  return http.del<void>('/api/admin/system/roles/' + id)
}

export function grantRolePermissions(id: number, permissionIds: number[]) {
  return http.put<void>('/api/admin/system/roles/' + id + '/permissions', { permissionIds })
}
// ===== 权限（阶段九：system/permission 页面）=====

export interface PermissionNode {
  id: number
  permCode: string
  permName: string
  permType: string
  parentId: number
  routePath?: string | null
  component?: string | null
  sort?: number | null
  status?: string
  children?: PermissionNode[]
}

export function getPermissionTree() {
  return http.get<PermissionNode[]>('/api/admin/system/permissions/tree')
}

export interface PermissionSave {
  permCode: string
  permName: string
  permType: string
  parentId: number
  routePath?: string | null
  component?: string | null
  sort?: number | null
  status?: string
}

export function createPermission(data: PermissionSave) {
  return http.post<number>('/api/admin/system/permissions', data)
}

export function updatePermission(id: number, data: PermissionSave) {
  return http.put<void>('/api/admin/system/permissions/' + id, data)
}

export function deletePermission(id: number) {
  return http.del<void>('/api/admin/system/permissions/' + id)
}

// ===== 操作日志（阶段九：system/log 页面）=====

export interface OperationLogVO {
  id: number
  operatorName?: string | null
  module?: string | null
  operation?: string | null
  requestUri?: string | null
  requestMethod?: string | null
  responseCode?: number | null
  success?: number | null
  errorMsg?: string | null
  ip?: string | null
  costMs?: number | null
  createTime?: string | null
}

export function getOperationLogs(query: {
  pageNo?: number
  pageSize?: number
  module?: string
  operator?: string
  success?: boolean
}) {
  return http.get<{ records: OperationLogVO[]; total: number }>('/api/admin/system/logs', query)
}