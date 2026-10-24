param([Parameter(Mandatory)][ValidatePattern('^run-v[0-9]+$')][string]$RunName,[Parameter(Mandatory)][string]$InstancePath)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'Prepare-Compatibility.ps1') -RunName $RunName -Profile core
$client=Join-Path $projectRoot ($RunName+'/client')
$mods=Join-Path $client 'mods';New-Item -ItemType Directory -Path $mods -Force | Out-Null
foreach($entry in (Get-Content -LiteralPath (Join-Path $projectRoot 'compatibility-simple.lock.json') -Raw | ConvertFrom-Json)){
    $file=Join-Path $InstancePath ('mods/'+$entry.name)
    if((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $entry.sha256){throw ('目标配方依赖与锁定版本不同：'+$entry.name)}
    Copy-Item -LiteralPath $file -Destination $mods
}
$config=Join-Path $client 'kubejs/config/kubejs_editor/project.json'
@{schemaVersion=2;recipes=@{};items=@{};configs=@();assets=@{};scripts=@{}} | ConvertTo-Json | Set-Content -LiteralPath $config -Encoding utf8NoBOM
$data=Join-Path $client 'kubejs/data/kubejs_editor/recipe'
New-Item -ItemType Directory -Path $data -Force | Out-Null
@'
{"type":"actuallyadditions:crushing","ingredient":{"item":"minecraft:stone"},"result":[{"result":{"count":1,"id":"minecraft:gravel"}}],"neoforge:conditions":[{"type":"neoforge:mod_loaded","modid":"actuallyadditions"}]}
'@ | Set-Content -LiteralPath (Join-Path $data 'conditional.json') -Encoding utf8NoBOM
@'
{"type":"actuallyadditions:crushing","ingredient":[{"item":"minecraft:stone"},{"item":"minecraft:dirt"}],"result":[{"result":{"count":1,"id":"minecraft:gravel"}}]}
'@ | Set-Content -LiteralPath (Join-Path $data 'alternative.json') -Encoding utf8NoBOM
$scripts=Join-Path $client 'kubejs/server_scripts'
New-Item -ItemType Directory -Path $scripts -Force | Out-Null
@'
ServerEvents.recipes(event => event.custom({type:'actuallyadditions:crushing',ingredient:{item:'minecraft:diorite'},result:[{result:{count:2,id:'minecraft:gravel'}}]}).id('kubejs_editor:runtime_simple'))
'@ | Set-Content -LiteralPath (Join-Path $scripts 'simple-verification.js') -Encoding utf8NoBOM
Write-Output "Prepared $RunName with pinned target machine mods; no player saves or accounts copied."
