[CmdletBinding()]
param(
    [string]$DependencyDir='',
    [string]$BuildTag=('hung-crowded-monsters-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
)
$ErrorActionPreference='Stop'

function Run-Checked {
    param([string]$Program,[string[]]$Arguments,[string]$Log)
    $taskPrevious=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        $taskMessages=& $Program @Arguments 2>&1
        $taskExit=$LASTEXITCODE
        $taskMessages | ForEach-Object { $_.ToString() } | Set-Content -Encoding UTF8 -LiteralPath $Log
    } finally { $ErrorActionPreference=$taskPrevious }
    if($taskExit -ne 0){Get-Content -LiteralPath $Log -Tail 16;throw "$Program failed: $taskExit"}
    $taskMessages | Select-Object -Last 1
}

if($BuildTag -notmatch '^[A-Za-z0-9_-]+$'){throw 'Invalid build tag'}
$taskWorkspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskRepositoryRoot=[IO.Path]::GetFullPath((Join-Path $taskWorkspace '../..'))
if([string]::IsNullOrWhiteSpace($DependencyDir)){$DependencyDir=Join-Path $taskRepositoryRoot '.codex-deps'}
Push-Location $taskWorkspace
try {
    $taskInput=(Resolve-Path -LiteralPath '..\jar\Hung_By_Son.jar').Path
    $taskInputHash='8B67184EE5AEBD1181931B80CD6F9EF0AC833637BE90B5EB5C5AA14289A55953'
    if((Get-FileHash -Algorithm SHA256 -LiteralPath $taskInput).Hash -ne $taskInputHash){throw 'Unexpected current Hung_By_Son input; expected the pinned real650 release'}

    $taskDeps=(Resolve-Path -LiteralPath $DependencyDir).Path
    $taskAsm=Join-Path $taskDeps 'asm-9.7.jar'
    $taskAsmTree=Join-Path $taskDeps 'asm-tree-9.7.jar'
    $taskMicro=Join-Path $taskDeps 'microemulator.jar'
    foreach($taskDependency in @(
        @{Path=$taskAsm;Hash='ADF46D5E34940BDF148ECDD26A9EE8EEA94496A72034FF7141066B3EEA5C4E9D'},
        @{Path=$taskAsmTree;Hash='62F4B3BC436045C1ACB5C3BA2D8EC556EC3369093D7F5D06C747EB04B56D52B1'},
        @{Path=$taskMicro;Hash='DBD5F3EB8365D3E839D6A203149E0E3776FC1A0585E16AC1FC23F76C9FCAE1C6'}
    )){
        if(-not (Test-Path -LiteralPath $taskDependency.Path -PathType Leaf)){throw "Missing dependency: $($taskDependency.Path)"}
        if((Get-FileHash -Algorithm SHA256 -LiteralPath $taskDependency.Path).Hash -ne $taskDependency.Hash){throw "Unexpected dependency bytes: $($taskDependency.Path)"}
    }
    $taskAsmCp=$taskAsm+';'+$taskAsmTree
    $taskFeatureCatalog='symbols/hung-real650-features-20261006.properties'
    if(-not (Test-Path -LiteralPath $taskFeatureCatalog -PathType Leaf)){throw 'Missing real650 feature catalog'}
    $taskCrowdedFixture='fixtures/hung-by-son/HungBySonCrowdedMonsterBinaryTest.java'
    if(-not (Test-Path -LiteralPath $taskCrowdedFixture -PathType Leaf)){throw 'Crowded-monster native binary fixture is not ready'}

    $taskBuild=Join-Path $taskWorkspace (Join-Path 'build' $BuildTag)
    if(Test-Path -LiteralPath $taskBuild){throw 'Build tag already exists'}
    $taskTools=Join-Path $taskBuild 'tools'
    $taskCompiled=Join-Path $taskBuild 'compiled'
    $taskSupport=Join-Path $taskBuild 'support-stage'
    $taskRuntimeSupport=Join-Path $taskBuild 'runtime-support'
    $taskHost=Join-Path $taskBuild 'host'
    $taskSemantic=Join-Path $taskBuild 'semantic'
    $taskSemanticHost=Join-Path $taskBuild 'semantic-host'
    New-Item -ItemType Directory -Path $taskTools,$taskCompiled,$taskSupport,$taskRuntimeSupport,$taskHost,$taskSemantic,$taskSemanticHost | Out-Null

    Run-Checked javac @('-encoding','UTF-8','--release','7','-cp',$taskAsmCp,'-d',$taskTools,
        'tools/luna/HungBySonMonsterSupportPatcher.java','tools/luna/HungBySonCombatHpBridgePatch.java','tools/luna/HungBySonParityJarVerifier.java') (Join-Path $taskBuild 'tools-compile.txt')
    Run-Checked javac @('-encoding','UTF-8','--release','7','-cp',$taskInput,'-d',$taskCompiled,
        'src/luna/api/MonsterCacheAccess.java','src/luna/features/MonsterCacheSupport.java','src/luna/features/MonsterTargetWatchSupport.java') (Join-Path $taskBuild 'support-compile.txt')

    $taskCompiledFeatures=Join-Path $taskCompiled 'luna/features'
    $taskStageFeatures=Join-Path $taskSupport 'luna/features'
    New-Item -ItemType Directory -Path $taskStageFeatures | Out-Null
    $taskSupportFiles=@(Get-ChildItem -LiteralPath $taskCompiledFeatures -File | Where-Object {
        $_.Name -match '^Monster(Cache|TargetWatch)Support(\$[^/]+)?\.class$'
    })
    if($taskSupportFiles.Count -eq 0){throw 'No compiled monster support classes were emitted'}
    foreach($taskClass in $taskSupportFiles){Copy-Item -LiteralPath $taskClass.FullName -Destination (Join-Path $taskStageFeatures $taskClass.Name)}

    $taskCandidate=Join-Path $taskBuild 'Hung_By_Son_crowded_fix.jar'
    $taskPatchReport=Join-Path $taskBuild 'support-patch-report.properties'
    Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskAsmCp",'HungBySonMonsterSupportPatcher',
        $taskInput,$taskSupport,$taskCandidate,$taskFeatureCatalog,$taskPatchReport) (Join-Path $taskBuild 'support-patch.txt')
    $taskCandidateHash=(Get-FileHash -Algorithm SHA256 -LiteralPath $taskCandidate).Hash

    # Native fixtures load their instrumented game classes themselves. Keeping
    # the complete game JAR on the parent classpath bypasses that instrumentation.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $taskZip=[IO.Compression.ZipFile]::OpenRead($taskCandidate)
    try {
        foreach($taskEntry in $taskZip.Entries){
            if(-not $taskEntry.FullName.StartsWith('luna/') -or -not $taskEntry.FullName.EndsWith('.class')){continue}
            if($taskEntry.FullName.StartsWith('luna/generated/') -and $taskEntry.FullName -ne 'luna/generated/InvestigateStorage.class'){continue}
            $taskDestination=[IO.Path]::GetFullPath((Join-Path $taskRuntimeSupport $taskEntry.FullName))
            if(-not $taskDestination.StartsWith($taskRuntimeSupport+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Runtime-support extraction escaped the build directory'}
            New-Item -ItemType Directory -Path (Split-Path -Parent $taskDestination) -Force | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($taskEntry,$taskDestination,$false)
        }
    } finally {$taskZip.Dispose()}

    $taskChanged=@(Get-Content -LiteralPath $taskPatchReport -Encoding UTF8 | Where-Object {$_ -match '^CHANGED_CLASS='} | ForEach-Object {$_.Substring('CHANGED_CLASS='.Length)})
    if($taskChanged.Count -eq 0){throw 'Patcher report did not enumerate the changed classes'}
    $taskAllowed=$taskChanged
    Run-Checked java (@('-Xverify:all','-cp',"$taskTools;$taskAsmCp",'HungBySonParityJarVerifier',$taskInput,$taskCandidate,$taskMicro,(Join-Path $taskBuild 'archive-boundary.txt'))+$taskAllowed) (Join-Path $taskBuild 'archive-boundary-log.txt')

    $taskValidation=Join-Path $taskBuild 'crowded-monsters-validation.properties'
    $taskText=[IO.File]::ReadAllText($taskFeatureCatalog)
    $taskText=[regex]::Replace($taskText,'(?m)^input\.sha256=[^\r\n]*',('input.sha256='+$taskInputHash))
    $taskText=[regex]::Replace($taskText,'(?m)^output\.sha256=[^\r\n]*',('output.sha256='+$taskCandidateHash))
    $taskChangedNames=@($taskChanged | ForEach-Object { $_.Substring(0,$_.Length-6) })
    $taskChangedCsv=[string]::Join(',', $taskChangedNames)
    $taskText=[regex]::Replace($taskText,'(?m)^changed\.classes=[^\r\n]*',('changed.classes='+$taskChangedCsv))
    $taskText=[regex]::Replace($taskText,'(?m)^added\.classes=[^\r\n]*','added.classes=')
    $taskText += "`r`nvalidation.scope=MonsterCacheSupport,MonsterTargetWatchSupport,HungMonsterCacheAccess`r`n"
    [IO.File]::WriteAllText($taskValidation,$taskText,[Text.UTF8Encoding]::new($false))

    $taskTiming=Join-Path $taskBuild 'real650-validation.properties'
    $taskOldTiming='build/hung-real650-x3-20261006-b/real650.properties'
    if(-not (Test-Path -LiteralPath $taskOldTiming -PathType Leaf)){throw 'Missing pinned real650 timing validation catalog'}
    $taskTimingText=[IO.File]::ReadAllText($taskOldTiming)
    $taskTimingText=[regex]::Replace($taskTimingText,'(?m)^input\.sha256=[^\r\n]*',('input.sha256='+$taskInputHash))
    $taskTimingText=[regex]::Replace($taskTimingText,'(?m)^output\.sha256=[^\r\n]*',('output.sha256='+$taskCandidateHash))
    [IO.File]::WriteAllText($taskTiming,$taskTimingText,[Text.UTF8Encoding]::new($false))

    $taskFixtureNames=@(
        'HungBySonMonsterCacheHostTest','HungBySonStaleTargetHostTest','HungBySonMonsterSelectionHostTest',
        'HungBySonInvestigateBinaryTest','HungBySonMonsterSelectionBinaryTest',
        'HungBySonInvestigateHudBinaryTest','HungBySonMonsterRecoveryBinaryTest',
        'HungBySonStaleTargetBinaryTest','HungBySonCrowdedMonsterBinaryTest','HungBySonCrowdedMonsterHostTest',
        'HungBySonRemoteAnimationBarrierTest','HungBySonRealTimeCombatHostTest'
    )
    $taskFixtureFiles=@($taskFixtureNames | ForEach-Object { "fixtures/hung-by-son/$_.java" })
    $taskClassPath="$taskCandidate;$taskAsm;$taskAsmTree;$taskMicro"
    Run-Checked javac (@('-encoding','UTF-8','-cp',$taskClassPath,'-d',$taskHost)+$taskFixtureFiles) (Join-Path $taskBuild 'fixtures-compile.txt')
    $taskHostCp="$taskHost;$taskRuntimeSupport;$taskTools;$taskAsm;$taskAsmTree;$taskMicro"
    foreach($taskTest in @('HungBySonMonsterCacheHostTest','HungBySonStaleTargetHostTest','HungBySonMonsterSelectionHostTest','HungBySonCrowdedMonsterHostTest')){
        Run-Checked java @('-Xverify:all','-cp',$taskHostCp,$taskTest,$taskCandidate) (Join-Path $taskBuild "$taskTest.txt")
    }
    foreach($taskTest in @('HungBySonMonsterSelectionBinaryTest','HungBySonInvestigateHudBinaryTest','HungBySonMonsterRecoveryBinaryTest','HungBySonStaleTargetBinaryTest','HungBySonCrowdedMonsterBinaryTest')){
        Run-Checked java @('-Xverify:all','-cp',$taskHostCp,$taskTest,$taskCandidate,$taskValidation,$taskMicro) (Join-Path $taskBuild "$taskTest.txt")
    }
    Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonRemoteAnimationBarrierTest',
        $taskCandidate,$taskValidation,$taskInput) (Join-Path $taskBuild 'remote-animation-barrier.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonRealTimeCombatHostTest',$taskInput,$taskCandidate,$taskTiming,
        'symbols/hung-tempo-menu-fixed-20261004.properties',$taskMicro,'--preserved-timing') (Join-Path $taskBuild 'real650-test.txt')

    Run-Checked javac (@('-encoding','UTF-8','--release','7','-d',$taskSemantic)+@(rg --files src -g '*.java')) (Join-Path $taskBuild 'semantic-compile.txt')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',$taskSemantic,'-d',$taskSemanticHost)+@(rg --files fixtures/luna -g '*.java')) (Join-Path $taskBuild 'semantic-host-compile.txt')
    foreach($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoCombatHostTest','LoginMaterialHostTest')){
        Run-Checked java @('-Xverify:all','-cp',"$taskSemantic;$taskSemanticHost",$taskTest) (Join-Path $taskBuild "$taskTest.txt")
    }

    & (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskCandidate -DependencyDir $taskDeps -ReportDir (Join-Path $taskBuild 'emulator-smoke') |
        Tee-Object -FilePath (Join-Path $taskBuild 'emulator-smoke.txt')
    if($LASTEXITCODE -ne 0){throw 'Isolated MicroEmulator startup failed'}

    [ordered]@{
        Verified=$true;Candidate=$taskCandidate;SHA256=$taskCandidateHash;InputSHA256=$taskInputHash
        ChangedClasses=$taskChanged;TimingMs=650;LiveServerTested=$false;X3Rebuilt=$false
        DependencyDir=$taskDeps;Fixture='HungBySonCrowdedMonsterBinaryTest'
        AdditionalFixture='HungBySonRemoteAnimationBarrierTest'
    } | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'verified.json')
    Write-Output "VERIFIED=$taskCandidate; SHA256=$taskCandidateHash"
} finally { Pop-Location }
