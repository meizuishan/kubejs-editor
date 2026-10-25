param([Parameter(Mandatory)][ValidatePattern('^run-v[0-9]+$')][string]$RunName,[string]$InstancePath='D:\PCL\.minecraft\versions\Beebeeblock - The Skyhive')
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw 'Use PowerShell 7.'}
$projectRoot=Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'Prepare-Compatibility.ps1') -RunName $RunName -Profile core
$client=Join-Path $projectRoot ($RunName+'/client')
$mods=Join-Path $client 'mods';New-Item -ItemType Directory -Force -Path $mods | Out-Null
$manifest=@()
foreach($entry in (Get-Content -LiteralPath (Join-Path $projectRoot 'compatibility-0.15.lock.json') -Raw | ConvertFrom-Json)){
    $source=Join-Path $InstancePath ('mods/'+$entry.name)
    $hash=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    if($hash -ne $entry.sha256){throw ('Runtime dependency differs from lock: '+$entry.name)}
    Copy-Item -LiteralPath $source -Destination $mods
    $manifest+=@{name=$entry.name;sha256=$hash}
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $client 'compat015-mods.json') -Encoding utf8NoBOM
$scripts=Join-Path $client 'kubejs/server_scripts';New-Item -ItemType Directory -Force -Path $scripts | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'examples/verification/generic015.js') -Destination (Join-Path $scripts 'framework.js')
@{schemaVersion=2;recipes=@{};items=@{};configs=@();assets=@{};scripts=@{}} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $client 'kubejs/config/kubejs_editor/project.json') -Encoding utf8NoBOM
Write-Output "Prepared $RunName for one framework verification session. No player world or machine-specific authoring fixture."
