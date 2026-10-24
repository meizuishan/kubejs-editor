param([ValidatePattern('^run-v[0-9]+$')][string]$RunName='run-v2')
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw '请使用 PowerShell 7'}
$projectRoot=Split-Path $PSScriptRoot -Parent
$fixtures=Join-Path $projectRoot 'build/verification'
foreach($name in @('server','client')){
    $target=Join-Path $projectRoot ($RunName+'/'+$name)
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    Get-ChildItem -LiteralPath $fixtures -File -Recurse | ForEach-Object {
        $destination=Join-Path $target ([IO.Path]::GetRelativePath($fixtures,$_.FullName))
        New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
        Copy-Item -LiteralPath $_.FullName -Destination $destination
    }
}
$server=Join-Path $projectRoot ($RunName+'/server')
'eula=true' | Set-Content -LiteralPath (Join-Path $server 'eula.txt') -Encoding utf8
@"
online-mode=false
server-ip=127.0.0.1
server-port=25586
level-type=minecraft:flat
generate-structures=false
view-distance=2
simulation-distance=2
"@ | Set-Content -LiteralPath (Join-Path $server 'server.properties') -Encoding utf8
$client=Join-Path $projectRoot ($RunName+'/client')
@"
lang:zh_cn
guiScale:2
maxFps:60
renderDistance:2
simulationDistance:5
tutorialStep:none
"@ | Set-Content -LiteralPath (Join-Path $client 'options.txt') -Encoding utf8
$mods=Join-Path $client 'mods';New-Item -ItemType Directory -Force -Path $mods | Out-Null
$instance='D:\Abyss\Creat Abyss\versions\1.21.1-NeoForge_21.1.259'
Get-ChildItem -LiteralPath (Join-Path $instance 'mods') -Filter '*.jar' | Where-Object { $_.Name -match '(ftb-(library|chunks|quests|teams)-|InventoryProfilesNext-|libIPN-|kotlinforforge-|architectury-|cloth-config-)' } | ForEach-Object {Copy-Item -LiteralPath $_.FullName -Destination $mods}
$compat=Get-Content -LiteralPath (Join-Path $projectRoot 'compatibility.lock.json') -Raw | ConvertFrom-Json
foreach($entry in $compat){
    $file=Join-Path (Join-Path $instance 'mods') $entry.name
    if((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $entry.sha256){throw "兼容依赖内容已更新，请重新锁定：$($entry.name)"}
    Copy-Item -LiteralPath $file -Destination $mods
}
$compat | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $client 'compatibility-dependencies.json') -Encoding utf8
Write-Output "Fixtures and FTB/IPN compatibility dependencies prepared in $RunName only."
