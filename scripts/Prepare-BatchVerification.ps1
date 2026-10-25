param([Parameter(Mandatory)][ValidatePattern('^run-v[0-9]+$')][string]$RunName,[string]$InstancePath='D:\PCL\.minecraft\versions\Beebeeblock - The Skyhive')
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw 'Use PowerShell 7.'}
& (Join-Path $PSScriptRoot 'Prepare-Compatibility.ps1') -RunName $RunName -Profile core
$projectRoot=Split-Path $PSScriptRoot -Parent
$client=Join-Path $projectRoot ($RunName+'/client')
$mods=Join-Path $client 'mods';New-Item -ItemType Directory -Force -Path $mods | Out-Null
$manifest=@()
foreach($entry in (Get-Content -LiteralPath (Join-Path $projectRoot 'compatibility-batch.lock.json') -Raw | ConvertFrom-Json)){
    $source=Join-Path $InstancePath ('mods/'+$entry.name)
    $hash=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    if($hash -ne $entry.sha256){throw ('Runtime dependency differs from lock: '+$entry.name)}
    Copy-Item -LiteralPath $source -Destination $mods
    $manifest+=@{name=$entry.name;sha256=$hash}
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $client 'batch-mods.json') -Encoding utf8NoBOM
Write-Output "Run prepareBatchVerification -PclientDir=$RunName/client before starting the single isolated client."
