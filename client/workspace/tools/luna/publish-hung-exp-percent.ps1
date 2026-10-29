[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$StageBuild,[switch]$Resume)
$ErrorActionPreference = 'Stop'
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskBuild = (Resolve-Path -LiteralPath $StageBuild).Path
$taskBuildRoot = Join-Path $taskWorkspace 'build'
if (-not $taskBuild.StartsWith($taskBuildRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Stage escaped workspace build root' }
$taskProof = Get-Content -LiteralPath (Join-Path $taskBuild 'verified.json') -Encoding UTF8 -Raw | ConvertFrom-Json
if (-not $taskProof.Verified -or $taskProof.Variant -ne 'x1' -or $taskProof.Decimals -ne 3 -or -not $taskProof.NativeExpAndRmsTested) { throw 'Complete x1 verification is required' }
if (Test-Path -LiteralPath (Join-Path $taskBuild 'publish.json')) { throw 'Stage already published' }
$taskCandidate = (Resolve-Path -LiteralPath $taskProof.Candidate).Path
if (-not $taskCandidate.StartsWith($taskBuild + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Candidate escaped verified stage' }
$taskHash = $taskProof.SHA256
$taskExpected = $taskProof.InputSHA256
if ($taskExpected -ne 'BCF304BB6245CDBB6F48D5888404F8186E4E0895E2DCDAF608C68D53FEADA6E2') { throw 'Unexpected release preimage' }
if ((Get-FileHash -LiteralPath $taskCandidate -Algorithm SHA256).Hash -ne $taskHash) { throw 'Candidate changed after verification' }
$taskReleaseRoot = [IO.Path]::GetFullPath((Join-Path $taskWorkspace '../jar'))
$taskTarget = Join-Path $taskReleaseRoot 'Hung_By_Son.jar'
if ((Get-FileHash -LiteralPath $taskTarget -Algorithm SHA256).Hash -ne $taskExpected) { throw 'Release changed after verification' }
$taskOthers = @{}
Get-ChildItem -LiteralPath $taskReleaseRoot -File -Filter '*.jar' | Where-Object {$_.Name -ne 'Hung_By_Son.jar'} | ForEach-Object {
    $taskOthers[$_.FullName] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
}
$taskGolden = Join-Path $taskWorkspace 'inputs/luna_final-golden-baseline.jar'
$taskGoldenHash = (Get-FileHash -LiteralPath $taskGolden -Algorithm SHA256).Hash
$taskBackupRoot = Join-Path $taskReleaseRoot 'backup'
if (-not (Test-Path -LiteralPath $taskBackupRoot)) { New-Item -ItemType Directory -Path $taskBackupRoot | Out-Null }
$taskTag = Split-Path -Leaf $taskBuild
$taskBackup = Join-Path $taskBackupRoot ('Hung_By_Son-before-' + $taskTag + '.jar')
if (Test-Path -LiteralPath $taskBackup) {
    if (-not $Resume -or (Get-FileHash -LiteralPath $taskBackup -Algorithm SHA256).Hash -ne $taskExpected) { throw 'Existing backup requires -Resume and exact hash' }
} else { Copy-Item -LiteralPath $taskTarget -Destination $taskBackup }
if ((Get-FileHash -LiteralPath $taskBackup -Algorithm SHA256).Hash -ne $taskExpected) { throw 'Backup hash mismatch' }
$taskStaged = Join-Path $taskBuild 'publish-copy.jar'
if (Test-Path -LiteralPath $taskStaged) {
    if (-not $Resume -or (Get-FileHash -LiteralPath $taskStaged -Algorithm SHA256).Hash -ne $taskHash) { throw 'Existing staged copy requires -Resume and exact hash' }
} else { Copy-Item -LiteralPath $taskCandidate -Destination $taskStaged }
$taskAtomicOld = Join-Path $taskBuild 'atomic-old.jar'
if (Test-Path -LiteralPath $taskAtomicOld) { throw 'Atomic backup already exists' }
if ((Get-FileHash -LiteralPath $taskStaged -Algorithm SHA256).Hash -ne $taskHash) { throw 'Staged candidate hash mismatch' }
[IO.File]::Replace($taskStaged,$taskTarget,$taskAtomicOld)
if ((Get-FileHash -LiteralPath $taskTarget -Algorithm SHA256).Hash -ne $taskHash -or
    (Get-FileHash -LiteralPath $taskAtomicOld -Algorithm SHA256).Hash -ne $taskExpected) { throw 'Published or atomic backup hash mismatch' }
foreach ($taskOther in $taskOthers.Keys) {
    if ((Get-FileHash -LiteralPath $taskOther -Algorithm SHA256).Hash -ne $taskOthers[$taskOther]) { throw 'Unrelated endpoint changed' }
}
if ((Get-FileHash -LiteralPath $taskGolden -Algorithm SHA256).Hash -ne $taskGoldenHash) { throw 'Golden changed' }
[ordered]@{Published=$true;Target=$taskTarget;SHA256=$taskHash;Backup=$taskBackup;BackupSHA256=$taskExpected
    OtherEndpointsUnchanged=$true;GoldenUnchanged=$true;LiveServerTested=$false} | ConvertTo-Json |
    Set-Content -LiteralPath (Join-Path $taskBuild 'publish.json') -Encoding UTF8
Write-Output "PUBLISHED=$taskTarget SHA256=$taskHash BACKUP=$taskBackup"
