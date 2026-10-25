param([Parameter(Mandatory)][ValidatePattern('^run-v[0-9]+$')][string]$RunName,[ValidateSet('core','full')][string]$Profile='core',[string]$InstancePath='D:\PCL\.minecraft\versions\Beebeeblock - The Skyhive')
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw 'Use PowerShell 7.'}
$projectRoot=Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'Prepare-Compatibility.ps1') -RunName $RunName -Profile $Profile
$client=Join-Path $projectRoot ($RunName+'/client')
$mods=Join-Path $client 'mods';New-Item -ItemType Directory -Force -Path $mods | Out-Null
$manifest=@()
foreach($entry in (Get-Content -LiteralPath (Join-Path $projectRoot 'compatibility-universal.lock.json') -Raw | ConvertFrom-Json)){
    $name=$entry.name
    $source=Join-Path $InstancePath ('mods/'+$name)
    $hash=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    if($hash -ne $entry.sha256){throw ("Universal verification dependency changed: "+$name)}
    $manifest+=@{name=$name;sha256=$hash}
    Copy-Item -LiteralPath $source -Destination $mods
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $client 'universal-mods.json') -Encoding utf8NoBOM
New-Item -ItemType Directory -Force -Path (Join-Path $client 'kubejs/startup_scripts'),(Join-Path $client 'kubejs/server_scripts') | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'examples/verification/universal-startup.js') -Destination (Join-Path $client 'kubejs/startup_scripts/universal.js')
Copy-Item -LiteralPath (Join-Path $projectRoot 'examples/verification/universal-recipes.js') -Destination (Join-Path $client 'kubejs/server_scripts/universal.js')
@{schemaVersion=2;recipes=@{};items=@{};configs=@();assets=@{};scripts=@{}} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $client 'kubejs/config/kubejs_editor/project.json') -Encoding utf8NoBOM
Write-Output "Prepared ${RunName}: native MI / Forbidden Arcanus / Ars Nouveau, isolated test world only."
