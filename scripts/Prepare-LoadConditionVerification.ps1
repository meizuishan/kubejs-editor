param([Parameter(Mandatory)][ValidatePattern('^run-v[0-9]+$')][string]$RunName)
$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'Prepare-Compat015Verification.ps1') -RunName $RunName
$projectRoot=Split-Path $PSScriptRoot -Parent
$client=Join-Path $projectRoot ($RunName+'/client')
# The general framework already supplies the typed recipe. Add native conditional data sources.
$data=@{type='kubejs_editor_verification:generic';feedstock=@{ingredient=@{item='minecraft:iron_ingot'};count=20480};bath=@{ingredient=@{fluid='minecraft:water'};amount=250};product=@{id='minecraft:gold_ingot';count=2};extra=@{key='kubejs_editor_verification:red';quantity=4};settings=@{work=40;enabled=$true};conditions=@{work=3;enabled=$false}}
$recipes=Join-Path $client 'kubejs/data/kubejs_editor_verification/recipe'
New-Item -ItemType Directory -Force -Path $recipes | Out-Null
$data['neoforge:conditions']=@(@{type='kubejs_editor_verification:feature';enabled=$true})
$data | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $recipes 'conditional_live.json') -Encoding utf8NoBOM
$data['neoforge:conditions']=@(@{type='kubejs_editor_verification:feature';enabled=$false})
$data | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $recipes 'conditional_source.json') -Encoding utf8NoBOM
Write-Output "Prepared ${RunName}: one generic serializer, registered custom condition and true/false datapack sources; no player data."
