param([string]$InstancePath,[string]$RestoreBackup)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw 'Use PowerShell 7.'}

function Assert-EditorMigrationGameClosed([string]$Root) {
    $running=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -and ($_.CommandLine.IndexOf($Root,[StringComparison]::OrdinalIgnoreCase) -ge 0 -or ($_.CommandLine -match 'net\.minecraft\.client\.main\.Main' -and $_.CommandLine -notmatch '--gameDir[ =]'))})
    if($running.Count){throw 'Minecraft is running; close it before migrating. No game process will be stopped.'}
}

function Get-EditorCheckedPath([string]$Root,[string]$Relative) {
    $absoluteRoot=[IO.Path]::GetFullPath($Root).TrimEnd('\')
    $target=[IO.Path]::GetFullPath((Join-Path $absoluteRoot $Relative))
    if(!$target.StartsWith($absoluteRoot+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Migration path escaped instance'}
    $cursor=$target
    while($cursor -and $cursor -ne $absoluteRoot){
        if((Test-Path -LiteralPath $cursor) -and ((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw "Migration refuses linked path: $cursor"}
        $cursor=Split-Path $cursor -Parent
    }
    return $target
}
function Assert-EditorWorkspaceEmpty([string]$Folder) {
    foreach($name in @('project.json','state.json')){
        $file=Join-Path $Folder $name
        if(!(Test-Path -LiteralPath $file)){continue}
        $data=Get-Content -LiteralPath $file -Raw | ConvertFrom-Json -AsHashtable
        if($data.schemaVersion -notin @(1,2)){throw "Unknown editor schema: $file"}
        if($name -eq 'state.json'){
            if($data.activation -or $data.manualFiles.Count -or $data.frozen.Count -or $data.paths.Count){throw "Editor ownership or transaction is not empty: $file"}
            foreach($owned in $data.managedHashes.Keys){if($owned -notin @('kubejs/config/abyss_editor/applied-items.json','kubejs/config/kubejs_editor/applied-items.json')){throw "Existing managed content: $owned"}}
            $data=$data.applied
        }
        if(!$data -or !$data.ContainsKey('items') -or !$data.ContainsKey('recipes')){throw "Incomplete editor workspace: $file"}
        foreach($key in @('items','recipes','configs','assets','scripts')){if($data[$key].Count){throw "Existing editor content in $file ($key); migration stopped without clearing it"}}
    }
    $list=Join-Path $Folder 'applied-items.json'
    if(Test-Path -LiteralPath $list){$registered=Get-Content -LiteralPath $list -Raw | ConvertFrom-Json -NoEnumerate;if($null -eq $registered -or $registered -isnot [array] -or $registered.Count){throw 'Existing or invalid registered items; migration stopped'}}
    $baseline=Join-Path $Folder 'resource-baselines.json'
    if((Test-Path -LiteralPath $baseline) -and (Get-Content -LiteralPath $baseline -Raw | ConvertFrom-Json -AsHashtable).Count){throw 'Existing resource overrides; migration stopped'}
}
function Undo-EditorWorkspaceMigration([string]$Root,[string]$Backup) {
    $rootPath=[IO.Path]::GetFullPath($Root).TrimEnd('\')
    Assert-EditorMigrationGameClosed $rootPath
    $backupPath=[IO.Path]::GetFullPath($Backup)
    $checked=Get-EditorCheckedPath $rootPath 'kubejs-editor-migration-backups'
    if(!$backupPath.StartsWith($checked+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Invalid migration backup'}
    $record=Get-Content -LiteralPath (Join-Path $backupPath 'manifest.json') -Raw | ConvertFrom-Json
    if($record.instance -ne $rootPath){throw 'Backup belongs to another instance'}
    $new=Get-EditorCheckedPath $rootPath 'kubejs/config/kubejs_editor'
    if($record.generatedNew -and (Test-Path -LiteralPath $new)){Move-Item -LiteralPath $new -Destination (Join-Path $backupPath ('failed-new-'+[guid]::NewGuid().ToString()))}
    foreach($entry in $record.moved){
        $destination=Get-EditorCheckedPath $rootPath $entry.relative
        $saved=Get-EditorCheckedPath $backupPath $entry.saved
        if(Test-Path -LiteralPath $saved){New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null;Move-Item -LiteralPath $saved -Destination $destination}
    }
    if($record.optionsSaved){Copy-Item -LiteralPath (Join-Path $backupPath 'options.txt') -Destination (Get-EditorCheckedPath $rootPath 'options.txt') -Force}
}
function Invoke-EditorWorkspaceMigration([string]$Root) {
    $rootPath=[IO.Path]::GetFullPath($Root).TrimEnd('\')
    Assert-EditorMigrationGameClosed $rootPath
    $old=Get-EditorCheckedPath $rootPath 'kubejs/config/abyss_editor'
    if(!(Test-Path -LiteralPath $old)){return $null}
    $new=Get-EditorCheckedPath $rootPath 'kubejs/config/kubejs_editor'
    Assert-EditorWorkspaceEmpty $old
    if(Test-Path -LiteralPath $new){Assert-EditorWorkspaceEmpty $new}
    $legacy=@('kubejs/config/abyss_editor','kubejs/server_scripts/abyss_editor','kubejs/startup_scripts/abyss_editor','kubejs/client_scripts/abyss_editor','kubejs/assets/abyss_editor')
    foreach($relative in $legacy[1..4]){$path=Get-EditorCheckedPath $rootPath $relative;if((Test-Path -LiteralPath $path) -and @(Get-ChildItem -LiteralPath $path -Recurse -File -Force).Count){throw "Legacy generated files exist: $relative; migration stopped"}}
    $ui=$null
    foreach($folder in @($new,$old)){$path=Join-Path $folder 'ui.json';if(!$ui -and (Test-Path -LiteralPath $path)){$ui=[IO.File]::ReadAllBytes($path)}}
    $templates=@{}
    foreach($folder in @($old,$new)){$path=Join-Path $folder 'templates';if(Test-Path -LiteralPath $path){foreach($file in Get-ChildItem -LiteralPath $path -Recurse -File){$relative=[IO.Path]::GetRelativePath($path,$file.FullName);if($templates.ContainsKey($relative) -and $templates[$relative] -ne [Convert]::ToBase64String([IO.File]::ReadAllBytes($file.FullName))){throw "Conflicting template: $relative"};$templates[$relative]=[Convert]::ToBase64String([IO.File]::ReadAllBytes($file.FullName))}}}
    $backup=Get-EditorCheckedPath $rootPath ('kubejs-editor-migration-backups/'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff')+'-'+[guid]::NewGuid().ToString())
    New-Item -ItemType Directory -Path $backup | Out-Null
    $options=Get-EditorCheckedPath $rootPath 'options.txt'
    $record=[ordered]@{instance=$rootPath;moved=@();optionsSaved=(Test-Path -LiteralPath $options);generatedNew=$false}
    if($record.optionsSaved){Copy-Item -LiteralPath $options -Destination (Join-Path $backup 'options.txt')}
    $manifest=Join-Path $backup 'manifest.json'
    try{
        foreach($relative in @($legacy)+@('kubejs/config/kubejs_editor')){
            $path=Get-EditorCheckedPath $rootPath $relative
            if(Test-Path -LiteralPath $path){$saved='original/'+$relative;$destination=Get-EditorCheckedPath $backup $saved;New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null;$record.moved+=@{relative=$relative;saved=$saved};$record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifest -Encoding utf8;Move-Item -LiteralPath $path -Destination $destination}
        }
        New-Item -ItemType Directory -Path $new | Out-Null
        $record.generatedNew=$true;$record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifest -Encoding utf8
        $project=[ordered]@{schemaVersion=2;recipes=@{};items=@{};configs=@();assets=@{};scripts=@{}}
        $project | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $new 'project.json') -Encoding utf8
        @{schemaVersion=2;applied=$project;paths=@{};managedHashes=@{};frozen=@{};manualFiles=@()} | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $new 'state.json') -Encoding utf8
        if($ui){[IO.File]::WriteAllBytes((Join-Path $new 'ui.json'),$ui)}
        foreach($relative in $templates.Keys){$path=Get-EditorCheckedPath $new ('templates/'+$relative);New-Item -ItemType Directory -Force -Path (Split-Path $path -Parent) | Out-Null;[IO.File]::WriteAllBytes($path,[Convert]::FromBase64String($templates[$relative]))}
        if($record.optionsSaved){
            $lines=@([IO.File]::ReadAllLines($options));$oldBinding=$lines | Where-Object {$_ -match '^key_key\.abyss_editor\.open:'} | Select-Object -Last 1
            $newBinding=$lines | Where-Object {$_ -match '^key_key\.kubejs_editor\.open:'}
            $lines=@($lines | Where-Object {$_ -notmatch '^key_key\.abyss_editor\.open:'})
            if($oldBinding -and !$newBinding){$lines+=($oldBinding -replace '^key_key\.abyss_editor\.open:','key_key.kubejs_editor.open:')}
            [IO.File]::WriteAllLines($options,$lines,[Text.UTF8Encoding]::new($false))
        }
        $record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifest -Encoding utf8
        return $backup
    } catch {
        $failure=$_;if(Test-Path -LiteralPath $manifest){Undo-EditorWorkspaceMigration $rootPath $backup};throw $failure
    }
}
if($MyInvocation.InvocationName -ne '.'){
    if(!$InstancePath){throw 'InstancePath is required'}
    if($RestoreBackup){Undo-EditorWorkspaceMigration $InstancePath $RestoreBackup}else{Invoke-EditorWorkspaceMigration $InstancePath}
}
