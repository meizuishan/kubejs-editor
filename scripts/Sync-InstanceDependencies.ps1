param([string]$InstancePath='D:\Abyss\Creat Abyss\versions\1.21.1-NeoForge_21.1.259')
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw '请使用 PowerShell 7'}
$projectRoot=Split-Path $PSScriptRoot -Parent
$lock=Get-Content -LiteralPath (Join-Path $projectRoot 'dependencies.lock.json') -Raw | ConvertFrom-Json
foreach($entry in $lock){
    $found=@(Get-ChildItem -LiteralPath (Join-Path $InstancePath 'mods') -File | Where-Object {$_.Name.EndsWith($entry.sourceSuffix,[StringComparison]::OrdinalIgnoreCase)})
    if($found.Count -ne 1){throw "无法唯一定位依赖：$($entry.sourceSuffix)"}
    $hash=(Get-FileHash -LiteralPath $found[0].FullName -Algorithm SHA256).Hash
    if($hash -ne $entry.sha256){throw "依赖内容与锁定版本不一致：$($entry.sourceSuffix)"}
    $destination=Join-Path $projectRoot $entry.target
    New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
    Copy-Item -LiteralPath $found[0].FullName -Destination $destination
}
Write-Output '已同步并校验锁定的本机依赖。'
