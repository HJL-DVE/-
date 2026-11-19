const TOKEN_KEY = 'teaclub_token'
const MEMBER_KEY = 'teaclub_member'

export interface StoredMember {
  memberId?: number | string
  memberNo?: string
  nickname?: string
  avatarUrl?: string
  phone?: string
}

export function getToken(): string {
  try {
    return (uni.getStorageSync(TOKEN_KEY) as string) || ''
  } catch (e) {
    return ''
  }
}

export function setToken(token: string): void {
  uni.setStorageSync(TOKEN_KEY, token)
}

export function clearToken(): void {
  try {
    uni.removeStorageSync(TOKEN_KEY)
    uni.removeStorageSync(MEMBER_KEY)
  } catch (e) {
    // ignore
  }
}

export function hasToken(): boolean {
  return getToken().length > 0
}

export function getStoredMember(): StoredMember | null {
  try {
    const raw = uni.getStorageSync(MEMBER_KEY)
    return raw ? (JSON.parse(raw as string) as StoredMember) : null
  } catch (e) {
    return null
  }
}

export function setStoredMember(member: StoredMember): void {
  uni.setStorageSync(MEMBER_KEY, JSON.stringify(member))
}

export const AUTH_EXPIRED_EVENT = 'teaclub:auth-expired'

export function emitAuthExpired(): void {
  uni.$emit(AUTH_EXPIRED_EVENT)
}

export const LOGIN_PAGE = '/pages/user/login'

export function gotoLogin(): void {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1].route : ''
  if (current && ('/' + current).indexOf(LOGIN_PAGE) === 0) return
  uni.reLaunch({ url: LOGIN_PAGE })
}
