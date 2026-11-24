[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Environment', 'CleanBuild', 'Jvm', 'Native', 'Instrumentation', 'Dictionaries', 'Dist', 'Install', 'Capture')]
    [string]$Task,
    [string]$OutputDirectory,
    [string]$Serial,
    [string[]]$Apk
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (!$OutputDirectory) {
    $OutputDirectory = Join-Path $repo ('docs/testing/artifacts/' + (Get-Date -Format 'yyyy-MM-dd-HHmmss'))
}
$OutputDirectory = (New-Item -ItemType Directory -Path $OutputDirectory -Force).FullName

function Invoke-Logged {
    param([string]$Command, [string[]]$Arguments, [string]$File)
    & $Command @Arguments 2>&1 | Tee-Object (Join-Path $OutputDirectory $File)
    if ($LASTEXITCODE -ne 0) {
        throw "$Command exited with code $LASTEXITCODE. See $File."
    }
}

Push-Location $repo
try {
    $gradle = Join-Path $repo 'gradlew.bat'
    $adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe' } else { 'adb' }
    $device = if ($Serial) { @('-s', $Serial) } else { @('-e') }
    switch ($Task) {
        'Environment' {
            Invoke-Logged $gradle @('--version') 'gradle-version.txt'
            Invoke-Logged 'git' @('rev-parse', 'HEAD') 'commit.txt'
            Invoke-Logged $adb @('devices', '-l') 'devices.txt'
        }
        'CleanBuild' {
            Invoke-Logged $gradle @('clean', '--console=plain') 'clean.txt'
            Invoke-Logged $gradle @(':app:assembleDebug', ':ime-harness:assembleDebug', '--console=plain') 'build.txt'
        }
        'Jvm' {
            Invoke-Logged $gradle @(':app:testDebugUnitTest', '--console=plain') 'jvm.txt'
        }
        'Native' {
            $linuxPath = & wsl.exe -- wslpath -a ($repo -replace '\\', '/')
            if ($LASTEXITCODE -ne 0) { throw 'WSL could not resolve the repository path.' }
            Invoke-Logged 'wsl.exe' @('--', 'bash', "$linuxPath/xt9kdb-scaffold/cpp/test/run_tests.sh") 'native.txt'
        }
        'Instrumentation' {
            if ($Serial) { $env:ANDROID_SERIAL = $Serial }
            Invoke-Logged $gradle @(':app:connectedDebugAndroidTest', ':ime-harness:connectedDebugAndroidTest', '--console=plain') 'instrumentation.txt'
        }
        'Dictionaries' {
            $arguments = @('tools/dictionaries/count_forms.py', 'app/src/main/assets/bbmodern', '--json')
            if ($Apk.Count -gt 1) { throw 'Supply at most one reference APK for dictionary comparison.' }
            if ($Apk.Count -eq 1) { $arguments += @('--reference-apk', $Apk[0]) }
            Invoke-Logged 'python' $arguments 'dictionaries.json'
        }
        'Dist' {
            Invoke-Logged 'python' @('-m', 'unittest', 'discover', '-s', 'dist/tests', '-v') 'dist-python.txt'
        }
        'Install' {
            if (!$Apk) { throw 'Supply one or more APK paths with -Apk.' }
            foreach ($path in $Apk) {
                $apkPath = (Resolve-Path -LiteralPath $path).Path
                Invoke-Logged $adb ($device + @('install', '-r', $apkPath)) ((Split-Path $apkPath -Leaf) + '.install.txt')
            }
        }
        'Capture' {
            $remotePng = '/data/local/tmp/bbkb-audit.png'
            $remoteXml = '/data/local/tmp/bbkb-audit.xml'
            Invoke-Logged $adb ($device + @('shell', 'rm', '-f', $remotePng, $remoteXml)) 'capture-prepare.txt'
            Invoke-Logged $adb ($device + @('shell', 'screencap', '-p', $remotePng)) 'screencap.txt'
            Invoke-Logged $adb ($device + @('pull', $remotePng, (Join-Path $OutputDirectory 'screen.png'))) 'screen-pull.txt'
            Invoke-Logged $adb ($device + @('shell', 'uiautomator', 'dump', $remoteXml)) 'uiautomator.txt'
            Invoke-Logged $adb ($device + @('pull', $remoteXml, (Join-Path $OutputDirectory 'window.xml'))) 'window-pull.txt'
            Invoke-Logged $adb ($device + @('logcat', '-d', '-b', 'crash')) 'logcat-crash.txt'
        }
    }
} finally {
    Pop-Location
}
