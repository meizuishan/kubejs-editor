param([string]$OutputPath)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw 'Use PowerShell 7.'}
$projectRoot=Split-Path $PSScriptRoot -Parent
$buildText=Get-Content -LiteralPath (Join-Path $projectRoot 'build.gradle') -Raw
if($buildText -notmatch "(?m)^version\s*=\s*'([^']+)'"){throw 'Cannot determine version'}
$version=$Matches[1]
if(!$OutputPath){$OutputPath=Join-Path (Split-Path $projectRoot -Parent) ('releases/kubejs-editor-'+$version)}
$release=[IO.Path]::GetFullPath($OutputPath)
New-Item -ItemType Directory -Force -Path $release | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jars=@("kubejs-editor-$version.jar","kubejs-editor-$version-sources.jar")
foreach($name in $jars){
    $source=Join-Path $projectRoot ('build/libs/'+$name)
    $archive=[IO.Compression.ZipFile]::OpenRead($source)
    try{
        foreach($entry in $archive.Entries){if($entry.FullName -match '^(io/github/abyss/|assets/abyss_editor/|net/astronomy/|dev/kubejseditor/verification/)' -or $entry.FullName -match '^META-INF/jarjar/'){throw "Unexpected release entry: $($entry.FullName)"}}
        foreach($required in @('LICENSE','NOTICE.md')){if(!$archive.GetEntry($required)){throw "Missing license: $name / $required"}}
        if($name -notlike '*-sources.jar'){
            $metadata=$archive.GetEntry('META-INF/neoforge.mods.toml');if(!$metadata){throw 'Missing mod metadata'}
            $reader=[IO.StreamReader]::new($metadata.Open());try{$text=$reader.ReadToEnd()}finally{$reader.Dispose()}
            if(!$text.Contains('modId="kubejs_editor"') -or !$text.Contains('displayName="KubeJS Editor"') -or !$text.Contains('version="'+$version+'"')){throw 'Incorrect release identity'}
            if(!$archive.GetEntry('dev/kubejseditor/content/StorageInventory.class') -or !$archive.GetEntry('dev/kubejseditor/recipe/SimpleRecipeAdapter.class') -or !$archive.GetEntry('dev/kubejseditor/client/JeiRecipeSources.class') -or !$archive.GetEntry('assets/kubejs_editor/lang/zh_cn.json')){throw 'Missing content, compatibility or language assets'}
            if(!$text.Contains('versionRange="[21.1.251,21.2)"') -or !$text.Contains('versionRange="[19.27.0.335,20)"')){throw 'Unexpected dependency compatibility range'}
        }
    }finally{$archive.Dispose()}
    Copy-Item -LiteralPath $source -Destination (Join-Path $release $name) -Force
}
foreach($name in @('Install-Release.ps1','Migrate-Workspace.ps1')){Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $release $name) -Force}
foreach($name in @('LICENSE','NOTICE.md','README.md','README.en.md','CHANGELOG.md')){Copy-Item -LiteralPath (Join-Path $projectRoot $name) -Destination (Join-Path $release $name) -Force}
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/verification.json') -Destination (Join-Path $release 'verification.json') -Force
foreach($name in @('REGISTRATION.md','FORCED_COMPATIBILITY.md','SIMPLE_RECIPES.md','DEVELOPMENT.md','PUBLISHING.md')){Copy-Item -LiteralPath (Join-Path $projectRoot ('docs/'+$name)) -Destination (Join-Path $release $name) -Force}
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs') -Destination $release -Recurse -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'examples') -Destination $release -Recurse -Force
@"
# KubeJS Editor $version

用 PowerShell 7 运行 Install-Release.ps1；安装前关闭 Minecraft。其他实例使用 -InstancePath 指定目录。安装器备份旧 Jar，并仅迁移已确认无内容、无未完成事务的旧编辑器工作区。

支持 Minecraft 1.21.1、NeoForge 21.1.251+（21.1 系列）、Java 21，依赖 KubeJS 与 JEI 19.27.0.335+（19 系列）。进入世界按 K，或使用 /kubejseditor（保留 /kjseditor）。

顶部独立的强制兼容栏（实验）默认关闭；可从 JEI 实际来源或现有配方导入符合条件的未知机器类型。操作及只读边界见 [简单配方](docs/SIMPLE_RECIPES.md)。

作物和存储设置见 [注册说明](docs/REGISTRATION.md)。存储范围：1～9 行、1～12 列、每格 1～4096；不可堆叠物品仍限 1。存储脚本需要保留本模组。

开发构建请解压 kubejs-editor-$version-source.zip，再按 [开发说明](docs/DEVELOPMENT.md) 操作。源码包不包含开发依赖 Jar 或玩家数据。所有根目录交付文件的校验值见 SHA256SUMS.txt。

[验证报告](verification.json) · [强制兼容研究](docs/FORCED_COMPATIBILITY.md)
"@ | Set-Content -LiteralPath (Join-Path $release 'README-release.md') -Encoding utf8NoBOM
$evidenceStream=[IO.File]::Open((Join-Path $release 'verification-evidence.zip'),[IO.FileMode]::Create)
$evidence=[IO.Compression.ZipArchive]::new($evidenceStream,[IO.Compression.ZipArchiveMode]::Create)
try{
    $proof=@('run-v78/client/pack-startup.json','run-v78/client/pack-mods.json','run-v86/client/compatibility-result.json','run-v87/client/screenshots/simple-powah.png','run-v87/client/screenshots/simple-scale3.png','build/release-build-hashes.json','build/release-migration-test.log','build/release-installer-test.log')
    foreach($run in @(87,83)){foreach($stage in 1..2){$proof+='run-v'+$run+'/client/simple-stage'+$stage+'.json'}}
    if(Test-Path -LiteralPath (Join-Path $projectRoot 'build/skyhive-install-verification.json')){$proof+='build/skyhive-install-verification.json'}
    foreach($relative in $proof){$file=Join-Path $projectRoot $relative;if(!(Test-Path -LiteralPath $file)){throw "Missing release evidence: $relative"};[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($evidence,$file,$relative,[IO.Compression.CompressionLevel]::Optimal) | Out-Null}
}finally{$evidence.Dispose();$evidenceStream.Dispose()}
$zipName="kubejs-editor-$version-source.zip"
$zipFile=Join-Path $release ($zipName+'.pending')
$stream=[IO.File]::Open($zipFile,[IO.FileMode]::Create)
$zip=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create)
try{
    $files=@()
    foreach($dir in @('src','scripts','docs','examples','gradle','.github')){$files+=Get-ChildItem -LiteralPath (Join-Path $projectRoot $dir) -Recurse -File -Force}
    $files+=Get-ChildItem -LiteralPath $projectRoot -File -Force | Where-Object {$_.Extension -in @('.md','.gradle','.properties','.json') -or $_.Name -in @('LICENSE','gradlew','gradlew.bat','.gitignore','.gitattributes')}
    foreach($file in $files | Sort-Object FullName -Unique){
        $relative=[IO.Path]::GetRelativePath($projectRoot,$file.FullName).Replace('\','/')
        $entry=$zip.CreateEntry($relative,[IO.Compression.CompressionLevel]::Optimal);$entry.LastWriteTime=[DateTimeOffset]::new(2000,1,1,0,0,0,[TimeSpan]::Zero)
        $destination=$entry.Open();$inputStream=[IO.File]::OpenRead($file.FullName);try{$inputStream.CopyTo($destination)}finally{$inputStream.Dispose();$destination.Dispose()}
    }
}finally{$zip.Dispose();$stream.Dispose()}
Move-Item -LiteralPath $zipFile -Destination (Join-Path $release $zipName) -Force
$checksums=@()
foreach($file in Get-ChildItem -LiteralPath $release -File | Where-Object {$_.Name -ne 'SHA256SUMS.txt'}){$checksums+=((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()+'  '+$file.Name)}
$checksums | Sort-Object | Set-Content -LiteralPath (Join-Path $release 'SHA256SUMS.txt') -Encoding utf8NoBOM
Write-Output "Packaged $release; production/source Jar entries, identity, licenses and test exclusion verified."
