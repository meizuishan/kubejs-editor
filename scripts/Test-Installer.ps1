$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$buildText=Get-Content -LiteralPath (Join-Path $projectRoot 'build.gradle') -Raw
if($buildText -notmatch "(?m)^version\s*=\s*'([^']+)'"){throw 'Cannot determine version'}
$name="kubejs-editor-$($Matches[1]).jar"
$root=Join-Path $projectRoot ('build/installer-tests/'+[guid]::NewGuid().ToString())
$release=Join-Path $root 'release';New-Item -ItemType Directory -Force -Path $release | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot ('build/libs/'+$name)) -Destination (Join-Path $release $name)
((Get-FileHash -LiteralPath (Join-Path $release $name)).Hash+'  '+$name) | Set-Content -LiteralPath (Join-Path $release 'SHA256SUMS.txt')
$checks=0
function Check([bool]$Value,[string]$Message){if(!$Value){throw $Message};$script:checks++}
function Fixture([string]$Name){
    $folder=Join-Path $root $Name;New-Item -ItemType Directory -Force -Path (Join-Path $folder 'mods') | Out-Null
    foreach($old in @('abyss-kjs-editor-0.7.0.jar','kjs-editor-0.10.0.jar','kubejs-editor-0.10.0.jar')){'old jar' | Set-Content -LiteralPath (Join-Path $folder ('mods/'+$old))}
    $config=Join-Path $folder 'kubejs/config/abyss_editor';New-Item -ItemType Directory -Force -Path $config | Out-Null
    @{schemaVersion=2;recipes=@{};items=@{};configs=@();assets=@{};scripts=@{}} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $config 'project.json')
    return $folder
}
$installer=Join-Path $PSScriptRoot 'Install-Release.ps1'
$folder=Fixture 'empty'
& pwsh.exe -NoProfile -File $installer -InstancePath $folder -ReleaseDir $release *> (Join-Path $root 'success.log')
Check ($LASTEXITCODE -eq 0) 'Installer failed on empty workspace'
Check (@(Get-ChildItem -LiteralPath (Join-Path $folder 'mods') -Filter '*.jar').Count -eq 1) 'Duplicate mod jars remain'
Check ((Get-FileHash -LiteralPath (Join-Path $folder ('mods/'+$name))).Hash -eq (Get-FileHash -LiteralPath (Join-Path $release $name)).Hash) 'Installed jar hash differs'
Check (Test-Path -LiteralPath (Join-Path $folder 'kubejs/config/kubejs_editor/project.json')) 'Migration not installed'
& pwsh.exe -NoProfile -File $installer -InstancePath $folder -ReleaseDir $release *> (Join-Path $root 'idempotent.log')
Check ($LASTEXITCODE -eq 0) 'Identical installation is not idempotent'
$folder=Fixture 'nonempty';$project=Join-Path $folder 'kubejs/config/abyss_editor/project.json'
'{"schemaVersion":2,"recipes":{},"items":{"kubejs:keep":{"id":"kubejs:keep"}},"configs":[],"assets":{},"scripts":{}}' | Set-Content -LiteralPath $project
$hash=(Get-FileHash -LiteralPath $project).Hash
& pwsh.exe -NoProfile -File $installer -InstancePath $folder -ReleaseDir $release *> (Join-Path $root 'refusal.log')
Check ($LASTEXITCODE -ne 0) 'Nonempty workspace accepted'
Check ((Get-FileHash -LiteralPath $project).Hash -eq $hash) 'Rejected migration changed content'
Check (@(Get-ChildItem -LiteralPath (Join-Path $folder 'mods') -Filter '*.jar').Count -eq 3) 'Rejected installation changed old jars'
Check (!(Test-Path -LiteralPath (Join-Path $folder ('mods/'+$name)))) 'New jar installed after migration refusal'
Write-Output "PASS: $checks installation, hash, old-jar detection, idempotency and rejection checks. Fixtures: $root"
# Expected rejection cases launch a child process with exit code 1. Do not leak
# that deliberate failure into CI's enclosing PowerShell step after all checks pass.
$global:LASTEXITCODE=0
