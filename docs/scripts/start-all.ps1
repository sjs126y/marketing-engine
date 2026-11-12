# ============================================================================
#  一键启动三端：营销中台(Java) + 前端(Next.js) + 上游 AI 助手(Python)
#
#  用法：
#    pwsh -File docs/scripts/start-all.ps1                  # 首次会自动构建/装依赖
#    pwsh -File docs/scripts/start-all.ps1 -SkipBuild       # 跳过 mvn package
#    pwsh -File docs/scripts/start-all.ps1 -Restart         # 先停再起
#    pwsh -File docs/scripts/start-all.ps1 -Only mid            # 只起某一个
#    pwsh -File docs/scripts/start-all.ps1 -Only mid,web,upstream
#
#  日志：<repo>/logs/{mid,web,upstream}.log
# ============================================================================
param(
    [switch]$SkipBuild,
    [switch]$Restart,
    [ValidateSet('mid', 'web', 'upstream')]
    [string[]]$Only = @('mid', 'web', 'upstream')   # mid = 中台, web = 前端, upstream = 上游
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# ---------- 路径 ----------
$repo = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$serverDir   = Join-Path $repo 'server'
$webDir      = Join-Path $repo 'web'
$upstreamDir = Join-Path $repo 'upstream-ai-app'
$logsDir     = Join-Path $repo 'logs'
$jar         = Join-Path $serverDir 'marketing-engine-app\target\marketing-engine-app.jar'
$upstreamVenv = Join-Path $upstreamDir '.venv\Scripts\python.exe'

New-Item -ItemType Directory -Path $logsDir -Force | Out-Null

$MID_PORT = 8092
$WEB_PORT = 3000
$UP_PORT  = 9000

function Say($msg, $color = 'Gray') { Write-Host $msg -ForegroundColor $color }
function Step($msg) { Write-Host "`n$msg" -ForegroundColor Cyan }
function Ok($msg)   { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "  [!]  $msg" -ForegroundColor Yellow }
function Bad($msg)  { Write-Host "  [X]  $msg" -ForegroundColor Red }

function Test-Port([int]$port) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    return [bool]$conn
}

function Stop-Port([int]$port, [string]$label) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) { return }
    $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($procId in $pids) {
        try {
            Stop-Process -Id $procId -Force -ErrorAction Stop
            Warn "已停止占用 $port 的进程（$label, PID $procId）"
        } catch {
            Warn "无法停止 PID $procId：$($_.Exception.Message)"
        }
    }
    Start-Sleep -Seconds 1
}

function Wait-Http([string]$url, [int]$seconds = 90, [string]$label = '') {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $resp = Invoke-WebRequest $url -TimeoutSec 3 -UseBasicParsing
            if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400) { return $true }
        } catch { }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Find-Npm {
    # pwsh 里 Get-Command npm 常解析到 npm.ps1，Start-Process 无法直接执行，
    # 需要拿到同目录下的 npm.cmd（或可执行文件）。
    $cmd = Get-Command npm -ErrorAction SilentlyContinue
    if ($cmd) {
        $dir = Split-Path $cmd.Source -Parent
        $candidate = Join-Path $dir 'npm.cmd'
        if (Test-Path $candidate) { return $candidate }
        if ($cmd.Source -match '\.(cmd|exe)$') { return $cmd.Source }
    }
    foreach ($p in @('C:\Program Files\nodejs\npm.cmd', 'D:\Develop\NodeJs\nodejs\npm.cmd')) {
        if (Test-Path $p) { return $p }
    }
    throw '找不到 npm.cmd，请安装 Node.js 18+ 并确保在 PATH 中'
}

function Find-Java {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $candidates += 'C:\Users\MR\Java\jdk-21.0.7\bin\java.exe'
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { $candidates += $cmd.Source }
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) { return $c }
    }
    throw '找不到 java.exe，请安装 JDK 21 或设置 JAVA_HOME'
}

Say '============================================================' 'White'
Say ' 营销引擎 · 三端一键启动' 'White'
Say '============================================================' 'White'

# ---------- 0. 中间件 ----------
Step '[0/4] 检查中间件（MySQL / Redis / RabbitMQ）'
$compose = Join-Path $serverDir 'docs\dev-ops\docker-compose-environment.yml'
$running = @()
try { $running = docker ps --format '{{.Names}}' 2>$null } catch { }
$needUp = -not ($running -contains 'mysql' -and $running -contains 'redis' -and $running -contains 'rabbitmq')
if ($needUp) {
    Warn '中间件未全部运行，尝试 docker compose up -d'
    docker compose -f $compose up -d 2>&1 | Out-Null
    Start-Sleep -Seconds 8
} else {
    Ok 'MySQL / Redis / RabbitMQ 均在运行'
}

# ---------- 1. 中台 ----------
if ($Only -contains 'mid') {
    Step "[1/4] 营销中台（Java, :$MID_PORT）"
    if ($Restart) { Stop-Port $MID_PORT '中台' }
    if (Test-Port $MID_PORT) {
        Ok ":$MID_PORT 已在运行，跳过"
    } else {
        $javaExe = Find-Java
        if (-not (Test-Path $jar)) {
            Warn '未找到 jar，开始构建（首次会下载依赖，耐心等）'
            $mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
            if (-not $mvn) { throw '找不到 mvn，请安装 Maven 或先手动构建' }
            Push-Location $serverDir
            & $mvn -B -q -DskipTests package
            Pop-Location
            if (-not (Test-Path $jar)) { throw '构建失败，未生成 jar' }
        } elseif (-not $SkipBuild) {
            Say '  使用已有 jar（要重新构建请先删除，或去掉 -SkipBuild 并手动 mvn package）'
        }

        Start-Process -FilePath $javaExe `
            -ArgumentList @('-jar', "`"$jar`"") `
            -WorkingDirectory $repo `
            -RedirectStandardOutput (Join-Path $logsDir 'mid.log') `
            -RedirectStandardError  (Join-Path $logsDir 'mid.err.log') `
            -WindowStyle Hidden
        if (Wait-Http "http://127.0.0.1:$MID_PORT/actuator/health" 120 '中台') {
            Ok "中台已就绪 http://127.0.0.1:$MID_PORT"
        } else {
            Bad "中台启动超时，看日志：$logsDir\mid.log"
        }
    }
}

# ---------- 2. 上游 ----------
if ($Only -contains 'upstream') {
    Step "[2/4] 上游 AI 助手（Python, :$UP_PORT）"
    if ($Restart) { Stop-Port $UP_PORT '上游' }
    if (Test-Port $UP_PORT) {
        Ok ":$UP_PORT 已在运行，跳过"
    } else {
        if (-not (Test-Path $upstreamVenv)) {
            Warn '未找到虚拟环境，创建并安装依赖'
            $py = (Get-Command python -ErrorAction SilentlyContinue).Source
            if (-not $py) { throw '找不到 python，请安装 Python 3.10+' }
            & $py -m venv (Join-Path $upstreamDir '.venv')
            & $upstreamVenv -m pip install --quiet --disable-pip-version-check `
                -i https://mirrors.aliyun.com/pypi/simple/ fastapi uvicorn
        }
        Start-Process -FilePath $upstreamVenv `
            -ArgumentList @('app.py') `
            -WorkingDirectory $upstreamDir `
            -RedirectStandardOutput (Join-Path $logsDir 'upstream.log') `
            -RedirectStandardError  (Join-Path $logsDir 'upstream.err.log') `
            -WindowStyle Hidden
        if (Wait-Http "http://127.0.0.1:$UP_PORT/health" 60 '上游') {
            Ok "上游已就绪 http://127.0.0.1:$UP_PORT"
        } else {
            Bad "上游启动超时，看日志：$logsDir\upstream.log"
        }
    }
}

# ---------- 3. 前端 ----------
if ($Only -contains 'web') {
    Step "[3/4] 前端（Next.js, :$WEB_PORT）"
    if ($Restart) { Stop-Port $WEB_PORT '前端' }
    if (Test-Port $WEB_PORT) {
        Ok ":$WEB_PORT 已在运行，跳过"
    } else {
        if (-not (Test-Path (Join-Path $webDir 'node_modules'))) {
            Warn '未找到 node_modules，执行 npm install（首次较慢）'
            Push-Location $webDir
            & npm install --no-audit --no-fund
            Pop-Location
        }
        # 前端要读后端地址，缺配置会退到作者留的 mock 服务
        $envLocal = Join-Path $webDir '.env.local'
        if (-not (Test-Path $envLocal)) {
            "API_HOST_URL=http://127.0.0.1:$MID_PORT" | Out-File -FilePath $envLocal -Encoding utf8
            Ok '已生成 web/.env.local（指向本地中台）'
        }

        $npmCmd = Find-Npm
        try {
            Start-Process -FilePath $npmCmd `
                -ArgumentList @('run', 'dev') `
                -WorkingDirectory $webDir `
                -RedirectStandardOutput (Join-Path $logsDir 'web.log') `
                -RedirectStandardError  (Join-Path $logsDir 'web.err.log') `
                -WindowStyle Hidden `
                -ErrorAction Stop
        } catch {
            Bad "前端启动失败：$($_.Exception.Message)"
        }
        if (Wait-Http "http://localhost:$WEB_PORT" 120 '前端') {
            Ok "前端已就绪 http://localhost:$WEB_PORT"
        } else {
            Bad "前端启动超时，看日志：$logsDir\web.log"
        }
    }
}

# ---------- 4. 汇总 ----------
Step '[4/4] 启动结果'
$rows = @(
    @{ Name = '上游 AI 助手 (Python)'; Url = "http://127.0.0.1:$UP_PORT";              Port = $UP_PORT },
    @{ Name = '中台首页 / 活动页';     Url = "http://localhost:$WEB_PORT";             Port = $WEB_PORT },
    @{ Name = '运营台';                Url = "http://localhost:$WEB_PORT/console";     Port = $WEB_PORT },
    @{ Name = '中台健康检查 (Java)';   Url = "http://127.0.0.1:$MID_PORT/actuator/health"; Port = $MID_PORT }
)
foreach ($r in $rows) {
    $alive = Test-Port $r.Port
    $mark = if ($alive) { 'UP  ' } else { 'DOWN' }
    $color = if ($alive) { 'Green' } else { 'Red' }
    Write-Host ("  [{0}] {1,-24} {2}" -f $mark, $r.Name, $r.Url) -ForegroundColor $color
}

Say ''
Say '  演示账号：'
Say '    上游 App      注册新账号，或用已有 alice / alice123'
Say '    运营台         admin / admin123  （或 operator / operator123）'
Say ''
Say '  推荐走法：上游登录 → 签到 → 去抽奖 → 回上游等 8 秒看额度到账'
Say ''
Say "  日志目录：$logsDir"
Say '  停止全部：pwsh -File docs/scripts/stop-all.ps1'
Say '============================================================' 'White'
