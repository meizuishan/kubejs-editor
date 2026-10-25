param([Parameter(Mandatory)][ValidatePattern('^run-v[0-9]+$')][string]$RunName,[string]$InstancePath='D:\PCL\.minecraft\versions\All the Mods 10 - ATM10')
$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'Prepare-Compatibility.ps1') -RunName $RunName -Profile core
$root=Split-Path $PSScriptRoot -Parent;$client=Join-Path $root ($RunName+'/client')
New-Item -ItemType Directory -Force -Path (Join-Path $client 'mods') | Out-Null
$manifest=@()
foreach($entry in Get-Content -LiteralPath (Join-Path $root 'compatibility-registry.lock.json') -Raw | ConvertFrom-Json){$file=Join-Path $InstancePath ('mods/'+$entry.name);if((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $entry.sha256){throw ('Dependency changed: '+$entry.name)};Copy-Item -LiteralPath $file -Destination (Join-Path $client 'mods');$manifest+=$entry}
$manifest | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $client 'registry-mods.json') -Encoding utf8NoBOM
@{schemaVersion=2;recipes=@{};items=@{};configs=@();assets=@{};scripts=@{}} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $client 'kubejs/config/kubejs_editor/project.json') -Encoding utf8NoBOM
$data=Join-Path $client 'kubejs/data/kubejs_editor_verification/kubejs_editor_verification/process'
New-Item -ItemType Directory -Force -Path $data | Out-Null
'{"input":{"item":"minecraft:iron_ingot"},"result":{"id":"minecraft:gold_ingot","components":{"minecraft:custom_data":{"native":true}}},"duration":40}' | Set-Content (Join-Path $data 'base.json') -Encoding utf8NoBOM
Write-Output "Prepared ${RunName}: native registries without forbidden_arcanus_js. No screenshots or player world copied."
