param(
    [string]$Serial = "emulator-5800",
    [string]$OutDir = "Z:\BBK\docs\testing\artifacts\probe"
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
& $adb -s $Serial shell am force-stop dev.bbkb.ime.harness
& $adb -s $Serial shell am start -n dev.bbkb.ime.harness/.HarnessActivity
Start-Sleep -Seconds 3
& $adb -s $Serial shell uiautomator dump /data/local/tmp/probe0.xml | Out-Null
& $adb -s $Serial exec-out cat /data/local/tmp/probe0.xml | Set-Content -Encoding utf8 (Join-Path $OutDir "launch.xml")
# tap the "plain" editor (first EditText)
[xml]$doc = Get-Content (Join-Path $OutDir "launch.xml")
$node = $doc.SelectNodes("//node[@class='android.widget.EditText']")[0]
$bounds = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
$cx = [int](($bounds[0] + $bounds[2]) / 2); $cy = [int](($bounds[1] + $bounds[3]) / 2)
& $adb -s $Serial shell input tap $cx $cy
Start-Sleep -Seconds 3
& $adb -s $Serial shell uiautomator dump /data/local/tmp/probe1.xml | Out-Null
& $adb -s $Serial exec-out cat /data/local/tmp/probe1.xml | Set-Content -Encoding utf8 (Join-Path $OutDir "keyboard.xml")
& $adb -s $Serial exec-out screencap -p | Set-Content -Encoding byte (Join-Path $OutDir "keyboard.png")
Write-Output "saved to $OutDir"
