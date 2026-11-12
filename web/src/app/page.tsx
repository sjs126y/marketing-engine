'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'

const DEFAULT_ACTIVITY = '100301'
const DEFAULT_USER = 'demo-user'

export default function Home() {
  const [userId, setUserId] = useState(DEFAULT_USER)
  const [activityId, setActivityId] = useState(DEFAULT_ACTIVITY)

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    setUserId(params.get('userId') || DEFAULT_USER)
    setActivityId(params.get('activityId') || DEFAULT_ACTIVITY)
  }, [])

  const playHref = `/play?userId=${encodeURIComponent(userId)}&activityId=${encodeURIComponent(activityId)}`

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-4">
          <div>
            <div className="text-xs text-slate-400">marketing-engine</div>
            <h1 className="text-lg font-semibold text-slate-900">营销中台</h1>
          </div>
          <nav className="flex items-center gap-4 text-sm">
            <Link href="/play" className="text-slate-600 hover:text-slate-900">用户抽奖页</Link>
            <Link href="/console" className="text-slate-600 hover:text-slate-900">运营台</Link>
            <a
              href="http://127.0.0.1:9000"
              className="rounded-md border border-slate-300 px-3 py-1.5 text-slate-700 hover:bg-slate-100"
            >
              上游 App ↗
            </a>
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-6 py-12">
        <h2 className="text-2xl font-semibold text-slate-900">三个端，各司其职</h2>
        <p className="mt-2 max-w-2xl text-sm leading-relaxed text-slate-500">
          这个仓库里跑着三套东西：上游业务方（Python AI 助手）、营销中台（Java）、以及中台的两个前端界面。
          中台负责判定「给谁、给什么、给多少」，业务资产（积分、对话次数）留在上游。
        </p>

        <div className="mt-8 grid gap-4 md:grid-cols-3">
          <Card
            tag="① 上游业务方"
            title="AI 助手"
            desc="签到、对话、做任务拿抽奖次数。自己持有积分与对话次数，通过开放接口调中台。"
            action={{ href: 'http://127.0.0.1:9000', label: '打开上游 App', external: true }}
            tone="slate"
          />
          <Card
            tag="② 中台 · 用户端"
            title="抽奖页"
            desc="用户从上游跳过来抽奖。奖品、概率、库存全部来自中台，页面不做任何计算。"
            action={{ href: playHref, label: '进入抽奖页' }}
            tone="dark"
          />
          <Card
            tag="③ 中台 · 运营端"
            title="运营台"
            desc="配置校验、发布、库存对账、消息积压、发奖记录。"
            action={{ href: '/console', label: '进入运营台' }}
            tone="slate"
          />
        </div>

        <div className="mt-10 rounded-2xl border border-slate-200 bg-white p-6">
          <div className="text-sm font-medium text-slate-800">链路</div>
          <pre className="mt-3 overflow-x-auto rounded-lg bg-slate-50 p-4 text-xs leading-relaxed text-slate-600">
{`上游 AI 助手 ──签到/任务──► 营销中台（判定奖励）
      │                          │
      │◄──── 发奖回调 ────────────┘
      │                          提示「去抽奖」
      ▼                              │
   自己记账  ◄────────── 用户抽奖 ◄──┘`}
          </pre>
        </div>

        <div className="mt-6 text-xs text-slate-400">
          本地默认用户 <code className="rounded bg-slate-100 px-1">{userId}</code>，活动{' '}
          <code className="rounded bg-slate-100 px-1">{activityId}</code>。
          可以在 URL 上带 <code className="rounded bg-slate-100 px-1">?userId=xxx&amp;activityId=yyy</code> 切换。
        </div>
      </main>
    </div>
  )
}

function Card({
  tag,
  title,
  desc,
  action,
  tone,
}: {
  tag: string
  title: string
  desc: string
  action: { href: string; label: string; external?: boolean }
  tone: 'dark' | 'slate'
}) {
  const primary = tone === 'dark'
  return (
    <div className="flex flex-col rounded-2xl border border-slate-200 bg-white p-5">
      <div className="text-xs text-slate-400">{tag}</div>
      <div className="mt-1 text-base font-semibold text-slate-900">{title}</div>
      <p className="mt-2 flex-1 text-sm leading-relaxed text-slate-500">{desc}</p>
      <a
        href={action.href}
        className={
          'mt-4 inline-block rounded-md px-3 py-2 text-center text-sm ' +
          (primary
            ? 'bg-slate-900 text-white hover:bg-slate-800'
            : 'border border-slate-300 text-slate-700 hover:bg-slate-100')
        }
      >
        {action.label}
        {action.external ? ' ↗' : ''}
      </a>
    </div>
  )
}
