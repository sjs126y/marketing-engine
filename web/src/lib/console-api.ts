// 运营台接口封装：统一带上运营 token，统一处理错误。
// 浏览器端运行，后端地址来自 next.config.js 注入的 API_HOST_URL。

const API_HOST = process.env.API_HOST_URL || 'http://127.0.0.1:8092'

const TOKEN_KEY = 'me.console.token'
const USER_KEY = 'me.console.user'

export type ConsoleSession = {
  token: string
  username: string
  role: string
}

export function getSession(): ConsoleSession | null {
  if (typeof window === 'undefined') return null
  const token = window.localStorage.getItem(TOKEN_KEY)
  const username = window.localStorage.getItem(USER_KEY)
  if (!token || !username) return null
  return { token, username, role: '' }
}

export function saveSession(token: string, username: string, role: string) {
  window.localStorage.setItem(TOKEN_KEY, token)
  window.localStorage.setItem(USER_KEY, username)
  window.localStorage.setItem('me.console.role', role)
}

export function clearSession() {
  window.localStorage.removeItem(TOKEN_KEY)
  window.localStorage.removeItem(USER_KEY)
  window.localStorage.removeItem('me.console.role')
}

function authHeaders(): Record<string, string> {
  const session = getSession()
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (session) headers.Authorization = `Bearer ${session.token}`
  return headers
}

async function parse(resp: Response) {
  const text = await resp.text()
  let body: any = null
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    body = { code: String(resp.status), info: text }
  }
  if (!resp.ok) {
    const message = body?.info || body?.message || `HTTP ${resp.status}`
    const error: any = new Error(message)
    error.status = resp.status
    error.body = body
    throw error
  }
  return body
}

/** 运营登录。鉴权关闭时后端也会签发 token，前端逻辑保持一致。 */
export async function operatorLogin(username: string, password: string) {
  const resp = await fetch(`${API_HOST}/console/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  return parse(resp)
}

/** 只校验，不改状态。 */
export async function validateStrategy(strategyId: string) {
  const resp = await fetch(`${API_HOST}/console/v1/strategy/${strategyId}/validate`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return parse(resp)
}

/** 校验通过后发布：清缓存 + 重新预热概率表。 */
export async function publishStrategy(strategyId: string) {
  const resp = await fetch(`${API_HOST}/console/v1/strategy/${strategyId}/publish`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return parse(resp)
}

/** 看板总览。 */
export async function dashboardOverview(strategyId?: string) {
  const query = strategyId ? `?strategyId=${encodeURIComponent(strategyId)}` : ''
  const resp = await fetch(`${API_HOST}/console/v1/dashboard/overview${query}`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return parse(resp)
}

/** 最近的中奖记录（运营看板）。 */
export async function recentAwards(limit = 20) {
  const resp = await fetch(`${API_HOST}/console/v1/awards/recent?limit=${limit}`, {
    method: 'GET',
    headers: authHeaders(),
  })
  return parse(resp)
}

/** 上游充值（模拟上游业务系统调用，用于看板上的"上游调用量"演示）。 */
export async function rechargeQuota(userId: string, sku: number, outBusinessNo: string) {
  const resp = await fetch(`${API_HOST}/open/v1/quota/recharge`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId, sku, outBusinessNo }),
  })
  return parse(resp)
}

export { API_HOST }
