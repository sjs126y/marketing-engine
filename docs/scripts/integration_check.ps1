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

Write-Host ("`n===== 结果：{0} 通过，{1} 失败 =====" -f $pass, $fail) -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Yellow' })
