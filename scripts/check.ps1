$ErrorActionPreference = 'Stop'

if (-not $env:JAVA_HOME) {
    Write-Error 'JAVA_HOME に JDK 17 以上を設定してください。'
}

& "$PSScriptRoot\..\gradlew.bat" clean testDebugUnitTest lintDebug assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host '検証完了: app/build/outputs/apk/debug/app-debug.apk'
