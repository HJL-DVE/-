import { http } from './request'
import type { LoginResult, MenuNode, UserInfo } from './types'

export function login(data: { username: string; password: string }): Promise<LoginResult> {
  return http.post<LoginResult>('/api/admin/auth/login', data)
}

export function getMe(): Promise<UserInfo> {
  return http.get<UserInfo>('/api/admin/auth/me')
}

export function getMenus(): Promise<MenuNode[]> {
  return http.get<MenuNode[]>('/api/admin/auth/menus')
}

export function changePassword(data: { oldPassword: string; newPassword: string }): Promise<void> {
  return http.put<void>('/api/admin/auth/password', data)
}

export function logout(): Promise<void> {
  return http.post<void>('/api/admin/auth/logout')
}
