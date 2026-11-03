// 后端统一返回 { code, description, detail }，code=200 成功
export interface ApiResult<T = any> {
  code: number
  description: string
  detail: T
}

const TOKEN_KEY = 'zzbbs_token'
export const getToken = () => localStorage.getItem(TOKEN_KEY) || ''
export const setToken = (t: string | null) =>
  t ? localStorage.setItem(TOKEN_KEY, t) : localStorage.removeItem(TOKEN_KEY)

// snake_case -> camelCase，兼容后端 map 返回的字段命名
const toCamel = (s: string) => s.replace(/_([a-z])/g, (_, c) => c.toUpperCase())
export function camelize(v: any): any {
  if (Array.isArray(v)) return v.map(camelize)
  if (v && typeof v === 'object') {
    return Object.fromEntries(Object.entries(v).map(([k, val]) => [toCamel(k), camelize(val)]))
  }
  return v
}

export class ApiError extends Error {}

async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) headers.token = token
  let payload: BodyInit | undefined
  if (body instanceof FormData) payload = body
  else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  const res = await fetch(url, { method, headers, body: payload, credentials: 'include' })
  let data: ApiResult<T>
  try {
    data = await res.json()
  } catch {
    throw new ApiError(`请求失败 (${res.status})`)
  }
  if (data.code !== 200) {
    if (data.code === 201 && /token/.test(data.description || '')) setToken(null)
    throw new ApiError(data.description || '请求失败')
  }
  return camelize(data.detail) as T
}

export const api = {
  get: <T = any>(url: string) => request<T>('GET', url),
  post: <T = any>(url: string, body?: unknown) => request<T>('POST', url, body),
  put: <T = any>(url: string, body?: unknown) => request<T>('PUT', url, body),
  del: <T = any>(url: string) => request<T>('DELETE', url),
}

export interface Page<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages?: number
}
export interface User {
  id: number
  username: string
  avatar?: string
  email?: string
  bio?: string
  website?: string
  score?: number
  inTime?: string
  active?: boolean
}
export interface Tag {
  id: number
  name: string
  description?: string
  icon?: string
  topicCount: number
}
export interface TopicItem {
  id: number
  title: string
  content?: string
  username: string
  avatar?: string
  inTime: string
  commentCount: number
  view: number
  top?: boolean
  good?: boolean
}
export interface CommentNode {
  id: number
  userId: number
  username: string
  avatar?: string
  content: string
  inTime: string
  commentId?: number | null
  upIds?: string
  layer?: number
  children?: Record<string, CommentNode[]>
}

export const avatarUrl = (a?: string) => (a ? a : '')
export const fmtTime = (s?: string | number) => {
  if (!s) return ''
  const d = new Date(s)
  return isNaN(d.getTime()) ? String(s) : d.toLocaleString()
}
