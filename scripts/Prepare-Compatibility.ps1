param([ValidatePattern('^run-v[0-9]+$')][string]$RunName,[ValidateSet('core','create','dragons','vintage','full')][string]$Profile,[string]$WorldTemplate)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw '请使用 PowerShell 7'}
$projectRoot=Split-Path $PSScriptRoot -Parent
$client=Join-Path $projectRoot ($RunName+'/client')
if(Test-Path -LiteralPath $client){throw '请使用全新的隔离目录，避免旧测试数据影响结果'}
New-Item -ItemType Directory -Force -Path $client | Out-Null
@"
lang:zh_cn
guiScale:2
maxFps:60
renderDistance:2
simulationDistance:5
tutorialStep:none
"@ | Set-Content -LiteralPath (Join-Path $client 'options.txt') -Encoding utf8
$config=Join-Path $client 'kubejs/config/kubejs_editor'
New-Item -ItemType Directory -Force -Path $config | Out-Null
$recipes=[ordered]@{}
foreach($mode in @(@('legacy','minecraft:crafting_shapeless'),@('optional','create:pressing'),@('dragons','create_dragons_plus:ending'))){
 $data=if($mode[0] -eq 'legacy'){@{type=$mode[1];ingredients=@(@{item='minecraft:iron_ingot'});result=@{id='minecraft:oak_button'}}}else{@{type=$mode[1];ingredients=@(@{item='minecraft:iron_ingot'});results=@(@{id='minecraft:iron_nugget'})}}
 $id='compat:'+ $mode[0];$recipes[$id]=@{id=$id;operation='add';enabled=$true;data=$data}
}
@{schemaVersion=1;recipes=$recipes;items=@{};configs=@();assets=@{};scripts=@{}} | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $config 'project.json') -Encoding utf8
$destination=Join-Path $client 'saves/EditorVerification'
New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
if(!$WorldTemplate){$WorldTemplate=Join-Path $projectRoot '.verification-fixtures/world-template.zip'}
if(Test-Path -LiteralPath $WorldTemplate -PathType Leaf){
 Add-Type -AssemblyName System.IO.Compression.FileSystem
 $archive=[IO.Compression.ZipFile]::OpenRead($WorldTemplate)
 try{if(!$archive.GetEntry('level.dat')){throw '模板 Zip 根目录必须包含 level.dat'};foreach($entry in $archive.Entries){$target=[IO.Path]::GetFullPath((Join-Path $destination $entry.FullName));if(!$target.StartsWith([IO.Path]::GetFullPath($destination)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw '模板 Zip 路径越界'}}}finally{$archive.Dispose()}
 [IO.Compression.ZipFile]::ExtractToDirectory([IO.Path]::GetFullPath($WorldTemplate),$destination)
}else{
 $world=if(Test-Path -LiteralPath $WorldTemplate -PathType Container){$WorldTemplate}else{Join-Path $projectRoot 'run-v9/server/world'}
 if(!(Test-Path -LiteralPath (Join-Path $world 'level.dat'))){throw '缺少隔离世界模板；请用 -WorldTemplate 指定专用测试世界目录或 Zip，不使用玩家存档'}
 Copy-Item -LiteralPath $world -Destination $destination -Recurse
}
if($Profile -eq 'full'){
 $instance='D:\Abyss\Creat Abyss\versions\1.21.1-NeoForge_21.1.259'
 $mods=Join-Path $client 'mods';New-Item -ItemType Directory -Force -Path $mods | Out-Null
 Get-ChildItem -LiteralPath (Join-Path $instance 'mods') -Filter '*.jar' | Where-Object {$_.Name -match '(ftb-(library|chunks|quests|teams)-|InventoryProfilesNext-|libIPN-|kotlinforforge-|architectury-|cloth-config-)'} | ForEach-Object {Copy-Item -LiteralPath $_.FullName -Destination $mods}
 foreach($entry in (Get-Content -LiteralPath (Join-Path $projectRoot 'compatibility.lock.json') -Raw | ConvertFrom-Json)){
  $file=Join-Path (Join-Path $instance 'mods') $entry.name
  if((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $entry.sha256){throw ('兼容依赖已更新：'+$entry.name)}
  Copy-Item -LiteralPath $file -Destination $mods
 }
}
Write-Output "Prepared $RunName ($Profile) with a legacy draft in an isolated test world."
