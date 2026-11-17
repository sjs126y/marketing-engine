# 前后端联调回归：按浏览器的真实调用顺序跑一遍
# 用法：pwsh -File integration_check.ps1
$ErrorActionPreference = 'Stop'
$MID = 'http://127.0.0.1:8092'
$UP  = 'http://127.0.0.1:9000'
$WEB = 'http://localhost:3000'

$pass = 0; $fail = 0
function Check($name, $cond, $detail) {
    if ($cond) { $script:pass++; Write-Host ("  [PASS] {0,-34} {1}" -f $name, $detail) -ForegroundColor Green }
    else       { $script:fail++; Write-Host ("  [FAIL] {0,-34} {1}" -f $name, $detail) -ForegroundColor Red }
}

$user = 'u' + (Get-Date -Format 'HHmmss')
$pwd  = 'test123456'

# ---------- 开放接口签名（权益中心运营接口用） ----------
# 与 ApiKeyAuthenticationFilter 口径一致：HMAC-SHA256("method`npath`ntimestamp`nbody")
$script:OpenApiKey = 'demo-client'
$script:OpenApiSecret = 'demo-secret-please-change'

function Open-Call($Method, $Path, $Body, $Query) {
    $bodyText = if ($null -eq $Body) { '' } else { ($Body | ConvertTo-Json -Compress -Depth 5) }
    $ts = [string][int64]([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())
    $payload = "$Method`n$Path`n$ts`n$bodyText"
    $hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($script:OpenApiSecret))
    $sig = -join ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($payload)) | ForEach-Object { $_.ToString('x2') })
    $headers = @{ 'X-Api-Key' = $script:OpenApiKey; 'X-Timestamp' = $ts; 'X-Sign' = $sig }
    $url = "$MID$Path"
    if ($Query) {
        $qs = ($Query.GetEnumerator() | ForEach-Object { "$($_.Key)=$([uri]::EscapeDataString([string]$_.Value))" }) -join '&'
        $url = "$url`?$qs"
    }
    if ($null -eq $Body) { return Invoke-RestMethod $url -Method $Method -Headers $headers }
    # 传字节而不是字符串，避免 PowerShell 5.1 用 Latin-1 编码导致签名对不上
    return Invoke-RestMethod $url -Method $Method -Headers $headers -ContentType 'application/json; charset=utf-8' `
            -Body ([Text.Encoding]::UTF8.GetBytes($bodyText))
}

Write-Host "`n=== 1. 上游：注册 / 登录 / 会话 ===" -ForegroundColor Cyan
$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$r = Invoke-RestMethod "$UP/api/register" -Method Post -ContentType 'application/json' `
        -Body (@{username=$user;password=$pwd;nickname='联调用户'}|ConvertTo-Json) -WebSession $s
Check '注册' ($r.ok -eq $true) "userId=$($r.userId)"

try { Invoke-WebRequest "$UP/api/me" -TimeoutSec 10 | Out-Null; $unauth = 200 } catch { $unauth = $_.Exception.Response.StatusCode.value__ }
Check '未登录访问 /api/me 被拒' ($unauth -eq 401) "HTTP $unauth"

$me = Invoke-RestMethod "$UP/api/me" -WebSession $s
Check '注册赠送积分' ($me.credit -eq 20) "credit=$($me.credit)"

Write-Host "`n=== 2. 上游：签到（幂等） ===" -ForegroundColor Cyan
$sign1 = Invoke-RestMethod "$UP/api/sign" -Method Post -ContentType 'application/json' -Body '{}' -WebSession $s
Check '首次签到' ($sign1.ok -eq $true) "+$($sign1.credit) 积分"
$sign2 = Invoke-RestMethod "$UP/api/sign" -Method Post -ContentType 'application/json' -Body '{}' -WebSession $s
Check '重复签到被拦' ($sign2.ok -eq $false) $sign2.reason

Write-Host "`n=== 3. 上游：做任务换抽奖次数（开放接口 + HMAC） ===" -ForegroundColor Cyan
$rc = Invoke-RestMethod "$UP/api/recharge" -Method Post -ContentType 'application/json' -Body '{}' -WebSession $s
Check '签名调用开放接口' ($rc.ok -eq $true) "orderId=$($rc.orderId)"

Write-Host "`n=== 4. 上游：换中台 JWT ===" -ForegroundColor Cyan
$link = Invoke-RestMethod "$UP/api/play-link" -WebSession $s
$token = [uri]::UnescapeDataString([regex]::Match($link.url,'token=([^&]+)').Groups[1].Value)
Check '拿到 JWT' ($token.Length -gt 100) "长度 $($token.Length)"
$h = @{ Authorization = "Bearer $token" }
$body = @{ userId=$user; activityId=100301 } | ConvertTo-Json

Write-Host "`n=== 5. 中台：无 token 被拒 ===" -ForegroundColor Cyan
try { Invoke-WebRequest "$MID/api/v1/raffle/activity/draw" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 10 | Out-Null; $noauth = 200 } catch { $noauth = $_.Exception.Response.StatusCode.value__ }
Check '无 token 抽奖' ($noauth -eq 403) "HTTP $noauth"

Write-Host "`n=== 6. 中台：抽奖页要调的三个接口 ===" -ForegroundColor Cyan
$acc = (Invoke-RestMethod "$MID/api/v1/raffle/activity/query_user_activity_account" -Method Post -ContentType 'application/json' -Headers $h -Body $body)
Check '账户额度' ($acc.code -eq '0000') "剩余 $($acc.data.dayCountSurplus)"
$list = (Invoke-RestMethod "$MID/api/v1/raffle/strategy/query_raffle_award_list" -Method Post -ContentType 'application/json' -Headers $h -Body $body)
Check '奖品列表' ($list.code -eq '0000' -and $list.data.Count -eq 8) "$($list.data.Count) 个奖品"
$chinese = ($list.data | Where-Object { $_.awardTitle -match '积分|对话' }).Count
Check '奖品名是中文（编码正确）' ($chinese -eq 8) "$chinese/8 匹配"

Write-Host "`n=== 7. 中台：抽 5 次 ===" -ForegroundColor Cyan
$titles = @()
for ($i=1; $i -le 5; $i++) {
    Start-Sleep -Milliseconds 400
    $d = Invoke-RestMethod "$MID/api/v1/raffle/activity/draw" -Method Post -ContentType 'application/json' -Headers $h -Body $body
    if ($d.code -eq '0000') { $titles += $d.data.awardTitle } else { $titles += "ERR:$($d.code)" }
}
Check '5 次抽奖全部成功' (($titles | Where-Object { $_ -notlike 'ERR*' }).Count -eq 5) ($titles -join ' / ')
Check '只发出积分或对话次数' (($titles | Where-Object { $_ -match '积分|对话' }).Count -eq 5) ''

Write-Host "`n=== 8. 中台 -> 上游：回调入账 ===" -ForegroundColor Cyan
Start-Sleep -Seconds 6
$after = Invoke-RestMethod "$UP/api/me" -WebSession $s
$gained = ($after.credit -ne 30) -or ($after.llmQuota -ne 0)
Check '上游账户有变化' $gained "积分=$($after.credit) 次数=$($after.llmQuota)"
$awardFlows = ($after.flows | Where-Object { $_.kind -like 'AWARD*' }).Count
Check '上游流水有发奖记录' ($awardFlows -ge 1) "$awardFlows 条"

Write-Host "`n=== 9. 运营台 ===" -ForegroundColor Cyan
$op = Invoke-RestMethod "$MID/console/auth/login" -Method Post -ContentType 'application/json' `
        -Body (@{username='admin';password='admin123'}|ConvertTo-Json)
Check '运营登录' ($op.role -eq 'ADMIN') "role=$($op.role)"
$oh = @{ Authorization = "Bearer $($op.token)" }

try { Invoke-WebRequest "$MID/console/v1/dashboard/overview" -Method Post -TimeoutSec 10 | Out-Null; $consoleNoAuth = 200 } catch { $consoleNoAuth = $_.Exception.Response.StatusCode.value__ }
Check '无 token 访问看板' ($consoleNoAuth -eq 403) "HTTP $consoleNoAuth"

$dash = Invoke-RestMethod "$MID/console/v1/dashboard/overview?strategyId=100006" -Method Post -Headers $oh
Check '看板-库存对账' ($null -ne $dash.stock) "SKU=$($dash.stock.skuCount) 不一致=$($dash.stock.inconsistentCount)"
$aw = Invoke-RestMethod "$MID/console/v1/awards/recent?limit=5" -Headers $oh
Check '发奖记录' ($aw.code -eq '0000') "$($aw.count) 条"
$val = Invoke-RestMethod "$MID/console/v1/strategy/100006/validate" -Method Post -Headers $oh
Check '策略校验' ($val.errorCount -eq 0) "错误 $($val.errorCount) 警告 $($val.warnCount)"

Write-Host "`n=== 10. 前端页面可达 ===" -ForegroundColor Cyan
foreach ($p in @('/', '/play', '/console', '/console/login', '/console/dashboard', '/console/strategy', '/console/awards')) {
    try { $st = (Invoke-WebRequest "$WEB$p" -TimeoutSec 40).StatusCode } catch { $st = 0 }
    Check "页面 $p" ($st -eq 200) "HTTP $st"
}
try { $upSt = (Invoke-WebRequest "$UP/" -MaximumRedirection 0 -TimeoutSec 10).StatusCode } catch { $upSt = $_.Exception.Response.StatusCode.value__ }
Check '上游首页未登录重定向' ($upSt -eq 302) "HTTP $upSt"


Write-Host "`n=== 11. 权益中心：过期回收 / 对账 / 到期提醒 ===" -ForegroundColor Cyan
$cu = 'c' + (Get-Date -Format 'HHmmss')
$cs = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = Invoke-RestMethod "$UP/api/register" -Method Post -ContentType 'application/json' `
        -Body (@{username=$cu;password='test123456';nickname='权益验收'}|ConvertTo-Json) -WebSession $cs
$me0 = Invoke-RestMethod "$UP/api/me" -WebSession $cs
Check '注册赠送进入权益中心' ($me0.credit -eq 20) "credit=$($me0.credit)"

$null = Invoke-RestMethod "$UP/api/sign" -Method Post -ContentType 'application/json' -Body '{}' -WebSession $cs
$sign2 = Invoke-RestMethod "$UP/api/sign" -Method Post -ContentType 'application/json' -Body '{}' -WebSession $cs
$me1 = Invoke-RestMethod "$UP/api/me" -WebSession $cs
Check '签到只发一次积分（不双发）' ($me1.credit -eq 30) "credit=$($me1.credit)"
Check '重复签到被拦' ($sign2.ok -eq $false) $sign2.reason

$exp = Open-Call 'POST' '/open/v1/credit/grant' @{ userId=$cu; scene='task'; amount=5; eventId="expire:$cu"; ttlDays=-1 } $null
Check '造「立即过期」批次' ($exp.code -eq '0000' -and $exp.granted -eq $true) "balance=$($exp.balance)"
$run = Open-Call 'POST' '/open/v1/credit/expire-run' $null $null
Check '过期回收执行' ($run.code -eq '0000' -and $run.expired -ge 1) "回收 $($run.expired) 个批次"
$bal = Open-Call 'GET' '/open/v1/credit/balance' $null @{ userId=$cu }
Check '到期后余额减少' ($bal.balance -eq 30) "balance=$($bal.balance)"
$rec = Open-Call 'GET' '/open/v1/credit/records' $null @{ userId=$cu; limit=10 }
$expireCount = @($rec.records | Where-Object { $_.type -eq 'expire' }).Count
Check '出现 expire 流水' ($expireCount -ge 1) "$expireCount 条"

$null = Open-Call 'POST' '/open/v1/credit/grant' @{ userId=$cu; scene='task'; amount=7; eventId="soon:$cu"; ttlDays=1 } $null
$bal2 = Open-Call 'GET' '/open/v1/credit/balance' $null @{ userId=$cu }
Check '余额返回即将过期提示' ($bal2.expiringSoon -ge 7) "expiringSoon=$($bal2.expiringSoon) 最近到期=$($bal2.nearestExpireAt)"
$me2 = Invoke-RestMethod "$UP/api/me" -WebSession $cs
Check '主站透出到期提醒' ($me2.expiringSoon -ge 7) "expiringSoon=$($me2.expiringSoon)"

$rc = Open-Call 'GET' '/open/v1/credit/reconcile' $null @{ userId=$cu }
Check '单用户对账一致' ($rc.consistent -eq $true) "账户=$($rc.accountBalance) 批次=$($rc.batchRemaining) 流水=$($rc.ledgerSum)"
$rall = Open-Call 'POST' '/open/v1/credit/reconcile-run' $null $null
Check '全量对账无差异' ($rall.code -eq '0000' -and $rall.inconsistent -eq 0) "扫描 $($rall.scanned) 不一致 $($rall.inconsistent)"

Write-Host "`n=== 12. 支付闭环：下单 / 回调幂等 / 异步发放 / 关单 ===" -ForegroundColor Cyan
$pu = 'p' + (Get-Date -Format 'HHmmss')
$ps = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = Invoke-RestMethod "$UP/api/register" -Method Post -ContentType 'application/json' `
        -Body (@{username=$pu;password='test123456';nickname='支付验收'}|ConvertTo-Json) -WebSession $ps
$po = "ORD$pu"
$ord = Open-Call 'POST' '/open/v1/pay/order' @{ userId=$pu; sku='credit_100'; orderNo=$po } $null
Check '下单成功' ($ord.code -eq '0000' -and $ord.state -eq 'created') "state=$($ord.state) 应付=$($ord.amountCent)分"
$ord2 = Open-Call 'POST' '/open/v1/pay/order' @{ userId=$pu; sku='credit_100'; orderNo=$po } $null
Check '重复下单幂等' ($ord2.orderNo -eq $po -and $ord2.state -eq 'created') "state=$($ord2.state)"
$prod = Open-Call 'GET' '/open/v1/pay/products' $null $null
Check '商品目录可用' (@($prod.products.PSObject.Properties).Count -ge 3) "$(@($prod.products.PSObject.Properties).Count) 个商品"
$bad = Open-Call 'POST' '/open/v1/pay/callback' @{ outTradeNo="BAD-$po"; orderNo=$po; amountCent=1; notifySeq=1 } $null
Check '金额不符被拒' ($bad.accepted -eq $false) $bad.reason
$cb = Open-Call 'POST' '/open/v1/pay/callback' @{ outTradeNo="MOCK-$po"; orderNo=$po; amountCent=100; notifySeq=1 } $null
Check '支付回调受理' ($cb.accepted -eq $true -and $cb.state -eq 'paid') "state=$($cb.state)"
Start-Sleep -Seconds 3
$oq = Open-Call 'GET' '/open/v1/pay/order' $null @{ orderNo=$po; userId=$pu }
Check '权益异步发放完成' ($oq.state -eq 'granted') "state=$($oq.state)"
$pb = Open-Call 'GET' '/open/v1/credit/balance' $null @{ userId=$pu }
Check '积分到账（20 注册 + 100 购买）' ($pb.balance -eq 120) "balance=$($pb.balance)"
$cb2 = Open-Call 'POST' '/open/v1/pay/callback' @{ outTradeNo="MOCK-$po"; orderNo=$po; amountCent=100; notifySeq=2 } $null
Check '重复回调幂等' ($cb2.duplicated -eq $true) $cb2.reason
$pb2 = Open-Call 'GET' '/open/v1/credit/balance' $null @{ userId=$pu }
Check '重复回调不重复入账' ($pb2.balance -eq 120) "balance=$($pb2.balance)"
$poT = "$po-T"
$null = Open-Call 'POST' '/open/v1/pay/order' @{ userId=$pu; sku='credit_500'; orderNo=$poT } $null
$close = Open-Call 'POST' '/open/v1/pay/close-timeout' $null @{ timeoutMinutes=-1 }
$oqT = Open-Call 'GET' '/open/v1/pay/order' $null @{ orderNo=$poT; userId=$pu }
Check '超时关单' ($oqT.state -eq 'closed') "state=$($oqT.state) 本轮关闭 $($close.closed) 单"
$cbT = Open-Call 'POST' '/open/v1/pay/callback' @{ outTradeNo="MOCK-$poT"; orderNo=$poT; amountCent=400; notifySeq=1 } $null
Check '已关闭订单拒绝回调' ($cbT.accepted -eq $false) $cbT.reason

Write-Host "`n=== 13. 支付退款与三方对账 ===" -ForegroundColor Cyan
$ru = 'r' + (Get-Date -Format 'HHmmss')
$rs = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = Invoke-RestMethod "$UP/api/register" -Method Post -ContentType 'application/json' `
        -Body (@{username=$ru;password='test123456';nickname='退款验收'}|ConvertTo-Json) -WebSession $rs
$ro = "RORD$ru"
$null = Open-Call 'POST' '/open/v1/pay/order' @{ userId=$ru; sku='credit_100'; orderNo=$ro } $null
$null = Open-Call 'POST' '/open/v1/pay/callback' @{ outTradeNo="MOCK-$ro"; orderNo=$ro; amountCent=100; notifySeq=1 } $null
Start-Sleep -Seconds 3
$ref = Open-Call 'POST' '/open/v1/pay/refund' @{ orderNo=$ro; userId=$ru } $null
Check '全额退款（未使用全退）' ($ref.refundedCent -eq 100 -and $ref.reversed -eq 100) "冲正=$($ref.reversed) 退款=$($ref.refundedCent)分"
$rb = Open-Call 'GET' '/open/v1/credit/balance' $null @{ userId=$ru }
Check '退款后余额回到注册赠送' ($rb.balance -eq 20) "balance=$($rb.balance)"
$ref2 = Open-Call 'POST' '/open/v1/pay/refund' @{ orderNo=$ro; userId=$ru } $null
Check '重复退款幂等' ($ref2.duplicated -eq $true) "state=$($ref2.state)"
$ro2 = "RORD2$ru"
$null = Open-Call 'POST' '/open/v1/pay/order' @{ userId=$ru; sku='credit_500'; orderNo=$ro2 } $null
$null = Open-Call 'POST' '/open/v1/pay/callback' @{ outTradeNo="MOCK-$ro2"; orderNo=$ro2; amountCent=400; notifySeq=1 } $null
Start-Sleep -Seconds 3
$null = Open-Call 'POST' '/open/v1/credit/consume' @{ userId=$ru; scene='chat'; amount=100; bizNo="rc-$ru" } $null
$refp = Open-Call 'POST' '/open/v1/pay/refund' @{ orderNo=$ro2; userId=$ru } $null
Check '部分退款按未消耗折算' ($refp.reversed -gt 0 -and $refp.refundedCent -gt 0 -and $refp.refundedCent -lt 400) "冲正=$($refp.reversed) 退款=$($refp.refundedCent)/400分"
$prc = Open-Call 'POST' '/open/v1/pay/reconcile-run' $null $null
Check '支付三方对账无差异' ($prc.inconsistent -eq 0) "扫描 $($prc.scanned) 单，不一致 $($prc.inconsistent)"
Write-Host ("`n===== 结果：{0} 通过，{1} 失败 =====" -f $pass, $fail) -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Yellow' })
