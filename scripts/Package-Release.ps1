param([string]$OutputPath)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw 'Use PowerShell 7.'}
$projectRoot=Split-Path $PSScriptRoot -Parent
$buildText=Get-Content -LiteralPath (Join-Path $projectRoot 'build.gradle') -Raw
if($buildText -notmatch "(?m)^version\s*=\s*'([^']+)'"){throw 'Cannot determine version'}
$version=$Matches[1]
$policy=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'release-files.json') -Raw | ConvertFrom-Json
if($policy.schemaVersion -ne 1){throw 'Unsupported release file policy.'}
$compatibility=Get-Content -LiteralPath (Join-Path $projectRoot 'compatibility-versions.lock.json') -Raw | ConvertFrom-Json
if(!$OutputPath){$OutputPath=Join-Path (Split-Path $projectRoot -Parent) ('releases/kubejs-editor-'+$version)}
$release=[IO.Path]::GetFullPath($OutputPath)
if((Test-Path -LiteralPath $release) -and @(Get-ChildItem -LiteralPath $release -Force).Count){throw 'Use a new empty output directory to avoid carrying over unlisted files.'}
New-Item -ItemType Directory -Force -Path $release | Out-Null
function SourceFile([string]$relative){
    $file=[IO.Path]::GetFullPath((Join-Path $projectRoot $relative))
    if(!$file.StartsWith($projectRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw "Source path escapes project: $relative"}
    if(!(Test-Path -LiteralPath $file -PathType Leaf)){throw "Missing release source: $relative"}
    if((Get-Item -LiteralPath $file).Attributes -band [IO.FileAttributes]::ReparsePoint){throw "Release source must not be a link: $relative"}
    return $file
}
function AssertEntryName([string]$name){
    if([IO.Path]::IsPathRooted($name) -or $name.Contains('\') -or @($name.Split('/') | Where-Object {$_ -in @('','..','.')}).Count){throw "Invalid archive path: $name"}
    if($name -match '(?i)(^|/)(AGENTS\.md|HANDOFF\.md|CHANGELOG\.md|GITHUB_RELEASE\.md|WORKSPACE_CLEANUP\.md|DEVELOPMENT\.md|PUBLISHING\.md|verification\.json|verification-evidence\.zip)$|(^|/)(\.codex|\.git|\.verification-fixtures|logs|work|build|research|screenshots|saves|playerdata|accounts|sessions)/|\.jsonl$|\.log$'){throw "Internal file is not distributable: $name"}
}
function AddReleaseFile($archive,[string]$file,[string]$name){
    AssertEntryName $name
    $entry=$archive.CreateEntry($name,[IO.Compression.CompressionLevel]::Optimal)
    $entry.LastWriteTime=[DateTimeOffset]::new(2000,1,1,0,0,0,[TimeSpan]::Zero)
    $destination=$entry.Open();$inputStream=[IO.File]::OpenRead($file)
    try{$inputStream.CopyTo($destination)}finally{$inputStream.Dispose();$destination.Dispose()}
}
$jars=@("kubejs-editor-$version.jar","kubejs-editor-$version-sources.jar")
foreach($name in $jars){
    $source=SourceFile ('build/libs/'+$name)
    $archive=[IO.Compression.ZipFile]::OpenRead($source)
    try{
        foreach($entry in $archive.Entries){
            if($entry.FullName.EndsWith('/')){continue};AssertEntryName $entry.FullName
            if($entry.FullName -match '^(io/github/abyss/|assets/abyss_editor/|net/astronomy/|dev/kubejseditor/verification/|META-INF/jarjar/)'){throw "Unexpected Jar entry: $($entry.FullName)"}
        }
        foreach($required in @('LICENSE','NOTICE.md')){if(!$archive.GetEntry($required)){throw "Missing license: $name / $required"}}
        if($name -notlike '*-sources.jar'){
            $metadata=$archive.GetEntry('META-INF/neoforge.mods.toml');if(!$metadata){throw 'Missing mod metadata'}
            $reader=[IO.StreamReader]::new($metadata.Open());try{$text=$reader.ReadToEnd()}finally{$reader.Dispose()}
            foreach($expected in @('modId="kubejs_editor"','displayName="KubeJS Editor"',('version="'+$version+'"'),('versionRange="['+$compatibility.minimum.neoforge+',21.2)"'),('versionRange="['+$compatibility.minimum.jei+',20)"'))){if(!$text.Contains($expected)){throw "Incorrect release metadata: $expected"}}
            foreach($required in @('dev/kubejseditor/content/StorageInventory.class','dev/kubejseditor/recipe/SimpleRecipeAdapter.class','dev/kubejseditor/client/JeiRecipeSources.class','assets/kubejs_editor/lang/zh_cn.json')){if(!$archive.GetEntry($required)){throw "Missing runtime feature: $required"}}
            if($version -eq '0.18.0'){foreach($required in @('dev/kubejseditor/model/RegistryEntryTarget.class','dev/kubejseditor/runtime/RegistryDataSources.class','dev/kubejseditor/runtime/GeneratedDataPack.class','dev/kubejseditor/client/AppliedDeletionScreen.class','kubejs_editor/source-conversions.json','dev/kubejseditor/client/FieldPatchScreen.class','dev/kubejseditor/client/GenericRecipeSources.class','dev/kubejseditor/client/GenericDataBrowser.class','dev/kubejseditor/client/AdaptationStrategies.class','dev/kubejseditor/recipe/compat/JsonPatches.class','dev/kubejseditor/recipe/compat/PublicObjectGraph.class')){if(!$archive.GetEntry($required)){throw "Missing runtime feature: $required"}}}
        }
    }finally{$archive.Dispose()}
    Copy-Item -LiteralPath $source -Destination (Join-Path $release $name)
}
$publicFiles=[ordered]@{}
foreach($name in $jars){$publicFiles[$name]=Join-Path $release $name}
foreach($name in @('Install-Release.ps1','Migrate-Workspace.ps1')){
    Copy-Item -LiteralPath (SourceFile ('scripts/'+$name)) -Destination (Join-Path $release $name)
    $publicFiles[$name]=Join-Path $release $name
}
foreach($name in @('LICENSE','NOTICE.md')){
    Copy-Item -LiteralPath (SourceFile $name) -Destination (Join-Path $release $name)
    $publicFiles[$name]=Join-Path $release $name
}
foreach($document in $policy.documents){
    $name=[string]$document.target;AssertEntryName $name
    if($publicFiles.Contains($name)){throw "Duplicate public file: $name"}
    $target=Join-Path $release $name
    New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
    Copy-Item -LiteralPath (SourceFile $document.source) -Destination $target
    $publicFiles[$name]=$target
}
$sourceFiles=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
foreach($directory in $policy.sourceDirectories){
    $folder=[IO.Path]::GetFullPath((Join-Path $projectRoot $directory))
    if(!$folder.StartsWith($projectRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Source directory escapes project.'}
    foreach($file in Get-ChildItem -LiteralPath $folder -Recurse -File -Force){
        $relative=[IO.Path]::GetRelativePath($projectRoot,$file.FullName).Replace('\','/')
        AssertEntryName $relative;$sourceFiles[$relative]=SourceFile $relative
    }
}
foreach($relative in $policy.sourceFiles){AssertEntryName $relative;$sourceFiles[$relative]=SourceFile $relative}
foreach($document in $policy.documents){$sourceFiles[[string]$document.target]=SourceFile $document.source}
$sourceName="kubejs-editor-$version-source.zip"
$stream=[IO.File]::Open((Join-Path $release ($sourceName+'.pending')),[IO.FileMode]::CreateNew)
$archive=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create)
try{foreach($name in $sourceFiles.Keys | Sort-Object){AddReleaseFile $archive $sourceFiles[$name] $name}}finally{$archive.Dispose();$stream.Dispose()}
Move-Item -LiteralPath (Join-Path $release ($sourceName+'.pending')) -Destination (Join-Path $release $sourceName)
$publicName="kubejs-editor-$version-release.zip"
$stream=[IO.File]::Open((Join-Path $release ($publicName+'.pending')),[IO.FileMode]::CreateNew)
$archive=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create)
try{
    $hashes=@()
    foreach($name in $publicFiles.Keys){
        AddReleaseFile $archive $publicFiles[$name] $name
        $hashes+=((Get-FileHash -LiteralPath $publicFiles[$name] -Algorithm SHA256).Hash.ToLowerInvariant()+'  '+$name)
    }
    $entry=$archive.CreateEntry('SHA256SUMS.txt');$entry.LastWriteTime=[DateTimeOffset]::new(2000,1,1,0,0,0,[TimeSpan]::Zero)
    $writer=[IO.StreamWriter]::new($entry.Open(),[Text.UTF8Encoding]::new($false))
    try{$writer.Write(($hashes -join [char]10)+[char]10)}finally{$writer.Dispose()}
}finally{$archive.Dispose();$stream.Dispose()}
Move-Item -LiteralPath (Join-Path $release ($publicName+'.pending')) -Destination (Join-Path $release $publicName)
$checksums=foreach($name in @($jars)+@($publicName,$sourceName)){((Get-FileHash -LiteralPath (Join-Path $release $name) -Algorithm SHA256).Hash.ToLowerInvariant()+'  '+$name)}
$checksums | Sort-Object | Set-Content -LiteralPath (Join-Path $release 'SHA256SUMS.txt') -Encoding utf8NoBOM
Write-Output "Packaged $release using an explicit file allowlist: $($publicFiles.Count) public files and $($sourceFiles.Count) source files."
