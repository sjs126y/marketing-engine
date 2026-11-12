'use client'

import { useEffect, useState } from 'react'
import { recentAwards } from '@/lib/console-api'

type Record = {
  userId: string
  activityId: number
  strategyId: number
  orderId: string
  awardId: number
  awardTitle: string
  awardTime: string
  awardState: string
}

const STATE_STYLE: Record<string, string> = {
  create: 'bg-slate-100 text-slate-600',
  complete: 'bg-emerald-100 text-emerald-700',
  fail: 'bg-red-100 text-red-700',
}

export default function AwardRecordsPage() {
  const [records, setRecords] = useState<Record[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const data = await recentAwards(30)
      setRecords(data.records ?? [])
    } catch (err: any) {
      setError(err.message || '加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const pending = records.filter((r) => r.awardState === 'create').length

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">发奖记录</h1>
          <p className="mt-1 text-sm text-slate-500">
            中奖记录落在分片表里，按中奖时间倒序取最近 30 条。
          </p>
        </div>
        <button
          onClick={load}
          disabled={loading}
          className="rounded-md border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-100 disabled:opacity-50"
        >
          {loading ? '加载中…' : '刷新'}
        </button>
      </div>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
      )}

      <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
        <span className="font-medium">这条链路目前止步于「落库 + 消息投递」。</span>
        下面的记录里 <code className="rounded bg-amber-100 px-1">{pending}</code> 条状态仍是
        <code className="mx-1 rounded bg-amber-100 px-1">create</code>
        ——奖品没有真正交付到用户手里，因为还没有发奖渠道把状态推进到
        <code className="mx-1 rounded bg-amber-100 px-1">complete</code>。这就是下一步要补的「下游」。
      </div>

      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">中奖时间</th>
              <th className="px-4 py-3 font-medium">用户</th>
              <th className="px-4 py-3 font-medium">奖品</th>
              <th className="px-4 py-3 font-medium">抽奖单号</th>
              <th className="px-4 py-3 font-medium">状态</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {records.length === 0 && !loading && (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-slate-400">
                  还没有中奖记录，去 C 端演示页抽一次奖
                </td>
              </tr>
            )}
            {records.map((r) => (
              <tr key={r.orderId + r.awardId}>
                <td className="px-4 py-2 text-slate-500">{formatTime(r.awardTime)}</td>
                <td className="px-4 py-2 text-slate-700">{r.userId}</td>
                <td className="px-4 py-2 text-slate-700">
                  {r.awardId} {r.awardTitle}
                </td>
                <td className="px-4 py-2 font-mono text-xs text-slate-500">{r.orderId}</td>
                <td className="px-4 py-2">
                  <span
                    className={
                      'rounded px-1.5 py-0.5 text-xs ' +
                      (STATE_STYLE[r.awardState] ?? 'bg-slate-100 text-slate-600')
                    }
                  >
                    {r.awardState}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function formatTime(value: string) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
