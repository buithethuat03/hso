[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$StageBuild,
    [switch]$VerifyOnly,
    [switch]$Resume
)
$ErrorActionPreference = 'Stop'
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskBuildRoot = Join-Path $taskWorkspace 'build'
$taskBuild = (Resolve-Path -LiteralPath $StageBuild).Path
if (-not $taskBuild.StartsWith($taskBuildRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Verified stage must be inside workspace/build'
}
$taskTag = Split-Path -Leaf $taskBuild
if ($taskTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid stage tag' }
$taskProof = Get-Content -Encoding UTF8 -Raw -LiteralPath (Join-Path $taskBuild 'verified.json') | ConvertFrom-Json
if (-not $taskProof.Verified -or $taskProof.Published -or -not $taskProof.AllClassesLinked -or
    -not $taskProof.AllOtherEndpointsPreserved -or -not $taskProof.NativeInputEventsVerified -or
    -not $taskProof.SharedFarmDelayVerified -or -not $taskProof.ManualMovementPriorityVerified -or
    -not $taskProof.MovementResume100msVerified -or -not $taskProof.InventoryAutoAttackVerified -or
    -not $taskProof.Map48CacheDisabledVerified) {
    throw 'Complete unpublished verification including shared Farm delay and movement priority is required'
}
if (Test-Path -LiteralPath (Join-Path $taskBuild 'publish.json')) { throw 'Stage was already published' }
$taskExpectedBefore = 'A23574A528F8DDF668D07021BCEAB722AD4057ECBEDCB28FCE4A34DDBA17BC34'
if ($taskProof.SingleSourceInputSHA256 -ne '188C481E59318B27468F29DE7420C90FE1E9027BD77A22B4710A312FD6BDB2AD') {
    throw 'Unexpected verified source hash'
}
$taskCandidate = (Resolve-Path -LiteralPath $taskProof.SingleCandidate).Path
if (-not $taskCandidate.StartsWith($taskBuild + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Candidate escaped verified stage'
}
$taskCandidateHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskCandidate).Hash
if ($taskCandidateHash -ne $taskProof.SingleCandidateSHA256) { throw 'Verified candidate changed' }
$taskBinaryLines = @(Get-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'auto-attack-binary.txt'))
foreach ($taskMarker in @('NATIVE_PATHS_VERIFIED=true', 'RMS_LIFECYCLE_VERIFIED=true',
    'HUD_SNAPSHOTS_VERIFIED=true', 'REMOTE_BARRIER_VERIFIED=true',
    'SHARED_FARM_DELAY_VERIFIED=true', 'MANUAL_MOVEMENT_PRIORITY_VERIFIED=true',
    'MOVEMENT_RESUME_100MS_VERIFIED=true', 'INVENTORY_AUTO_ATTACK_VERIFIED=true')) {
    if ($taskBinaryLines -cnotcontains $taskMarker) { throw "Missing binary result: $taskMarker" }
}
if (-not (Select-String -LiteralPath (Join-Path $taskBuild 'HungBySonMonsterCacheHostTest.txt') `
    -Pattern '^MAP48_CACHE_DISABLED_VERIFIED=true$' -Quiet)) { throw 'Missing map48 cache exclusion result' }
if (-not (Select-String -LiteralPath (Join-Path $taskBuild 'class-link-and-resource-check.txt') -Pattern '^AUTO_ATTACK_JAR=PASS' -Quiet)) {
    throw 'Missing independent JAR verifier pass'
}
if (-not (Select-String -LiteralPath (Join-Path $taskBuild 'native-input-events.txt') -Pattern '^NATIVE_INPUT_EVENTS_VERIFIED=true$' -Quiet)) {
    throw 'Missing native keyboard/touch input result'
}
$taskJarRoot = [IO.Path]::GetFullPath((Join-Path $taskWorkspace '../jar'))
$taskTarget = Join-Path $taskJarRoot 'Hung_By_Son.jar'
$taskTargetHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskTarget).Hash
$taskAlreadyReplaced = $Resume -and $taskTargetHash -eq $taskCandidateHash
if ($taskTargetHash -ne $taskExpectedBefore -and -not $taskAlreadyReplaced) { throw 'Release preimage changed' }
$taskOther = @{}
Get-ChildItem -LiteralPath $taskJarRoot -File -Filter '*.jar' | Where-Object { $_.Name -ne 'Hung_By_Son.jar' } | ForEach-Object {
    $taskOther[$_.FullName] = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash
}
$taskExpectedOther = @($taskProof.ReleaseJarEndpoints | Where-Object { $_.Name -ne 'Hung_By_Son.jar' })
if ($taskExpectedOther.Count -ne $taskOther.Count) { throw 'Unrelated endpoint inventory differs from verified stage' }
foreach ($taskEndpoint in $taskExpectedOther) {
    $taskEndpointPath = Join-Path $taskJarRoot $taskEndpoint.Name
    if (-not $taskOther.ContainsKey($taskEndpointPath) -or $taskOther[$taskEndpointPath] -ne $taskEndpoint.SHA256) {
        throw 'Unrelated endpoint differs from verified stage'
    }
}
$taskGolden = Join-Path $taskWorkspace 'inputs/luna_final-golden-baseline.jar'
$taskGoldenHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskGolden).Hash
if ($taskGoldenHash -ne '06622CD81DC6790871A5863D465D4E352B29A94BB6FD0062C54F7CF9C6C47E0F') { throw 'Golden baseline changed' }
$taskBackupRoot = Join-Path $taskJarRoot 'backup'
$taskBackup = Join-Path $taskBackupRoot ('Hung_By_Son-before-' + $taskTag + '.jar')
$taskPublishRoot = Join-Path $taskBuild 'publish-stage'
$taskStaged = Join-Path $taskPublishRoot 'Hung_By_Son.jar'
$taskAtomicOld = Join-Path $taskPublishRoot 'Hung_By_Son.atomic-old'
if ((Test-Path -LiteralPath $taskBackup) -and
    (-not $Resume -or (Get-FileHash -Algorithm SHA256 -LiteralPath $taskBackup).Hash -ne $taskExpectedBefore)) {
    throw 'Backup already exists or has an unexpected hash'
}
if ((Test-Path -LiteralPath $taskPublishRoot) -and -not $Resume) { throw 'Publication staging directory already exists' }
if ($taskAlreadyReplaced -and
    (-not (Test-Path -LiteralPath $taskBackup) -or -not (Test-Path -LiteralPath $taskAtomicOld) -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskBackup).Hash -ne $taskExpectedBefore -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskAtomicOld).Hash -ne $taskExpectedBefore)) {
    throw 'Resuming a completed replacement requires both verified preimage backups'
}
if ($VerifyOnly) {
    Write-Output ('PUBLICATION_READY=' + $taskTarget + ' SHA256=' + $taskCandidateHash + ' BACKUP=' + $taskBackup)
    return
}
if (-not (Test-Path -LiteralPath $taskBackupRoot)) { New-Item -ItemType Directory -Path $taskBackupRoot | Out-Null }
if (-not (Test-Path -LiteralPath $taskPublishRoot)) { New-Item -ItemType Directory -Path $taskPublishRoot | Out-Null }
if (-not (Test-Path -LiteralPath $taskBackup)) { Copy-Item -LiteralPath $taskTarget -Destination $taskBackup }
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskBackup).Hash -ne $taskExpectedBefore) { throw 'Backup hash mismatch' }
if (-not $taskAlreadyReplaced) {
    if (-not (Test-Path -LiteralPath $taskStaged)) { Copy-Item -LiteralPath $taskCandidate -Destination $taskStaged }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskStaged).Hash -ne $taskCandidateHash) { throw 'Staged copy hash mismatch' }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskTarget).Hash -ne $taskExpectedBefore) { throw 'Release changed before replacement' }
    if (Test-Path -LiteralPath $taskAtomicOld) { throw 'Atomic backup already exists' }
    [IO.File]::Replace($taskStaged, $taskTarget, $taskAtomicOld)
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskTarget).Hash -ne $taskCandidateHash -or
    (Get-FileHash -Algorithm SHA256 -LiteralPath $taskAtomicOld).Hash -ne $taskExpectedBefore) { throw 'Atomic replacement hash mismatch' }
$taskOtherNow = @(Get-ChildItem -LiteralPath $taskJarRoot -File -Filter '*.jar' | Where-Object { $_.Name -ne 'Hung_By_Son.jar' })
if ($taskOtherNow.Count -ne $taskOther.Count) { throw 'Unrelated endpoint inventory changed' }
foreach ($taskJar in $taskOtherNow) {
    if (-not $taskOther.ContainsKey($taskJar.FullName) -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskJar.FullName).Hash -ne $taskOther[$taskJar.FullName]) {
        throw 'Unrelated endpoint changed'
    }
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskGolden).Hash -ne $taskGoldenHash) { throw 'Golden changed' }
[ordered]@{
    Published = $true
    VerifiedStage = $taskBuild
    Target = $taskTarget
    SHA256 = $taskCandidateHash
    BeforeSHA256 = $taskExpectedBefore
    Backup = $taskBackup
    OtherEndpointsUnchanged = $true
    GoldenUnchanged = $true
    LiveServerTested = $false
} | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'publish.json')
Write-Output ('PUBLISHED=' + $taskTarget + ' SHA256=' + $taskCandidateHash + ' BACKUP=' + $taskBackup)
