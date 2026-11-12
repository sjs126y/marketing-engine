'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

const API_HOST = process.env.API_HOST_URL || 'http://127.0.0.1:8092'

type Award = {
  awardId: number
  awardTitle: string
  sort: number
  awardCountSurplus?: number
  isAwardUnlock?: boolean
}

type Account = {
  totalCount: number
  totalCountSurplus: number
  dayCount: number
  dayCountSurplus: number
}

type DrawResult = {
  awardId: number
  awardTitle: string
}

const GRID_ORDER = [0, 1, 2, 5, 8, 7, 6, 3] // 顺时针 8 格，中心是按钮

/** 把 HTTP 状态翻成人话，避免出现 "Unexpected end of JSON input" 这种提示。 */
function httpError(status: number, action: string): string {
  if (status === 401 || status === 403) {
    return `${action}被拒绝（HTTP ${status}）：抽奖页需要上游换发的 token，请从上游 App 点「去抽奖」进入`
  }
  return `${action}失败（HTTP ${status}）`
}

/** 解析 JWT 的 sub（只解码、不验签；验签在中台做）。 */
function subOf(token: string): string | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const json = decodeURIComponent(
      atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join(''),
    )
    return JSON.parse(json).sub ?? null
  } catch {
    return null
  }
}

export default function PlayPage() {
  const [userId, setUserId] = useState('')
  const [activityId, setActivityId] = useState('')
  const [token, setToken] = useState('')
  const [awards, setAwards] = useState<Award[]>([])
  const [account, setAccount] = useState<Account | null>(null)
  const [result, setResult] = useState<DrawResult | null>(null)
  const [rolling, setRolling] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)
  const [error, setError] = useState<string | null>(null)
  const [ready, setReady] = useState(false)
  const timer = useRef<number | null>(null)
  const rollingRef = useRef(false)

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const urlToken = params.get('token') || ''
    setToken(urlToken)
    // 身份以 token 为准：从 JWT 的 sub 解出 userId，仅用于展示
    setUserId(subOf(urlToken) || params.get('userId') || 'demo-user')
    setActivityId(params.get('activityId') || '100301')
  }, [])

  const authHeaders = useCallback((): Record<string, string> => {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (token) headers.Authorization = `Bearer ${token}`
    return headers
  }, [token])

  const loadAwards = useCallback(
    async (uid: string, aid: string) => {
      const resp = await fetch(`${API_HOST}/api/v1/raffle/strategy/query_raffle_award_list`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ userId: uid, activityId: Number(aid) }),
      })
      if (!resp.ok) throw new Error(httpError(resp.status, '奖品列表'))
      const data = await resp.json()
      if (data.code !== '0000') throw new Error(data.info || '奖品列表加载失败')
      const list: Award[] = (data.data || []).sort((a: Award, b: Award) => a.sort - b.sort)
      setAwards(list.slice(0, 8))
      setReady(list.length > 0)
      return list
    },
    [authHeaders],
  )

  const loadAccount = useCallback(async (uid: string, aid: string) => {
    const resp = await fetch(`${API_HOST}/api/v1/raffle/activity/query_user_activity_account`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ userId: uid, activityId: Number(aid) }),
    })
    if (!resp.ok) throw new Error(httpError(resp.status, '账户额度'))
    const data = await resp.json()
    if (data.code === '0000') setAccount(data.data)
  }, [authHeaders])

  useEffect(() => {
    if (!userId || !activityId) return
    Promise.all([loadAwards(userId, activityId), loadAccount(userId, activityId)]).catch((e) =>
      setError(e.message),
    )
  }, [userId, activityId, loadAwards, loadAccount])

  async function armory() {
    setError(null)
    try {
      const resp = await fetch(
        `${API_HOST}/api/v1/raffle/activity/armory?activityId=${activityId}`,
        { headers: authHeaders() },
      )
      const data = await resp.json()
      if (data.code !== '0000') throw new Error(data.info || '预热失败')
      await loadAwards(userId, activityId)
    } catch (e: any) {
      setError(e.message)
    }
  }

  async function draw() {
    if (rolling) return
    setResult(null)
    setError(null)
    setRolling(true)

    let index = 0
    const speed = 70
    const spin = () => {
      setActiveIndex(GRID_ORDER[index % GRID_ORDER.length])
      index++
      if (rollingRef.current) timer.current = window.setTimeout(spin, speed)
    }
    rollingRef.current = true
    spin()

    try {
      const resp = await fetch(`${API_HOST}/api/v1/raffle/activity/draw`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ userId, activityId: Number(activityId) }),
      })
      if (!resp.ok) throw new Error(httpError(resp.status, '抽奖'))
      const data = await resp.json()

      // 让动画至少转 1.2 秒，避免闪一下就停
      await new Promise((r) => setTimeout(r, 1200))

      rollingRef.current = false
      if (timer.current) window.clearTimeout(timer.current)

      if (data.code !== '0000') {
        setRolling(false)
        setError(data.info || '抽奖失败')
        setActiveIndex(-1)
        return
      }

      const won: DrawResult = {
        awardId: data.data.awardId,
        awardTitle: data.data.awardTitle,
      }
      const idx = awards.findIndex((a) => a.awardId === won.awardId)
      if (idx >= 0) setActiveIndex(idx)
      setResult(won)
      setRolling(false)
      loadAccount(userId, activityId)
    } catch (e: any) {
      rollingRef.current = false
      if (timer.current) window.clearTimeout(timer.current)
      setRolling(false)
      setError(e.message || '网络错误')
    }
  }

  useEffect(() => {
    return () => {
      if (timer.current) window.clearTimeout(timer.current)
    }
  }, [])

  const grid = useMemo(() => {
    const cells: (Award | null)[] = new Array(9).fill(null)
    GRID_ORDER.forEach((pos, i) => {
      cells[pos] = awards[i] ?? null
    })
    return cells
  }, [awards])

  const remaining = account?.dayCountSurplus ?? account?.totalCountSurplus ?? null

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-4">
          <div>
            <div className="text-xs text-slate-400">营销活动</div>
            <h1 className="text-lg font-semibold text-slate-900">幸运抽奖</h1>
          </div>
          <div className="flex items-center gap-5 text-sm">
            <div className="text-right">
              <div className="text-xs text-slate-400">今日剩余抽奖次数</div>
              <div className="text-xl font-semibold text-slate-900">
                {remaining === null ? '—' : remaining}
                <span className="ml-1 text-xs font-normal text-slate-400">
                  / {account?.dayCount ?? '—'}
                </span>
              </div>
            </div>
            <div className="text-right">
              <div className="text-xs text-slate-400">当前用户</div>
              <div className="font-mono text-xs text-slate-600">{userId || '—'}</div>
            </div>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-6 py-8">
        {error && (
          <div className="mb-5 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        {!ready && (
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
            <span>抽奖还没预热（Redis 里没有概率表），先装配一次。</span>
            <button
              onClick={armory}
              className="rounded-md bg-amber-900 px-3 py-1.5 text-xs text-white hover:bg-amber-800"
            >
              装配概率表
            </button>
          </div>
        )}

        <div className="grid gap-6 md:grid-cols-[1.15fr_1fr]">
          {/* 九宫格 */}
          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <div className="mb-4 flex items-center justify-between">
              <span className="text-sm font-medium text-slate-700">九宫格抽奖</span>
              <span className="text-xs text-slate-400">点击中间按钮开始</span>
            </div>
            <div className="grid grid-cols-3 gap-2.5">
              {grid.map((award, pos) => {
                const isCenter = pos === 4
                if (isCenter) {
                  return (
                    <button
                      key="center"
                      onClick={draw}
                      disabled={rolling || !ready}
                      className="flex aspect-square items-center justify-center rounded-xl bg-slate-900 text-sm font-medium text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-40"
                    >
                      {rolling ? '抽奖中' : '立即抽奖'}
                    </button>
                  )
                }
                const active = activeIndex === pos
                return (
                  <div
                    key={pos}
                    className={
                      'flex aspect-square flex-col items-center justify-center rounded-xl border p-2 text-center transition ' +
                      (active
                        ? 'border-amber-400 bg-amber-50 shadow-[0_0_0_3px_rgba(251,191,36,.25)]'
                        : 'border-slate-200 bg-slate-50')
                    }
                  >
                    <div className="text-2xl">🎁</div>
                    <div className="mt-1 line-clamp-2 text-xs text-slate-600">
                      {award ? award.awardTitle : '—'}
                    </div>
                    {award && award.awardCountSurplus !== undefined && (
                      <div className="mt-0.5 text-[10px] text-slate-400">
                        剩 {award.awardCountSurplus}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          </div>

          {/* 结果 + 说明 */}
          <div className="space-y-5">
            <div className="rounded-2xl border border-slate-200 bg-white p-5">
              <div className="text-sm font-medium text-slate-700">抽奖结果</div>
              {result ? (
                <div className="mt-4 rounded-xl bg-gradient-to-br from-amber-50 to-white p-5 text-center ring-1 ring-amber-200">
                  <div className="text-4xl">🎉</div>
                  <div className="mt-2 text-lg font-semibold text-slate-900">
                    {result.awardTitle}
                  </div>
                  <div className="mt-1 text-xs text-slate-500">奖品 ID {result.awardId}</div>
                  <div className="mt-3 text-xs text-slate-400">
                    奖品会由中台异步回调上游，到账后可在上游页面看到
                  </div>
                </div>
              ) : (
                <div className="mt-4 rounded-xl border border-dashed border-slate-200 p-8 text-center text-sm text-slate-400">
                  {rolling ? '正在抽奖…' : '还没有抽奖'}
                </div>
              )}
            </div>

            <div className="rounded-2xl border border-slate-200 bg-white p-5 text-sm text-slate-600">
              <div className="font-medium text-slate-800">这个页面在链路里的位置</div>
              <ul className="mt-2 space-y-1.5 text-xs leading-relaxed">
                <li>· 用户从上游 App 点「去抽奖」跳到这里，带上 <code className="rounded bg-slate-100 px-1">userId</code></li>
                <li>· 你看到的奖品与概率全部来自中台，页面不做任何抽奖计算</li>
                <li>· 中奖后中台会异步通知上游，由上游把奖品记进用户账户</li>
              </ul>
              <a
                href="/console/dashboard"
                className="mt-4 inline-block text-xs text-slate-500 underline hover:text-slate-800"
              >
                运营台 →
              </a>
            </div>
          </div>
        </div>

        {/* 奖品列表 */}
        <div className="mt-8 rounded-2xl border border-slate-200 bg-white">
          <div className="border-b border-slate-100 px-5 py-3 text-sm font-medium text-slate-700">
            奖池（{awards.length} 个奖品）
          </div>
          <div className="grid gap-px bg-slate-100 sm:grid-cols-2 lg:grid-cols-4">
            {awards.map((a) => (
              <div key={a.awardId} className="bg-white px-4 py-3">
                <div className="text-sm text-slate-800">{a.awardTitle}</div>
                <div className="mt-1 flex items-center gap-2 text-xs text-slate-400">
                  <span>ID {a.awardId}</span>
                  {a.awardCountSurplus !== undefined && <span>剩余 {a.awardCountSurplus}</span>}
                  {a.isAwardUnlock === false && (
                    <span className="rounded bg-amber-100 px-1 text-amber-700">未解锁</span>
                  )}
                </div>
              </div>
            ))}
            {awards.length === 0 && (
              <div className="bg-white px-4 py-6 text-sm text-slate-400">暂无奖品</div>
            )}
          </div>
        </div>
      </main>
    </div>
  )
}
