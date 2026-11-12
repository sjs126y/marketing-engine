'use client'

import { useState } from 'react'
import { dashboardOverview, rechargeQuota } from '@/lib/console-api'

type StockRow = {
  sku: number
  stockCount: number
  redisConsumed: number
  dbConsumed: number
  skipped: boolean
}

type AwardRow = {
  awardId: number
  awardTitle: string
  awardRate: number
  awardCount: number
  awardCountSurplus: number
}

type Overview = {
  stock: { skuCount: number; inconsistentCount: number; detail: StockRow[] }
  outbox: { pending: number }
  strategyId?: string
  awards?: AwardRow[]
}

export default function DashboardPage() {
  const [strategyId, setStrategyId] = useState('100006')
  const [data, setData] = useState<Overview | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [upstream, setUpstream] = useState<{ ok: number; fail: number; last: string | null }>({
    ok: 0,
    fail: 0,
    last: null,
  })

  async function load() {
    setError(null)
    setLoading(true)
    try {
      setData(await dashboardOverview(strategyId))
    } catch (err: any) {
      setError(err.message || '加载失败')
    } finally {
      setLoading(false)
    }
  }

  async function simulateUpstream() {
    const userId = `order-${Date.now()}`
    try {
      await rechargeQuota(userId, 9011, `ORDER-${Date.now()}`)
      setUpstream((s) => ({ ...s, ok: s.ok + 1, last: userId }))
    } catch (err: any) {
      setUpstream((s) => ({ ...s, fail: s.fail + 1, last: err.message }))
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">数据看板</h1>
          <p className="mt-1 text-sm text-slate-500">
            三块数据回答「系统现在健不健康」：库存对账、消息积压、概率配置。
          </p>
        </div>
        <div className="flex items-end gap-3">
          <label className="text-sm text-slate-600">
            策略 ID
            <input
              className="mt-1 block w-36 rounded-md border border-slate-300 px-3 py-2 text-slate-900 outline-none focus:border-slate-500"
              value={strategyId}
              onChange={(e) => setStrategyId(e.target.value)}
            />
          </label>
          <button
            onClick={load}
            disabled={loading}
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
          >
            {loading ? '加载中…' : '加载'}
          </button>
        </div>
      </div>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
      )}

      <div className="grid gap-4 md:grid-cols-3">
        <Card title="库存对账" hint="Redis 已扣 vs 数据库已扣">
          {data ? (
            <>
              <div className="text-2xl font-semibold text-slate-900">{data.stock.skuCount}</div>
              <div className="text-xs text-slate-500">个 SKU</div>
              <div
                className={
                  'mt-2 text-sm ' +
                  (data.stock.inconsistentCount > 0 ? 'text-red-600' : 'text-emerald-600')
                }
              >
                {data.stock.inconsistentCount > 0
                  ? `${data.stock.inconsistentCount} 个存在偏差`
                  : '无偏差'}
              </div>
            </>
          ) : (
            <Placeholder />
          )}
        </Card>

        <Card title="消息积压" hint="outbox 待投递">
          {data ? (
            <>
              <div
                className={
                  'text-2xl font-semibold ' +
                  (data.outbox.pending > 100 ? 'text-amber-600' : 'text-slate-900')
                }
              >
                {data.outbox.pending}
              </div>
              <div className="text-xs text-slate-500">条 pending</div>
              <div className="mt-2 text-sm text-slate-500">
                {data.outbox.pending === 0 ? '无积压' : '调度器每秒扫一批'}
              </div>
            </>
          ) : (
            <Placeholder />
          )}
        </Card>

        <Card title="上游调用" hint="模拟订单系统调开放接口">
          <div className="text-2xl font-semibold text-slate-900">{upstream.ok}</div>
          <div className="text-xs text-slate-500">
            成功 / 失败 {upstream.fail}
            {upstream.last ? ` · 最近 ${upstream.last.slice(0, 16)}` : ''}
          </div>
          <button
            onClick={simulateUpstream}
            className="mt-3 rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-100"
          >
            模拟一次上游充值
          </button>
        </Card>
      </div>

      {data && (
        <>
          <Panel title="SKU 库存明细">
            <table className="w-full text-sm">
              <thead className="text-left text-xs text-slate-500">
                <tr>
                  <Th>SKU</Th>
                  <Th>总量</Th>
                  <Th>Redis 已扣</Th>
                  <Th>DB 已扣</Th>
                  <Th>偏差</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.stock.detail.map((row) => {
                  const diff = row.dbConsumed - row.redisConsumed
                  return (
                    <tr key={row.sku}>
                      <Td>{row.sku}</Td>
                      <Td>{row.stockCount}</Td>
                      <Td>{row.skipped ? '未预热' : row.redisConsumed}</Td>
                      <Td>{row.dbConsumed}</Td>
                      <Td className={diff === 0 ? 'text-emerald-600' : 'text-amber-600'}>
                        {row.skipped ? '-' : diff}
                      </Td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </Panel>

          {data.awards && data.awards.length > 0 && (
            <Panel title={`策略 ${data.strategyId} 的概率与库存`}>
              <table className="w-full text-sm">
                <thead className="text-left text-xs text-slate-500">
                  <tr>
                    <Th>奖品</Th>
                    <Th>配置概率</Th>
                    <Th>总量</Th>
                    <Th>剩余</Th>
                    <Th>已发</Th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {data.awards.map((row) => (
                    <tr key={row.awardId}>
                      <Td>
                        {row.awardId} {row.awardTitle}
                      </Td>
                      <Td>{(Number(row.awardRate) * 100).toFixed(2)}%</Td>
                      <Td>{row.awardCount}</Td>
                      <Td>{row.awardCountSurplus}</Td>
                      <Td>{row.awardCount - row.awardCountSurplus}</Td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Panel>
          )}
        </>
      )}
    </div>
  )
}

function Card({ title, hint, children }: { title: string; hint: string; children: React.ReactNode }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5">
      <div className="text-sm font-medium text-slate-700">{title}</div>
      <div className="text-xs text-slate-400">{hint}</div>
      <div className="mt-3">{children}</div>
    </div>
  )
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white">
      <div className="border-b border-slate-100 px-5 py-3 text-sm font-medium text-slate-700">{title}</div>
      <div className="px-5 py-4">{children}</div>
    </div>
  )
}

function Placeholder() {
  return <div className="text-sm text-slate-400">点「加载」获取数据</div>
}

function Th({ children }: { children: React.ReactNode }) {
  return <th className="py-2 font-medium">{children}</th>
}

function Td({ children, className }: { children: React.ReactNode; className?: string }) {
  return <td className={'py-2 ' + (className ?? 'text-slate-700')}>{children}</td>
}
