'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useEffect, useState } from 'react'
import { clearSession, getSession } from '@/lib/console-api'

const NAV = [
  { href: '/console/dashboard', label: '数据看板' },
  { href: '/console/strategy', label: '策略配置' },
  { href: '/console/awards', label: '发奖记录' },
]

export default function ConsoleLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const [username, setUsername] = useState<string | null>(null)
  const isLoginPage = pathname === '/console/login'

  useEffect(() => {
    const session = getSession()
    setUsername(session ? session.username : null)
  }, [pathname])

  return (
    <div className="min-h-screen bg-slate-50 text-slate-800">
      {!isLoginPage && (
        <header className="border-b border-slate-200 bg-white">
          <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-3">
            <div className="flex items-center gap-6">
              <Link href="/console/dashboard" className="text-base font-semibold text-slate-900">
                营销引擎 · 运营台
              </Link>
              <nav className="flex gap-1">
                {NAV.map((item) => {
                  const active = pathname === item.href
                  return (
                    <Link
                      key={item.href}
                      href={item.href}
                      className={
                        'rounded-md px-3 py-1.5 text-sm transition ' +
                        (active
                          ? 'bg-slate-900 text-white'
                          : 'text-slate-600 hover:bg-slate-100')
                      }
                    >
                      {item.label}
                    </Link>
                  )
                })}
              </nav>
            </div>
            <div className="flex items-center gap-3 text-sm">
              <span className="text-slate-500">{username ?? '未登录'}</span>
              <button
                className="rounded-md border border-slate-300 px-3 py-1 text-slate-600 hover:bg-slate-100"
                onClick={() => {
                  clearSession()
                  window.location.href = '/console/login'
                }}
              >
                退出
              </button>
              <Link href="/" className="text-slate-500 hover:text-slate-800">
                返回 C 端演示
              </Link>
            </div>
          </div>
        </header>
      )}
      <main className="mx-auto max-w-6xl px-6 py-8">{children}</main>
    </div>
  )
}
