# 打包项目给队友：只包含 git 已跟踪的文件，自动排除 server/.env（密钥）、target/ 等。
# 用法：在任意目录执行  powershell -File D:\minicamp-dev\server\scripts\package.ps1
# 输出：项目上一级目录下的 minicamp-dev-<日期时间>.zip
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $root

$dirty = git status --porcelain
if ($dirty) {
  Write-Host "注意：有未提交的改动，压缩包只包含已提交的版本："
  Write-Host $dirty
}

$stamp = Get-Date -Format "yyyyMMdd-HHmm"
$out = Join-Path (Split-Path $root -Parent) "minicamp-dev-$stamp.zip"
git archive --format=zip --prefix=minicamp-dev/ -o $out HEAD
if ($LASTEXITCODE -ne 0) { throw "git archive 失败" }

# 再次确认没有打进密钥文件
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($out)
$bad = $zip.Entries | Where-Object { $_.FullName -match '(^|/)\.env$' }
$count = $zip.Entries.Count
$zip.Dispose()
if ($bad) { Remove-Item $out; throw "压缩包里出现了 .env，已删除，请检查 .gitignore" }

Write-Host "已生成：$out（$count 个条目，不含 .env）"
