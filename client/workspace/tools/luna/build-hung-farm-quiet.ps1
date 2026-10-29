[CmdletBinding()]
param(
    [string]$InputJar = '..\jar\Hung_By_Son.jar',
    [string]$DependencyDir = '',
    [string]$BuildTag = ('hung-farm-quiet-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
)

$ErrorActionPreference = 'Stop'
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskWorkspace
if ([string]::IsNullOrWhiteSpace($DependencyDir)) {
    if (-not [string]::IsNullOrWhiteSpace($env:LUNA_DEPS)) { $DependencyDir = $env:LUNA_DEPS }
    else { $DependencyDir = '..\..\.codex-deps' }
}

$taskExpectedInputHash = '826E8FE3BCE4D08541A206BDA7C91DCC01CF06BFC6B956D744BD1F36FBC26EA4'
$taskBaseBuild = 'build/hung-farm-special-attack-20261023-a'
$taskExpectedDonorHash = 'A8A4165F0E78C7E852444A425CB8C0611BF772A70F6D44B1374091225732FF0E'
$taskExpectedAutoInputHash = '188C481E59318B27468F29DE7420C90FE1E9027BD77A22B4710A312FD6BDB2AD'

function Run-Checked {
    param([string]$Program, [string[]]$Arguments, [string]$Log)
    $taskPriorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $taskLines = @(& $Program @Arguments 2>&1 | ForEach-Object { $_.ToString() })
        $taskExit = $LASTEXITCODE
        $taskLines | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild $Log)
    } finally { $ErrorActionPreference = $taskPriorPreference }
    if ($taskExit -ne 0) {
        Get-Content -LiteralPath (Join-Path $taskBuild $Log) -Tail 24
        throw "$Program failed with exit code $taskExit ($Log)"
    }
    if ($taskLines.Count -gt 0) { $taskLines | Select-Object -Last 1 }
}

function Get-PropertyValue {
    param([string]$Text, [string]$Name)
    $taskPattern = '(?m)^' + [regex]::Escape($Name) + '=([^\r\n]*)'
    $taskMatches = [regex]::Matches($Text, $taskPattern)
    if ($taskMatches.Count -ne 1) { throw "Expected exactly one '$Name' property" }
    return $taskMatches[0].Groups[1].Value.Trim()
}

function Set-PropertyValue {
    param([string]$Text, [string]$Name, [string]$Value)
    $taskPattern = '(?m)^' + [regex]::Escape($Name) + '=[^\r\n]*'
    if ([regex]::Matches($Text, $taskPattern).Count -ne 1) { throw "Expected exactly one '$Name' property" }
    return [regex]::Replace($Text, $taskPattern, ($Name + '=' + $Value))
}

function Rebind-Catalog {
    param([string]$Source, [string]$Name, [string]$ExpectedBaseHash)
    $taskText = Get-Content -Raw -Encoding UTF8 -LiteralPath $Source
    $taskSourceHash = Get-PropertyValue $taskText 'output.sha256'
    if (-not $taskSourceHash.Equals($ExpectedBaseHash, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Catalog is not bound to the pinned special-attack base: $Source ($taskSourceHash)"
    }
    $taskText = Set-PropertyValue $taskText 'output.sha256' $taskCandidateHash
    $taskPath = Join-Path $taskBuild $Name
    [IO.File]::WriteAllText($taskPath, $taskText, [Text.UTF8Encoding]::new($false))
    return $taskPath
}

if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid BuildTag' }
$taskInput = (Resolve-Path -LiteralPath $InputJar).Path
$taskInputHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskInput).Hash
if (-not $taskInputHash.Equals($taskExpectedInputHash, [StringComparison]::OrdinalIgnoreCase)) {
    throw "InputJar must be the pinned Hung_By_Son candidate ($taskExpectedInputHash); got $taskInputHash"
}

$taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
$taskAsm = Join-Path $taskDeps 'asm-9.7.jar'
$taskAsmTree = Join-Path $taskDeps 'asm-tree-9.7.jar'
$taskMicro = Join-Path $taskDeps 'microemulator.jar'
foreach ($taskDependency in @($taskAsm, $taskAsmTree, $taskMicro)) {
    if (-not (Test-Path -LiteralPath $taskDependency -PathType Leaf)) { throw "Missing dependency: $taskDependency" }
}
$taskAsmCp = $taskAsm + ';' + $taskAsmTree

$taskRequiredFiles = @(
    'tools/luna/HungBySonFarmQuietPatcher.java',
    'tools/luna/HungBySonExpInfoPack.java',
    'tools/legacy/hsosky/HungBySonFullOfflinePatcher.java',
    'fixtures/hung-by-son/HungBySonSpecialAttackBinaryTest.java',
    'fixtures/hung-by-son/HungBySonFarmSpecialAttackBinaryTest.java',
    'fixtures/luna/FarmSpecialAttackHostTest.java',
    (Join-Path $taskBaseBuild 'battlefield.properties'),
    (Join-Path $taskBaseBuild 'auto-attack.properties'),
    (Join-Path $taskBaseBuild 'exp.properties'),
    (Join-Path $taskBaseBuild 'recovery.properties'),
    (Join-Path $taskBaseBuild 'timing.properties'),
    (Join-Path $taskBaseBuild 'noclip.properties')
)
foreach ($taskFile in $taskRequiredFiles) {
    if (-not (Test-Path -LiteralPath $taskFile -PathType Leaf)) { throw "Required source, fixture, or base catalog is missing: $taskFile" }
}

$taskBuild = Join-Path $taskWorkspace ('build/' + $BuildTag)
if (Test-Path -LiteralPath $taskBuild) { throw 'BuildTag already exists; use a fresh unique tag' }
New-Item -ItemType Directory -Path $taskBuild, (Join-Path $taskBuild 'support'),
    (Join-Path $taskBuild 'tools'), (Join-Path $taskBuild 'host') | Out-Null
$taskSupport = Join-Path $taskBuild 'support'
$taskTools = Join-Path $taskBuild 'tools'
$taskHost = Join-Path $taskBuild 'host'
$taskCandidate = Join-Path $taskBuild 'Hung_By_Son.jar'
$taskFarmSpecialCatalog = Join-Path $taskBuild 'farm-special-attack.properties'
$taskPatchReport = Join-Path $taskBuild 'patch-report.txt'

Run-Checked javac @('-encoding', 'UTF-8', '-cp', $taskAsmCp, '-d', $taskTools,
    'tools/luna/HungBySonFarmQuietPatcher.java',
    'tools/luna/HungBySonExpInfoPack.java',
    'tools/legacy/hsosky/HungBySonFullOfflinePatcher.java') 'tools-compile.txt'

$taskSources = @(& rg --files src -g '*.java')
if ($taskSources.Count -eq 0) { throw 'No Luna support sources found under src/' }
Run-Checked javac (@('-encoding', 'UTF-8', '--release', '7', '-cp', $taskMicro, '-d', $taskSupport) + $taskSources) 'support-compile.txt'

Run-Checked java @('-Xverify:all', '-cp', "$taskTools;$taskAsmCp", 'HungBySonFarmQuietPatcher',
    $taskInput, $taskSupport, $taskCandidate, $taskFarmSpecialCatalog, $taskPatchReport) 'patch.txt'
if (-not (Test-Path -LiteralPath $taskCandidate -PathType Leaf) -or
    -not (Test-Path -LiteralPath $taskFarmSpecialCatalog -PathType Leaf) -or
    -not (Test-Path -LiteralPath $taskPatchReport -PathType Leaf)) {
    throw 'Farm Special Attack patcher did not create its candidate, catalog, and report'
}
$taskCandidateHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskCandidate).Hash
$taskFarmSpecialText = Get-Content -Raw -Encoding UTF8 -LiteralPath $taskFarmSpecialCatalog
if (-not (Get-PropertyValue $taskFarmSpecialText 'output.sha256').Equals($taskCandidateHash, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Farm Special Attack catalog output.sha256 does not match the staged candidate'
}
if ((Get-PropertyValue $taskFarmSpecialText 'special.menu.action') -ne '-116') {
    throw 'Farm Special Attack catalog must retain native special menu action -116'
}
if (-not (Get-PropertyValue $taskFarmSpecialText 'input.sha256').Equals($taskInputHash, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Farm Special Attack catalog input.sha256 does not match the pinned candidate input'
}

# The baseline catalogs describe behaviors already present in the input JAR. Bind their
# output hashes to this newly staged candidate so each binary regression verifies these bytes.
$taskBattlefield = Rebind-Catalog (Join-Path $taskBaseBuild 'battlefield.properties') 'battlefield.properties' $taskInputHash
$taskAuto = Rebind-Catalog (Join-Path $taskBaseBuild 'auto-attack.properties') 'auto-attack.properties' $taskInputHash
$taskAutoText = [IO.File]::ReadAllText($taskAuto)
if ((Get-PropertyValue $taskAutoText 'special.menu.action') -ne '-116') {
    throw 'Rebound Auto Attack catalog must retain native special menu action -116'
}
if (-not (Get-PropertyValue $taskAutoText 'input.sha256').Equals($taskExpectedAutoInputHash, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Auto Attack catalog must retain its pinned legacy input reference'
}
$taskExp = Rebind-Catalog (Join-Path $taskBaseBuild 'exp.properties') 'exp.properties' $taskInputHash
$taskRecovery = Rebind-Catalog (Join-Path $taskBaseBuild 'recovery.properties') 'recovery.properties' $taskInputHash
$taskTiming = Rebind-Catalog (Join-Path $taskBaseBuild 'timing.properties') 'timing.properties' $taskInputHash
$taskTimingReference = (Resolve-Path -LiteralPath '..\jar\backup\Hung_By_Son-before-hung-auto-attack-20261014-final-c.jar').Path
$taskTimingText = [IO.File]::ReadAllText($taskTiming)
$taskTimingText = Set-PropertyValue $taskTimingText 'input.sha256' (Get-FileHash -Algorithm SHA256 -LiteralPath $taskTimingReference).Hash
[IO.File]::WriteAllText($taskTiming, $taskTimingText, [Text.UTF8Encoding]::new($false))
$taskNoclip = Rebind-Catalog (Join-Path $taskBaseBuild 'noclip.properties') 'noclip.properties' $taskInputHash
$taskNoclipText = [IO.File]::ReadAllText($taskNoclip)
$taskNoclipText = Set-PropertyValue $taskNoclipText 'noclip.output.sha256' $taskCandidateHash
$taskNoclipText = Set-PropertyValue $taskNoclipText 'validation.inputsha' $taskInputHash
[IO.File]::WriteAllText($taskNoclip, $taskNoclipText, [Text.UTF8Encoding]::new($false))

$taskSemanticFixtures = @(& rg --files fixtures/luna -g '*.java') + @(
    'fixtures/hung-by-son/BattlefieldHostTest.java',
    'fixtures/hung-by-son/ExpEstimateHostTest.java',
    'fixtures/hung-by-son/ExpPercentInterpolatorHostTest.java',
    'fixtures/hung-by-son/ExpInfoHostTest.java'
)
Run-Checked javac (@('-encoding', 'UTF-8', '--release', '7', '-cp', "$taskSupport;$taskMicro", '-d', $taskHost) + $taskSemanticFixtures) 'semantic-fixtures-compile.txt'

foreach ($taskTest in @('LunaSemanticHostTest', 'LunaControllerHostTest', 'LunaRouteHostTest',
    'AutoCombatHostTest', 'LoginMaterialHostTest', 'AutoAttackHostTest', 'SpecialAttackHostTest', 'FarmSpecialAttackHostTest')) {
    Run-Checked java @('-Xverify:all', '-cp', "$taskSupport;$taskHost;$taskMicro", $taskTest) ($taskTest + '.txt')
}
foreach ($taskTest in @('BattlefieldHostTest', 'ExpEstimateHostTest', 'ExpPercentInterpolatorHostTest', 'ExpInfoHostTest')) {
    Run-Checked java @('-Xverify:all', '-cp', "$taskCandidate;$taskHost;$taskMicro", $taskTest) ($taskTest + '.txt')
}

$taskBinaryNames = @(
    'HungBySonAutoAttackBinaryTest', 'HungBySonAutoAttackInputBinaryTest', 'HungBySonAutoAttackPkCorpseHostTest',
    'HungBySonBattlefieldBinaryTest', 'HungBySonSpecialAttackBinaryTest', 'HungBySonFarmSpecialAttackBinaryTest',
    'HungBySonMonsterCacheHostTest', 'HungBySonStaleTargetHostTest', 'HungBySonMonsterSelectionHostTest',
    'HungBySonCrowdedMonsterHostTest', 'HungBySonCrowdedMonsterBinaryTest', 'HungBySonMonsterRecoveryBinaryTest',
    'HungBySonRemoteAnimationBarrierTest', 'HungBySonRealTimeCombatHostTest', 'HungBySonTempoClickHostTest',
    'HungBySonNoclipMenuHostTest', 'HungBySonNoclipCollisionHostTest', 'HungBySonFullOfflineHostTest',
    'HungBySonEnhancementWaitRegressionTest', 'HungBySonEnhancementNpcSessionTest', 'TradeLocalTest',
    'HungBySonExpBinaryTest', 'HungBySonExpInfoBinaryTest', 'ExpInfoModalGuardBinaryTest'
)
$taskBinarySources = @($taskBinaryNames | ForEach-Object { 'fixtures/hung-by-son/' + $_ + '.java' })
Run-Checked javac (@('-encoding', 'UTF-8', '-cp', "$taskCandidate;$taskTools;$taskAsmCp;$taskMicro", '-d', $taskHost) + $taskBinarySources) 'binary-fixtures-compile.txt'
$taskHostCp = "$taskHost;$taskTools;$taskAsmCp;$taskMicro"

$taskAutoReference = (Resolve-Path -LiteralPath '..\jar\backup\Hung_By_Son-before-hung-auto-attack-20261014-final-c.jar').Path
$taskDonor = (Resolve-Path -LiteralPath '..\jar\bachho_final.jar').Path
if (-not ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskAutoReference).Hash).Equals($taskExpectedAutoInputHash, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Pinned native Auto Attack regression source hash changed'
}
if (-not ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskDonor).Hash).Equals($taskExpectedDonorHash, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Pinned native Auto Attack donor hash changed'
}
Run-Checked java @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonAutoAttackBinaryTest',
    $taskAutoReference, $taskCandidate, $taskDonor, $taskAuto, $taskMicro, (Join-Path $taskBuild 'auto-attack-binary')) 'native-auto-attack.txt'
Run-Checked java @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonSpecialAttackBinaryTest',
    $taskCandidate, $taskAuto, $taskFarmSpecialCatalog) 'special-attack-binary.txt'
Run-Checked java @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonFarmSpecialAttackBinaryTest',
    $taskCandidate, $taskAuto, $taskFarmSpecialCatalog) 'farm-special-attack-binary.txt'
Run-Checked java @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonBattlefieldBinaryTest',
    $taskCandidate, $taskAuto, $taskBattlefield) 'battlefield-native.txt'
Run-Checked java @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonAutoAttackPkCorpseHostTest',
    $taskCandidate, $taskAuto) 'pk-corpse.txt'
Run-Checked java @('-Xverify:all', '-cp', "$taskHost;$taskMicro", 'HungBySonAutoAttackInputBinaryTest',
    $taskCandidate, $taskAuto, $taskMicro, (Join-Path $taskBuild 'native-input')) 'native-input.txt'

foreach ($taskTest in @('HungBySonMonsterCacheHostTest', 'HungBySonStaleTargetHostTest',
    'HungBySonMonsterSelectionHostTest', 'HungBySonCrowdedMonsterHostTest')) {
    Run-Checked java @('-Xverify:all', '-cp', "$taskCandidate;$taskHost;$taskMicro", $taskTest, $taskCandidate) ($taskTest + '.txt')
}
foreach ($taskTest in @('HungBySonMonsterRecoveryBinaryTest', 'HungBySonCrowdedMonsterBinaryTest')) {
    Run-Checked java @('-Xverify:all', '-cp', $taskHostCp, $taskTest, $taskCandidate, $taskRecovery, $taskMicro) ($taskTest + '.txt')
}
Run-Checked java @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonRemoteAnimationBarrierTest',
    $taskCandidate, $taskRecovery, $taskInput, '--current-input') 'remote-animation.txt'
Run-Checked java @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonRealTimeCombatHostTest',
    $taskTimingReference, $taskCandidate, $taskTiming, 'symbols/hung-tempo-menu-fixed-20261004.properties',
    $taskMicro, '--auto-attack-regression', $taskAuto) 'native-timing.txt'
foreach ($taskTest in @('HungBySonTempoClickHostTest', 'HungBySonNoclipMenuHostTest')) {
    Run-Checked java @('-Xverify:all', '-cp', $taskHostCp, $taskTest, $taskCandidate, $taskNoclip) ($taskTest + '.txt')
}
Run-Checked java @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonNoclipCollisionHostTest',
    $taskInput, $taskCandidate, $taskNoclip) 'noclip-collision.txt'

Run-Checked java @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonFullOfflineHostTest',
    $taskCandidate, '268') 'offline.txt'

foreach ($taskTest in @('HungBySonEnhancementWaitRegressionTest', 'HungBySonEnhancementNpcSessionTest')) {
    Run-Checked java @('-Xverify:all', '-cp', $taskHostCp, $taskTest, $taskCandidate) ($taskTest + '.txt')
}
Run-Checked java @('-Xverify:all', '-cp', $taskHostCp, 'TradeLocalTest', $taskCandidate, $taskMicro) 'trade-local.txt'
foreach ($taskTest in @('HungBySonExpBinaryTest', 'HungBySonExpInfoBinaryTest', 'ExpInfoModalGuardBinaryTest')) {
    Run-Checked java @('-Xverify:all', '-cp', $taskHostCp, $taskTest, $taskCandidate, $taskExp,
        $taskMicro, (Join-Path $taskBuild $taskTest)) ($taskTest + '.txt')
}

& (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskCandidate -DependencyDir $taskDeps -ReportDir (Join-Path $taskBuild 'startup')
if (-not $?) { throw 'Emulator startup failed' }

[ordered]@{
    Verified = $true
    Published = $false
    Variant = 'x1'
    SpecialAttack = $true
    FarmSpecialAttack = $true
    Battlefield = $true
    LiveServerTested = $false
    Input = $taskInput
    InputSHA256 = $taskInputHash
    Candidate = $taskCandidate
    SHA256 = $taskCandidateHash
    DependencyDir = $taskDeps
    SpecialCatalog = $taskFarmSpecialCatalog
    FarmSpecialCatalog = $taskFarmSpecialCatalog
    AutoAttackCatalog = $taskAuto
    BattlefieldCatalog = $taskBattlefield
    AllClassesLinked = $true
    CacheNameExclusion = $true
    SessionOnly = $true
} | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 (Join-Path $taskBuild 'verified.json')

Write-Output "VERIFIED=$taskCandidate SHA256=$taskCandidateHash"
