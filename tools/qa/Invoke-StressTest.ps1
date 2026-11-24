[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('fork', 'upstream', 'mod')][string]$Build,
    [int]$Keys = 10000,
    [int]$Minutes = 30,
    [string]$Serial = 'emulator-5800',
    [string]$OutputDirectory
)
# Continuous mixed-language keyboard stress with host- and guest-side memory sampling.
#
#   .\tools\qa\Invoke-StressTest.ps1 -Build fork -Keys 10000 -Minutes 30
#
# The keystrokes are real MotionEvents injected into the IME (see AutocorrectBenchmarkTest#stress);
# this script only drives the run and records what happens around it: the keyboard process PSS and
# native heap from the guest, and the emulator's working set on the host, sampled throughout.
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (!$OutputDirectory) {
    $OutputDirectory = Join-Path $repo 'docs/testing/artifacts/audit-2026-11-24/stress'
}
$OutputDirectory = (New-Item -ItemType Directory -Path $OutputDirectory -Force).FullName
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'

$packages = @{
    fork     = 'dev.bbkb.ime.debug'
    upstream = 'dev.bbkb.ime'
    mod      = 'com.blackberry.keyboard'
}
$imes = @{
    fork     = 'dev.bbkb.ime.debug/dev.bbkb.ime.core.BlackBerryIME'
    upstream = 'dev.bbkb.ime/dev.bbkb.ime.core.BlackBerryIME'
    mod      = 'com.blackberry.keyboard/com.blackberry.inputmethod.core.BlackBerryIME'
}
$package = $packages[$Build]
$ime = $imes[$Build]

function Invoke-Adb {
    param([string[]]$Arguments)
    $ErrorActionPreference = 'Continue'
    $output = & $adb @('-s', $Serial) @Arguments 2>&1
    return ($output | Out-String).Trim()
}

function Get-MemorySample {
    $pssText = Invoke-Adb @('shell', 'dumpsys', 'meminfo', $package)
    $pss = if ($pssText -match 'TOTAL\s+(\d+)') { [int]$Matches[1] } else { -1 }
    $native = if ($pssText -match 'Native Heap\s+(\d+)') { [int]$Matches[1] } else { -1 }
    $hostProcess = Get-Process -Name 'qemu-system-x86_64-headless' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    [pscustomobject]@{
        At = (Get-Date).ToString('HH:mm:ss')
        GuestPssMb = [math]::Round($pss / 1024)
        NativeHeapMb = [math]::Round($native / 1024)
        HostWorkingSetMb = if ($hostProcess) { [math]::Round($hostProcess.WorkingSet64 / 1MB) } else { -1 }
        HostThreads = if ($hostProcess) { $hostProcess.Threads.Count } else { -1 }
    }
}

Push-Location $repo
try {
    Write-Output "stress: $Build ($package), $Keys keys, up to $Minutes min"
    Invoke-Adb @('shell', 'ime', 'set', $ime) | Out-Null
    Invoke-Adb @('shell', 'logcat', '-c') | Out-Null

    $instrumentArgs = @(
        'shell', 'am', 'instrument', '-w',
        '-e', 'class', 'dev.bbkb.ime.harness.AutocorrectBenchmarkTest#stress',
        '-e', 'stress', 'true',
        '-e', 'keys', "$Keys",
        '-e', 'minutes', "$Minutes",
        '-e', 'tag', $Build,
        'dev.bbkb.ime.harness.test/androidx.test.runner.AndroidJUnitRunner'
    )
    $process = Start-Process -FilePath $adb -ArgumentList (@('-s', $Serial) + $instrumentArgs) `
        -RedirectStandardOutput (Join-Path $OutputDirectory "$Build-instrument.txt") `
        -NoNewWindow -PassThru

    $samples = @()
    $samples += Get-MemorySample
    while (!$process.HasExited) {
        Start-Sleep -Seconds 20
        if ($process.HasExited) { break }
        $sample = Get-MemorySample
        $samples += $sample
        Write-Output ("  {0}  PSS {1} MB  native {2} MB  host {3} MB / {4} threads" -f `
            $sample.At, $sample.GuestPssMb, $sample.NativeHeapMb, $sample.HostWorkingSetMb, $sample.HostThreads)
    }
    $process.WaitForExit()
    $samples += Get-MemorySample
    $samples | Export-Csv -Path (Join-Path $OutputDirectory "$Build-memory.csv") -NoTypeInformation

    Invoke-Adb @('shell', 'logcat', '-d', '-b', 'crash') |
        Out-File (Join-Path $OutputDirectory "$Build-logcat-crash.txt")
    Invoke-Adb @('shell', 'logcat', '-d', '-b', 'events', '-s', 'am_anr:V', 'am_crash:V', 'am_kill:V') |
        Out-File (Join-Path $OutputDirectory "$Build-logcat-anr.txt")
    Invoke-Adb @('pull', "/sdcard/Android/data/dev.bbkb.ime.harness/files/bbkb-stress-$Build.json",
        (Join-Path $OutputDirectory "$Build-stress.json")) | Write-Output

    $report = Get-Content (Join-Path $OutputDirectory "$Build-stress.json") -Raw | ConvertFrom-Json
    Write-Output ""
    Write-Output ("keys={0} elapsed={1}s rate={2:N1}/s lost={3} duplicated={4}" -f `
        $report.keys, [math]::Round($report.elapsedMs / 1000), $report.keysPerSecond,
        $report.lostCharacters, $report.duplicatedCharacters)
    Write-Output ("panels={0}/{1} keyboardCycles={2} languageSwitches={3} ruAvailable={4}" -f `
        $report.panelCycles, ($report.panelCycles + $report.panelFailures),
        $report.keyboardCycles, $report.languageSwitches, $report.russianAvailable)
    Write-Output ("latency p50={0}ms p95={1}ms p99={2}ms (samples={3})" -f `
        $report.latencyP50, $report.latencyP95, $report.latencyP99, $report.latencySamples)
    $first = $samples | Select-Object -First 1
    $last = $samples | Select-Object -Last 1
    Write-Output ("memory: guest PSS {0} -> {1} MB, native heap {2} -> {3} MB, host {4} -> {5} MB, threads {6} -> {7}" -f `
        $first.GuestPssMb, $last.GuestPssMb, $first.NativeHeapMb, $last.NativeHeapMb,
        $first.HostWorkingSetMb, $last.HostWorkingSetMb, $first.HostThreads, $last.HostThreads)
    if ($report.errors.Count -gt 0) { Write-Output ("errors: " + ($report.errors -join '; ')) }
} finally {
    Pop-Location
}
