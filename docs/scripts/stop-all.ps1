# ============================================================================
#  停止三端服务：营销中台(:8092) / 前端(:3000) / 上游 AI 助手(:9000)
#
#  用法：
#    pwsh -File docs/scripts/stop-all.ps1             # 只停应用，中间件不动
#    pwsh -File docs/scripts/stop-all.ps1 -Middleware # 连 Docker 中间件一起停
# ============================================================================
param(
    [switch]$Middleware
)

$ErrorActionPreference = 'Continue'
$repo = Resolve-Path (Join-Path $PSScriptRoot '..\..')

$targets = @(
    @{ Port = 8092; Label = '营销中台 (Java)' },
    @{ Port = 3000; Label = '前端 (Next.js)' },
    @{ Port = 9000; Label = '上游 AI 助手 (Python)' }
)

Write-Host "`n停止三端服务" -ForegroundColor Cyan

foreach ($t in $targets) {
    $conns = Get-NetTCPConnection -LocalPort $t.Port -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) {
        Write-Host ("  [--] {0,-22} :{1} 未在运行" -f $t.Label, $t.Port) -ForegroundColor DarkGray
        continue
    }
    $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($procId in $pids) {
        try {
            Stop-Process -Id $procId -Force -ErrorAction Stop
            Write-Host ("  [OK] {0,-22} :{1} 已停止 (PID {2})" -f $t.Label, $t.Port, $procId) -ForegroundColor Green
        } catch {
            Write-Host ("  [X]  {0,-22} PID {1} 停止失败：{2}" -f $t.Label, $procId, $_.Exception.Message) -ForegroundColor Red
        }
    }
}

if ($Middleware) {
    $compose = Join-Path $repo 'server\docs\dev-ops\docker-compose-environment.yml'
    if (Test-Path $compose) {
        Write-Host "`n停止中间件（MySQL / Redis / RabbitMQ）" -ForegroundColor Cyan
        docker compose -f $compose down 2>&1 | Out-Null
        Write-Host '  [OK] 中间件已停止' -ForegroundColor Green
    }
} else {
    Write-Host "`n中间件（Docker）保持运行；要一起停请加 -Middleware" -ForegroundColor DarkGray
}

Write-Host ''
