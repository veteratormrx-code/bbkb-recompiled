param(
    [string]$Serial = "emulator-5800",
    [ValidateSet('fork', 'upstream', 'mod')]
    [Alias('Builds')]
    [string[]]$Build = @('fork', 'upstream', 'mod'),
    [ValidateSet('en', 'ru')]
    [Alias('Languages')]
    [string[]]$Language = @('en', 'ru'),
    [int]$Limit = 0,
    # Rows per scenario type. The corpus is ordered by type, so a plain -Limit only reaches the
    # first group; a stratified sample keeps every type represented.
    [int]$Sample = 0,
    [int]$Gap = 150,
    [int]$Settle = 200,
    [switch]$SkipInstall,
    # The emulator's host footprint and thread count grow with sustained UI automation, and the
    # guest slows down with them. Restarting between combinations keeps every run on a comparable
    # baseline; the data partition survives, so nothing is reinstalled.
    [switch]$RestartBetween
)
# Runs the autocorrect corpus against one or more installed IME builds with real touch events on
# the harness activity, and stores the raw per-row JSON plus the logcat of each run.
#
#   .\tools\qa\Invoke-ImeBenchmark.ps1 -Build fork -Language en -Limit 20      # quick check
#   .\tools\qa\Invoke-ImeBenchmark.ps1                                         # full comparison
#
# Preconditions: emulator running, `:ime-harness` debug + androidTest APKs built (the script
# builds and installs them unless -SkipInstall), GMS present for the 4PDA mod.
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$artifactDir = Join-Path $root 'docs/testing/artifacts/audit-2026-11-24/bench'
$logDir = Join-Path $artifactDir 'logs'
New-Item -ItemType Directory -Force -Path $artifactDir, $logDir | Out-Null

$builds = @{
    fork     = @{
        Package = 'dev.bbkb.ime.debug'
        Ime     = 'dev.bbkb.ime.debug/dev.bbkb.ime.core.BlackBerryIME'
        Label   = 'fork (dev.bbkb.ime.debug)'
    }
    upstream = @{
        Package = 'dev.bbkb.ime'
        Ime     = 'dev.bbkb.ime/.core.BlackBerryIME'
        Label   = 'upstream beta.29 (dev.bbkb.ime)'
    }
    mod      = @{
        Package = 'com.blackberry.keyboard'
        Ime     = 'com.blackberry.keyboard/com.blackberry.inputmethod.core.BlackBerryIME'
        Label   = '4PDA mod 4.1904.3.16701'
    }
}

if (-not $SkipInstall) {
    Push-Location $root
    try {
        & .\gradlew.bat :ime-harness:assembleDebug :ime-harness:assembleDebugAndroidTest --console=plain
        if ($LASTEXITCODE -ne 0) { throw "harness build failed" }
    } finally {
        Pop-Location
    }
    & $adb -s $Serial install -r -t (Join-Path $root 'ime-harness/build/outputs/apk/debug/ime-harness-debug.apk') | Out-Null
    & $adb -s $Serial install -r -t (Join-Path $root 'ime-harness/build/outputs/apk/androidTest/debug/ime-harness-debug-androidTest.apk') | Out-Null
}

function Invoke-Adb([string[]]$Arguments) {
    # Windows PowerShell turns native stderr into a terminating error under -Stop, and adb writes
    # progress to stderr on success; keep the preference local so only exit codes decide.
    $ErrorActionPreference = 'Continue'
    $output = & $adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb $($Arguments -join ' ') failed: $output" }
    return $output
}

function Invoke-AdbLenient([string[]]$Arguments) {
    $ErrorActionPreference = 'Continue'
    & $adb -s $Serial @Arguments 2>&1 | Out-Null
}

function Save-AdbOutput([string[]]$Arguments, [string]$Path) {
    $ErrorActionPreference = 'Continue'
    & $adb -s $Serial @Arguments 2>&1 | Set-Content -Encoding utf8 $Path
}

function Clear-Package([string]$Package) {
    # A cleared package gives the build its genuine fresh-install defaults and drops any words
    # earlier runs taught it; the measurement must not inherit them.
    Invoke-AdbLenient @('shell', 'pm', 'clear', $Package)
}

foreach ($name in $Build) {
    $target = $builds[$name]
    foreach ($lang in $Language) {
        $tag = "$name-$lang"
        if ($RestartBetween -and !($name -eq $Build[0] -and $lang -eq $Language[0])) {
            Write-Output "restarting the emulator to reset the baseline"
            & (Join-Path $PSScriptRoot 'Manage-AuditEmulator.ps1') -Action Restart | Out-Null
        }
        Write-Output "=== $($target.Label) / $lang ==="
        Clear-Package $target.Package
        Invoke-Adb @('shell', 'ime', 'enable', $target.Ime) | Out-Null
        Invoke-Adb @('shell', 'ime', 'set', $target.Ime) | Out-Null
        $active = Invoke-Adb @('shell', 'settings', 'get', 'secure', 'default_input_method')
        Write-Output "active IME: $active"
        Invoke-AdbLenient @('logcat', '-c')

        $instrument = & $adb -s $Serial shell am instrument -w `
            -e class dev.bbkb.ime.harness.AutocorrectBenchmarkTest#runCorpus `
            -e language $lang `
            -e limit $Limit `
            -e sample $Sample `
            -e gap $Gap `
            -e settle $Settle `
            -e tag $name `
            dev.bbkb.ime.harness.test/androidx.test.runner.AndroidJUnitRunner 2>&1
        $instrument | Set-Content -Encoding utf8 (Join-Path $logDir "$tag-instrument.txt")
        $instrument | Select-Object -Last 6

        $devicePath = "/sdcard/Android/data/dev.bbkb.ime.harness/files/bbkb-bench-$name-$lang.json"
        $localPath = Join-Path $artifactDir "$tag.json"
        Invoke-AdbLenient @('pull', $devicePath, $localPath)
        if (Test-Path $localPath) {
            Write-Output "saved $localPath ($((Get-Item $localPath).Length) bytes)"
        } else {
            Write-Output "MISSING $devicePath - the run did not produce a report"
        }
        Save-AdbOutput @('logcat', '-d', '-t', '4000') (Join-Path $logDir "$tag-logcat.txt")
        Invoke-AdbLenient @('shell', 'rm', '-f', $devicePath)    }
}

# Leave the emulator on the build under development.
Invoke-Adb @('shell', 'ime', 'set', $builds['fork'].Ime) | Out-Null
Write-Output "restored $($builds['fork'].Ime)"
