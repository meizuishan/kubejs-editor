param(
    [string]$InstancePath='D:\Abyss\Creat Abyss\versions\1.21.1-NeoForge_21.1.259',
    [string]$ReleaseDir=$PSScriptRoot
)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw '请使用 PowerShell 7'}
$instance=[IO.Path]::GetFullPath($InstancePath)
$mods=Join-Path $instance 'mods'
if(!(Test-Path -LiteralPath $mods -PathType Container)){throw "找不到实例 mods 目录：$mods"}
$release=[IO.Path]::GetFullPath($ReleaseDir)
$name='kubejs-editor-0.12.0.jar'
$source=Join-Path $release $name
$expected=((Get-Content -LiteralPath (Join-Path $release 'SHA256SUMS.txt') | Where-Object {$_ -match ('\s'+[regex]::Escape($name)+'$')}) -split '\s+')[0]
if(!$expected -or (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $expected){throw '发布 Jar 校验失败'}
function Assert-GameClosed {
    $running=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -and ($_.CommandLine.IndexOf($instance,[StringComparison]::OrdinalIgnoreCase) -ge 0 -or ($_.CommandLine -match 'net\.minecraft\.client\.main\.Main' -and $_.CommandLine -notmatch '--gameDir[ =]'))})
    if($running.Count){throw '当前 Minecraft 实例正在运行，请关闭后重新安装；安装器不会停止游戏。'}
}
Assert-GameClosed
. (Join-Path $PSScriptRoot 'Migrate-Workspace.ps1') -InstancePath $InstancePath
$migrationBackup=$null
$old=@(Get-ChildItem -LiteralPath $mods -File -Filter '*.jar' | Where-Object {$_.Name -like 'abyss-kjs-editor-*.jar' -or $_.Name -like 'kjs-editor-*.jar' -or $_.Name -like 'kubejs-editor-*.jar'})
$destination=Join-Path $mods $name
if(Test-Path -LiteralPath $destination -PathType Container){throw '目标 Jar 路径是目录，未修改实例。'}
if(!(Test-Path -LiteralPath (Join-Path $instance 'kubejs/config/abyss_editor')) -and $old.Count -eq 1 -and $old[0].Name -eq $name -and (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -eq $expected){Write-Output "已安装相同版本：$destination";exit 0}
$backup=Join-Path $release ('installed-backups/'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Force -Path $backup | Out-Null
$staged=Join-Path $mods ($name+'.pending')
$moved=@()
$installed=$false
try {
    Copy-Item -LiteralPath $source -Destination $staged
    if((Get-FileHash -LiteralPath $staged -Algorithm SHA256).Hash -ne $expected){throw '暂存 Jar 校验失败'}
    Assert-GameClosed
    $migrationBackup=Invoke-EditorWorkspaceMigration $instance
    foreach($file in $old){
        $saved=Join-Path $backup ($file.Name+'.disabled')
        Move-Item -LiteralPath $file.FullName -Destination $saved
        $moved+=@{Original=$file.FullName;Saved=$saved}
    }
    Move-Item -LiteralPath $staged -Destination $destination
    $installed=$true
    if((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -ne $expected){throw '安装后校验失败'}
} catch {
    $failure=$_
    if(Test-Path -LiteralPath $staged){Move-Item -LiteralPath $staged -Destination (Join-Path $backup 'failed-staged.jar.disabled')}
    if($installed -and (Test-Path -LiteralPath $destination)){Move-Item -LiteralPath $destination -Destination (Join-Path $backup 'failed-new.jar.disabled')}
    foreach($file in $moved){if(Test-Path -LiteralPath $file.Saved){Move-Item -LiteralPath $file.Saved -Destination $file.Original}}
    if($migrationBackup){Undo-EditorWorkspaceMigration $instance $migrationBackup}
    throw $failure
}
Write-Output "已安装：$destination"
Write-Output "旧 Jar 备份：$backup"
if($migrationBackup){Write-Output "编辑器迁移备份：$migrationBackup"}
Write-Output '玩家存档、账号、现有脚本和世界生成数据未更改。'
