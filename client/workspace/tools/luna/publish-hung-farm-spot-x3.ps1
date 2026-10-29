[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$StageBuild,
    [string]$ExpectedX3PreimageSHA256 = '1FB24D590235A8E1DBA2381095BABB711F2A4AE8940E43DF3B6EF7DF086A80CF'
)
# Replaces only Hung_By_Son_x3.jar with a verified Farm spot X3 stage; x1 must already be its source.
$ErrorActionPreference = 'Stop'
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskWorkspace
$taskBuildRoot = [IO.Path]::GetFullPath((Join-Path $taskWorkspace 'build'))
$taskStage = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $StageBuild).Path)
if (-not $taskStage.StartsWith($taskBuildRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Verified stage must be inside client/workspace/build'
}
$taskTag = Split-Path -Leaf $taskStage
if ($taskTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid stage tag' }
if (Test-Path -LiteralPath (Join-Path $taskStage 'published.json')) { throw 'Stage was already published' }
$taskProof = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $taskStage 'verified.json') | ConvertFrom-Json
foreach ($taskGate in @('Verified', 'X3ChecksPassed', 'SingleVerified', 'AllX3ClassesLinked', 'LauncherTransitionVerified',
    'BattlefieldAllSlotsVerified', 'FarmSpotAllSlotsVerified', 'ReleaseJarSnapshotUnchanged')) {
    if (-not $taskProof.$taskGate) { throw ('Incomplete X3 verification: ' + $taskGate) }
}
if ($taskProof.Published) { throw 'Stage was already published' }
if($taskProof.RollingExpWindowSeconds -eq 300){
    if(-not $taskProof.RollingExpAllSlotsVerified -or -not (Select-String -LiteralPath $taskProof.RollingExpAllSlotsLog -Pattern '^HungRollingExpX3HostTest PASS checks=' -Quiet)){throw 'Rolling EXP has not passed in all three slots'}
}
if($taskProof.SingleClassCount -eq 287){
    if(-not $taskProof.EconomyInfo -or -not $taskProof.EconomyAllSlotsVerified -or
        -not (Select-String -LiteralPath $taskProof.EconomyAllSlotsLog -Pattern '^HungEconomyX3HostTest PASS checks=' -Quiet)){throw 'Economy changes have not passed in all three slots'}
}
if (-not (Select-String -LiteralPath $taskProof.FarmSpotAllSlotsLog -Pattern '^HungBySonFarmSpotX3HostTest PASS' -Quiet)) {
    throw 'Farm spot X3 fixture did not pass'
}
$taskCandidate = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $taskProof.X3Candidate).Path)
if (-not $taskCandidate.StartsWith($taskStage + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Candidate escaped its verified stage'
}
$taskCandidateHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskCandidate).Hash
if ($taskCandidateHash -cne ([string]$taskProof.X3CandidateSHA256).ToUpperInvariant()) { throw 'Candidate bytes changed after testing' }

$taskJarRoot = [IO.Path]::GetFullPath((Join-Path $taskWorkspace '../jar'))
$taskTarget = Join-Path $taskJarRoot 'Hung_By_Son_x3.jar'
$taskSingle = Join-Path $taskJarRoot 'Hung_By_Son.jar'
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskSingle).Hash -cne ([string]$taskProof.SingleInputSHA256).ToUpperInvariant()) {
    throw 'Published x1 is not the verified X3 source'
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskTarget).Hash -cne $ExpectedX3PreimageSHA256.ToUpperInvariant()) {
    throw 'X3 release preimage changed; refusing replacement'
}
$taskGolden = Join-Path $taskWorkspace 'inputs/luna_final-golden-baseline.jar'
$taskGoldenHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskGolden).Hash
$taskOther = [ordered]@{}
foreach ($taskFile in (Get-ChildItem -LiteralPath $taskJarRoot -File -Filter '*.jar' | Sort-Object Name)) {
    if ($taskFile.Name -ne 'Hung_By_Son_x3.jar') { $taskOther[$taskFile.FullName] = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskFile.FullName).Hash }
}

$taskBackupRoot = Join-Path $taskJarRoot 'backup'
if (-not (Test-Path -LiteralPath $taskBackupRoot -PathType Container)) { New-Item -ItemType Directory -Path $taskBackupRoot | Out-Null }
$taskBackup = Join-Path $taskBackupRoot ('Hung_By_Son_x3-before-' + $taskTag + '.jar')
if (Test-Path -LiteralPath $taskBackup) { throw 'Backup already exists; inspect the prior publication' }
Copy-Item -LiteralPath $taskTarget -Destination $taskBackup
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskBackup).Hash -cne $ExpectedX3PreimageSHA256.ToUpperInvariant()) {
    throw 'Backup verification failed'
}
$taskStaged = Join-Path $taskJarRoot ('.Hung_By_Son_x3-' + $taskTag + '.tmp')
if (Test-Path -LiteralPath $taskStaged) { throw 'Staged temporary copy already exists' }
Copy-Item -LiteralPath $taskCandidate -Destination $taskStaged
try {
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskStaged).Hash -cne $taskCandidateHash) { throw 'Staged copy verification failed' }
    Move-Item -LiteralPath $taskStaged -Destination $taskTarget -Force
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskTarget).Hash -cne $taskCandidateHash) { throw 'Published bytes mismatch' }
} catch {
    if (Test-Path -LiteralPath $taskStaged) { Remove-Item -LiteralPath $taskStaged -Force }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskTarget).Hash -cne $ExpectedX3PreimageSHA256.ToUpperInvariant()) {
        Copy-Item -LiteralPath $taskBackup -Destination $taskTarget -Force
    }
    throw
}
foreach ($taskPath in $taskOther.Keys) {
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskPath).Hash -cne $taskOther[$taskPath]) { throw "Another release endpoint changed: $taskPath" }
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskGolden).Hash -cne $taskGoldenHash) { throw 'Golden baseline changed during publication' }
[ordered]@{
    Published = $true; Target = $taskTarget; SHA256 = $taskCandidateHash; Backup = $taskBackup
    PreimageSHA256 = $ExpectedX3PreimageSHA256.ToUpperInvariant(); SingleSHA256 = ([string]$taskProof.SingleInputSHA256).ToUpperInvariant()
    OtherEndpointsUnchanged = $true; GoldenUnchanged = $true
} | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskStage 'published.json')
Write-Output ('PUBLISHED_X3=' + $taskTarget + ' SHA256=' + $taskCandidateHash + ' BACKUP=' + $taskBackup)
