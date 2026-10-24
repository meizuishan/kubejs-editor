param([Parameter(Mandatory)][ValidatePattern('^run-v[0-9]+$')][string]$RunName,[Parameter(Mandatory)][string]$InstancePath)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw '请使用 PowerShell 7'}
$projectRoot=Split-Path $PSScriptRoot -Parent
$client=Join-Path $projectRoot ($RunName+'/client')
if(Test-Path -LiteralPath $client){throw '请使用全新的隔离目录'}
$mods=Join-Path $client 'mods';New-Item -ItemType Directory -Path $mods -Force | Out-Null
$manifest=@(foreach($file in Get-ChildItem -LiteralPath (Join-Path $InstancePath 'mods') -File -Filter '*.jar'){
    if($file.Name -match '^(abyss-kjs-editor|kjs-editor|kubejs-editor|kubejs-neoforge|rhino-|jei-).*\.jar$'){continue}
    Copy-Item -LiteralPath $file.FullName -Destination $mods
    @{name=$file.Name;sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
})
# Explicit content/configuration allow-list: no saves, account files, launch profiles or caches.
foreach($dir in @('config','defaultconfigs','kubejs','datapacks','resourcepacks','fancymenu_data','moonlight-global-datapacks','patchouli_books','skyblockbuilder','tlm_custom_pack')){
    $source=Join-Path $InstancePath $dir
    if(Test-Path -LiteralPath $source){Copy-Item -LiteralPath $source -Destination (Join-Path $client $dir) -Recurse}
}
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $client 'pack-mods.json') -Encoding utf8NoBOM
@"
lang:zh_cn
guiScale:2
maxFps:30
renderDistance:2
simulationDistance:5
tutorialStep:none
"@ | Set-Content -LiteralPath (Join-Path $client 'options.txt') -Encoding utf8NoBOM
Write-Output "Prepared $RunName with $($manifest.Count) target jars; KubeJS/Rhino/JEI supplied by pinned Gradle coordinates."
