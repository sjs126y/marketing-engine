'use client'

import { useEffect } from 'react'

/** /console 默认进看板；未登录先跳登录页。 */
export default function ConsoleIndexPage() {
  useEffect(() => {
    const hasToken = typeof window !== 'undefined' && window.localStorage.getItem('me.console.token')
    window.location.replace(hasToken ? '/console/dashboard' : '/console/login')
  }, [])
  return <div className="p-6 text-sm text-slate-500">正在跳转…</div>
}
