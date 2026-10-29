[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$SingleInputJar,
    [string]$SingleVerifiedJson = '',
    [Parameter(Mandatory=$true)][string]$ExpectedSingleSourceHash,
    [Parameter(Mandatory=$true)][string]$ExpectedSingleClassSetHash,
    [string]$SingleBattlefieldCatalog = '',
    [string]$SingleAutoCatalog = 'build\hung-dungeon-daily-limit-20261025-c\auto-attack.properties',
    [string]$SinglePortCatalog = 'build\hung-dungeon-daily-limit-20261025-c\maze.properties',
    [string]$TimingCatalog = 'build\hung-map48-inventory-resume100-20261014-d\real650-validation.properties',
    [string]$TemplateJar = '..\jar\backup\autotrade_v27_revive05_horse_x3.jar',
    [string]$X3ReleaseJar = '..\jar\Hung_By_Son_x3.jar',
    [string]$ExpectedX1ReleaseHash = 'F07535B4B0A7B71A896F9F5DBE9C9871C846B92827EA0273910CEC704EBBF600',
    [string]$ExpectedX3ReleaseHash = 'FC0565EBCABF58344FD6B5A4B00F36DBD066116E13CDE7158085F872D2956FD3',
    [string]$ExpectedTemplateHash = '5DC9E9821904617B8A8171F45530DA3E078E6492EC6E37580E160AE6AC1732DC',
    [int]$ExpectedSingleClassCount = 275,
    [int]$ExpectedX3ClassCount = 848,
    [string]$DependencyDir = '',
    [string]$BuildTag = ('hung-maze-x3-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')),
    [switch]$AllowUnverifiedSingleCandidate
)

# X3 of the maze-exclusive x1 (build-hung-maze-exclusive.ps1): lock pacing, stationary dungeon,
# exclusive Auto Mê cung and folded Phó chỉ huy dialogs in every slot, each slot with its own state.
$ErrorActionPreference = 'Stop'
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskWorkspace
$taskBuildRoot = [IO.Path]::GetFullPath((Join-Path $taskWorkspace 'build'))
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid BuildTag' }
if ($ExpectedSingleClassCount -ne 275) { throw 'Maze-exclusive source must contain 275 classes (lock x1 + MovementArbiter + dungeon/maze controllers)' }
$taskCalculatedX3Count = 3 * ($ExpectedSingleClassCount - 1) + 26
if ($ExpectedX3ClassCount -ne $taskCalculatedX3Count) {
    throw "ExpectedX3ClassCount must be $taskCalculatedX3Count for $ExpectedSingleClassCount source classes"
}
if ($ExpectedSingleSourceHash -notmatch '^[A-Fa-f0-9]{64}$' -or
    $ExpectedSingleClassSetHash -notmatch '^[A-Fa-f0-9]{64}$') {
    throw 'Set the verified Battlefield x1 SHA-256 and class-set SHA-256 pins before staging'
}

if ([string]::IsNullOrWhiteSpace($DependencyDir)) {
    $DependencyDir = if ($env:LUNA_DEPS) { $env:LUNA_DEPS } else { Join-Path $taskWorkspace '../../.codex-deps' }
}
$taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
$taskAsm = Join-Path $taskDeps 'asm-9.7.jar'
$taskAsmTree = Join-Path $taskDeps 'asm-tree-9.7.jar'
$taskMicro = Join-Path $taskDeps 'microemulator.jar'
foreach ($taskDependency in @($taskAsm,$taskAsmTree,$taskMicro)) {
    if (-not (Test-Path -LiteralPath $taskDependency -PathType Leaf)) { throw "Missing dependency: $taskDependency" }
}
$taskAsmCp = $taskAsm + ';' + $taskAsmTree

function Get-Hash([string]$Path) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToUpperInvariant()
}

function Set-PropertyLine([string]$Text,[string]$Name,[string]$Value) {
    $taskPattern = '(?m)^' + [regex]::Escape($Name) + '=[^\r\n]*'
    if ([regex]::IsMatch($Text,$taskPattern)) { return [regex]::Replace($Text,$taskPattern,($Name + '=' + $Value)) }
    if ($Text.Length -gt 0 -and -not $Text.EndsWith("`n")) { $Text += [Environment]::NewLine }
    return $Text + $Name + '=' + $Value + [Environment]::NewLine
}

function Get-ClassSet([string]$JarPath) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $taskArchive = [IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $taskNames = @($taskArchive.Entries | Where-Object { $_.FullName.EndsWith('.class',[StringComparison]::Ordinal) } |
            ForEach-Object { $_.FullName })
        [Array]::Sort($taskNames,[StringComparer]::Ordinal)
        $taskBytes = [Text.UTF8Encoding]::new($false).GetBytes(([string]::Join("`n",$taskNames) + "`n"))
        $taskSha = [Security.Cryptography.SHA256]::Create()
        try { $taskDigest = $taskSha.ComputeHash($taskBytes) }
        finally { $taskSha.Dispose() }
        $taskHex = ([BitConverter]::ToString($taskDigest)).Replace('-','')
        return [pscustomobject]@{ Count=$taskNames.Count; SHA256=$taskHex }
    } finally { $taskArchive.Dispose() }
}

function Get-JarSnapshot([string]$Directory) {
    $taskResult = [ordered]@{}
    foreach ($taskJar in (Get-ChildItem -LiteralPath $Directory -File -Filter '*.jar' | Sort-Object Name)) {
        $taskResult[$taskJar.Name] = Get-Hash $taskJar.FullName
    }
    return $taskResult
}

function Run-Checked([string]$Program,[string[]]$Arguments,[string]$LogPath) {
    $taskPrior = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Program @Arguments 1> ($LogPath + '.stdout') 2> ($LogPath + '.stderr')
        $taskExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $taskPrior }
    if ($taskExit -ne 0) {
        if (Test-Path -LiteralPath ($LogPath + '.stderr')) { Get-Content -LiteralPath ($LogPath + '.stderr') -Tail 30 }
        if (Test-Path -LiteralPath ($LogPath + '.stdout')) { Get-Content -LiteralPath ($LogPath + '.stdout') -Tail 30 }
        throw "$Program failed with exit code $taskExit; see $LogPath"
    }
    $taskCombined = @()
    if (Test-Path -LiteralPath ($LogPath + '.stdout')) { $taskCombined += Get-Content -LiteralPath ($LogPath + '.stdout') }
    if (Test-Path -LiteralPath ($LogPath + '.stderr')) { $taskCombined += Get-Content -LiteralPath ($LogPath + '.stderr') }
    $taskCombined | Set-Content -LiteralPath $LogPath -Encoding UTF8
}

$taskSingle = (Resolve-Path -LiteralPath $SingleInputJar).Path
$taskSingleStage = Split-Path -Parent $taskSingle
if (-not $SingleBattlefieldCatalog) {
    $taskSingleStem = [IO.Path]::GetFileNameWithoutExtension($taskSingle)
    if ($taskSingleStem -match '-([abc])$') { $taskBattlefieldCatalogName = 'battlefield-' + $Matches[1] + '.properties' }
    else { $taskBattlefieldCatalogName = 'battlefield.properties' }
    $SingleBattlefieldCatalog = Join-Path $taskSingleStage $taskBattlefieldCatalogName
}
$taskSingleBattlefieldCatalog = (Resolve-Path -LiteralPath $SingleBattlefieldCatalog).Path
$taskBattlefieldProperties = Get-Content -LiteralPath $taskSingleBattlefieldCatalog -Encoding UTF8 -Raw | ConvertFrom-StringData
$taskProofPath = ''
$taskProof = $null
if ($SingleVerifiedJson) {
    $taskProofPath = (Resolve-Path -LiteralPath $SingleVerifiedJson).Path
    $taskProof = Get-Content -LiteralPath $taskProofPath -Encoding UTF8 -Raw | ConvertFrom-Json
} elseif (-not $AllowUnverifiedSingleCandidate) {
    throw 'Supply SingleVerifiedJson, or explicitly allow a candidate-only staging pass'
}
$taskSingleCatalog = (Resolve-Path -LiteralPath $SingleAutoCatalog).Path
$taskTimingSource = (Resolve-Path -LiteralPath $TimingCatalog).Path
$taskTemplate = (Resolve-Path -LiteralPath $TemplateJar).Path
$taskX3Release = (Resolve-Path -LiteralPath $X3ReleaseJar).Path
$taskSingleHash = Get-Hash $taskSingle
$taskSingleVerified = $false
if ($taskProof) {
    if (-not $taskProof.Verified -or [string]$taskProof.SHA256 -cne $ExpectedSingleSourceHash -or
        $taskSingleHash -cne $ExpectedSingleSourceHash) {
        throw 'Single candidate is not the exact SHA-256 recorded by its verified.json'
    }
    if ($taskProof.Candidate -and (Resolve-Path -LiteralPath ([string]$taskProof.Candidate)).Path -cne $taskSingle) {
        throw 'Single candidate path differs from the candidate bound by verified.json'
    }
    $taskSingleVerified = $true
    $taskProofStage = Split-Path -Parent $taskProofPath
} else {
    if (-not $AllowUnverifiedSingleCandidate -or $taskSingleHash -cne $ExpectedSingleSourceHash) {
        throw 'Candidate-only staging requires an explicit switch and the exact pinned x1 SHA-256'
    }
    $taskProofStage = Split-Path -Parent $taskSingle
}
if (-not $taskProofStage.StartsWith($taskBuildRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
    -not $taskSingle.StartsWith($taskProofStage + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {
    throw 'x1 candidate and optional proof must remain together under workspace/build'
}
$taskSingleClassSet = Get-ClassSet $taskSingle
if ($taskSingleClassSet.Count -ne $ExpectedSingleClassCount -or
    $taskSingleClassSet.SHA256 -cne $ExpectedSingleClassSetHash.ToUpperInvariant()) {
    throw "Battlefield class set differs from the pinned count/hash ($($taskSingleClassSet.Count), $($taskSingleClassSet.SHA256))"
}
if ([string]$taskBattlefieldProperties['output.sha256'] -cne $taskSingleHash) {
    throw 'Battlefield symbol catalog is not bound to the verified single candidate'
}
$taskTemplateHash = Get-Hash $taskTemplate
if ($taskTemplateHash -cne $ExpectedTemplateHash.ToUpperInvariant()) { throw 'Launcher template hash differs from the pinned X3 template' }
if ((Get-Hash $taskX3Release) -cne $ExpectedX3ReleaseHash.ToUpperInvariant()) { throw 'Current X3 release preimage changed before staging' }
$taskJarRoot = (Resolve-Path -LiteralPath '..\jar').Path
$taskReleaseSnapshot = Get-JarSnapshot $taskJarRoot
$taskSingleRelease = (Resolve-Path -LiteralPath '..\jar\Hung_By_Son.jar').Path
if ((Get-Hash $taskSingleRelease) -cne $ExpectedX1ReleaseHash.ToUpperInvariant()) {
    throw 'Current published x1 release preimage changed before staging'
}
$taskGolden = (Resolve-Path -LiteralPath 'inputs\luna_final-golden-baseline.jar').Path
$taskGoldenHash = Get-Hash $taskGolden

$taskBuild = [IO.Path]::GetFullPath((Join-Path $taskBuildRoot $BuildTag))
if (-not $taskBuild.StartsWith($taskBuildRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {
    throw 'Build stage escaped workspace/build'
}
if (Test-Path -LiteralPath $taskBuild) { throw 'BuildTag already exists; use a fresh tag' }
$taskTools = Join-Path $taskBuild 'tools'
$taskSupport = Join-Path $taskBuild 'support'
$taskHost = Join-Path $taskBuild 'host'
$taskRuntime = Join-Path $taskBuild 'runtime-home'
$taskScreens = Join-Path $taskBuild 'screenshots'
New-Item -ItemType Directory -Path $taskBuild,$taskTools,$taskSupport,$taskHost,$taskRuntime,$taskScreens | Out-Null

$taskJavac = (Get-Command javac -ErrorAction Stop).Source
    $taskJava = (Get-Command java -ErrorAction Stop).Source
    Run-Checked $taskJavac @('-encoding','UTF-8','--release','7','-cp',$taskAsmCp,'-d',$taskTools,
        'tools/luna/HungBySonX3Packer.java','tools/legacy/luna/HsoChildX3Packer.java',
        'tools/legacy/luna/RepackSupportX3.java') (Join-Path $taskBuild 'tools-compile.txt')
    $taskSlotSupportSource = Join-Path $taskSupport 'X3SlotSupport.java'
    Copy-Item -LiteralPath 'tools/legacy/luna/x3/X3SlotSupport.java.in' -Destination $taskSlotSupportSource
    Run-Checked $taskJavac @('-encoding','UTF-8','--release','7','-d',$taskSupport,$taskSlotSupportSource) `
        (Join-Path $taskBuild 'slot-support-compile.txt')

    $taskCandidate = Join-Path $taskBuild 'Hung_By_Son_x3.jar'
    $taskPackerReport = Join-Path $taskBuild 'x3-pack-report.txt'
    $taskPackerProperties = $taskPackerReport + '.properties'
    Run-Checked $taskJava @('-Xverify:all','-cp',($taskTools+';'+$taskAsmCp),'HungBySonX3Packer',
        $taskSingle,$taskTemplate,$taskCandidate,(Join-Path $taskSupport 'X3SlotSupport.class'),
        $taskPackerReport,$taskSingleHash,'BATTLEFIELD',$ExpectedSingleClassSetHash.ToUpperInvariant()) `
        (Join-Path $taskBuild 'x3-pack.txt')

    $taskCandidateHash = Get-Hash $taskCandidate
    $taskPacker = Get-Content -LiteralPath $taskPackerProperties -Encoding UTF8 -Raw | ConvertFrom-StringData
    foreach ($taskExpected in @{
        'profile'='BATTLEFIELD'; 'source.sha256'=$taskSingleHash; 'template.sha256'=$taskTemplateHash;
        'output.sha256'=$taskCandidateHash; 'source.classset.sha256'=$ExpectedSingleClassSetHash.ToUpperInvariant();
        'source.classes'=[string]$ExpectedSingleClassCount; 'output.classes'=[string]$ExpectedX3ClassCount;
        'shared.exp.owner'='luna/generated/ExpEstimateSharedRmsStore';
        'shared.exp.store'='hbs_exp_levels_v2'; 'cross.slot.references'='shared-exp-only';
        'cross.slot.unapproved.references'='none'
    }.GetEnumerator()) {
        if ([string]$taskPacker[$taskExpected.Key] -cne [string]$taskExpected.Value) {
            throw "Packer report property $($taskExpected.Key) differs from the pinned Battlefield X3 contract"
        }
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $taskArchive = [IO.Compression.ZipFile]::OpenRead($taskCandidate)
    try {
        $taskClassEntries = @($taskArchive.Entries | Where-Object { $_.FullName.EndsWith('.class',[StringComparison]::Ordinal) })
        if ($taskClassEntries.Count -ne $ExpectedX3ClassCount) { throw 'Packed class count differs from the pinned Battlefield X3 count' }
        $taskClassReport = foreach ($taskEntry in ($taskClassEntries | Sort-Object FullName)) {
            $taskStream = $taskEntry.Open()
            try {
                $taskHeader = New-Object byte[] 8
                $taskRead = $taskStream.Read($taskHeader,0,$taskHeader.Length)
                if ($taskRead -ne 8 -or $taskHeader[0] -ne 0xCA -or $taskHeader[1] -ne 0xFE -or
                    $taskHeader[2] -ne 0xBA -or $taskHeader[3] -ne 0xBE) { throw "Invalid class bytes: $($taskEntry.FullName)" }
                $taskMajor = ([int]$taskHeader[6] -shl 8) -bor [int]$taskHeader[7]
                if ($taskMajor -gt 51) { throw "Class exceeds Java 7 maximum: $($taskEntry.FullName) ($taskMajor)" }
                [pscustomobject]@{ Name=$taskEntry.FullName; Major=$taskMajor }
            } finally { $taskStream.Dispose() }
        }
        $taskClassReport | ForEach-Object { '{0}={1}' -f $_.Name,$_.Major } |
            Set-Content -LiteralPath (Join-Path $taskBuild 'x3-class-version.txt') -Encoding UTF8
        "ENTRIES=$($taskArchive.Entries.Count)`nCLASSES=$($taskClassEntries.Count)`nMAX_MAJOR=$(($taskClassReport | Measure-Object Major -Maximum).Maximum)`nSOURCE_CLASSSET_SHA256=$ExpectedSingleClassSetHash`nPASS=packer archive roundtrip, resource/wrapper preservation, per-slot reference boundary, class versions <= 51" |
            Set-Content -LiteralPath (Join-Path $taskBuild 'archive-boundary.txt') -Encoding UTF8
    } finally { $taskArchive.Dispose() }

    $taskAutoText = Set-PropertyLine ([IO.File]::ReadAllText($taskSingleCatalog)) 'output.sha256' $taskSingleHash
    [IO.File]::WriteAllText((Join-Path $taskBuild 'single-auto-attack.properties'),$taskAutoText,[Text.UTF8Encoding]::new($false))
    $taskTimingText = [IO.File]::ReadAllText($taskTimingSource)
    $taskTimingText = Set-PropertyLine $taskTimingText 'input.sha256' $taskSingleHash
    $taskTimingText = Set-PropertyLine $taskTimingText 'output.sha256' $taskCandidateHash
    $taskTimingText = Set-PropertyLine $taskTimingText 'x3.class.count' ([string]$ExpectedX3ClassCount)
    $taskTimingText = Set-PropertyLine $taskTimingText 'auto.attack.delay.default.ms' '650'
    $taskTimingText = Set-PropertyLine $taskTimingText 'profile' 'BATTLEFIELD'
    $taskX3Timing = Join-Path $taskBuild 'x3-host-validation.properties'
    [IO.File]::WriteAllText($taskX3Timing,$taskTimingText,[Text.UTF8Encoding]::new($false))

    $taskFixtures = @('fixtures/x3/HungBySonX3HostTest.java','fixtures/x3/HsoChildX3LauncherTest.java',
        'fixtures/x3/HungBySonCrowdedCacheX3HostTest.java','fixtures/x3/HungBySonAutoAttackX3HostTest.java',
        'fixtures/x3/HungBySonBattlefieldX3HostTest.java','fixtures/x3/HungBySonFarmSpotX3HostTest.java',
        'fixtures/x3/HungBySonModUpLvX3HostTest.java')
    Run-Checked $taskJavac (@('-encoding','UTF-8','-cp',($taskCandidate+';'+$taskTools+';'+$taskAsmCp+';'+$taskMicro),'-d',$taskHost)+$taskFixtures) `
        (Join-Path $taskBuild 'fixtures-compile.txt')
    $taskHostCp = $taskHost+';'+$taskTools+';'+$taskAsmCp+';'+$taskMicro
    $taskRuntimeCp = $taskHost+';'+$taskMicro
    Run-Checked $taskJava @('-Xverify:all','-cp',$taskHostCp,'HungBySonX3HostTest',
        $taskCandidate,$taskSingle,$taskX3Timing) (Join-Path $taskBuild 'x3-host.txt')
    if (-not (Select-String -LiteralPath (Join-Path $taskBuild 'x3-host.txt') -Pattern "PASS .* $ExpectedX3ClassCount linked classes" -Quiet)) {
        throw 'HungBySonX3HostTest did not link every expected class'
    }
    $taskBattlefieldLog = Join-Path $taskBuild 'battlefield-all-slots.txt'
    Run-Checked $taskJava @('-Xverify:all','-cp',$taskHostCp,'HungBySonBattlefieldX3HostTest',
        $taskCandidate,$taskSingle,(Join-Path $taskBuild 'single-auto-attack.properties'),
        $taskSingleBattlefieldCatalog,$taskPackerReport) $taskBattlefieldLog
    if (-not (Select-String -LiteralPath $taskBattlefieldLog -Pattern '^BATTLEFIELD_X3_VERIFIED=true$' -Quiet) -or
        -not (Select-String -LiteralPath $taskBattlefieldLog -Pattern '^HungBySonBattlefieldX3HostTest PASS checks=[0-9]+' -Quiet)) {
        throw 'Battlefield X3 fixture did not report its all-slot pass'
    }
    $taskFarmSpotLog = Join-Path $taskBuild 'farm-spot-all-slots.txt'
    Run-Checked $taskJava @('-Xverify:all','-cp',$taskHostCp,'HungBySonFarmSpotX3HostTest',$taskCandidate,$taskMicro) $taskFarmSpotLog
    if (-not (Select-String -LiteralPath $taskFarmSpotLog -Pattern '^HungBySonFarmSpotX3HostTest PASS checks=[0-9]+' -Quiet)) {
        throw 'Farm spot X3 fixture did not report its all-slot pass'
    }
    $taskPortCatalog = Get-Content -LiteralPath (Resolve-Path -LiteralPath $SinglePortCatalog).Path -Encoding UTF8 -Raw | ConvertFrom-StringData
    if ([string]$taskPortCatalog['output.sha256'] -cne $taskSingleHash -or [string]$taskPortCatalog['modulv.port.owner'] -cne 'HungBySonModUpLv') {
        throw 'ModUpLv port catalog is not bound to the single candidate'
    }
    $taskPortLog = Join-Path $taskBuild 'moduplv-all-slots.txt'
    Run-Checked $taskJava @('-Xverify:all','-cp',$taskHostCp,'HungBySonModUpLvX3HostTest',$taskCandidate,$taskMicro,
        [string]$taskPortCatalog['cast.owner']) $taskPortLog
    if (-not (Select-String -LiteralPath $taskPortLog -Pattern '^HungBySonModUpLvX3HostTest PASS checks=[0-9]+' -Quiet)) {
        throw 'ModUpLv port X3 fixture did not report its all-slot pass'
    }
    Run-Checked $taskJava @('-Xverify:all','-cp',$taskHostCp,'HungBySonCrowdedCacheX3HostTest',$taskCandidate) `
        (Join-Path $taskBuild 'crowded-x3-host.txt')

    $taskLauncherScreens = Join-Path $taskScreens 'launcher'
    New-Item -ItemType Directory -Path $taskLauncherScreens | Out-Null
    $taskLauncherArgs = @('-Xverify:all',"-Duser.home=$taskRuntime",'-Djava.awt.headless=true','-cp',$taskRuntimeCp,
        'HsoChildX3LauncherTest',$taskCandidate,$taskLauncherScreens,'Hung By Son')
    Run-Checked $taskJava $taskLauncherArgs (Join-Path $taskBuild 'launcher.txt')
    if ((Get-Item -LiteralPath (Join-Path $taskBuild 'launcher.txt.stderr')).Length -ne 0) {
        throw 'MicroEmulator launcher wrote to stderr'
    }
    if (-not (Select-String -LiteralPath (Join-Path $taskBuild 'launcher.txt') -Pattern '1 -> 2 -> 3 -> 1' -Quiet)) {
        throw 'Launcher did not retain all three virtual clients through 1 -> 2 -> 3 -> 1'
    }

    $taskAutoAttackDir = Join-Path $taskScreens 'auto-attack-x3'
    New-Item -ItemType Directory -Path $taskAutoAttackDir | Out-Null
    Run-Checked $taskJava @('-Xverify:all','-cp',$taskRuntimeCp,'HungBySonAutoAttackX3HostTest',
        $taskCandidate,$taskSingle,(Join-Path $taskBuild 'single-auto-attack.properties'),
        $taskPackerReport,$taskMicro,$taskAutoAttackDir) (Join-Path $taskBuild 'auto-attack-x3-host.txt')

    if ((Get-Hash $taskGolden) -cne $taskGoldenHash) { throw 'Golden baseline changed during staging' }
    $taskReleaseAfter = Get-JarSnapshot $taskJarRoot
    if ((ConvertTo-Json $taskReleaseAfter -Compress) -cne (ConvertTo-Json $taskReleaseSnapshot -Compress)) {
        throw 'A release JAR changed during X3 staging'
    }
    [ordered]@{
        Verified=$taskSingleVerified; X3ChecksPassed=$true; Published=$false; LiveServerTested=$false; Profile='BATTLEFIELD';
        SingleInput=$taskSingle; SingleInputSHA256=$taskSingleHash; SingleVerified=$taskSingleVerified; SingleVerifiedJson=$taskProofPath;
        SingleClassCount=$ExpectedSingleClassCount; SingleClassSetSHA256=$ExpectedSingleClassSetHash.ToUpperInvariant();
        X3Candidate=$taskCandidate; X3CandidateSHA256=$taskCandidateHash; X3Classes=$ExpectedX3ClassCount;
        X1ReleasePreimage=$taskSingleRelease; X1ReleasePreimageSHA256=$ExpectedX1ReleaseHash.ToUpperInvariant();
        X3ReleasePreimage=$taskX3Release; X3ReleasePreimageSHA256=$ExpectedX3ReleaseHash.ToUpperInvariant();
        Template=$taskTemplate; TemplateSHA256=$taskTemplateHash; DependencyDir=$taskDeps;
        SharedExpBackendRootOnly=$true; AllX3ClassesLinked=$true; LauncherTransitionVerified=$true;
        BattlefieldAllSlotsVerified=$true; BattlefieldAllSlotsLog=$taskBattlefieldLog;
        FarmSpotAllSlotsVerified=$true; FarmSpotAllSlotsLog=$taskFarmSpotLog;
        ModUpLvAllSlotsVerified=$true; ModUpLvAllSlotsLog=$taskPortLog; LockPacingPerSlot=$true; FileLog='none';
        CrowdedCacheRegressionRun=$true; AutoAttackRmsAndVirtualInputRegressionRun=$true;
        ArchiveBoundary=(Join-Path $taskBuild 'archive-boundary.txt');
        PackerReport=$taskPackerReport; PackerProperties=$taskPackerProperties;
        X3TimingCatalog=$taskX3Timing; ReleaseJarSnapshotUnchanged=$true
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskBuild 'verified.json') -Encoding UTF8
Write-Output "VERIFIED_X3=$taskCandidate SHA256=$taskCandidateHash CLASSES=$ExpectedX3ClassCount STAGE_ONLY=true"
