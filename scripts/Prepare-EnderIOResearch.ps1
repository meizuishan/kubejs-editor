param([Parameter(Mandatory)][ValidatePattern('^run-v[0-9]+$')][string]$RunName,[Parameter(Mandatory)][string]$InstancePath)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'Prepare-Compatibility.ps1') -RunName $RunName -Profile core
$client=Join-Path $projectRoot ($RunName+'/client')
$mods=Join-Path $client 'mods';New-Item -ItemType Directory -Force -Path $mods | Out-Null
foreach($entry in (Get-Content -LiteralPath (Join-Path $projectRoot 'compatibility-research.lock.json') -Raw | ConvertFrom-Json)){
    $source=Join-Path $InstancePath ('mods/'+$entry.name)
    if((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $entry.sha256){throw ('Research dependency differs from lock: '+$entry.name)}
    Copy-Item -LiteralPath $source -Destination $mods
}
Copy-Item -LiteralPath (Join-Path $projectRoot 'examples/research/enderio-alloy-skyhive.json') -Destination (Join-Path $client 'research-request.json')
@{schemaVersion=2;recipes=@{};items=@{};configs=@();assets=@{};scripts=@{}} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $client 'kubejs/config/kubejs_editor/project.json') -Encoding utf8NoBOM
Write-Output "Prepared $RunName for an isolated Ender IO research transaction; no production state or player saves copied."
