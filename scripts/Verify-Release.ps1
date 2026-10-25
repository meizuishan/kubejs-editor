param([Parameter(Mandatory)][string]$ReleasePath,[switch]$ExerciseInstaller)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw 'Use PowerShell 7.'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$projectRoot=Split-Path $PSScriptRoot -Parent
$release=[IO.Path]::GetFullPath($ReleasePath)
$buildText=Get-Content -LiteralPath (Join-Path $projectRoot 'build.gradle') -Raw
if($buildText -notmatch "(?m)^version\s*=\s*'([^']+)'"){throw 'Cannot determine build version'}
$version=$Matches[1]
function ReadEntry($entry){$reader=[IO.StreamReader]::new($entry.Open());try{return $reader.ReadToEnd()}finally{$reader.Dispose()}}
function HashEntry($entry){$inputStream=$entry.Open();try{return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($inputStream)).ToLowerInvariant()}finally{$inputStream.Dispose()}}
function VerifyZipSums($zip){
    $count=0
    foreach($line in (ReadEntry $zip.GetEntry('SHA256SUMS.txt')) -split '\r?\n'){
        if(!$line){continue};if($line -notmatch '^([a-fA-F0-9]{64})  (.+)$'){throw 'Malformed public checksum'}
        $expected=$Matches[1];$target=$zip.GetEntry($Matches[2]);if(!$target -or (HashEntry $target) -ne $expected){throw "Public checksum mismatch: $line"};$count++
    }
    return $count
}
$rootChecks=0
foreach($line in Get-Content -LiteralPath (Join-Path $release 'SHA256SUMS.txt')){
    if($line -notmatch '^([a-fA-F0-9]{64})  (.+)$'){throw 'Malformed release checksum'}
    $expected=$Matches[1];$target=[IO.Path]::GetFullPath((Join-Path $release $Matches[2]))
    if(!$target.StartsWith($release+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Checksum path escapes release'}
    if((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $expected){throw "Checksum mismatch: $target"};$rootChecks++
}
$jar=[IO.Compression.ZipFile]::OpenRead((Join-Path $release "kubejs-editor-$version.jar"))
try{
    foreach($name in @('LICENSE','NOTICE.md','META-INF/neoforge.mods.toml','kubejs.plugins.txt','icon.png','kubejs_editor/source-conversions.json')){if(!$jar.GetEntry($name)){throw "Missing runtime entry: $name"}}
    foreach($entry in $jar.Entries){if($entry.FullName -match 'verification/|^META-INF/jarjar/|^io/github/abyss/|^assets/abyss_editor/|(^|/)screenshots/'){throw "Unexpected runtime content: $($entry.FullName)"}}
    $classes=@($jar.Entries | Where-Object FullName -like '*.class')
    foreach($entry in $classes){$inputStream=$entry.Open();$header=[byte[]]::new(8);try{$inputStream.ReadExactly($header)}finally{$inputStream.Dispose()};if($header[6]*256+$header[7] -ne 65){throw 'Runtime class does not target Java 21'}}
    $metadata=ReadEntry $jar.GetEntry('META-INF/neoforge.mods.toml')
    foreach($expected in @('modId="kubejs_editor"',('version="'+$version+'"'),'versionRange="[1.21.1]"','versionRange="[21.1.199,21.2)"','versionRange="[19.25.0.322,20)"','versionRange="[2101.7.2-build.368,)"')){if(!$metadata.Contains($expected)){throw "Incorrect metadata: $expected"}}
}finally{$jar.Dispose()}
$public=[IO.Compression.ZipFile]::OpenRead((Join-Path $release "kubejs-editor-$version-release.zip"))
try{
    if(@($public.Entries.FullName | Group-Object | Where-Object Count -gt 1).Count){throw 'Duplicate public entries'}
    foreach($entry in $public.Entries){if($entry.FullName -match '(^|/)(installed-backups|logs|screenshots|saves|playerdata|accounts|build|work|research)/|\.log$|verification-evidence\.zip$'){throw "Private/test content in public download: $($entry.FullName)"}}
    $publicChecks=VerifyZipSums $public
    foreach($entry in $public.Entries | Where-Object FullName -like '*.md'){
        $text=ReadEntry $entry
        foreach($match in [regex]::Matches($text,'(?<!!)\[[^\]]+\]\((?<url>[^)]+)\)')){
            $url=$match.Groups['url'].Value.Trim('<','>')
            if($url -match '^(https?://|mailto:|#|[A-Za-z]:|/)'){continue}
            $url=($url -split '#')[0];if(!$url){continue}
            $base=if($entry.FullName.Contains('/')){$entry.FullName.Substring(0,$entry.FullName.LastIndexOf('/')+1)}else{''}
            if(!$public.GetEntry($base+$url)){throw "Broken public guide link: $($entry.FullName) -> $url"}
        }
    }
    $installer=ReadEntry $public.GetEntry('Install-Release.ps1')
    if($installer.Contains('D:\Abyss') -or !$installer.Contains('[Parameter(Mandatory)]')){throw 'Public installer retains an implicit development target'}
}finally{$public.Dispose()}
$sources=[IO.Compression.ZipFile]::OpenRead((Join-Path $release "kubejs-editor-$version-source.zip"))
try{
    foreach($entry in $sources.Entries){if($entry.FullName -eq 'gradle/wrapper/gradle-wrapper.jar'){continue};if($entry.FullName -match '(^|/)(screenshots|\.git|run-v[0-9]+|saves|installed-backups)/|\.(jar|class)$'){throw "Unexpected developer archive entry: $($entry.FullName)"}}
}finally{$sources.Dispose()}
$evidence=[IO.Compression.ZipFile]::OpenRead((Join-Path $release 'verification-evidence.zip'))
try{if(@($evidence.Entries | Where-Object FullName -match '(?i)\.(png|jpe?g|webp|gif|bmp)$').Count){throw 'Verification images remain in evidence ZIP'}}finally{$evidence.Dispose()}
$installerPassed=$null
if($ExerciseInstaller){
    $fixture=Join-Path $projectRoot ('build/release-preflight-'+$version+'/public-install-'+[guid]::NewGuid().ToString('N'))
    $package=Join-Path $fixture 'package';$instance=Join-Path $fixture 'instance';[IO.Compression.ZipFile]::ExtractToDirectory((Join-Path $release "kubejs-editor-$version-release.zip"),$package)
    New-Item -ItemType Directory -Force -Path (Join-Path $instance 'mods') | Out-Null
    'old-editor-fixture' | Set-Content -LiteralPath (Join-Path $instance 'mods/kubejs-editor-0.15.3.jar')
    'player-file-fixture' | Set-Content -LiteralPath (Join-Path $instance 'keep.txt')
    & pwsh.exe -NoProfile -File (Join-Path $package 'Install-Release.ps1') -InstancePath $instance -ReleaseDir $package *> (Join-Path $fixture 'installation.log')
    if($LASTEXITCODE){throw 'Installer from public ZIP failed'}
    if((Get-FileHash -LiteralPath (Join-Path $instance "mods/kubejs-editor-$version.jar")).Hash -ne (Get-FileHash -LiteralPath (Join-Path $release "kubejs-editor-$version.jar")).Hash){throw 'Public installer delivered different bytes'}
    if(@(Get-ChildItem -LiteralPath (Join-Path $instance 'mods') -File -Filter '*.jar').Count -ne 1 -or (Get-Content -LiteralPath (Join-Path $instance 'keep.txt')) -ne 'player-file-fixture'){throw 'Public installer changed unrelated content or left duplicate Jars'}
    $installerPassed=$true
}
$audit=[ordered]@{version=$version;date='2026-10-25';passed=$true;rootChecksumFiles=$rootChecks;publicChecksumFiles=$publicChecks;productionClasses=$classes.Count;javaClassVersion=65;identityAndDependencyRanges=$true;integrationAndBundledDependenciesAbsent=$true;validationImagesAbsent=$true;publicGuideLinksValid=$true;publicInstallerRequiresExplicitTarget=$true;publicZipInstallerExercised=$installerPassed;runtimeJarSha256=(Get-FileHash -LiteralPath (Join-Path $release "kubejs-editor-$version.jar")).Hash.ToLowerInvariant()}
$audit | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $projectRoot "build/release-preflight-$version/release-audit.json") -Encoding utf8NoBOM
Write-Output "PASS: $rootChecks release checksums, $publicChecks public files, $($classes.Count) production classes, guide links and image exclusion."
