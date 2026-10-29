[CmdletBinding()]
param(
    [string]$InputJar = '..\jar\backup\Hung_By_Son-before-real650-x3-20261006.jar',
    [string]$DependencyDir = '',
    [string]$BuildTag = ('hung-investigate-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')),
    [switch]$KeepInheritedCombatTiming,
    [ValidateRange(600,32767)][int]$AutoGateMs = 600
)
$ErrorActionPreference = 'Stop'
function Run-Checked {
    param([string]$Program, [string[]]$Arguments, [string]$Log)
    $taskPrevious = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Program @Arguments 2>&1 | ForEach-Object { $_.ToString() } | Tee-Object -FilePath $Log
        $taskExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $taskPrevious }
    if ($taskExit -ne 0) { throw "$Program failed: $taskExit" }
}
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid BuildTag' }
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $taskWorkspace
try {
    $taskInput = (Resolve-Path -LiteralPath $InputJar).Path
    if ((Get-FileHash -LiteralPath $taskInput).Hash -ne '490AE902E7F4D3975663123283F4E93509CFA263E9E5E7D482DD078B500DCA1F') { throw 'Unexpected Hung 500ms input' }
    if ([string]::IsNullOrWhiteSpace($DependencyDir)) {
        $DependencyDir = if ($env:LUNA_DEPS) { $env:LUNA_DEPS } else { Join-Path $env:LOCALAPPDATA 'Temp/hso-server-list-deps' }
    }
    $taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
    $taskAsm = (Join-Path $taskDeps 'asm-9.7.jar') + ';' + (Join-Path $taskDeps 'asm-tree-9.7.jar')
    $taskMicro = Join-Path $taskDeps 'microemulator.jar'
    foreach ($taskDependency in @((Join-Path $taskDeps 'asm-9.7.jar'),(Join-Path $taskDeps 'asm-tree-9.7.jar'),$taskMicro)) {
        if (-not (Test-Path -LiteralPath $taskDependency -PathType Leaf)) { throw "Missing dependency $taskDependency" }
    }
    $taskBuild = Join-Path $taskWorkspace (Join-Path 'build' $BuildTag)
    if (Test-Path -LiteralPath $taskBuild) { throw 'BuildTag already used' }
    $taskTools = Join-Path $taskBuild 'tools'; $taskSupport = Join-Path $taskBuild 'support'; $taskHost = Join-Path $taskBuild 'host'
    $taskSemantic = Join-Path $taskBuild 'semantic'; $taskSemanticHost = Join-Path $taskBuild 'semantic-host'
    $taskSupportSrc = Join-Path $taskBuild 'support-src/luna/generated'
    New-Item -ItemType Directory -Path $taskTools,$taskSupport,$taskHost,$taskSemantic,$taskSemanticHost,$taskSupportSrc | Out-Null
    Copy-Item -LiteralPath 'tools/luna/InvestigateStorage.java.in' -Destination (Join-Path $taskSupportSrc 'InvestigateStorage.java')
    Run-Checked javac @('-encoding','UTF-8','--release','7','-cp',$taskAsm,'-d',$taskTools,
        'tools/luna/HungBySonInvestigatePatcher.java','tools/luna/HungBySonMonsterSelectionPatch.java',
        'tools/luna/HungBySonStaleTargetPatch.java','tools/luna/HungBySonCombatHpBridgePatch.java',
        'tools/luna/HungBySonNativeTimingPatcher.java',
        'tools/luna/HungBySonParityJarVerifier.java','tools/luna/HungBySonAutoGatePatcher.java',
        'tools/legacy/hsosky/HungBySonFullOfflinePatcher.java') (Join-Path $taskBuild 'tools-compile.txt')
    Run-Checked javac @('-encoding','UTF-8','--release','7','-cp',"$taskInput;$taskMicro",'-d',$taskSupport,
        'src/luna/api/MonsterCacheAccess.java','src/luna/api/MonsterCacheTemplateNames.java','src/luna/features/MonsterCacheSupport.java',
        'src/luna/api/MonsterSelectionAccess.java','src/luna/features/MonsterSelectionSupport.java',
        'src/luna/features/MonsterTargetWatchSupport.java',
        (Join-Path $taskSupportSrc 'InvestigateStorage.java')) (Join-Path $taskBuild 'support-compile.txt')
    $taskInitialName = if ($KeepInheritedCombatTiming) { 'Hung_By_Son_investigate.jar' } else { 'Hung_By_Son_investigate-inherited-timing.jar' }
    $taskOutput = Join-Path $taskBuild $taskInitialName; $taskCatalog = Join-Path $taskBuild 'investigate.properties'
    Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskAsm",'HungBySonInvestigatePatcher',$taskInput,
        $taskSupport,$taskOutput,$taskCatalog,(Join-Path $taskBuild 'patch-report.txt')) (Join-Path $taskBuild 'patch.txt')
    $taskFixtures = @('fixtures/hung-by-son/HungBySonMonsterCacheHostTest.java','fixtures/hung-by-son/HungBySonInvestigateBinaryTest.java',
        'fixtures/hung-by-son/HungBySonMonsterSelectionHostTest.java','fixtures/hung-by-son/HungBySonMonsterSelectionBinaryTest.java',
        'fixtures/hung-by-son/HungBySonInvestigateHudBinaryTest.java',
        'fixtures/hung-by-son/HungBySonMonsterRecoveryBinaryTest.java',
        'fixtures/hung-by-son/HungBySonStaleTargetHostTest.java','fixtures/hung-by-son/HungBySonStaleTargetBinaryTest.java',
        'fixtures/hung-by-son/HungBySonAutoGateHostTest.java','fixtures/hung-by-son/HungBySonNativeTimingHostTest.java',
        'fixtures/hung-by-son/HungBySonTempoClickHostTest.java',
        'fixtures/hung-by-son/HungBySonNoclipMenuHostTest.java','fixtures/hung-by-son/HungBySonNoclipCollisionHostTest.java',
        'fixtures/hung-by-son/HungBySonFullOfflineHostTest.java','fixtures/hung-by-son/HungBySonEnhancementWaitRegressionTest.java',
        'fixtures/hung-by-son/HungBySonEnhancementNpcSessionTest.java','fixtures/hung-by-son/TradeLocalTest.java')
    $taskCp = "$taskHost;$taskSupport;$taskTools;$taskAsm;$taskMicro"
    Run-Checked javac (@('-encoding','UTF-8','-cp',$taskCp,'-d',$taskHost) + $taskFixtures) (Join-Path $taskBuild 'fixtures-compile.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonMonsterCacheHostTest',$taskOutput) (Join-Path $taskBuild 'cache-host.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonMonsterSelectionHostTest',$taskOutput) (Join-Path $taskBuild 'selection-host.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonInvestigateBinaryTest',$taskInput,$taskOutput,$taskCatalog,$taskMicro) (Join-Path $taskBuild 'binary-rms-test.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonMonsterSelectionBinaryTest',$taskOutput,$taskCatalog,$taskMicro) (Join-Path $taskBuild 'selection-binary.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonInvestigateHudBinaryTest',$taskOutput,$taskCatalog,$taskMicro) (Join-Path $taskBuild 'hud-binary.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonMonsterRecoveryBinaryTest',$taskOutput,$taskCatalog,$taskMicro) (Join-Path $taskBuild 'recovery-binary.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonStaleTargetHostTest',$taskOutput) (Join-Path $taskBuild 'stale-host.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonStaleTargetBinaryTest',$taskOutput,$taskCatalog,$taskMicro) (Join-Path $taskBuild 'stale-binary.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonAutoGateHostTest',
        '../jar/backup/Hung_By_Son-before-auto500-20261005.jar',$taskOutput,
        'symbols/hung-auto500-20261005.properties',$taskMicro) (Join-Path $taskBuild 'combat-gate-test.txt')
    $taskOutputHash = (Get-FileHash -LiteralPath $taskOutput).Hash
    $taskNoclipCatalog = Join-Path $taskBuild 'noclip-validation.properties'
    $taskNoclipText = [IO.File]::ReadAllText('symbols/hung-noclip-20261004.properties')
    $taskNoclipText = [regex]::Replace($taskNoclipText,'(?m)^(noclip\.)?output\.sha256=[^\r\n]*',('${1}output.sha256=' + $taskOutputHash))
    [IO.File]::WriteAllText($taskNoclipCatalog,$taskNoclipText,[Text.UTF8Encoding]::new($false))
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonTempoClickHostTest',$taskOutput,$taskNoclipCatalog) (Join-Path $taskBuild 'tempo-clicks.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonNoclipMenuHostTest',$taskOutput,$taskNoclipCatalog) (Join-Path $taskBuild 'noclip-menu.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonNoclipCollisionHostTest',
        '../jar/backup/Hung_By_Son-before-noclip-menu-20261004.jar',$taskOutput,$taskNoclipCatalog) (Join-Path $taskBuild 'noclip-collision.txt')
    foreach ($taskTest in @('HungBySonFullOfflineHostTest','HungBySonEnhancementWaitRegressionTest','HungBySonEnhancementNpcSessionTest','TradeLocalTest')) {
        $taskArguments = @('-Xverify:all','-cp',$taskCp,$taskTest,$taskOutput)
        if ($taskTest -eq 'HungBySonFullOfflineHostTest') {
            $taskAdded = (Get-Content $taskCatalog | Where-Object { $_ -match '^added\.classes=' }).Substring('added.classes='.Length).Split(',').Count
            $taskArguments += [string](221 + $taskAdded)
        }
        if ($taskTest -eq 'TradeLocalTest') { $taskArguments += $taskMicro }
        Run-Checked java $taskArguments (Join-Path $taskBuild "$taskTest.txt")
    }
    $taskSemanticSources = @(rg --files src -g '*.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-d',$taskSemantic) + $taskSemanticSources) (Join-Path $taskBuild 'semantic-compile.txt')
    $taskLunaFixtures = @(rg --files fixtures/luna -g '*.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',$taskSemantic,'-d',$taskSemanticHost) + $taskLunaFixtures) (Join-Path $taskBuild 'semantic-host-compile.txt')
    foreach ($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoCombatHostTest','LoginMaterialHostTest')) {
        Run-Checked java @('-Xverify:all','-cp',"$taskSemantic;$taskSemanticHost",$taskTest) (Join-Path $taskBuild "$taskTest.txt")
    }
    if (-not $KeepInheritedCombatTiming) {
        # Validate the inherited feature build first, then restore only original native timing.
        $taskInheritedOutput = $taskOutput
        $taskNativeName = if ($AutoGateMs -eq 600) { 'Hung_By_Son_investigate.jar' } else { 'Hung_By_Son_investigate-native-timing.jar' }
        $taskOutput = Join-Path $taskBuild $taskNativeName
        $taskNativeReference = '../jar/backup/Hung_By_Son-before-cast300-20260930.jar'
        $taskTimingCatalog = Join-Path $taskBuild 'native-timing.properties'
        Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskAsm",'HungBySonNativeTimingPatcher',
            $taskInheritedOutput,$taskNativeReference,'symbols/hung-auto500-20261005.properties',
            $taskOutput,$taskTimingCatalog) (Join-Path $taskBuild 'native-timing-patch.txt')
        Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonNativeTimingHostTest',
            $taskNativeReference,$taskOutput,$taskTimingCatalog,$taskMicro) (Join-Path $taskBuild 'native-timing-test.txt')
        $taskAllowedTiming = (Get-Content $taskTimingCatalog | Where-Object { $_ -match '^changed\.classes=' }).Substring('changed.classes='.Length).Split(',')
        Run-Checked java (@('-Xverify:all','-cp',"$taskTools;$taskAsm",'HungBySonParityJarVerifier',
            $taskInheritedOutput,$taskOutput,$taskMicro,(Join-Path $taskBuild 'native-timing-boundary.txt')) + $taskAllowedTiming) (Join-Path $taskBuild 'native-timing-boundary-log.txt')
        if ($AutoGateMs -ne 600) {
            $taskNativeOutput = $taskOutput
            $taskOutput = Join-Path $taskBuild 'Hung_By_Son_investigate.jar'
            $taskGateCatalog = Join-Path $taskBuild 'auto-gate.properties'
            Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskAsm",'HungBySonAutoGatePatcher',
                $taskNativeOutput,$taskOutput,$taskTimingCatalog,$taskGateCatalog,
                (Get-FileHash -LiteralPath $taskNativeOutput).Hash,'600',[string]$AutoGateMs) (Join-Path $taskBuild 'auto-gate-patch.txt')
            Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonAutoGateHostTest',
                $taskNativeOutput,$taskOutput,$taskGateCatalog,$taskMicro,'600',[string]$AutoGateMs) (Join-Path $taskBuild 'auto-gate-final-test.txt')
            Run-Checked java (@('-Xverify:all','-cp',"$taskTools;$taskAsm",'HungBySonParityJarVerifier',
                $taskNativeOutput,$taskOutput,$taskMicro,(Join-Path $taskBuild 'auto-gate-boundary.txt')) + $taskAllowedTiming) (Join-Path $taskBuild 'auto-gate-boundary-log.txt')
        }
        $taskOutputHash = (Get-FileHash -LiteralPath $taskOutput).Hash
        $taskFinalCatalog = Join-Path $taskBuild 'final-investigate-validation.properties'
        $taskFinalText = [IO.File]::ReadAllText($taskCatalog)
        $taskFinalText = [regex]::Replace($taskFinalText,'(?m)^output\.sha256=[^\r\n]*',('output.sha256=' + $taskOutputHash))
        $taskFinalText = [regex]::Replace($taskFinalText,'(?m)^auto\.delay\.ms=[^\r\n]*',('auto.delay.ms=' + $AutoGateMs))
        $taskFinalText = [regex]::Replace($taskFinalText,'(?m)^request\.extra\.ms=[^\r\n]*','request.extra.ms=0')
        [IO.File]::WriteAllText($taskFinalCatalog,$taskFinalText,[Text.UTF8Encoding]::new($false))
        foreach ($taskTest in @('HungBySonMonsterCacheHostTest','HungBySonMonsterSelectionHostTest','HungBySonStaleTargetHostTest')) {
            Run-Checked java @('-Xverify:all','-cp',$taskCp,$taskTest,$taskOutput) (Join-Path $taskBuild "final-$taskTest.txt")
        }
        foreach ($taskTest in @('HungBySonMonsterSelectionBinaryTest','HungBySonInvestigateHudBinaryTest','HungBySonMonsterRecoveryBinaryTest','HungBySonStaleTargetBinaryTest')) {
            Run-Checked java @('-Xverify:all','-cp',$taskCp,$taskTest,$taskOutput,$taskFinalCatalog,$taskMicro) (Join-Path $taskBuild "final-$taskTest.txt")
        }
    }
    & (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskOutput -DependencyDir $taskDeps -ReportDir (Join-Path $taskBuild 'smoke') | Tee-Object -FilePath (Join-Path $taskBuild 'emulator-smoke.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Emulator startup failed' }
    [ordered]@{Verified=$true;Candidate=$taskOutput;SHA256=$taskOutputHash;InputSHA256=(Get-FileHash -LiteralPath $taskInput).Hash;LiveServerTested=$false} |
        ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $taskBuild 'verified.json')
    Write-Output "VERIFIED=$taskOutput; SHA256=$taskOutputHash"
} finally { Pop-Location }
