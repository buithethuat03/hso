[CmdletBinding()]
param(
    [string]$InputJar='..\jar\Hung_By_Son_investigate.jar',
    [string]$DependencyDir='',
    [string]$BuildTag=('hung-real650-x3-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
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
    if($taskExit -ne 0){Get-Content -LiteralPath $Log -Tail 12;throw "$Program failed: $taskExit"}
    $taskMessages | Select-Object -Last 1
}
if($BuildTag -notmatch '^[A-Za-z0-9_-]+$'){throw 'Invalid build tag'}
$taskWorkspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $taskWorkspace
try {
    $taskInput=(Resolve-Path -LiteralPath $InputJar).Path
    $taskInputHash='0F92CFF49E6BE0E7A55FF8A48B9EE3460DBA451E2C4CD0F04D232FC62DDBEA8C'
    if((Get-FileHash -LiteralPath $taskInput).Hash -ne $taskInputHash){throw 'Unexpected latest corpse/HP source'}
    if([string]::IsNullOrWhiteSpace($DependencyDir)){$DependencyDir=if($env:LUNA_DEPS){$env:LUNA_DEPS}else{Join-Path $env:LOCALAPPDATA 'Temp/hso-server-list-deps'}}
    $taskDeps=(Resolve-Path -LiteralPath $DependencyDir).Path
    $taskAsm=(Join-Path $taskDeps 'asm-9.7.jar')+';'+(Join-Path $taskDeps 'asm-tree-9.7.jar')
    $taskMicro=Join-Path $taskDeps 'microemulator.jar'
    $taskBuild=Join-Path $taskWorkspace (Join-Path 'build' $BuildTag)
    if(Test-Path -LiteralPath $taskBuild){throw 'Build tag already exists'}
    $taskTools=Join-Path $taskBuild 'tools';$taskHost=Join-Path $taskBuild 'host';$taskSupport=Join-Path $taskBuild 'support';$taskSemantic=Join-Path $taskBuild 'semantic';$taskSemanticHost=Join-Path $taskBuild 'semantic-host'
    New-Item -ItemType Directory -Path $taskTools,$taskHost,$taskSupport,$taskSemantic,$taskSemanticHost | Out-Null
    Run-Checked javac @('-encoding','UTF-8','--release','7','-cp',$taskAsm,'-d',$taskTools,
        'tools/luna/HungBySonRealTimeCombatPatcher.java','tools/luna/HungBySonNativeTimingPatcher.java','tools/luna/HungBySonParityJarVerifier.java',
        'tools/luna/HungBySonX3Packer.java','tools/legacy/luna/HsoChildX3Packer.java','tools/legacy/luna/RepackSupportX3.java','tools/legacy/hsosky/HungBySonFullOfflinePatcher.java') (Join-Path $taskBuild 'tools-compile.txt')
    $taskSingle=Join-Path $taskBuild 'Hung_By_Son.jar';$taskTiming=Join-Path $taskBuild 'real650.properties'
    Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskAsm",'HungBySonRealTimeCombatPatcher',$taskInput,
        'symbols/hung-investigate-match-update-20261006.properties',$taskSingle,$taskTiming,$taskInputHash,'700','650') (Join-Path $taskBuild 'timing-patch.txt')
    $taskSingleHash=(Get-FileHash -LiteralPath $taskSingle).Hash
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $taskZip=[IO.Compression.ZipFile]::OpenRead($taskSingle)
    try {
        foreach($taskEntry in $taskZip.Entries){
            if(-not $taskEntry.FullName.StartsWith('luna/') -or -not $taskEntry.FullName.EndsWith('.class')){continue}
            if($taskEntry.FullName.StartsWith('luna/generated/') -and $taskEntry.FullName -ne 'luna/generated/InvestigateStorage.class'){continue}
            $taskDestination=Join-Path $taskSupport $taskEntry.FullName
            New-Item -ItemType Directory -Path (Split-Path -Parent $taskDestination) -Force | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($taskEntry,$taskDestination,$false)
        }
    } finally {$taskZip.Dispose()}
    $taskFixtures=@('HungBySonRealTimeCombatHostTest','HungBySonMonsterCacheHostTest','HungBySonInvestigateBinaryTest','HungBySonMonsterSelectionHostTest','HungBySonMonsterSelectionBinaryTest','HungBySonInvestigateHudBinaryTest','HungBySonMonsterRecoveryBinaryTest','HungBySonStaleTargetHostTest','HungBySonStaleTargetBinaryTest','HungBySonTempoClickHostTest','HungBySonNoclipMenuHostTest','HungBySonNoclipCollisionHostTest','HungBySonFullOfflineHostTest','HungBySonEnhancementWaitRegressionTest','HungBySonEnhancementNpcSessionTest','TradeLocalTest')
    $taskFiles=@($taskFixtures | ForEach-Object { "fixtures/hung-by-son/$_.java" })
    $taskCp="$taskHost;$taskSupport;$taskTools;$taskAsm;$taskMicro"
    Run-Checked javac (@('-encoding','UTF-8','-cp',"$taskCp;$taskSingle",'-d',$taskHost)+$taskFiles+@('fixtures/x3/HungBySonX3HostTest.java','fixtures/x3/HsoChildX3LauncherTest.java')) (Join-Path $taskBuild 'fixtures-compile.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonRealTimeCombatHostTest',$taskInput,$taskSingle,$taskTiming,'symbols/hung-tempo-menu-fixed-20261004.properties',$taskMicro) (Join-Path $taskBuild 'real650-test.txt')
    $taskAllowed=(Get-Content $taskTiming | Where-Object { $_ -match '^changed\.classes=' }).Substring('changed.classes='.Length).Split(',')
    Run-Checked java (@('-Xverify:all','-cp',"$taskTools;$taskAsm",'HungBySonParityJarVerifier',$taskInput,$taskSingle,$taskMicro,(Join-Path $taskBuild 'single-boundary.txt'))+$taskAllowed) (Join-Path $taskBuild 'single-boundary-log.txt')
    $taskValidation=Join-Path $taskBuild 'investigate-validation.properties'
    $taskText=[IO.File]::ReadAllText('symbols/hung-investigate-corpse-20261006.properties')
    $taskText=[regex]::Replace($taskText,'(?m)^output\.sha256=[^\r\n]*',('output.sha256='+$taskSingleHash))
    $taskText=[regex]::Replace($taskText,'(?m)^auto\.delay\.ms=[^\r\n]*','auto.delay.ms=650')
    [IO.File]::WriteAllText($taskValidation,$taskText,[Text.UTF8Encoding]::new($false))
    foreach($taskTest in @('HungBySonMonsterCacheHostTest','HungBySonMonsterSelectionHostTest','HungBySonStaleTargetHostTest')){Run-Checked java @('-Xverify:all','-cp',$taskCp,$taskTest,$taskSingle) (Join-Path $taskBuild "$taskTest.txt")}
    foreach($taskTest in @('HungBySonMonsterSelectionBinaryTest','HungBySonInvestigateHudBinaryTest','HungBySonMonsterRecoveryBinaryTest','HungBySonStaleTargetBinaryTest')){Run-Checked java @('-Xverify:all','-cp',$taskCp,$taskTest,$taskSingle,$taskValidation,$taskMicro) (Join-Path $taskBuild "$taskTest.txt")}
    $taskNoclip=Join-Path $taskBuild 'noclip-validation.properties';$taskText=[IO.File]::ReadAllText('symbols/hung-noclip-20261004.properties')
    $taskText=[regex]::Replace($taskText,'(?m)^(noclip\.)?output\.sha256=[^\r\n]*',('${1}output.sha256='+$taskSingleHash));[IO.File]::WriteAllText($taskNoclip,$taskText,[Text.UTF8Encoding]::new($false))
    foreach($taskTest in @('HungBySonTempoClickHostTest','HungBySonNoclipMenuHostTest')){Run-Checked java @('-Xverify:all','-cp',$taskCp,$taskTest,$taskSingle,$taskNoclip) (Join-Path $taskBuild "$taskTest.txt")}
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonNoclipCollisionHostTest','../jar/backup/Hung_By_Son-before-noclip-menu-20261004.jar',$taskSingle,$taskNoclip) (Join-Path $taskBuild 'collision.txt')
    foreach($taskTest in @('HungBySonFullOfflineHostTest','HungBySonEnhancementWaitRegressionTest','HungBySonEnhancementNpcSessionTest','TradeLocalTest')){
        $taskArgs=@('-Xverify:all','-cp',$taskCp,$taskTest,$taskSingle)
        if($taskTest -eq 'HungBySonFullOfflineHostTest'){$taskArgs+='242'}
        if($taskTest -eq 'TradeLocalTest'){$taskArgs+=$taskMicro}
        Run-Checked java $taskArgs (Join-Path $taskBuild "$taskTest.txt")
    }
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-d',$taskSemantic)+@(rg --files src -g '*.java')) (Join-Path $taskBuild 'semantic-compile.txt')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',$taskSemantic,'-d',$taskSemanticHost)+@(rg --files fixtures/luna -g '*.java')) (Join-Path $taskBuild 'semantic-host-compile.txt')
    foreach($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoCombatHostTest','LoginMaterialHostTest')){Run-Checked java @('-Xverify:all','-cp',"$taskSemantic;$taskSemanticHost",$taskTest) (Join-Path $taskBuild "$taskTest.txt")}
    & (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskSingle -DependencyDir $taskDeps -ReportDir (Join-Path $taskBuild 'single-smoke')
    Copy-Item -LiteralPath 'tools/legacy/luna/x3/X3SlotSupport.java.in' -Destination (Join-Path $taskSupport 'X3SlotSupport.java')
    Run-Checked javac @('-encoding','UTF-8','--release','7','-d',$taskSupport,(Join-Path $taskSupport 'X3SlotSupport.java')) (Join-Path $taskBuild 'x3-helper-compile.txt')
    $taskX3=Join-Path $taskBuild 'Hung_By_Son_x3.jar'
    Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskAsm",'HungBySonX3Packer',$taskSingle,'../jar/backup/autotrade_v27_revive05_horse_x3.jar',$taskX3,(Join-Path $taskSupport 'X3SlotSupport.class'),(Join-Path $taskBuild 'x3-pack-report.txt'),$taskSingleHash) (Join-Path $taskBuild 'x3-pack.txt')
    Run-Checked java @('-Xverify:all','-cp',"$taskHost;$taskTools;$taskAsm;$taskMicro",'HungBySonX3HostTest',$taskX3,$taskSingle,$taskTiming) (Join-Path $taskBuild 'x3-host.txt')
    $taskRuntime=Join-Path $taskBuild 'x3-runtime-home';New-Item -ItemType Directory -Path $taskRuntime | Out-Null
    $taskLauncherArgs=@('-Xverify:all',"-Duser.home=$taskRuntime",'-Djava.awt.headless=true','-cp',"$taskHost;$taskMicro",'HsoChildX3LauncherTest',$taskX3,(Join-Path $taskBuild 'screenshots'),'Hung By Son')
    $taskPrevious=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        & java @taskLauncherArgs 1> (Join-Path $taskBuild 'x3-launcher.out.log') 2> (Join-Path $taskBuild 'x3-launcher.err.log')
        $taskLauncherExit=$LASTEXITCODE
    } finally {$ErrorActionPreference=$taskPrevious}
    if($taskLauncherExit -ne 0 -or (Get-Item -LiteralPath (Join-Path $taskBuild 'x3-launcher.err.log')).Length -ne 0){throw 'Launcher failed or logged errors'}
    if(Select-String -LiteralPath (Join-Path $taskBuild 'x3-launcher.out.log') -Pattern 'Exception|Error|not found|loi load'){throw 'Launcher stdout contains runtime errors'}
    [ordered]@{Verified=$true;Single=$taskSingle;SingleSHA256=$taskSingleHash;X3=$taskX3;X3SHA256=(Get-FileHash -LiteralPath $taskX3).Hash;InputSHA256=$taskInputHash;MinimumMs=650;LiveServerTested=$false} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskBuild 'verified.json') -Encoding UTF8
    Write-Output "VERIFIED_SINGLE=$taskSingle; VERIFIED_X3=$taskX3"
} finally {Pop-Location}
