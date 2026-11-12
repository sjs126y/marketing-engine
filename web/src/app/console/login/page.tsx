'use client'

import { useState } from 'react'
import { operatorLogin, saveSession } from '@/lib/console-api'

export default function ConsoleLoginPage() {
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('admin123')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const result = await operatorLogin(username, password)
      saveSession(result.token, result.username, result.role)
      window.location.href = '/console/dashboard'
    } catch (err: any) {
      setError(err.message || '登录失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100">
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-8 shadow-sm"
      >
        <h1 className="text-lg font-semibold text-slate-900">运营台登录</h1>
        <p className="mt-1 text-sm text-slate-500">
          鉴权关闭时（本地默认）可直接进入；开启后需运营账号。
        </p>

        <label className="mt-6 block text-sm text-slate-600">
          账号
          <input
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-slate-900 outline-none focus:border-slate-500"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
        </label>

        <label className="mt-4 block text-sm text-slate-600">
          口令
          <input
            type="password"
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-slate-900 outline-none focus:border-slate-500"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>

        {error && (
          <div className="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>
        )}

        <button
          type="submit"
          disabled={loading}
          className="mt-6 w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
        >
          {loading ? '登录中…' : '登录'}
        </button>

        <p className="mt-4 text-xs text-slate-400">
          演示账号：admin / admin123（ADMIN），operator / operator123（OPERATOR）
        </p>
      </form>
    </div>
  )
}
