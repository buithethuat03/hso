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
$taskProof = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'verified.json') | ConvertFrom-Json
foreach ($taskGate in @('Verified', 'FullClassLink', 'SharedExpBackendRootOnly', 'AutoAttackRmsSlotIsolation',
    'NativeInputEventsVerified', 'VirtualInputChannelsVerified', 'SharedFarmDelayVerified', 'ManualMovementPriorityVerified',
    'ExpNativeAndSharedFileRmsVerified', 'CrowdedX3Verified', 'AllOtherEndpointsPreserved',
    'MovementResume100msVerified', 'InventoryAutoAttackVerified', 'Map48CacheDisabledVerified')) {
    if (-not $taskProof.$taskGate) { throw ('Incomplete X3 verification: ' + $taskGate) }
}
if ($taskProof.Published -or (Test-Path -LiteralPath (Join-Path $taskBuild 'publish.json'))) {
    throw 'Stage was already published'
}
$taskCandidate = (Resolve-Path -LiteralPath $taskProof.X3Candidate).Path
if (-not $taskCandidate.StartsWith($taskBuild + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Candidate escaped verified stage'
}
$taskCandidateHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskCandidate).Hash
if ($taskCandidateHash -ne $taskProof.X3CandidateSHA256 -or $taskProof.X3Classes -ne 797) {
    throw 'Verified X3 candidate changed'
}
$taskJarRoot = [IO.Path]::GetFullPath((Join-Path $taskWorkspace '../jar'))
$taskSingle = Join-Path $taskJarRoot 'Hung_By_Son.jar'
$taskSingleHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskSingle).Hash
if ($taskSingleHash -ne $taskProof.SingleSourceSHA256) { throw 'Single release differs from verified X3 source' }
if ($taskProof.NativeInputEventsBoundToSourceSHA256 -ne $taskSingleHash) { throw 'Native input proof is bound to a different single source' }
$taskSingleProofPath = (Resolve-Path -LiteralPath $taskProof.SingleVerifiedJson).Path
if (-not $taskSingleProofPath.StartsWith($taskBuildRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Single verification proof escaped workspace/build'
}
$taskSingleProof = Get-Content -Raw -Encoding UTF8 -LiteralPath $taskSingleProofPath | ConvertFrom-Json
$taskSinglePublish = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path (Split-Path -Parent $taskSingleProofPath) 'publish.json') | ConvertFrom-Json
if (-not $taskSingleProof.Verified -or -not $taskSingleProof.SharedFarmDelayVerified -or
    -not $taskSingleProof.ManualMovementPriorityVerified -or -not $taskSingleProof.MovementResume100msVerified -or
    -not $taskSingleProof.InventoryAutoAttackVerified -or -not $taskSingleProof.Map48CacheDisabledVerified -or
    -not $taskSinglePublish.Published -or
    $taskSingleProof.SingleCandidateSHA256 -ne $taskSingleHash -or $taskSinglePublish.SHA256 -ne $taskSingleHash) {
    throw 'Source single was not verified and published with shared delay and movement priority'
}
$taskAutoLog = @(Get-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'auto-attack-x3-host.txt'))
foreach ($taskMarker in @('X3_AUTO_ATTACK_VERIFIED=true', 'X3_AUTO_RMS_TWO_PROCESS_VERIFIED=true',
    'X3_VIRTUAL_INPUT_VERIFIED=true', 'SHARED_FARM_DELAY_VERIFIED=true', 'MANUAL_MOVEMENT_PRIORITY_VERIFIED=true',
    'MOVEMENT_RESUME_100MS_VERIFIED=true', 'INVENTORY_AUTO_ATTACK_VERIFIED=true',
    'WORLD_CHANGE_MOVEMENT_DELAY_VERIFIED=true')) {
    if ($taskAutoLog -cnotcontains $taskMarker) { throw ('Missing X3 result: ' + $taskMarker) }
}
if (-not (Select-String -LiteralPath (Join-Path $taskBuild 'x3-host.txt') -Pattern '^MAP48_CACHE_DISABLED_VERIFIED=true$' -Quiet)) {
    throw 'Missing per-slot map48 cache result'
}
if ((Get-Item -LiteralPath (Join-Path $taskBuild 'launcher.err.log')).Length -ne 0 -or
    -not (Select-String -LiteralPath (Join-Path $taskBuild 'launcher.out.log') -Pattern '1 -> 2 -> 3 -> 1' -Quiet)) {
    throw 'Missing clean held-* launcher transition'
}
$taskExpectedBefore = 'F7EBBB2D80CC7DE5D338776BF561CEF5D8E7CDC117729341799D5273DF60EE2E'
$taskTarget = Join-Path $taskJarRoot 'Hung_By_Son_x3.jar'
$taskTargetHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskTarget).Hash
$taskAlreadyReplaced = $Resume -and $taskTargetHash -eq $taskCandidateHash
if ($taskTargetHash -ne $taskExpectedBefore -and -not $taskAlreadyReplaced) { throw 'X3 endpoint preimage changed' }
if ($taskProof.ExistingX3EndpointSHA256 -ne $taskExpectedBefore) { throw 'Unexpected verified X3 preimage' }
$taskExpectedOther = @($taskProof.ReleaseJarEndpoints | Where-Object { $_.Name -ne 'Hung_By_Son_x3.jar' })
$taskOtherNow = @(Get-ChildItem -LiteralPath $taskJarRoot -File -Filter '*.jar' | Where-Object { $_.Name -ne 'Hung_By_Son_x3.jar' })
if ($taskExpectedOther.Count -ne $taskOtherNow.Count) { throw 'Unrelated endpoint inventory changed' }
$taskExpectedEndpointHashes = @{}
foreach ($taskEndpoint in $taskExpectedOther) {
    $taskExpectedEndpointHash = $taskEndpoint.SHA256
    if ($taskEndpoint.Name -eq 'Hung_By_Son.jar' -and $taskProof.SingleStagedBeforePublication) {
        if ($taskEndpoint.SHA256 -ne $taskProof.SingleEndpointAtStagingSHA256 -or
            $taskSinglePublish.BeforeSHA256 -ne $taskEndpoint.SHA256 -or
            $taskSinglePublish.SHA256 -ne $taskProof.SingleSourceSHA256) {
            throw 'Staged X3 source transition does not match the verified single publication'
        }
        $taskExpectedEndpointHash = $taskSingleHash
    }
    $taskExpectedEndpointHashes[$taskEndpoint.Name] = $taskExpectedEndpointHash
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $taskJarRoot $taskEndpoint.Name)).Hash -ne $taskExpectedEndpointHash) {
        throw ('Unrelated endpoint changed: ' + $taskEndpoint.Name)
    }
}
$taskGolden = Join-Path $taskWorkspace 'inputs/luna_final-golden-baseline.jar'
$taskGoldenHash = '06622CD81DC6790871A5863D465D4E352B29A94BB6FD0062C54F7CF9C6C47E0F'
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskGolden).Hash -ne $taskGoldenHash) { throw 'Golden changed' }
$taskBackupRoot = Join-Path $taskJarRoot 'backup'
$taskBackup = Join-Path $taskBackupRoot ('Hung_By_Son_x3-before-' + $taskTag + '.jar')
$taskPublishRoot = Join-Path $taskBuild 'publish-stage'
$taskStaged = Join-Path $taskPublishRoot 'Hung_By_Son_x3.jar'
$taskAtomicOld = Join-Path $taskPublishRoot 'Hung_By_Son_x3.atomic-old'
if ((Test-Path -LiteralPath $taskBackup) -and
    (-not $Resume -or (Get-FileHash -Algorithm SHA256 -LiteralPath $taskBackup).Hash -ne $taskExpectedBefore)) {
    throw 'Backup already exists or has an unexpected hash'
}
if ((Test-Path -LiteralPath $taskPublishRoot) -and -not $Resume) { throw 'Publication staging already exists' }
if ($taskAlreadyReplaced -and
    (-not (Test-Path -LiteralPath $taskBackup) -or -not (Test-Path -LiteralPath $taskAtomicOld) -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskBackup).Hash -ne $taskExpectedBefore -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskAtomicOld).Hash -ne $taskExpectedBefore)) {
    throw 'Resume requires both verified preimage backups'
}
if ($VerifyOnly) {
    Write-Output ('PUBLICATION_READY=' + $taskTarget + ' SHA256=' + $taskCandidateHash + ' BACKUP=' + $taskBackup)
    return
}
if (-not (Test-Path -LiteralPath $taskBackupRoot)) { New-Item -ItemType Directory -Path $taskBackupRoot | Out-Null }
if (-not (Test-Path -LiteralPath $taskPublishRoot)) { New-Item -ItemType Directory -Path $taskPublishRoot | Out-Null }
if (-not (Test-Path -LiteralPath $taskBackup)) { Copy-Item -LiteralPath $taskTarget -Destination $taskBackup }
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskBackup).Hash -ne $taskExpectedBefore) { throw 'Backup mismatch' }
if (-not $taskAlreadyReplaced) {
    Copy-Item -LiteralPath $taskCandidate -Destination $taskStaged
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskStaged).Hash -ne $taskCandidateHash) { throw 'Staged hash mismatch' }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskTarget).Hash -ne $taskExpectedBefore) { throw 'X3 changed before replacement' }
    if (Test-Path -LiteralPath $taskAtomicOld) { throw 'Atomic backup already exists' }
    [IO.File]::Replace($taskStaged, $taskTarget, $taskAtomicOld)
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskTarget).Hash -ne $taskCandidateHash -or
    (Get-FileHash -Algorithm SHA256 -LiteralPath $taskAtomicOld).Hash -ne $taskExpectedBefore) {
    throw 'Atomic replacement read-back mismatch'
}
foreach ($taskEndpoint in $taskExpectedOther) {
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $taskJarRoot $taskEndpoint.Name)).Hash -ne $taskExpectedEndpointHashes[$taskEndpoint.Name]) {
        throw ('Unrelated endpoint changed: ' + $taskEndpoint.Name)
    }
}
if (@(Get-ChildItem -LiteralPath $taskJarRoot -File -Filter '*.jar' | Where-Object { $_.Name -ne 'Hung_By_Son_x3.jar' }).Count -ne $taskExpectedOther.Count) {
    throw 'Unrelated endpoint inventory changed after replacement'
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskGolden).Hash -ne $taskGoldenHash) { throw 'Golden changed' }
[ordered]@{
    Published = $true
    VerifiedStage = $taskBuild
    Target = $taskTarget
    SHA256 = $taskCandidateHash
    BeforeSHA256 = $taskExpectedBefore
    Backup = $taskBackup
    SingleSourceSHA256 = $taskSingleHash
    OtherEndpointsUnchanged = $true
    GoldenUnchanged = $true
    LiveServerTested = $false
} | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'publish.json')
Write-Output ('PUBLISHED=' + $taskTarget + ' SHA256=' + $taskCandidateHash + ' BACKUP=' + $taskBackup)
