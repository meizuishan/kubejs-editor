param([string]$OutputPath)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw 'Use PowerShell 7.'}
$projectRoot=Split-Path $PSScriptRoot -Parent
$buildText=Get-Content -LiteralPath (Join-Path $projectRoot 'build.gradle') -Raw
if($buildText -notmatch "(?m)^version\s*=\s*'([^']+)'"){throw 'Cannot determine version'}
$version=$Matches[1]
$compatibility=Get-Content -LiteralPath (Join-Path $projectRoot 'compatibility-versions.lock.json') -Raw | ConvertFrom-Json
if(!$OutputPath){$OutputPath=Join-Path (Split-Path $projectRoot -Parent) ('releases/kubejs-editor-'+$version)}
$release=[IO.Path]::GetFullPath($OutputPath)
New-Item -ItemType Directory -Force -Path $release | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Add-ReleaseFile($archive,[string]$file,[string]$name){
    $entry=$archive.CreateEntry($name,[IO.Compression.CompressionLevel]::Optimal)
    $entry.LastWriteTime=[DateTimeOffset]::new(2000,1,1,0,0,0,[TimeSpan]::Zero)
    $destination=$entry.Open();$inputStream=[IO.File]::OpenRead($file)
    try{$inputStream.CopyTo($destination)}finally{$inputStream.Dispose();$destination.Dispose()}
}
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
            if($version -eq '0.16.0'){foreach($entry in @('dev/kubejseditor/client/AppliedDeletionScreen.class','dev/kubejseditor/client/ConvertedRecipeSources.class','dev/kubejseditor/recipe/SourceConversion.class','kubejs_editor/source-conversions.json')){if(!$archive.GetEntry($entry)){throw "Missing batch/source feature: $entry"}}}
            if($version -in @('0.17.0','0.17.1','0.18.0')){foreach($entry in @('dev/kubejseditor/model/RegistryEntryTarget.class','dev/kubejseditor/runtime/RegistryDataSources.class','dev/kubejseditor/runtime/GeneratedDataPack.class','dev/kubejseditor/client/AppliedDeletionScreen.class','kubejs_editor/source-conversions.json')){if(!$archive.GetEntry($entry)){throw "Missing native registry feature: $entry"}}}
            if($version -eq '0.18.0'){foreach($entry in @('dev/kubejseditor/client/FieldPatchScreen.class','dev/kubejseditor/client/GenericRecipeSources.class','dev/kubejseditor/client/GenericDataBrowser.class','dev/kubejseditor/client/AdaptationStrategies.class','dev/kubejseditor/recipe/compat/JsonPatches.class','dev/kubejseditor/recipe/compat/PublicObjectGraph.class')){if(!$archive.GetEntry($entry)){throw "Missing generic patch feature: $entry"}}}
            if(!$text.Contains('versionRange="['+$compatibility.minimum.neoforge+',21.2)"') -or !$text.Contains('versionRange="['+$compatibility.minimum.jei+',20)"')){throw 'Unexpected dependency compatibility range'}
        }
    }finally{$archive.Dispose()}
    Copy-Item -LiteralPath $source -Destination (Join-Path $release $name) -Force
}
foreach($name in @('Install-Release.ps1','Migrate-Workspace.ps1')){Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $release $name) -Force}
foreach($name in @('LICENSE','NOTICE.md','README.md','README.en.md','CHANGELOG.md','HANDOFF.md')){Copy-Item -LiteralPath (Join-Path $projectRoot $name) -Destination (Join-Path $release $name) -Force}
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/verification.json') -Destination (Join-Path $release 'verification.json') -Force
foreach($name in @('REGISTRATION.md','FORCED_COMPATIBILITY.md','SIMPLE_RECIPES.md','UNIVERSAL_RECIPES.md','READONLY_AND_MAPPING.md','LOAD_CONDITION_OVERRIDE.md','VERSION_COMPATIBILITY.md','DEVELOPMENT.md','PUBLISHING.md','BATCH_AND_SOURCES.md')){Copy-Item -LiteralPath (Join-Path $projectRoot ('docs/'+$name)) -Destination (Join-Path $release $name) -Force}
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs') -Destination $release -Recurse -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'examples') -Destination $release -Recurse -Force
@"
# KubeJS Editor $version

关闭 Minecraft，将主 Jar 放入实例的 mods 目录，同一实例只保留一个编辑器版本。也可用 PowerShell 7 运行 ./Install-Release.ps1 -InstancePath '<实际实例目录>'。安装器要求明确指定实例，备份旧 Jar，并仅迁移已确认无内容、无未完成事务的旧编辑器工作区。

支持 Minecraft 1.21.1、NeoForge $($compatibility.minimum.neoforge)+（21.1 系列）、Java 21，依赖 KubeJS $($compatibility.minimum.kubejs)+ 与 JEI $($compatibility.minimum.jei)+（19 系列）。进入世界按 K，或使用 /kubejseditor（保留 /kjseditor）。声明范围及编译／实测区别见 [版本兼容说明](docs/VERSION_COMPATIBILITY.md)。

最后一项强制兼容默认开启，提供通用 JEI 编辑。清空、重置与新建列表见 [映射与入口](docs/MAPPING_AND_NAVIGATION.md)；原始字段修改及自动选择见 [通用补丁](docs/GENERIC_PATCHES.md)。打开后自动读取可识别字段；字段确认默认初级五类，高级模式保留路径与原始结构。同格 OR、多位置共用字段及其他原料类型的限制见 [通用配方说明](docs/UNIVERSAL_RECIPES.md)。缺少权威写入来源的展示保留只读。

“批量删除”位于“已有修改”子栏，允许多选后统一撤销，配方只重载一次；包含注册时统一等待重启。ExtendedAE Plus 转换来源及共享修改影响见 [来源与耗时](docs/BATCH_AND_SOURCES.md)。

原生数据包注册表也可进入同一编辑页面，例如不装 forbidden_arcanus_js 的锻造熔炉仪式。此类条目的应用、禁用、撤销与回滚需要重启，详见 [原生注册表配方](docs/NATIVE_REGISTRY_RECIPES.md)。

作物和存储设置见 [注册说明](docs/REGISTRATION.md)。存储范围：1～9 行、1～12 列、每格 1～4096；不可堆叠物品仍限 1。存储脚本需要保留本模组。

完整开发源码另见 kubejs-editor-$version-source.zip，解压后按 [开发说明](docs/DEVELOPMENT.md) 操作。源码包不包含开发依赖 Jar 或玩家数据。验证图片已按要求清理，历史测试结果保留在报告中。

[验证报告](verification.json) · [强制兼容研究](docs/FORCED_COMPATIBILITY.md)
"@ | Set-Content -LiteralPath (Join-Path $release 'README-release.md') -Encoding utf8NoBOM
$evidenceStream=[IO.File]::Open((Join-Path $release 'verification-evidence.zip'),[IO.FileMode]::Create)
$evidence=[IO.Compression.ZipArchive]::new($evidenceStream,[IO.Compression.ZipArchiveMode]::Create)
try{
    if($version -eq '0.18.0'){
        $proof=@('build/generic-0180/preparation.log','build/generic-0180/fixture-build.log','build/generic-0180/final-preparation.log','build/generic-0180/final-fixture-build.log','build/generic-0180/final-client.log','build/generic-0180/client.log','build/generic-0180/third-attempt.log','build/generic-0180/third-attempt-ui.json','build/generic-0180/third-attempt-patches.json','build/generic-0180/third-attempt-batch.json','build/generic-0180/fourth-attempt.log','build/generic-0180/fourth-attempt-patches.json','build/generic-0180/first-attempt.log','build/generic-0180/first-attempt.json','build/generic-0180/second-attempt.log','build/generic-0180/second-attempt-batch.json','build/generic-0180/second-attempt-patches.json','build/generic-0180/build-hashes.json','build/generic-0180/minimum-core.log','build/generic-0180/modern-button-core.log','build/generic-0180/target-core.log','build/generic-0180/newer-full.log','build/generic-0180/default-core.log','build/generic-0180/migration.log','build/generic-0180/installer.log','run-v124/client/ui-revision.json','run-v124/client/generic-patches.json','run-v124/client/batch-verification.json','run-v124/client/registry-mods.json','run-v124/client/batch-mods.json','compatibility-registry.lock.json','compatibility-batch.lock.json')
    }elseif($version -eq '0.17.1'){
        $proof=@('build/ui-0171/preparation.log','build/ui-0171/client.log','build/ui-0171/client-launch-arguments-failed.log','build/ui-0171/minimum-integration-unsupported.log','build/ui-0171/build-hashes.json','build/ui-0171/minimum-core.log','build/ui-0171/modern-button-core.log','build/ui-0171/target-core.log','build/ui-0171/newer-full.log','build/ui-0171/default-core.log','build/ui-0171/migration.log','run-v122/client/ui-revision.json','run-v122/client/batch-verification.json','run-v122/client/registry-mods.json','run-v122/client/batch-mods.json','compatibility-registry.lock.json','compatibility-batch.lock.json')
    }elseif($version -eq '0.17.0'){
        $proof=@('build/atm10-research/core-build.log','build/atm10-research/build-hashes.json','build/atm10-research/exact-api-build.log','build/atm10-research/exact-dependencies.json','build/atm10-research/exact-client.log','compatibility-registry.lock.json','run-v119/client/registry-stage1.json','run-v120/client/registry-mods.json','run-v121/client/batch-verification.json','build/atm10-research/batch-regression-target-jei.log','build/atm10-research/batch-regression.log')
        foreach($stage in 1..5){$proof+='run-v120/client/registry-stage'+$stage+'.json'}
        foreach($stage in 1..4){$proof+='build/atm10-research/registry-final-stage'+$stage+'.log'}
    }elseif($version -eq '0.16.0'){
        $proof=@('build/release-0.16.0/target-core-build.log','build/release-0.16.0/minimum-core-build.log','build/release-0.16.0/minimum-full-build.log','build/release-0.16.0/default-full-build.log','build/release-0.16.0/api-class-comparison.json','build/release-0.16.0/build-hashes.json','build/release-0.16.0/migration-test.log','build/release-0.16.0/installer-test.log')
        foreach($run in @(116,117,118)){$proof+='run-v'+$run+'/client/batch-verification.json'}
        $proof+=@('build/release-0.16.0/batch-client.log','build/release-0.16.0/batch-client-final.log','build/release-0.16.0/batch-client-complete.log','run-v118/client/batch-mods.json','run-v118/client/screenshots/batch-source-scale2.png','run-v118/client/screenshots/batch-delete-scale2.png','run-v118/client/screenshots/batch-delete-scale3.png')
        $proof+=@('build/release-0.16.0/pack-performance-baseline.json','build/release-0.16.0/isolated-last-performance.json')
    }elseif($version -eq '0.15.3'){
        $proof=@('build/version-compatibility/minimum-final-build.log','build/version-compatibility/minimum-full-build.log','build/version-compatibility/modern-button-build.log','build/version-compatibility/target-build.log','build/version-compatibility/latest-full-build.log','build/version-compatibility/build-hashes.json','build/version-compatibility/migration-test.log','build/version-compatibility/installer-test.log','build/version-compatibility/neoforge-metadata.xml','build/version-compatibility/jei-metadata.xml')
        $proof+=@('build/version-compatibility/matrix.json','build/version-compatibility/prerequisites.json','build/version-compatibility/default-build.log')
    }elseif($version -eq '0.15.2'){
        $proof=@('build/release-0.15.2-build-hashes.json','build/release-0.15.2-final-core-build.log','build/release-0.15.2-final-full-build.log','build/conditions-0152-client.log','build/conditions-0152-final-client.log','run-v114/client/load-conditions-verification.json','run-v115/client/load-conditions-verification.json','run-v115/client/compat015-mods.json','run-v115/client/screenshots/load-conditions-scale2.png','run-v115/client/screenshots/load-conditions-scale3.png')
        $proof+=@('build/release-0.15.2-migration-test.log','build/release-0.15.2-installer-test.log','build/release-0.15.2-client-archive.json')
    }elseif($version -eq '0.15.1'){
        $proof=@('build/release-0.15.1-build-hashes.json','build/release-0.15.1-core-build.log','build/release-0.15.1-full-build.log','build/chance-0151-client.log','run-v113/client/chance-mapping-verification.json','run-v113/client/compat015-mods.json')
    }elseif($version -eq '0.15.0'){
        $proof=@('build/release-0.15.0-build-hashes.json','build/release-0.15.0-final-core-build.log','build/release-0.15.0-final-full-build.log','build/compat015-single-client.log','run-v112/client/compat015-verification.json','run-v112/client/compat015-mods.json','run-v112/client/screenshots/compat015-scale2.png','run-v112/client/screenshots/compat015-scale3.png')
        foreach($name in @('migration-test','installer-test','installation-preflight')){$relative='build/release-0.15.0-'+$name+'.log';if(Test-Path -LiteralPath (Join-Path $projectRoot $relative)){$proof+=$relative}}
    }elseif($version -eq '0.14.1'){
        $proof=@('build/release-0.14.1-build-hashes.json','build/release-0.14.1-migration-test.log','build/release-0.14.1-installer-test.log','run-v109/client/reload-resilience.json','run-v110/client/pack-startup.json','run-v110/client/pack-mods.json')
        foreach($run in @(105,106,111)){foreach($stage in 1..2){$proof+='run-v'+$run+'/client/compat-interactions-stage'+$stage+'.json'}}
        foreach($stage in 1..2){$proof+='run-v107/client/universal-stage'+$stage+'.json'}
        foreach($number in 0..17){$proof+='run-v111/client/screenshots/compat-interaction-'+$number+'.png'}
        $proof+='run-v111/client/screenshots/compat-jei-button.png'
        $proof+='run-v111/client/kubejs/config/kubejs_editor/last-compatibility-diagnostic.json'
        foreach($run in @(105,106,107,111)){$proof+='run-v'+$run+'/client/universal-mods.json'}
    }elseif($version -eq '0.14.0'){
        $proof=@('build/release-build-hashes.json','build/release-migration-test.log','build/release-installer-test.log','run-v99/client/reload-resilience.json','run-v101/client/pack-startup.json','run-v101/client/pack-mods.json')
        foreach($run in @(96,98,100)){foreach($stage in 1..2){$proof+='run-v'+$run+'/client/universal-stage'+$stage+'.json'}}
        foreach($name in @('mi_eight_four-scale2','ritual-scale2','ritual-scale3','apparatus_1-scale2','apparatus_2-scale2','apparatus_4-scale2','apparatus_6-scale2','apparatus_8-scale2')){$proof+='run-v100/client/screenshots/universal-'+$name+'.png'}
        foreach($run in @(96,98,100)){$proof+='run-v'+$run+'/client/universal-mods.json'}
    }else{
        $proof=@('run-v78/client/pack-startup.json','run-v78/client/pack-mods.json','run-v86/client/compatibility-result.json','run-v87/client/screenshots/simple-powah.png','run-v87/client/screenshots/simple-scale3.png','build/release-build-hashes.json','build/release-migration-test.log','build/release-installer-test.log')
        foreach($run in @(87,83)){foreach($stage in 1..2){$proof+='run-v'+$run+'/client/simple-stage'+$stage+'.json'}}
    }
    $installProof='build/skyhive-install-'+$version+'.json';if(Test-Path -LiteralPath (Join-Path $projectRoot $installProof)){$proof+=$installProof}
    $atmProof='build/atm10-install-'+$version+'.json';if(Test-Path -LiteralPath (Join-Path $projectRoot $atmProof)){$proof+=$atmProof}
    # Validation pictures were deliberately removed; numerical reports and logs remain evidence.
    $proof=@($proof | Where-Object {$_ -notmatch '(?i)\.(png|jpe?g|webp|gif|bmp)$'})
    $preflight=Join-Path $projectRoot ('build/release-preflight-'+$version)
    if(Test-Path -LiteralPath (Join-Path $preflight 'matrix.json')){
        $proof+=@(Get-ChildItem -LiteralPath $preflight -File | Where-Object {$_.Name -in @('matrix.json','minimum-core.log','modern-button-core.log','target-core.log','newer-full.log','default-core.log','migration.log','installer.log','image-cleanup.json','release-audit.json')} | ForEach-Object {[IO.Path]::GetRelativePath($projectRoot,$_.FullName).Replace('\','/')})
    }
    foreach($relative in $proof){$file=Join-Path $projectRoot $relative;if(!(Test-Path -LiteralPath $file)){throw "Missing release evidence: $relative"};Add-ReleaseFile $evidence $file $relative}
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
        if($relative.StartsWith('docs/screenshots/')){continue}
        $entry=$zip.CreateEntry($relative,[IO.Compression.CompressionLevel]::Optimal);$entry.LastWriteTime=[DateTimeOffset]::new(2000,1,1,0,0,0,[TimeSpan]::Zero)
        $destination=$entry.Open();$inputStream=[IO.File]::OpenRead($file.FullName);try{$inputStream.CopyTo($destination)}finally{$inputStream.Dispose();$destination.Dispose()}
    }
}finally{$zip.Dispose();$stream.Dispose()}
Move-Item -LiteralPath $zipFile -Destination (Join-Path $release $zipName) -Force
# A curated public download excludes local installation backups and verification logs.
$publicName="kubejs-editor-$version-release.zip"
$publicStream=[IO.File]::Open((Join-Path $release ($publicName+'.pending')),[IO.FileMode]::Create)
$publicZip=[IO.Compression.ZipArchive]::new($publicStream,[IO.Compression.ZipArchiveMode]::Create)
$publicHashes=[Collections.Generic.List[string]]::new()
try{
    $publicFiles=@("kubejs-editor-$version.jar","kubejs-editor-$version-sources.jar",'Install-Release.ps1','Migrate-Workspace.ps1','LICENSE','NOTICE.md','README-release.md','CHANGELOG.md')
    $publicFiles+=@('GUIDE.md','REGISTRATION.md','UNIVERSAL_RECIPES.md','BATCH_AND_SOURCES.md','NATIVE_REGISTRY_RECIPES.md','MAPPING_AND_NAVIGATION.md','GENERIC_PATCHES.md','LOAD_CONDITION_OVERRIDE.md','VERSION_COMPATIBILITY.md','SIMPLE_RECIPES.md','FORCED_COMPATIBILITY.md','CONFIGURATION.md','TEMPLATES.md','VINTAGE.md','DEVELOPMENT.md','READONLY_AND_MAPPING.md','PUBLISHING.md','SKYHIVE_COMPATIBILITY_RESEARCH.md' | ForEach-Object {'docs/'+$_})
    foreach($relative in $publicFiles){$file=Join-Path $release $relative;Add-ReleaseFile $publicZip $file $relative;$publicHashes.Add((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()+'  '+$relative)}
    $report=Get-Content -LiteralPath (Join-Path $release 'verification.json') -Raw | ConvertFrom-Json
    $completedClient=@($report.runtimeVerification.attempts | Where-Object passed | Select-Object -Last 1)
    $clientChecks=if($report.runtimeVerification.checksTotal){$report.runtimeVerification.checksTotal}elseif($completedClient.Count){$completedClient[0].checks}else{0}
    $summary=[ordered]@{version=$version;minecraft='1.21.1';java=21;declared=$report.declared;regressionChecks=$report.build.checks;serializerFixtures=$report.build.serializerFixtures;compatibilityPreflight=$report.releasePreflight;isolatedClientChecks=$clientChecks;validationImagesRemoved=$true;limitations=@('Representative build/API combinations are checked; all dependency versions and mod machines are not game-tested.','Local singleplayer writing only; remote servers support local drafts and export.','Converted displays edit a shared native source and can affect original and compatible machines.','Native datapack registry changes require restart and a public full data Codec.','Pure displays without authoritative writable recipe sources remain read-only.','Physical full-pack machine processing and multiplayer were not exercised.');publication='Prepared locally; not uploaded to a hosting platform.'}
    $summaryBytes=[Text.Encoding]::UTF8.GetBytes(($summary | ConvertTo-Json -Depth 20))
    foreach($relative in @('verification.json','docs/verification.json')){$entry=$publicZip.CreateEntry($relative);$entry.LastWriteTime=[DateTimeOffset]::new(2000,1,1,0,0,0,[TimeSpan]::Zero);$output=$entry.Open();try{$output.Write($summaryBytes,0,$summaryBytes.Length)}finally{$output.Dispose()};$publicHashes.Add([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($summaryBytes)).ToLowerInvariant()+'  '+$relative)}
    $entry=$publicZip.CreateEntry('SHA256SUMS.txt');$entry.LastWriteTime=[DateTimeOffset]::new(2000,1,1,0,0,0,[TimeSpan]::Zero);$writer=[IO.StreamWriter]::new($entry.Open(),[Text.UTF8Encoding]::new($false));try{$writer.Write(($publicHashes -join "`n")+"`n")}finally{$writer.Dispose()}
}finally{$publicZip.Dispose();$publicStream.Dispose()}
Move-Item -LiteralPath (Join-Path $release ($publicName+'.pending')) -Destination (Join-Path $release $publicName) -Force
$checksums=@()
foreach($file in Get-ChildItem -LiteralPath $release -File | Where-Object {$_.Name -ne 'SHA256SUMS.txt'}){$checksums+=((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()+'  '+$file.Name)}
$checksums | Sort-Object | Set-Content -LiteralPath (Join-Path $release 'SHA256SUMS.txt') -Encoding utf8NoBOM
Write-Output "Packaged $release; production/source Jar entries, identity, licenses and test exclusion verified."
