import { getToken, clearToken, emitAuthExpired, gotoLogin } from './auth'

/** 唯一网关地址：所有业务请求必须经网关，禁止硬编码各微服务端口 */
export const BASE_URL = 'http://127.0.0.1:9000'

/** 契约 {@code code===0} 为成功 */
export const CODE_SUCCESS = 0

/**
 * 需要「清 token + 跳登录」的码：
 * 401 / 4011 来自契约；
 * 10101 为网关 JwtAuthGlobalFilter 实测返回的「未登录或登录已失效」（docs/33 未列出，实测补入）。
 */
export const UNAUTHORIZED_CODES: number[] = [401, 4011, 10101]

export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId?: string
  timestamp?: string
}

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'

export interface RequestOptions {
  url: string
  method?: HttpMethod
  data?: Record<string, unknown> | unknown[]
  /** 是否携带 Authorization，默认 true；白名单接口（登录、门店列表）传 false */
  auth?: boolean
  /** 失败时是否静默（不 toast），默认 false */
  silent?: boolean
  timeout?: number
}

export class ApiError extends Error {
  code: number
  traceId?: string

  constructor(code: number, message: string, traceId?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.traceId = traceId
  }
}

let authRedirecting = false

export function toast(title: string): void {
  uni.showToast({ title, icon: 'none', duration: 2200 })
}

function handleUnauthorized(message?: string): void {
  clearToken()
  emitAuthExpired()
  if (authRedirecting) return
  authRedirecting = true
  toast(message && message !== '' ? message : '登录已失效，请重新登录')
  setTimeout(() => {
    authRedirecting = false
    gotoLogin()
  }, 600)
}

/**
 * 统一请求封装。
 * 成功：resolve 信封里的 data；失败：reject ApiError，并已按契约做过 toast / 跳登录。
 */
export function request<T>(options: RequestOptions): Promise<T> {
  const { url, method = 'GET', data, auth = true, silent = false, timeout = 15000 } = options

  return new Promise<T>((resolve, reject) => {
    const header: Record<string, string> = { 'Content-Type': 'application/json' }
    if (auth) {
      const token = getToken()
      if (token) header.Authorization = 'Bearer ' + token
    }

    uni.request({
      url: BASE_URL + url,
      method,
      data: data as Record<string, unknown>,
      header,
      timeout,
      success: (res) => {
        const status = res.statusCode
        const body = res.data as ApiEnvelope<T> | undefined

        // 网关对未认证请求直接返回 HTTP 401 + {code:401}
        if (status === 401) {
          handleUnauthorized(body && body.message)
          reject(new ApiError(401, (body && body.message) || '未登录或登录已失效'))
          return
        }
        if (!body || typeof body.code !== 'number') {
          const msg = '接口返回格式异常（HTTP ' + status + '）'
          if (!silent) toast(msg)
          reject(new ApiError(status, msg))
          return
        }
        if (body.code === CODE_SUCCESS) {
          resolve(body.data)
          return
        }
        if (UNAUTHORIZED_CODES.indexOf(body.code) >= 0) {
          handleUnauthorized(body.message)
          reject(new ApiError(body.code, body.message || '未登录或登录已失效', body.traceId))
          return
        }
        if (!silent) toast(body.message || '请求失败（code=' + body.code + '）')
        reject(new ApiError(body.code, body.message || '请求失败', body.traceId))
      },
      fail: (err) => {
        const msg = '网络异常：' + (err && err.errMsg ? err.errMsg : '请检查网关是否可访问')
        if (!silent) toast(msg)
        reject(new ApiError(-1, msg))
      },
    })
  })
}

export const http = {
  get<T>(url: string, params?: Record<string, unknown>, options?: Partial<RequestOptions>) {
    const query = buildQuery(params)
    return request<T>({ url: query ? url + '?' + query : url, method: 'GET', ...options })
  },
  post<T>(url: string, data?: Record<string, unknown> | unknown[], options?: Partial<RequestOptions>) {
    return request<T>({ url, method: 'POST', data, ...options })
  },
  put<T>(url: string, data?: Record<string, unknown>, options?: Partial<RequestOptions>) {
    return request<T>({ url, method: 'PUT', data, ...options })
  },
  del<T>(url: string, data?: Record<string, unknown>, options?: Partial<RequestOptions>) {
    return request<T>({ url, method: 'DELETE', data, ...options })
  },
}

export function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return ''
  const parts: string[] = []
  Object.keys(params).forEach((key) => {
    const value = params[key]
    if (value === undefined || value === null || value === '') return
    parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(String(value)))
  })
  return parts.join('&')
}
