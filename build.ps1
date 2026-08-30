[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release')]
    [string]$Configuration = 'Debug',
    [string]$OutputDirectory = $env:SUGUNORU_OUTPUT_DIR,
    [string]$KeystorePath = $env:ANDROID_KEYSTORE_PATH
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = $PSScriptRoot
$isWindowsPlatform = $env:OS -eq 'Windows_NT'
$javaCommandName = if ($isWindowsPlatform) { 'java.exe' } else { 'java' }
$gradleWrapperName = if ($isWindowsPlatform) { 'gradlew.bat' } else { 'gradlew' }
$gradleWrapper = Join-Path $projectRoot $gradleWrapperName

if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw 'Gradle Wrapperが見つかりません。'
}

if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $javaExecutable = Join-Path $env:JAVA_HOME "bin/$javaCommandName"
    if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
        throw 'JAVA_HOMEの下にJava実行ファイルが見つかりません。'
    }
} else {
    $javaCommand = Get-Command $javaCommandName -ErrorAction SilentlyContinue
    if ($null -eq $javaCommand) {
        throw 'JDK 17以上をインストールし、JAVA_HOMEを設定してください。'
    }
    $javaExecutable = $javaCommand.Source
}

$javaVersionText = (& $javaExecutable -version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $javaVersionText -notmatch 'version "(?:1\.)?([0-9]+)') {
    throw 'Javaのバージョンを確認できませんでした。'
}
if ([int]$Matches[1] -lt 17) {
    throw 'JDK 17以上が必要です。JAVA_HOMEを更新してください。'
}

$sdkRoot = $env:ANDROID_SDK_ROOT
if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
    $sdkRoot = $env:ANDROID_HOME
}
if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
    $localProperties = Join-Path $projectRoot 'local.properties'
    if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match '^sdk\.dir=(.+)$' } |
            Select-Object -First 1
        if ($sdkLine -match '^sdk\.dir=(.+)$') {
            $sdkRoot = $Matches[1].Replace('\:', ':').Replace('\\', '\')
        }
    }
}
if ([string]::IsNullOrWhiteSpace($sdkRoot) -or -not (Test-Path -LiteralPath $sdkRoot -PathType Container)) {
    throw 'Android SDKが見つかりません。ANDROID_SDK_ROOTを設定してください。'
}
$env:ANDROID_SDK_ROOT = [IO.Path]::GetFullPath($sdkRoot)

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $projectRoot 'dist'
} elseif (-not [IO.Path]::IsPathRooted($OutputDirectory)) {
    $OutputDirectory = Join-Path $projectRoot $OutputDirectory
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)

if ($Configuration -eq 'Release') {
    $requiredNames = @(
        'ANDROID_KEYSTORE_PASSWORD',
        'ANDROID_KEY_ALIAS',
        'ANDROID_KEY_PASSWORD'
    )
    $missing = @()
    if ([string]::IsNullOrWhiteSpace($KeystorePath)) {
        $missing += 'ANDROID_KEYSTORE_PATH or -KeystorePath'
    }
    foreach ($name in $requiredNames) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
            $missing += $name
        }
    }
    if ($missing.Count -gt 0) {
        throw ('Release署名設定が不足しています: ' + ($missing -join ', '))
    }

    $resolvedKeystore = [IO.Path]::GetFullPath($KeystorePath)
    if (-not (Test-Path -LiteralPath $resolvedKeystore -PathType Leaf)) {
        throw '指定されたRelease keystoreが見つかりません。'
    }
    if ((Get-Item -LiteralPath $resolvedKeystore).Length -le 0) {
        throw '指定されたRelease keystoreが空です。'
    }
    $rootPrefix = $projectRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if ($resolvedKeystore.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Release keystoreはリポジトリ外の安全な場所を指定してください。'
    }
    $env:ANDROID_KEYSTORE_PATH = $resolvedKeystore
}

$gradleTasks = if ($Configuration -eq 'Release') {
    @('clean', 'testReleaseUnitTest', 'lintRelease', 'assembleRelease', '--no-daemon')
} else {
    @('clean', 'testDebugUnitTest', 'lintDebug', 'assembleDebug', '--no-daemon')
}

& $gradleWrapper @gradleTasks
if ($LASTEXITCODE -ne 0) {
    throw "Gradle $Configuration buildに失敗しました。"
}

$variant = $Configuration.ToLowerInvariant()
$sourceApk = Join-Path $projectRoot "app/build/outputs/apk/$variant/app-$variant.apk"
if (-not (Test-Path -LiteralPath $sourceApk -PathType Leaf) -or (Get-Item -LiteralPath $sourceApk).Length -le 0) {
    throw 'ビルド済みAPKが見つからないか、空です。'
}

$buildToolsRoot = Join-Path $env:ANDROID_SDK_ROOT 'build-tools'
$buildTools = Get-ChildItem -LiteralPath $buildToolsRoot -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1
if ($null -eq $buildTools) {
    throw 'Android SDK build-toolsが見つかりません。'
}
$apksignerName = if ($isWindowsPlatform) { 'apksigner.bat' } else { 'apksigner' }
$apksigner = Join-Path $buildTools.FullName $apksignerName
if (-not (Test-Path -LiteralPath $apksigner -PathType Leaf)) {
    throw 'apksignerが見つかりません。'
}
& $apksigner verify --verbose $sourceApk
if ($LASTEXITCODE -ne 0) {
    throw 'APK署名の検証に失敗しました。'
}
if ($Configuration -eq 'Release') {
    $certificateDetails = (& $apksigner verify --print-certs $sourceApk 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) {
        throw 'Release APK証明書の検証に失敗しました。'
    }
    if ($certificateDetails -match '(?i)Android Debug') {
        throw 'Release APKにデバッグ証明書が使用されています。'
    }
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$outputName = if ($Configuration -eq 'Release') { 'sugunoru.apk' } else { 'sugunoru-debug.apk' }
$destinationApk = Join-Path $OutputDirectory $outputName
Copy-Item -LiteralPath $sourceApk -Destination $destinationApk -Force

if ($Configuration -eq 'Release') {
    $hash = (Get-FileHash -LiteralPath $destinationApk -Algorithm SHA256).Hash.ToLowerInvariant()
    $checksum = "$hash  $outputName`n"
    [IO.File]::WriteAllText("$destinationApk.sha256", $checksum, [Text.UTF8Encoding]::new($false))
}

Write-Host "ビルド完了: $destinationApk"
