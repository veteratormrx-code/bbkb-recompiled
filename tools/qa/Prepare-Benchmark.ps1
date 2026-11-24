param(
    [string]$Serial = "emulator-5800",
    [string]$Language = "en",
    [string]$Ime = "dev.bbkb.ime.debug/dev.bbkb.ime.core.BlackBerryIME",
    [int]$Limit = 0,
    [string]$Tag = "fork",
    [switch]$ProbeOnly,
    [string]$ResultCopy
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $adb -s $Serial shell ime set $Ime
& $adb -s $Serial logcat -c
$arguments = @(
    "-Planguage=$Language",
    "-Plimit=$Limit",
    "-Ptag=$Tag",
    "-PprobeOnly=$($ProbeOnly.IsPresent.ToString().ToLower())"
)
Write-Output "gradle args: $($arguments -join ' ')"
Write-Output "gradle $($arguments -join ' ')" | Out-Null
$arguments | Set-Content -Encoding ascii "Z:\BBK\build\bench-args.txt"
