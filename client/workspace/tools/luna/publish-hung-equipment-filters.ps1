[CmdletBinding()]
param([string]$BuildTag = 'hung-sale-scroll-farm-match-20261025-a')
$ErrorActionPreference = 'Stop'
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskWorkspace
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid BuildTag' }
$taskBuild = Join-Path $taskWorkspace ('build/' + $BuildTag)
$taskProof = Join-Path $taskBuild 'verified.json'
$taskVerified = Get-Content -Raw -Encoding UTF8 -LiteralPath $taskProof | ConvertFrom-Json
$taskExpectedOld = '2F07C9183C568D929F29BC2FE5B12FBD73FBB36CFEA36C48C86CE22A2E6651AF'
$taskExpectedNew = 'E8A95AEBF84055D9137276792C7BD4D6E2468B21FF51A8B3C48E57CD8ACC2CFA'
$taskCandidate = Join-Path $taskBuild 'Hung_By_Son.jar'
$taskRelease = [IO.Path]::GetFullPath((Join-Path $taskWorkspace '../jar/Hung_By_Son.jar'))
$taskBackup = [IO.Path]::GetFullPath((Join-Path $taskWorkspace ('../jar/backup/Hung_By_Son-before-' + $BuildTag + '.jar')))
if (-not $taskVerified.Verified -or $taskVerified.Published -or $taskVerified.InputSHA256 -ne $taskExpectedOld -or $taskVerified.SHA256 -ne $taskExpectedNew) { throw 'Missing or stale verification record' }
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskCandidate).Hash -ne $taskExpectedNew) { throw 'Candidate changed' }
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskRelease).Hash -ne $taskExpectedOld) { throw 'Release changed; inspect before publishing' }
if ((Test-Path -LiteralPath $taskBackup) -and (Get-FileHash -Algorithm SHA256 -LiteralPath $taskBackup).Hash -ne $taskExpectedOld) { throw 'Existing backup does not match the pinned preimage' }
foreach ($taskLog in @('equipment-filters-binary.txt','equipment-filters-ui.txt','sale-return-scroll-binary.txt','auto-attack-native.txt','maze-binary.txt','class-linkage-offline.txt','startup/emulator-run.json')) {
    if (-not (Test-Path -LiteralPath (Join-Path $taskBuild $taskLog) -PathType Leaf)) { throw "Missing check $taskLog" }
}
$taskOther = @{}
foreach ($taskJar in Get-ChildItem -LiteralPath (Split-Path $taskRelease) -Filter '*.jar' -File) {
    if ($taskJar.FullName -ne $taskRelease) { $taskOther[$taskJar.FullName] = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskJar.FullName).Hash }
}
New-Item -ItemType Directory -Force -Path (Split-Path $taskBackup) | Out-Null
if (-not (Test-Path -LiteralPath $taskBackup)) { Copy-Item -LiteralPath $taskRelease -Destination $taskBackup }
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskBackup).Hash -ne $taskExpectedOld) { throw 'Backup verification failed' }
$taskStageDir = Join-Path $taskBuild 'publish-stage'
New-Item -ItemType Directory -Force -Path $taskStageDir | Out-Null
$taskStage = Join-Path $taskStageDir 'Hung_By_Son.jar'
Copy-Item -LiteralPath $taskCandidate -Destination $taskStage
[IO.File]::Replace($taskStage,$taskRelease,[NullString]::Value)
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskRelease).Hash -ne $taskExpectedNew) { throw 'Published hash mismatch' }
foreach ($taskPath in $taskOther.Keys) {
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskPath).Hash -ne $taskOther[$taskPath]) { throw "Other endpoint changed: $taskPath" }
}
$taskVerified.Published = $true
$taskVerified | Add-Member -NotePropertyName Backup -NotePropertyValue $taskBackup
$taskVerified | Add-Member -NotePropertyName BackupSHA256 -NotePropertyValue $taskExpectedOld
$taskVerified | Add-Member -NotePropertyName OtherEndpointsUnchanged -NotePropertyValue $true
$taskVerified | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath $taskProof
Write-Output "PUBLISHED=$taskRelease SHA256=$taskExpectedNew BACKUP=$taskBackup OTHER_ENDPOINTS_UNCHANGED=$($taskOther.Count)"
