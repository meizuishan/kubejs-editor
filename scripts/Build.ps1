param([ValidateSet('core','create','dragons','vintage','full')][string]$Profile='core',[switch]$Offline,[switch]$Integration,[string]$NeoForgeVersion,[string]$JeiVersion)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw 'Use PowerShell 7.'}
$projectRoot=Split-Path $PSScriptRoot -Parent
$savedOptions=$env:JAVA_TOOL_OPTIONS
try {
    # Gradle's JVM does not interpret HTTP(S)_PROXY automatically.
    if($env:HTTPS_PROXY){
        $buildProxy=[uri]$env:HTTPS_PROXY
        if($buildProxy.UserInfo){throw 'Authenticated proxy: configure Gradle credentials outside this script.'}
        $env:JAVA_TOOL_OPTIONS="$savedOptions -Dhttps.proxyHost=$($buildProxy.Host) -Dhttps.proxyPort=$($buildProxy.Port) -Dhttp.proxyHost=$($buildProxy.Host) -Dhttp.proxyPort=$($buildProxy.Port) -Dhttp.nonProxyHosts=localhost|127.*|[::1]"
    }
    Push-Location -LiteralPath $projectRoot
    try {
        $buildArgs=@('build',"-PcompatProfile=$Profile",'--no-daemon','--console=plain')
        if($Offline){$buildArgs+='--offline'}
        if($Integration){$buildArgs+='compileIntegrationJava'}
        if($NeoForgeVersion){$buildArgs+="-PneoForgeVersion=$NeoForgeVersion"}
        if($JeiVersion){$buildArgs+="-PjeiVersion=$JeiVersion"}
        & ./gradlew.bat @buildArgs
        if($LASTEXITCODE -ne 0){throw "Gradle failed with exit code $LASTEXITCODE"}
    } finally {Pop-Location}
} finally {$env:JAVA_TOOL_OPTIONS=$savedOptions}
