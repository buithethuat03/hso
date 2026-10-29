[CmdletBinding()]
param(
    [string]$InputJar='..\jar\Hung_By_Son.jar',
    [string]$DependencyDir='..\..\.codex-deps',
    [string]$BuildTag=('hung-battlefield-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
)
$ErrorActionPreference='Stop'
$taskWorkspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskWorkspace
if($BuildTag -notmatch '^[A-Za-z0-9_-]+$'){throw 'Invalid BuildTag'}
$taskInput=(Resolve-Path -LiteralPath $InputJar).Path
$taskDeps=(Resolve-Path -LiteralPath $DependencyDir).Path
$taskMicro=Join-Path $taskDeps 'microemulator.jar'
$taskAsmCp=(Join-Path $taskDeps 'asm-9.7.jar')+';'+(Join-Path $taskDeps 'asm-tree-9.7.jar')
$taskBuild=Join-Path $taskWorkspace ('build/'+$BuildTag)
if(Test-Path -LiteralPath $taskBuild){throw 'Fresh build directory required'}
New-Item -ItemType Directory -Path $taskBuild,(Join-Path $taskBuild 'support'),(Join-Path $taskBuild 'tools'),(Join-Path $taskBuild 'host') | Out-Null
$taskSupport=Join-Path $taskBuild 'support';$taskTools=Join-Path $taskBuild 'tools';$taskHost=Join-Path $taskBuild 'host'
$taskCandidate=Join-Path $taskBuild 'Hung_By_Son.jar'
function Run-Checked([string]$Program,[string[]]$Arguments,[string]$Log){
    $taskPrior=$ErrorActionPreference
    try{$ErrorActionPreference='Continue';$taskOutput=@(& $Program @Arguments 2>&1 | ForEach-Object {$_.ToString()});$taskExit=$LASTEXITCODE}
    finally{$ErrorActionPreference=$taskPrior}
    $taskOutput | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild $Log)
    if($taskExit -ne 0){$taskOutput | Select-Object -Last 16;throw "$Program failed: $Log"}
    $taskOutput | Select-Object -Last 1
}
Run-Checked javac @('-encoding','UTF-8','-cp',$taskAsmCp,'-d',$taskTools,'tools/luna/HungBySonExpInfoPack.java','tools/luna/HungBySonBattlefieldPatcher.java','tools/legacy/hsosky/HungBySonFullOfflinePatcher.java') 'tools-compile.txt'
$taskSources=@(& rg --files src -g '*.java')
Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',$taskMicro,'-d',$taskSupport)+$taskSources) 'support-compile.txt'
Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskAsmCp",'HungBySonBattlefieldPatcher',$taskInput,$taskSupport,$taskCandidate,(Join-Path $taskBuild 'battlefield.properties'),(Join-Path $taskBuild 'patch-report.txt')) 'patch.txt'
$taskHash=(Get-FileHash -LiteralPath $taskCandidate -Algorithm SHA256).Hash
function Rebind-Catalog([string]$Source,[string]$Name){
    $taskText=Get-Content -Raw -Encoding UTF8 -LiteralPath $Source
    $taskText=[regex]::Replace($taskText,'(?m)^output.sha256=.*$',('output.sha256='+$taskHash))
    $taskPath=Join-Path $taskBuild $Name
    [IO.File]::WriteAllText($taskPath,$taskText,[Text.UTF8Encoding]::new($false))
    return $taskPath
}
$taskRecovery=Rebind-Catalog 'symbols/hung-investigate-corpse-20261006.properties' 'recovery.properties'
$taskExp=Rebind-Catalog 'build/hung-exp-svg-20261019-b/info-symbols.properties' 'exp.properties'
$taskAuto=Rebind-Catalog 'build/hung-map48-inventory-resume100-20261014-d/auto-attack.properties' 'auto-attack.properties'
$taskTiming=Rebind-Catalog 'build/hung-map48-inventory-resume100-20261014-d/real650-validation.properties' 'timing.properties'
$taskTimingReference=(Resolve-Path -LiteralPath '../jar/backup/Hung_By_Son-before-hung-auto-attack-20261014-final-c.jar').Path
$taskTimingText=[IO.File]::ReadAllText($taskTiming)
$taskTimingText=[regex]::Replace($taskTimingText,'(?m)^input.sha256=.*$',('input.sha256='+(Get-FileHash $taskTimingReference).Hash))
[IO.File]::WriteAllText($taskTiming,$taskTimingText,[Text.UTF8Encoding]::new($false))
$taskNoclip=Rebind-Catalog 'build/hung-map48-inventory-resume100-20261014-d/noclip-validation.properties' 'noclip.properties'
$taskNoclipText=[IO.File]::ReadAllText($taskNoclip)
$taskNoclipText=[regex]::Replace($taskNoclipText,'(?m)^noclip.output.sha256=.*$',('noclip.output.sha256='+$taskHash))
$taskNoclipText=[regex]::Replace($taskNoclipText,'(?m)^validation.inputsha=.*$',('validation.inputsha='+(Get-FileHash $taskInput).Hash))
[IO.File]::WriteAllText($taskNoclip,$taskNoclipText,[Text.UTF8Encoding]::new($false))
$taskSemanticFixtures=@(& rg --files fixtures/luna -g '*.java')+@('fixtures/hung-by-son/BattlefieldHostTest.java','fixtures/hung-by-son/ExpEstimateHostTest.java','fixtures/hung-by-son/ExpPercentInterpolatorHostTest.java','fixtures/hung-by-son/ExpInfoHostTest.java')
Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',"$taskSupport;$taskMicro",'-d',$taskHost)+$taskSemanticFixtures) 'semantic-fixtures-compile.txt'
foreach($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoCombatHostTest','LoginMaterialHostTest','AutoAttackHostTest')){
    Run-Checked java @('-Xverify:all','-cp',"$taskSupport;$taskHost;$taskMicro",$taskTest) ($taskTest+'.txt')
}
foreach($taskTest in @('BattlefieldHostTest','ExpEstimateHostTest','ExpPercentInterpolatorHostTest','ExpInfoHostTest')){
    Run-Checked java @('-Xverify:all','-cp',"$taskCandidate;$taskHost;$taskMicro",$taskTest) ($taskTest+'.txt')
}
$taskBinaryNames=@('HungBySonAutoAttackBinaryTest','HungBySonAutoAttackInputBinaryTest','HungBySonAutoAttackPkCorpseHostTest','HungBySonBattlefieldBinaryTest',
    'HungBySonMonsterCacheHostTest','HungBySonStaleTargetHostTest','HungBySonMonsterSelectionHostTest',
    'HungBySonCrowdedMonsterHostTest','HungBySonCrowdedMonsterBinaryTest','HungBySonMonsterRecoveryBinaryTest',
    'HungBySonRemoteAnimationBarrierTest','HungBySonRealTimeCombatHostTest','HungBySonTempoClickHostTest',
    'HungBySonNoclipMenuHostTest','HungBySonNoclipCollisionHostTest','HungBySonFullOfflineHostTest',
    'HungBySonEnhancementWaitRegressionTest','HungBySonEnhancementNpcSessionTest','TradeLocalTest',
    'HungBySonExpBinaryTest','HungBySonExpInfoBinaryTest','ExpInfoModalGuardBinaryTest')
$taskBinarySources=@($taskBinaryNames | ForEach-Object {'fixtures/hung-by-son/'+$_+'.java'})
Run-Checked javac (@('-encoding','UTF-8','-cp',"$taskCandidate;$taskTools;$taskAsmCp;$taskMicro",'-d',$taskHost)+$taskBinarySources) 'binary-fixtures-compile.txt'
$taskHostCp="$taskHost;$taskTools;$taskAsmCp;$taskMicro"
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonBattlefieldBinaryTest',$taskCandidate,$taskAuto,(Join-Path $taskBuild 'battlefield.properties')) 'battlefield-native.txt'
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonAutoAttackPkCorpseHostTest',$taskCandidate,$taskAuto) 'pk-corpse.txt'
Run-Checked java @('-Xverify:all','-cp',"$taskHost;$taskMicro",'HungBySonAutoAttackInputBinaryTest',$taskCandidate,$taskAuto,$taskMicro,(Join-Path $taskBuild 'native-input')) 'native-input.txt'
foreach($taskTest in @('HungBySonMonsterCacheHostTest','HungBySonStaleTargetHostTest','HungBySonMonsterSelectionHostTest','HungBySonCrowdedMonsterHostTest')){
    Run-Checked java @('-Xverify:all','-cp',"$taskCandidate;$taskHost;$taskMicro",$taskTest,$taskCandidate) ($taskTest+'.txt')
}
foreach($taskTest in @('HungBySonMonsterRecoveryBinaryTest','HungBySonCrowdedMonsterBinaryTest')){
    Run-Checked java @('-Xverify:all','-cp',$taskHostCp,$taskTest,$taskCandidate,$taskRecovery,$taskMicro) ($taskTest+'.txt')
}
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonRemoteAnimationBarrierTest',$taskCandidate,$taskRecovery,$taskInput,'--current-input') 'remote-animation.txt'
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonRealTimeCombatHostTest',$taskTimingReference,$taskCandidate,$taskTiming,'symbols/hung-tempo-menu-fixed-20261004.properties',$taskMicro,'--auto-attack-regression',$taskAuto) 'native-timing.txt'
foreach($taskTest in @('HungBySonTempoClickHostTest','HungBySonNoclipMenuHostTest')){
    Run-Checked java @('-Xverify:all','-cp',$taskHostCp,$taskTest,$taskCandidate,$taskNoclip) ($taskTest+'.txt')
}
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonNoclipCollisionHostTest',$taskInput,$taskCandidate,$taskNoclip) 'noclip-collision.txt'
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonFullOfflineHostTest',$taskCandidate,'267') 'offline.txt'
foreach($taskTest in @('HungBySonEnhancementWaitRegressionTest','HungBySonEnhancementNpcSessionTest')){
    Run-Checked java @('-Xverify:all','-cp',$taskHostCp,$taskTest,$taskCandidate) ($taskTest+'.txt')
}
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'TradeLocalTest',$taskCandidate,$taskMicro) 'trade-local.txt'
foreach($taskTest in @('HungBySonExpBinaryTest','HungBySonExpInfoBinaryTest','ExpInfoModalGuardBinaryTest')){
    Run-Checked java @('-Xverify:all','-cp',$taskHostCp,$taskTest,$taskCandidate,$taskExp,$taskMicro,(Join-Path $taskBuild $taskTest)) ($taskTest+'.txt')
}
& (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskCandidate -DependencyDir $taskDeps -ReportDir (Join-Path $taskBuild 'startup')
if(-not $?){throw 'Emulator startup failed'}
[ordered]@{Verified=$true;Published=$false;Variant='x1';Battlefield=$true;LiveServerTested=$false;Input=$taskInput;
    InputSHA256=(Get-FileHash $taskInput).Hash;Candidate=$taskCandidate;SHA256=$taskHash;DependencyDir=$taskDeps;
    Catalog=(Join-Path $taskBuild 'battlefield.properties');NativeInverseVerified=$true;AllClassesLinked=$true;
    CacheNameExclusion=$true;SessionOnly=$true} | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 (Join-Path $taskBuild 'verified.json')
Write-Output "VERIFIED=$taskCandidate SHA256=$taskHash"
