$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Migrate-Workspace.ps1')
$testRoot=Join-Path (Split-Path $PSScriptRoot -Parent) ('build/migration-tests/'+[guid]::NewGuid().ToString())
New-Item -ItemType Directory -Force -Path $testRoot | Out-Null
$checks=0
function Check([bool]$Value,[string]$Message){if(!$Value){throw $Message};$script:checks++}
function Fixture([string]$Name){
    $folder=Join-Path $testRoot $Name;$legacy=Join-Path $folder 'kubejs/config/abyss_editor';New-Item -ItemType Directory -Force -Path $legacy | Out-Null
    $project=@{schemaVersion=2;recipes=@{};items=@{};configs=@();assets=@{};scripts=@{}}
    $project | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $legacy 'project.json')
    @{schemaVersion=2;applied=$project;paths=@{};managedHashes=@{};frozen=@{};manualFiles=@()} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $legacy 'state.json')
    '[]' | Set-Content -LiteralPath (Join-Path $legacy 'applied-items.json')
    '{"left":0.2,"right":0.25}' | Set-Content -LiteralPath (Join-Path $legacy 'ui.json')
    '{"boot":123}' | Set-Content -LiteralPath (Join-Path $legacy 'restart-required.json')
    'old-backup' | Set-Content -LiteralPath (Join-Path $legacy 'last-transaction.txt')
    @('key_key.abyss_editor.open:key.keyboard.j','unrelated:keep') | Set-Content -LiteralPath (Join-Path $folder 'options.txt')
    return $folder
}
$folder=Fixture 'empty'
$saved=Invoke-EditorWorkspaceMigration $folder
Check (!(Test-Path -LiteralPath (Join-Path $folder 'kubejs/config/abyss_editor'))) 'Legacy directory remains active'
Check (Test-Path -LiteralPath (Join-Path $folder 'kubejs/config/kubejs_editor/ui.json')) 'UI settings not migrated'
Check (!(Test-Path -LiteralPath (Join-Path $folder 'kubejs/config/kubejs_editor/last-transaction.txt'))) 'Old rollback pointer migrated'
Check ((Get-Content -LiteralPath (Join-Path $folder 'options.txt')) -contains 'key_key.kubejs_editor.open:key.keyboard.j') 'Custom key binding lost'
Check ($null -eq (Invoke-EditorWorkspaceMigration $folder)) 'Migration not idempotent'
Undo-EditorWorkspaceMigration $folder $saved
Check (Test-Path -LiteralPath (Join-Path $folder 'kubejs/config/abyss_editor/project.json')) 'Rollback did not restore old workspace'
Check ((Get-Content -LiteralPath (Join-Path $folder 'options.txt')) -contains 'key_key.abyss_editor.open:key.keyboard.j') 'Rollback did not restore options'
foreach($mode in @('pending','activation','script','registered','invalid','ownership')){
    $folder=Fixture $mode;$legacy=Join-Path $folder 'kubejs/config/abyss_editor'
    switch($mode){
        pending { $p=Get-Content -LiteralPath (Join-Path $legacy 'project.json') -Raw | ConvertFrom-Json -AsHashtable;$p.items['kubejs:test']=@{id='kubejs:test'};$p | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $legacy 'project.json') }
        activation { $p=Get-Content -LiteralPath (Join-Path $legacy 'state.json') -Raw | ConvertFrom-Json -AsHashtable;$p.activation=@{restart=$true};$p | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $legacy 'state.json') }
        script { $dir=Join-Path $folder 'kubejs/startup_scripts/abyss_editor';New-Item -ItemType Directory -Force -Path $dir | Out-Null;'custom script' | Set-Content -LiteralPath (Join-Path $dir 'items.js') }
        registered {'["kubejs:existing"]' | Set-Content -LiteralPath (Join-Path $legacy 'applied-items.json')}
        invalid {'broken json' | Set-Content -LiteralPath (Join-Path $legacy 'state.json')}
        ownership {$p=Get-Content -LiteralPath (Join-Path $legacy 'state.json') -Raw | ConvertFrom-Json -AsHashtable;$p.managedHashes['kubejs/assets/any.json']='hash';$p | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $legacy 'state.json')}
    }
    $rejected=$false;try{Invoke-EditorWorkspaceMigration $folder | Out-Null}catch{$rejected=$true}
    Check $rejected "Unsafe migration accepted: $mode"
    Check (Test-Path -LiteralPath $legacy) "Rejected migration changed legacy workspace: $mode"
}
Write-Output "PASS: $checks migration, refusal, idempotency and rollback checks. Fixtures: $testRoot"
