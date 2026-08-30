$ErrorActionPreference = 'Stop'

& "$PSScriptRoot\..\build.ps1" -Configuration Debug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
