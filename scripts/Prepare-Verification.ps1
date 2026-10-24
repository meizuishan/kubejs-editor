$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw '请使用 PowerShell 7'}
$projectRoot=Split-Path $PSScriptRoot -Parent
$fixtures=Join-Path $projectRoot 'build/verification'
if(!(Test-Path -LiteralPath $fixtures)){throw '请先执行 gradlew.bat verifyEditor'}
foreach($name in @('run-server','run-client')){
    $target=Join-Path $projectRoot $name
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    Get-ChildItem -LiteralPath $fixtures -File -Recurse | ForEach-Object {
        $relative=[IO.Path]::GetRelativePath($fixtures,$_.FullName)
        $destination=Join-Path $target $relative
        New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
        Copy-Item -LiteralPath $_.FullName -Destination $destination
    }
}
$server=Join-Path $projectRoot 'run-server'
'eula=true' | Set-Content -LiteralPath (Join-Path $server 'eula.txt') -Encoding utf8
@"
online-mode=false
server-ip=127.0.0.1
server-port=25585
level-type=minecraft:flat
generate-structures=false
view-distance=2
simulation-distance=2
"@ | Set-Content -LiteralPath (Join-Path $server 'server.properties') -Encoding utf8
$world=Join-Path $server 'world'
$clientWorld=Join-Path $projectRoot 'run-client/saves/EditorVerification'
if((Test-Path -LiteralPath $world) -and !(Test-Path -LiteralPath $clientWorld)){
    New-Item -ItemType Directory -Force -Path (Split-Path $clientWorld -Parent) | Out-Null
    Copy-Item -LiteralPath $world -Destination $clientWorld -Recurse
}
@"
lang:zh_cn
guiScale:2
maxFps:60
renderDistance:2
simulationDistance:5
"@ | Set-Content -LiteralPath (Join-Path $projectRoot 'run-client/options.txt') -Encoding utf8
Write-Output '隔离验证文件已准备。首次客户端验证前先完成一次 runServer。'
