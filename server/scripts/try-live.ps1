# 用真实 AI 连续跑 3 个不同背景，检查生成是否通过校验（需要后端已启动，且已设置 AI_* 环境变量）。
# 用法：在 server 目录执行  .\scripts\try-live.ps1
$ErrorActionPreference = "Stop"
$base = "http://localhost:8080/api"
$cases = @(
  @{ background = "朋友邀请我参加两天项目，我担心能力不足，每天只能投入两小时。"; chosenPath = "拒绝邀请"; unchosenPath = "接受邀请，负责一个小模块"; priority = "希望参与合作，也不想耽误队友" },
  @{ background = "大三，拿到了外地一家公司的实习 offer，但家里希望我留在本地考研，我自己也没想清楚。"; chosenPath = "留在本地准备考研"; unchosenPath = "去外地实习三个月"; priority = "想更清楚自己适合什么工作" },
  @{ background = "和合作了半年的搭档越来越疲惫，比赛还剩两周，我们共享一个项目仓库。"; chosenPath = "坚持做完比赛"; unchosenPath = "中途退出合作"; priority = "不想继续消耗自己，也不想让之前的成果白费" }
)
$health = Invoke-RestMethod "$base/health"
if (-not $health.aiConfigured) { Write-Host "后端显示 AI 未配置，请先设置 AI_ENDPOINT / AI_API_KEY / AI_MODEL 后重启后端"; exit 1 }

$i = 0
foreach ($c in $cases) {
  $i++
  Invoke-RestMethod "$base/reset" -Method Post | Out-Null
  $body = [Text.Encoding]::UTF8.GetBytes((@{ requestId = [guid]::NewGuid().ToString(); mode = "live"; input = $c } | ConvertTo-Json))
  $t0 = Get-Date
  $s = Invoke-RestMethod "$base/sessions" -Method Post -ContentType "application/json; charset=utf-8" -Body $body
  do { Start-Sleep -Milliseconds 800; $s = Invoke-RestMethod "$base/sessions/$($s.id)" } while ($s.status -eq "generating")
  $sec = "{0:N1}" -f ((Get-Date) - $t0).TotalSeconds
  Write-Host ""
  Write-Host "=== 案例 $i：$($c.unchosenPath)  →  $($s.status)（$sec 秒）"
  if ($s.status -eq "error") { Write-Host "错误：$($s.error.code) $($s.error.message)（详情见后端日志）"; continue }
  $st = $s.story
  Write-Host "签题：$($st.title)"
  Write-Host "问签：$($st.question)"
  Write-Host "假设：$($st.assumptions -join ' / ')"
  Write-Host "开场：$($st.opening)"
  Write-Host "节点：$($st.decision)"
  foreach ($o in $st.options) {
    Write-Host "  [$($o.id)] $($o.label)"
    Write-Host "      后续：$($o.outcome)"
    if ($o.reflection) { Write-Host "      回望：$($o.reflection.concern) → $($o.reflection.alternative)" } else { Write-Host "      回望：无" }
    Write-Host "      收据：$($o.receiptDraft.insight) / $($o.receiptDraft.nextStep)"
  }
}
Invoke-RestMethod "$base/reset" -Method Post | Out-Null
Write-Host ""
Write-Host "人工检查：是否从未选道路出发、是否遵守时间/能力限制、是否编造美好结局、回望是否滥用。"
