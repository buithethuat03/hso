[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$StageBuild,
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ExpectedInputSHA256
)

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

$taskProofPath = Join-Path $taskStage 'verified.json'
if (-not (Test-Path -LiteralPath $taskProofPath -PathType Leaf)) { throw 'Stage has no verified.json proof' }
$taskProof = Get-Content -Raw -Encoding UTF8 -LiteralPath $taskProofPath | ConvertFrom-Json
if (-not $taskProof.Verified -or $taskProof.Published -or -not $taskProof.SpecialAttack -or -not $taskProof.AllClassesLinked) {
    throw 'A verified, unpublished Special Attack stage with linked classes is required'
}
if (-not $taskProof.InputSHA256.Equals($ExpectedInputSHA256, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Verified stage input hash does not match ExpectedInputSHA256'
}

$taskInput = (Resolve-Path -LiteralPath $taskProof.Input).Path
$taskJarRoot = [IO.Path]::GetFullPath((Join-Path $taskWorkspace '../jar'))
$taskTarget = [IO.Path]::GetFullPath((Join-Path $taskJarRoot 'Hung_By_Son.jar'))
if (-not $taskInput.Equals($taskTarget, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Verified stage was not built from the Hung_By_Son.jar endpoint'
}
$taskCandidate = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $taskProof.Candidate).Path)
if (-not $taskCandidate.StartsWith($taskStage + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Candidate escaped its verified stage'
}
$taskCandidateHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskCandidate).Hash
if (-not $taskCandidateHash.Equals($taskProof.SHA256, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Verified candidate bytes changed after testing'
}

$taskSpecialCatalog = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $taskProof.SpecialCatalog).Path)
if (-not $taskSpecialCatalog.StartsWith($taskStage + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Special Attack catalog escaped its verified stage'
}
$taskCatalogOutput = @(Select-String -LiteralPath $taskSpecialCatalog -Pattern '^output\.sha256=(\S+)$')
if ($taskCatalogOutput.Count -ne 1 -or
    -not $taskCatalogOutput[0].Matches[0].Groups[1].Value.Equals($taskCandidateHash, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Special Attack catalog is not bound to the verified candidate'
}
$taskSemanticLog = Join-Path $taskStage 'SpecialAttackHostTest.txt'
$taskBinaryLog = Join-Path $taskStage 'special-attack-binary.txt'
if (-not (Select-String -LiteralPath $taskSemanticLog -Pattern '^SpecialAttackHostTest PASS \(' -Quiet)) {
    throw 'Special Attack semantic fixture did not pass'
}
if (-not (Test-Path -LiteralPath $taskBinaryLog -PathType Leaf) -or
    (Get-Item -LiteralPath $taskBinaryLog).Length -eq 0) {
    throw 'Special Attack binary fixture output is missing'
}
if (Test-Path -LiteralPath (Join-Path $taskStage 'published.json')) { throw 'Stage was already published' }

$taskExpectedBefore = $ExpectedInputSHA256.ToUpperInvariant()
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskTarget).Hash -cne $taskExpectedBefore) {
    throw 'Hung_By_Son.jar release preimage changed'
}
$taskOther = @{}
foreach ($taskJar in Get-ChildItem -LiteralPath $taskJarRoot -File -Filter '*.jar') {
    if ($taskJar.Name -cne 'Hung_By_Son.jar') {
        $taskOther[$taskJar.FullName] = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskJar.FullName).Hash
    }
}

$taskGolden = Join-Path $taskWorkspace 'inputs/luna_final-golden-baseline.jar'
$taskExpectedGolden = '06622CD81DC6790871A5863D465D4E352B29A94BB6FD0062C54F7CF9C6C47E0F'
$taskGoldenHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskGolden).Hash
if ($taskGoldenHash -cne $taskExpectedGolden) { throw 'Golden baseline hash changed' }

$taskBackupRoot = Join-Path $taskJarRoot 'backup'
if (-not (Test-Path -LiteralPath $taskBackupRoot -PathType Container)) {
    New-Item -ItemType Directory -Path $taskBackupRoot | Out-Null
}
$taskBackup = Join-Path $taskBackupRoot ('Hung_By_Son-before-' + $taskTag + '.jar')
if ((Test-Path -LiteralPath $taskBackup) -and
    (Get-FileHash -Algorithm SHA256 -LiteralPath $taskBackup).Hash -cne $taskExpectedBefore) {
    throw 'Existing stage backup does not match the release preimage'
}
$taskPublishRoot = Join-Path $taskStage 'publish-stage'
if (-not (Test-Path -LiteralPath $taskPublishRoot)) { New-Item -ItemType Directory -Path $taskPublishRoot | Out-Null }
$taskStaged = Join-Path $taskPublishRoot 'Hung_By_Son.jar'
$taskAtomicOld = Join-Path $taskPublishRoot 'Hung_By_Son.atomic-old.jar'
$taskRollbackNew = Join-Path $taskPublishRoot 'Hung_By_Son.rollback-new.jar'

if (Test-Path -LiteralPath $taskAtomicOld) { throw 'Atomic preimage already exists; inspect the prior publication' }
if (-not (Test-Path -LiteralPath $taskBackup)) { Copy-Item -LiteralPath $taskTarget -Destination $taskBackup }
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskBackup).Hash -cne $taskExpectedBefore) {
    throw 'Release backup verification failed'
}
if (-not (Test-Path -LiteralPath $taskStaged)) { Copy-Item -LiteralPath $taskCandidate -Destination $taskStaged }
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskStaged).Hash -cne $taskCandidateHash) {
    throw 'Staged candidate copy verification failed'
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskTarget).Hash -cne $taskExpectedBefore) {
    throw 'Release changed before atomic replacement'
}

$taskReplaced = $false
try {
    [IO.File]::Replace($taskStaged, $taskTarget, $taskAtomicOld)
    $taskReplaced = $true
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskTarget).Hash -cne $taskCandidateHash -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskAtomicOld).Hash -cne $taskExpectedBefore) {
        throw 'Atomic replacement hash verification failed'
    }
    foreach ($taskPath in $taskOther.Keys) {
        if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskPath).Hash -cne $taskOther[$taskPath]) {
            throw "Another release endpoint changed: $taskPath"
        }
    }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskGolden).Hash -cne $taskGoldenHash) {
        throw 'Golden baseline changed during publication'
    }
} catch {
    if ($taskReplaced -and (Test-Path -LiteralPath $taskAtomicOld) -and
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskAtomicOld).Hash -ceq $taskExpectedBefore) {
        [IO.File]::Replace($taskAtomicOld, $taskTarget, $taskRollbackNew)
        if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskTarget).Hash -cne $taskExpectedBefore) {
            throw 'Publication failed and the release preimage could not be restored'
        }
    }
    throw
}

$taskReceipt = [ordered]@{
    Published = $true
    VerifiedStage = $taskStage
    InputSHA256 = $taskExpectedBefore
    Target = $taskTarget
    SHA256 = $taskCandidateHash
    Backup = $taskBackup
    AtomicPreimage = $taskAtomicOld
    OtherEndpointsUnchanged = $true
    GoldenUnchanged = $true
    LiveServerTested = $false
}
$taskReceipt | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskStage 'published.json')
Write-Output ('PUBLISHED=' + $taskTarget + ' SHA256=' + $taskCandidateHash + ' BACKUP=' + $taskBackup)
