import axios, { type AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { clearToken, getToken } from '@/utils/auth'

export interface ApiEnvelope<T = unknown> {
  code: number
  message: string
  data: T
  traceId?: string
  timestamp?: string
}

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 静默模式：不弹出全局错误提示，由调用方自行渲染（如「接口未就绪」占位） */
    silent?: boolean
  }
}

export class ApiError extends Error {
  code: number
  traceId?: string
  httpStatus?: number

  constructor(code: number, message: string, traceId?: string, httpStatus?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.traceId = traceId
    this.httpStatus = httpStatus
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''
export const REQUEST_TIMEOUT = Number(import.meta.env.VITE_REQUEST_TIMEOUT) || 10000

function createTraceId(): string {
  const c = globalThis.crypto
  if (c && typeof c.randomUUID === 'function') return c.randomUUID().replace(/-/g, '')
  return Date.now().toString(16) + Math.random().toString(16).slice(2, 10)
}

let redirecting = false

function redirectToLogin(): void {
  clearToken()
  const { pathname, search, hash } = window.location
  if (pathname.startsWith('/login')) return
  if (redirecting) return
  redirecting = true
  window.location.assign('/login?redirect=' + encodeURIComponent(pathname + search + hash))
}

function handleBizError(env: ApiEnvelope, silent: boolean, httpStatus?: number): void {
  switch (env.code) {
    case 401:
      redirectToLogin()
      return
    case 4011:
      clearToken()
      redirectToLogin()
      return
    case 403:
      if (!silent) ElMessage.error(env.message || '无权限访问该资源')
      return
    default:
      if (!silent) ElMessage.error(env.message || '请求失败')
  }
}

const service = axios.create({
  baseURL: API_BASE_URL,
  timeout: REQUEST_TIMEOUT,
  headers: { 'Content-Type': 'application/json;charset=UTF-8' },
})

service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) config.headers.set('Authorization', 'Bearer ' + token)
    config.headers.set('X-Trace-Id', createTraceId())
    return config
  },
  (error) => Promise.reject(error),
)

service.interceptors.response.use(
  (response) => {
    const body = response.data
    // 非包装响应（文件流等）原样返回
    if (body === null || typeof body !== 'object' || !('code' in (body as Record<string, unknown>))) {
      return body
    }
    const env = body as ApiEnvelope
    if (env.code === 0) return env.data
    handleBizError(env, Boolean(response.config.silent), response.status)
    return Promise.reject(new ApiError(env.code, env.message || '请求失败', env.traceId, response.status))
  },
  (error: AxiosError<ApiEnvelope>) => {
    const silent = Boolean(error.config?.silent)

    if (error.code === 'ECONNABORTED' || /timeout/i.test(error.message || '')) {
      if (!silent) ElMessage.error('请求超时（' + REQUEST_TIMEOUT + 'ms），请检查网关与服务状态')
      return Promise.reject(new ApiError(-1, '请求超时'))
    }

    if (!error.response) {
      if (!silent) ElMessage.error('网络异常：无法连接网关 ' + (API_BASE_URL || '(相对路径)'))
      return Promise.reject(new ApiError(-2, '网络异常'))
    }

    const status = error.response.status
    const body = error.response.data
    const code = body && typeof body.code === 'number' ? body.code : status

    if (status === 401 || code === 401) {
      redirectToLogin()
      return Promise.reject(new ApiError(401, '登录已失效，请重新登录', body?.traceId, status))
    }
    if (code === 4011) {
      clearToken()
      redirectToLogin()
      return Promise.reject(new ApiError(4011, '登录已过期，请重新登录', body?.traceId, status))
    }
    if (status === 403 || code === 403) {
      if (!silent) ElMessage.error(body?.message || '无权限访问该资源')
      return Promise.reject(new ApiError(403, body?.message || '无权限', body?.traceId, status))
    }

    const env: ApiEnvelope = {
      code,
      message: body?.message || error.message || '请求失败',
      data: null,
      traceId: body?.traceId,
    }
    handleBizError(env, silent, status)
    return Promise.reject(new ApiError(code, env.message, env.traceId, status))
  },
)

type Params = Record<string, unknown>

export const http = {
  get<T>(url: string, params?: Params, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, { params, ...config }) as unknown as Promise<T>
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config) as unknown as Promise<T>
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config) as unknown as Promise<T>
  },
  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.patch(url, data, config) as unknown as Promise<T>
  },
  del<T>(url: string, params?: Params, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, { params, ...config }) as unknown as Promise<T>
  },
  /** multipart/form-data 文件上传封装（表单 + 文件同传） */
  upload<T>(
    url: string,
    file: Blob | File,
    fields?: Record<string, string | number | undefined>,
    config?: AxiosRequestConfig,
  ): Promise<T> {
    const form = new FormData()
    if (fields) {
      Object.entries(fields).forEach(([key, value]) => {
        if (value !== undefined && value !== null) form.append(key, String(value))
      })
    }
    form.append('file', file)
    return service.post(url, form, {
      ...config,
      headers: { 'Content-Type': 'multipart/form-data', ...(config?.headers || {}) },
    }) as unknown as Promise<T>
  },
}

export default service
