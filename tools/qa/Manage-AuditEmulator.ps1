[CmdletBinding()]
param(
    [ValidateSet('Start', 'Stop', 'Status', 'Restart', 'Trim', 'Watch')]
    [string]$Action = 'Start',
    [string]$Avd = 'bbkb_audit_api37',
    [int]$Port = 5800,
    [int]$Memory = 4096,
    [int]$Cores = 4,
    # Host working-set ceiling. Above it the process is holding memory it no longer needs; the guest
    # data partition survives a restart, so a restart costs a boot, not the test state.
    [int]$WatchLimitMb = 7000,
    # Above this the watchdog sheds the working set (pages go to the standby list, the guest keeps
    # them) before resorting to a restart.
    [int]$TrimAboveMb = 4500,
    [int]$WatchIntervalSec = 60,
    # 0 means watch until interrupted.
    [int]$WatchMinutes = 0
)
# Lifecycle for the audit emulator, with explicit host-memory accounting.
#
#   .\tools\qa\Manage-AuditEmulator.ps1 -Action Start
#   .\tools\qa\Manage-AuditEmulator.ps1 -Action Status
#   .\tools\qa\Manage-AuditEmulator.ps1 -Action Trim
#   .\tools\qa\Manage-AuditEmulator.ps1 -Action Restart -Memory 3072
#
# Why this exists: a long-lived emulator process accumulates threads and committed memory on the
# host (observed 1635 threads and 17 GB of private bytes after ~2 h of UI automation), which leaves
# the machine swapping while the guest itself still reports plenty of free RAM. Two tiers contain it:
# `Trim` asks Windows to empty the process working set, which returns resident pages to the standby
# list without touching the guest, and `Restart` is the hard reset for accumulated threads. `Watch`
# runs both automatically against `-TrimAboveMb` / `-WatchLimitMb`. `-no-snapshot-save` plus an
# explicit `-memory` keeps the next run from inheriting the same footprint.
$ErrorActionPreference = 'Stop'

$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$emulator = Join-Path $env:ANDROID_HOME 'emulator\emulator.exe'
$serial = "emulator-$Port"

if (-not ('BBKB.Memory' -as [type])) {
    Add-Type -Namespace BBKB -Name Memory -MemberDefinition @'
[System.Runtime.InteropServices.DllImport("psapi.dll", SetLastError = true)]
public static extern bool EmptyWorkingSet(System.IntPtr hProcess);
[System.Runtime.InteropServices.DllImport("kernel32.dll", SetLastError = true)]
public static extern bool SetProcessWorkingSetSize(System.IntPtr hProcess, System.IntPtr min, System.IntPtr max);
'@
}

function Get-EmulatorProcess {
    Get-Process -Name 'qemu-system-x86_64-headless' -ErrorAction SilentlyContinue
}

function Get-Footprint {
    $process = Get-EmulatorProcess | Select-Object -First 1
    if (-not $process) { return $null }
    [pscustomobject]@{
        Id = $process.Id
        WorkingSetMb = [math]::Round($process.WorkingSet64 / 1MB)
        PrivateMb = [math]::Round($process.PrivateMemorySize64 / 1MB)
        Threads = $process.Threads.Count
        StartedAt = $process.StartTime
    }
}

function Show-Status {
    $host_ = Get-CimInstance Win32_OperatingSystem
    "host free: {0} MB / {1} MB" -f `
        [math]::Round($host_.FreePhysicalMemory / 1KB), [math]::Round($host_.TotalVisibleMemorySize / 1KB) | Write-Output
    $footprint = Get-Footprint
    if ($footprint) {
        $footprint | Format-List | Out-String | Write-Output
        if ($footprint.WorkingSetMb -gt $WatchLimitMb) {
            Write-Warning ("emulator working set {0} MB exceeds the {1} MB ceiling; restart it with -Action Restart" -f `
                $footprint.WorkingSetMb, $WatchLimitMb)
        } elseif ($footprint.WorkingSetMb -gt $TrimAboveMb) {
            Write-Warning ("emulator working set {0} MB is above the {1} MB trim threshold; run -Action Trim" -f `
                $footprint.WorkingSetMb, $TrimAboveMb)
        }
    } else {
        Write-Output 'emulator: not running'
    }
    $device = & $adb devices 2>&1 | Select-String -Pattern $serial
    if ($device) { Write-Output $device.ToString().Trim() }
}

# ---------------------------------------------------------------------------------------------
# Host-memory containment
# ---------------------------------------------------------------------------------------------

$script:trimmedTotalMb = 0

function Trim-EmulatorWorkingSet {
    param([int]$ThresholdMb = $TrimAboveMb, [switch]$Force)
    $process = Get-EmulatorProcess | Select-Object -First 1
    if (-not $process) { return $null }
    $process.Refresh()
    $before = [math]::Round($process.WorkingSet64 / 1MB)
    if (-not $Force -and $before -le $ThresholdMb) {
        return [pscustomobject]@{ At = (Get-Date).ToString('HH:mm:ss'); Before = $before; After = $before; Delta = 0 }
    }
    # EmptyWorkingSet is the same call Task Manager's "Empty Working Set" uses: resident pages go to
    # the standby list, so nothing is lost and the guest keeps running from cache.
    [BBKB.Memory]::EmptyWorkingSet($process.Handle) | Out-Null
    [BBKB.Memory]::SetProcessWorkingSetSize($process.Handle, ([IntPtr](-1)), ([IntPtr](-1))) | Out-Null
    Start-Sleep -Milliseconds 800
    $process.Refresh()
    $after = [math]::Round($process.WorkingSet64 / 1MB)
    $delta = $before - $after
    if ($delta -gt 0) { $script:trimmedTotalMb += $delta }
    [pscustomobject]@{ At = (Get-Date).ToString('HH:mm:ss'); Before = $before; After = $after; Delta = $delta }
}

function Watch-Emulator {
    if (-not (Get-EmulatorProcess)) { throw 'nothing to watch; start the emulator first' }
    $deadline = if ($WatchMinutes -gt 0) { (Get-Date).AddMinutes($WatchMinutes) } else { [datetime]::MaxValue }
    Write-Output ("watching emulator: trim above {0} MB, restart above {1} MB, every {2} s" -f `
        $TrimAboveMb, $WatchLimitMb, $WatchIntervalSec)
    while ((Get-Date) -lt $deadline) {
        $footprint = Get-Footprint
        if (-not $footprint) { Write-Output 'emulator exited; watch stopping'; return }
        # Trim first, always: a working set over the ceiling is usually reclaimable resident pages,
        # not something that needs a restart, and a restart in the middle of an instrumented run
        # loses the run. Only a process that stays over the ceiling after a trim is restarted.
        $trim = Trim-EmulatorWorkingSet
        if ($trim -and $trim.Delta -gt 0) {
            Write-Output ("{0} trim: {1} MB -> {2} MB (freed {3} MB, session total {4} MB)" -f `
                $trim.At, $trim.Before, $trim.After, $trim.Delta, $script:trimmedTotalMb)
        } else {
            Write-Output ("{0} ok: ws {1} MB, threads {2}, private {3} MB" -f `
                (Get-Date).ToString('HH:mm:ss'), $footprint.WorkingSetMb, $footprint.Threads, $footprint.PrivateMb)
        }
        $after = Get-Footprint
        if ($after -and $after.WorkingSetMb -gt $WatchLimitMb) {
            Write-Warning ("working set {0} MB still above the {1} MB ceiling after trimming; restarting" -f `
                $after.WorkingSetMb, $WatchLimitMb)
            Stop-AuditEmulator
            Start-AuditEmulator
        }
        Start-Sleep -Seconds $WatchIntervalSec
    }
}

function Start-AuditEmulator {
    if (Get-EmulatorProcess) { throw 'an emulator is already running; use -Action Restart' }
    # -no-audio is deliberately absent: the keyboard plays key-click samples through the guest audio
    # stack, and muting the host backend changes what those code paths do. -no-metrics drops the
    # telemetry thread, which is pure overhead for an offline test rig.
    $arguments = @(
        "-avd", $Avd,
        "-memory", $Memory,
        "-cores", $Cores,
        "-no-snapshot",
        "-no-snapshot-save",
        "-no-boot-anim",
        "-no-window",
        "-gpu", "swiftshader_indirect",
        "-no-metrics",
        # Neither camera is used by the audit, and each configured camera keeps a host-side decode
        # thread and framebuffer alive for the lifetime of the process.
        "-camera-back", "none",
        "-camera-front", "none",
        "-netdelay", "none",
        "-netspeed", "full",
        "-ports", "$Port,$($Port + 1)"
    )
    Write-Output "starting $Avd (memory ${Memory} MB, ${Cores} cores)"
    Start-Process -FilePath $emulator -ArgumentList $arguments -WindowStyle Hidden

    $deadline = (Get-Date).AddMinutes(5)
    & $adb -s $serial wait-for-device | Out-Null
    while ((Get-Date) -lt $deadline) {
        $booted = (& $adb -s $serial shell getprop sys.boot_completed 2>&1) -join '' -match '1'
        if ($booted) { break }
        Start-Sleep -Seconds 3
    }
    if (-not $booted) { throw "emulator did not finish booting within 5 minutes" }
    # The boot broadcast fires before the launcher and the IME service are ready.
    Start-Sleep -Seconds 15
    Show-Status
}

function Stop-AuditEmulator {
    if (-not (Get-EmulatorProcess)) { Write-Output 'emulator: not running'; return }
    & $adb -s $serial emu kill 2>&1 | Out-Null
    $deadline = (Get-Date).AddSeconds(45)
    while ((Get-EmulatorProcess) -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 2 }
    if (Get-EmulatorProcess) {
        Write-Warning 'emulator ignored the graceful stop; terminating'
        Stop-Process -Name 'qemu-system-x86_64-headless' -Force
        Start-Sleep -Seconds 5
    }
    $host_ = Get-CimInstance Win32_OperatingSystem
    "host free: {0} MB" -f [math]::Round($host_.FreePhysicalMemory / 1KB) | Write-Output
}

switch ($Action) {
    'Start' { Start-AuditEmulator }
    'Stop' { Stop-AuditEmulator }
    'Restart' { Stop-AuditEmulator; Start-AuditEmulator }
    'Status' { Show-Status }
    'Trim' {
        $trim = Trim-EmulatorWorkingSet -Force
        if (-not $trim) { Write-Output 'emulator: not running' }
        else { Write-Output ("trim: {0} MB -> {1} MB (freed {2} MB)" -f $trim.Before, $trim.After, $trim.Delta) }
    }
    'Watch' { Watch-Emulator }
}
