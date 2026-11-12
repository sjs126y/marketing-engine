'use client'

import { useState } from 'react'
import { publishStrategy, validateStrategy } from '@/lib/console-api'

type Violation = {
  code: string
  severity: string
  target: string
  message: string
}

type Result = {
  code: string
  info: string
  published: boolean
  errorCount: number
  warnCount: number
  violations: Violation[]
}

export default function StrategyConsolePage() {
  const [strategyId, setStrategyId] = useState('100006')
  const [result, setResult] = useState<Result | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState<'validate' | 'publish' | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  async function run(action: 'validate' | 'publish') {
    setError(null)
    setNotice(null)
    setLoading(action)
    try {
      const data = action === 'validate'
        ? await validateStrategy(strategyId)
        : await publishStrategy(strategyId)
      setResult(data)
      if (action === 'publish') setNotice('已发布：缓存已失效并重新预热概率表')
    } catch (err: any) {
      // 发布被拒时后端返回 400 + 完整违规列表，这里照样展示
      if (err.body) {
        setResult(err.body)
        setNotice(null)
      } else {
        setError(err.message || '请求失败')
      }
    } finally {
      setLoading(null)
    }
  }

  const errors = result?.violations?.filter((v) => v.severity === 'ERROR') ?? []
  const warns = result?.violations?.filter((v) => v.severity !== 'ERROR') ?? []

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold text-slate-900">策略配置</h1>
        <p className="mt-1 text-sm text-slate-500">
          规则、树、概率都是数据驱动的，配错不会编译失败也不会抛异常——所以发布前先校验。
        </p>
      </div>

      <div className="flex flex-wrap items-end gap-3 rounded-xl border border-slate-200 bg-white p-5">
        <label className="text-sm text-slate-600">
          策略 ID
          <input
            className="mt-1 block w-40 rounded-md border border-slate-300 px-3 py-2 text-slate-900 outline-none focus:border-slate-500"
            value={strategyId}
            onChange={(e) => setStrategyId(e.target.value)}
          />
        </label>
        <button
          onClick={() => run('validate')}
          disabled={loading !== null}
          className="rounded-md border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-100 disabled:opacity-50"
        >
          {loading === 'validate' ? '校验中…' : '校验'}
        </button>
        <button
          onClick={() => run('publish')}
          disabled={loading !== null}
          className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
        >
          {loading === 'publish' ? '发布中…' : '校验并发布'}
        </button>
      </div>

      {notice && (
        <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          {notice}
        </div>
      )}
      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      {result && (
        <div className="space-y-4">
          <div className="flex gap-3">
            <Stat label="策略" value={strategyId} />
            <Stat label="错误" value={String(result.errorCount)} tone={result.errorCount > 0 ? 'bad' : 'good'} />
            <Stat label="警告" value={String(result.warnCount)} tone={result.warnCount > 0 ? 'warn' : 'good'} />
            <Stat label="状态" value={result.published ? '已发布' : '仅校验'} tone={result.published ? 'good' : 'idle'} />
          </div>

          <Section title={`错误（${errors.length}）`} empty="没有错误，可以发布" tone="bad">
            {errors.map((v, i) => (
              <ViolationRow key={i} violation={v} />
            ))}
          </Section>

          <Section title={`警告（${warns.length}）`} empty="没有警告" tone="warn">
            {warns.map((v, i) => (
              <ViolationRow key={i} violation={v} />
            ))}
          </Section>
        </div>
      )}

      <div className="rounded-xl border border-slate-200 bg-white p-5 text-sm text-slate-600">
        <p className="font-medium text-slate-800">怎么演示「配置错误被拦住」</p>
        <ol className="mt-2 list-decimal space-y-1 pl-5">
          <li>在库里把某个策略的奖品概率改成和不等于 1：<code className="rounded bg-slate-100 px-1">update strategy_award set award_rate = 0.1 where strategy_id = 100006;</code></li>
          <li>回到本页点「校验」→ 报 <code className="rounded bg-slate-100 px-1">RATE_NOT_NORMALIZED</code></li>
          <li>点「校验并发布」→ 被拒绝，不会带着错配置上线</li>
          <li>改回去（或执行 SQL 恢复）再点发布 → 成功，并自动清缓存 + 重新预热</li>
        </ol>
      </div>
    </div>
  )
}

function Stat({ label, value, tone }: { label: string; value: string; tone?: 'good' | 'bad' | 'warn' | 'idle' }) {
  const color =
    tone === 'bad' ? 'text-red-600'
      : tone === 'warn' ? 'text-amber-600'
        : tone === 'good' ? 'text-emerald-600'
          : 'text-slate-900'
  return (
    <div className="min-w-[110px] rounded-lg border border-slate-200 bg-white px-4 py-3">
      <div className="text-xs text-slate-500">{label}</div>
      <div className={'mt-1 text-lg font-semibold ' + color}>{value}</div>
    </div>
  )
}

function Section({
  title,
  empty,
  tone,
  children,
}: {
  title: string
  empty: string
  tone: 'bad' | 'warn'
  children: React.ReactNode
}) {
  const hasChildren = Array.isArray(children) ? children.length > 0 : !!children
  const accent = tone === 'bad' ? 'border-red-200' : 'border-amber-200'
  return (
    <div className={'rounded-xl border bg-white ' + accent}>
      <div className="border-b border-slate-100 px-5 py-3 text-sm font-medium text-slate-700">{title}</div>
      <div className="divide-y divide-slate-100">
        {hasChildren ? children : <div className="px-5 py-4 text-sm text-slate-400">{empty}</div>}
      </div>
    </div>
  )
}

function ViolationRow({ violation }: { violation: Violation }) {
  return (
    <div className="px-5 py-3">
      <div className="flex items-center gap-2">
        <span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-slate-600">
          {violation.code}
        </span>
        <span className="text-xs text-slate-400">{violation.target}</span>
      </div>
      <div className="mt-1 text-sm text-slate-700">{violation.message}</div>
    </div>
  )
}
