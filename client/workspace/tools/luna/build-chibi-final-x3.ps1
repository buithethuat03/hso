[CmdletBinding()]
param(
    [string]$DependencyDir=$env:LUNA_DEPS,
    [string]$BuildTag=('chibi-final-x3-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')),
    [switch]$UseStaged,
    [switch]$Publish
)
$ErrorActionPreference='Stop'
function Run-Checked {
    param([string]$Program,[string[]]$Arguments,[string]$Log)
    $taskPrevious=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        $taskLines=@(& $Program @Arguments 2>&1 | ForEach-Object {$_.ToString()})
        $taskExit=$LASTEXITCODE
        $taskLines | Set-Content -Encoding UTF8 -LiteralPath $Log
    }finally{$ErrorActionPreference=$taskPrevious}
    if($taskExit -ne 0){Get-Content -LiteralPath $Log -Tail 16;throw "$Program failed ($taskExit); see $Log"}
    $taskLines | Select-Object -Last 1
}
if($BuildTag -notmatch '^[A-Za-z0-9_-]+$'){throw 'Invalid build tag'}
if([string]::IsNullOrWhiteSpace($DependencyDir)){throw 'Pass -DependencyDir containing ASM 9.7, ASM Tree 9.7 and MicroEmulator'}
$taskWorkspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $taskWorkspace
try {
    $taskDeps=(Resolve-Path -LiteralPath $DependencyDir).Path
    foreach($taskName in @('asm-9.7.jar','asm-tree-9.7.jar','microemulator.jar')){if(-not(Test-Path -LiteralPath (Join-Path $taskDeps $taskName))){throw "Missing $taskName"}}
    $taskAsm=(Join-Path $taskDeps 'asm-9.7.jar')+';'+(Join-Path $taskDeps 'asm-tree-9.7.jar')
    $taskMicro=Join-Path $taskDeps 'microemulator.jar'
    $taskSource=(Resolve-Path -LiteralPath '../jar/chibi_final.jar').Path
    $taskSourceHash=(Get-FileHash -LiteralPath $taskSource).Hash
    if($taskSourceHash -ne 'A78EFD2FFA52B179A0EF77CABFDB6C5294E91C0BB3BD87635F891EC44A1B1DEB'){throw 'Unexpected Chibi Final source'}
    $taskTemplate=(Resolve-Path -LiteralPath '../jar/backup/autotrade_v27_revive05_horse_x3.jar').Path
    $taskTemplateHash=(Get-FileHash -LiteralPath $taskTemplate).Hash
    if($taskTemplateHash -ne '5DC9E9821904617B8A8171F45530DA3E078E6492EC6E37580E160AE6AC1732DC'){throw 'Launcher template changed'}
    $taskGolden=(Resolve-Path -LiteralPath 'inputs/luna_final-golden-baseline.jar').Path
    $taskGoldenHash=(Get-FileHash -LiteralPath $taskGolden).Hash
    if($taskGoldenHash -ne '06622CD81DC6790871A5863D465D4E352B29A94BB6FD0062C54F7CF9C6C47E0F'){throw 'Golden baseline changed'}
    $taskSnapshot=@{}
    Get-ChildItem -LiteralPath '../jar' -Filter '*.jar' -File | ForEach-Object {$taskSnapshot[$_.FullName]=(Get-FileHash -LiteralPath $_.FullName).Hash}
    $taskBuild=Join-Path $taskWorkspace (Join-Path 'build' $BuildTag)
    if((Test-Path -LiteralPath $taskBuild) -and -not $UseStaged){throw 'Use a fresh build tag'}
    if($UseStaged -and -not(Test-Path -LiteralPath $taskBuild)){throw 'No staged build to verify'}
    if(Test-Path -LiteralPath (Join-Path $taskBuild 'verified.json')){throw 'This build has already completed'}
    $taskTools=Join-Path $taskBuild 'tools';$taskSupport=Join-Path $taskBuild 'support';$taskHost=Join-Path $taskBuild 'host'
    $taskSemantic=Join-Path $taskBuild 'semantic';$taskSemanticHost=Join-Path $taskBuild 'semantic-host'
    New-Item -ItemType Directory -Force -Path $taskTools,$taskSupport,$taskHost,$taskSemantic,$taskSemanticHost | Out-Null
    $taskJar=Join-Path $taskBuild 'chibi_final_x3.jar';$taskReport=Join-Path $taskBuild 'pack-report.txt'
    Run-Checked javac @('-encoding','UTF-8','--release','7','-cp',$taskAsm,'-d',$taskTools,
        'tools/legacy/luna/RepackSupportX3.java','tools/legacy/luna/HsoChildX3Packer.java','tools/luna/ChibiFinalX3Packer.java',
        'tools/legacy/luna/VerifyAutoTradeSymbols.java','tools/legacy/luna/SenderOpcodeReader.java') (Join-Path $taskBuild 'tools-compile.txt')
    if($UseStaged){
        $taskReportText=[IO.File]::ReadAllText($taskReport);$taskHash=(Get-FileHash -LiteralPath $taskJar).Hash
        if($taskReportText -notmatch ('(?m)^output\.sha256='+$taskHash+'\r?$') -or
            $taskReportText -notmatch ('(?m)^source\.sha256='+$taskSourceHash+'\r?$') -or
            $taskReportText -notmatch ('(?m)^template\.sha256='+$taskTemplateHash+'\r?$')){throw 'Staged preimage/output/report mismatch'}
    }else{
        Copy-Item -LiteralPath 'tools/legacy/luna/x3/X3SlotSupport.java.in' -Destination (Join-Path $taskSupport 'X3SlotSupport.java')
        Run-Checked javac @('-encoding','UTF-8','--release','7','-d',$taskSupport,(Join-Path $taskSupport 'X3SlotSupport.java')) (Join-Path $taskBuild 'helper-compile.txt')
        Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskAsm",'ChibiFinalX3Packer',$taskSource,$taskTemplate,$taskJar,(Join-Path $taskSupport 'X3SlotSupport.class'),$taskReport) (Join-Path $taskBuild 'pack.txt')
    }
    $taskCp="$taskHost;$taskTools;$taskAsm;$taskMicro"
    Run-Checked javac @('-encoding','UTF-8','--release','7','-cp',$taskCp,'-d',$taskHost,'fixtures/x3/ChibiFinalX3HostTest.java','fixtures/x3/HsoChildX3LauncherTest.java','tools/legacy/luna/TestAutoTradeRoute.java') (Join-Path $taskBuild 'fixtures-compile.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'ChibiFinalX3HostTest',$taskJar,$taskSource,$taskTemplate) (Join-Path $taskBuild 'x3-host.txt')
    foreach($taskSlot in @('','b','c')){
        $taskInternal=if($taskSlot -eq ''){''}else{$taskSlot+'/'};$taskDotted=if($taskSlot -eq ''){''}else{$taskSlot+'.'}
        $taskLabel=if($taskSlot -eq ''){'root'}else{$taskSlot}
        Run-Checked java @('-Xverify:all','-cp',$taskCp,'VerifyAutoTradeSymbols',$taskJar,'symbols/autotrade-symbols.properties',$taskInternal) (Join-Path $taskBuild ('symbols-'+$taskLabel+'.txt'))
        Run-Checked java @('-Xverify:all','-cp',"$taskHost;$taskMicro",'TestAutoTradeRoute',$taskJar,$taskMicro,$taskDotted) (Join-Path $taskBuild ('route-'+$taskLabel+'.txt'))
    }
    $taskLauncherOut=Join-Path $taskBuild 'launcher.out.log';$taskLauncherErr=Join-Path $taskBuild 'launcher.err.log'
    $taskLauncherProof=$UseStaged -and (Test-Path -LiteralPath $taskLauncherOut) -and (Test-Path -LiteralPath $taskLauncherErr)
    if($taskLauncherProof){$taskLauncherProof=([IO.File]::ReadAllText($taskLauncherOut) -match 'PASS: one native launcher') -and ((Get-Item -LiteralPath $taskLauncherErr).Length -eq 0)}
    if(-not $taskLauncherProof){
        $taskRuntime=Join-Path $taskBuild 'runtime-home';New-Item -ItemType Directory -Force -Path $taskRuntime | Out-Null
        $taskPrevious=$ErrorActionPreference
        try{
            $ErrorActionPreference='Continue'
            & java '-Xverify:all' "-Duser.home=$taskRuntime" '-Djava.awt.headless=true' '-cp' "$taskHost;$taskMicro" HsoChildX3LauncherTest $taskJar (Join-Path $taskBuild 'screenshots') 'HSO CHIBI FINAL' 1> $taskLauncherOut 2> $taskLauncherErr
            $taskExit=$LASTEXITCODE
        }finally{$ErrorActionPreference=$taskPrevious}
        if($taskExit -ne 0){Get-Content -LiteralPath $taskLauncherOut,$taskLauncherErr -Tail 12;throw 'X3 launcher runtime failed'}
    }
    if((Get-Item -LiteralPath $taskLauncherErr).Length -ne 0 -or [IO.File]::ReadAllText($taskLauncherOut) -notmatch 'PASS: one native launcher'){throw 'Launcher proof missing or stderr not empty'}
    if(Select-String -LiteralPath $taskLauncherOut -Pattern 'Exception|VerifyError|LinkageError|NoClassDefFoundError|Unable|loi load|not found'){throw 'Launcher log contains runtime errors'}
    Write-Output 'CHIBI_FINAL_X3_LAUNCHER=PASS (three live clients; held-*; 1->2->3->1)'
    $taskSources=@(rg --files src -g '*.java');$taskFixtures=@(rg --files fixtures/luna -g '*.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',$taskMicro,'-d',$taskSemantic)+$taskSources) (Join-Path $taskBuild 'semantic-compile.txt')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',"$taskSemantic;$taskMicro",'-d',$taskSemanticHost)+$taskFixtures) (Join-Path $taskBuild 'semantic-host-compile.txt')
    foreach($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoCombatHostTest','LoginMaterialHostTest')){Run-Checked java @('-Xverify:all','-cp',"$taskSemantic;$taskSemanticHost;$taskMicro",$taskTest) (Join-Path $taskBuild ($taskTest+'.txt'))}
    foreach($taskPath in $taskSnapshot.Keys){if((Get-FileHash -LiteralPath $taskPath).Hash -ne $taskSnapshot[$taskPath]){throw "Existing release changed: $taskPath"}}
    if((Get-FileHash -LiteralPath $taskGolden).Hash -ne $taskGoldenHash -or (Get-FileHash -LiteralPath $taskTemplate).Hash -ne $taskTemplateHash){throw 'Golden/template changed'}
    $taskHash=(Get-FileHash -LiteralPath $taskJar).Hash
    $taskRelease=[IO.Path]::GetFullPath((Join-Path $taskWorkspace '../jar/chibi_final_x3.jar'));$taskBackup=$null
    if($Publish){
        if(Test-Path -LiteralPath $taskRelease){
            New-Item -ItemType Directory -Force -Path '../jar/backup' | Out-Null
            $taskBackup=[IO.Path]::GetFullPath((Join-Path $taskWorkspace ("../jar/backup/chibi_final_x3-before-"+$BuildTag+'.jar')))
            [IO.File]::Copy($taskRelease,$taskBackup,$false)
            if((Get-FileHash -LiteralPath $taskBackup).Hash -ne $taskSnapshot[$taskRelease]){throw 'Backup mismatch'}
        }
        $taskPending=Join-Path $taskBuild 'publish-pending.jar';[IO.File]::Copy($taskJar,$taskPending,$false)
        if(Test-Path -LiteralPath $taskRelease){[IO.File]::Replace($taskPending,$taskRelease,(Join-Path $taskBuild 'replaced-old.jar'))}else{[IO.File]::Move($taskPending,$taskRelease)}
        if((Get-FileHash -LiteralPath $taskRelease).Hash -ne $taskHash){throw 'Published hash mismatch'}
    }
    foreach($taskPath in $taskSnapshot.Keys){if($Publish -and $taskPath -eq $taskRelease){continue};if((Get-FileHash -LiteralPath $taskPath).Hash -ne $taskSnapshot[$taskPath]){throw 'Unrelated endpoint changed after publication'}}
    [ordered]@{Verified=$true;Published=[bool]$Publish;Candidate=$taskJar;Release=$taskRelease;Backup=$taskBackup;SHA256=$taskHash;
        Source=$taskSource;SourceSHA256=$taskSourceHash;Template=$taskTemplate;TemplateSHA256=$taskTemplateHash;
        Suite='HSO CHIBI FINAL X3';Vendor='HSO_CHIBI_FINAL_X3';Classes=676;Slots=@('default','b/','c/');RMSPrefixes=@('a','b','c');
        FullSourceCopies=3;SourceResourcesUnchanged=$true;WrapperClassesUnchanged=19;
        Emulator='Three live virtual clients; held-*; 1->2->3->1 preserves instances and loops';LiveServerTested=$false;
        SourceAndExistingReleasesUnchanged=$true;GoldenUnchanged=$true
    } | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'verified.json')
    Write-Output "CHIBI_FINAL_X3_RELEASE=verified; published=$([bool]$Publish); SHA256=$taskHash"
}finally{Pop-Location}
