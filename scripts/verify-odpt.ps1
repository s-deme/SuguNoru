[CmdletBinding()]
param(
    [string]$TokenFile,
    [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
)
$ErrorActionPreference = 'Stop'
# Requires the separate verification APK and androidTest APK, installed on one device.
# The token file contains only the token. Never put the value in command arguments.
$settings = @{ host = 'api-public.odpt.org'; token = 'public-verification' }
if ($TokenFile) {
    $settings.host = 'api.odpt.org'
    $settings.token = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $TokenFile)).Trim()
    if ([string]::IsNullOrWhiteSpace($settings.token)) { throw 'Token file is empty' }
}
& $Adb shell run-as jp.sugunoru.app.verification mkdir -p files
if ($LASTEXITCODE -ne 0) { throw 'Install the verification APK first' }
try {
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $Adb
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.ArgumentList.Add('shell')
    $start.ArgumentList.Add('-T')
    $start.ArgumentList.Add("run-as jp.sugunoru.app.verification sh -c 'cat > files/odpt-live.json'")
    $process = [Diagnostics.Process]::Start($start)
    $process.StandardInput.WriteLine(($settings | ConvertTo-Json -Compress))
    $process.StandardInput.Close()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw 'Could not configure live verification' }
    $result = & $Adb shell am instrument -w -e class jp.sugunoru.app.data.OdptLiveTest `
        jp.sugunoru.app.verification.test/android.test.InstrumentationTestRunner
    $result | Write-Output
    $report = $result -join "`n"
    if ($LASTEXITCODE -ne 0 -or $report -notmatch 'OK \(1 test\)' -or $report -match 'SKIPPED') {
        throw 'Live ODPT verification failed'
    }
} finally {
    & $Adb shell run-as jp.sugunoru.app.verification rm -f files/odpt-live.json
    $settings.token = $null
}
