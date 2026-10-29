[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$AsmJar,
    [Parameter(Mandatory=$true)][string]$AsmTreeJar,
    [Parameter(Mandatory=$true)][string]$MicroEmulatorJar,
    [string]$BuildTag = ('entry-self-fix-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')),
    [string]$PriorBuild = 'build/chibi-entry-fix-20261028-verified',
    [switch]$UseStaged,
    [switch]$Publish
)
$ErrorActionPreference='Stop'
function Run-Checked {
    param([string]$Program,[string[]]$Arguments,[string]$Log)
    $taskPrevious=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        & $Program @Arguments 2>&1 | ForEach-Object {$_.ToString()} | Tee-Object -FilePath $Log
        $taskExit=$LASTEXITCODE
    } finally {$ErrorActionPreference=$taskPrevious}
    if($taskExit -ne 0){throw "$Program failed ($taskExit); see $Log"}
}
if($BuildTag -notmatch '^[A-Za-z0-9_-]+$'){throw 'Invalid build tag'}
$taskWorkspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $taskWorkspace
try {
    $taskAsm=(Resolve-Path -LiteralPath $AsmJar).Path+';'+(Resolve-Path -LiteralPath $AsmTreeJar).Path
    $taskMicro=(Resolve-Path -LiteralPath $MicroEmulatorJar).Path
    $taskPrior=(Resolve-Path -LiteralPath $PriorBuild).Path
    $taskPriorRecord=Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $taskPrior 'verified.json') | ConvertFrom-Json
    if(-not $taskPriorRecord.Verified -or -not $taskPriorRecord.Published){throw 'Prior publication record is incomplete'}
    $taskDonor=(Resolve-Path -LiteralPath '../jar/Hso.jar').Path
    $taskDonorHash=(Get-FileHash -LiteralPath $taskDonor).Hash
    if($taskDonorHash -ne 'C69F9C6E9FF3AB998D21C9D78919859E5364C4D28B628723DC74DA48EFDC68C6'){throw 'Hso donor changed'}
    $taskGolden=(Resolve-Path -LiteralPath 'inputs/luna_final-golden-baseline.jar').Path
    $taskGoldenHash=(Get-FileHash -LiteralPath $taskGolden).Hash
    if($taskGoldenHash -ne '06622CD81DC6790871A5863D465D4E352B29A94BB6FD0062C54F7CF9C6C47E0F'){throw 'Golden baseline changed'}
    $taskSnapshot=@{}
    Get-ChildItem -LiteralPath '../jar' -File -Filter '*.jar' | ForEach-Object {$taskSnapshot[$_.FullName]=(Get-FileHash -LiteralPath $_.FullName).Hash}
    $taskBuild=Join-Path $taskWorkspace (Join-Path 'build' $BuildTag)
    if((Test-Path -LiteralPath $taskBuild) -and -not $UseStaged){throw 'Use a fresh build tag'}
    if($UseStaged -and -not (Test-Path -LiteralPath $taskBuild)){throw 'No staged build'}
    if(Test-Path -LiteralPath (Join-Path $taskBuild 'verified.json')){throw 'This build has already completed'}
    $taskTools=Join-Path $taskBuild 'tools';$taskSemantic=Join-Path $taskBuild 'semantic';$taskHost=Join-Path $taskBuild 'host'
    New-Item -ItemType Directory -Force -Path $taskTools,$taskSemantic,$taskHost | Out-Null
    $taskProfiles=@(
        [PSCustomObject]@{Key='chibi';Release=(Resolve-Path -LiteralPath '../jar/chibi_final.jar').Path;Expected='47C2A7F3A874133C37301F193B2A4FAA20572A9429DEB43C7D6C3A5C92A06D95';File='chibi_final.jar';BackupDir='../jar/backup';BackupStem='chibi_final-before-self-init'},
        [PSCustomObject]@{Key='luna';Release=(Resolve-Path -LiteralPath '../jar/luna_final (1).jar').Path;Expected='F46209F1E1AE9E34729C65DB80B5AEDCD645C9C3B1E4A1CC5A2FD732E76BA06A';File='luna_final_1.jar';BackupDir='inputs/releases';BackupStem='luna_final-1-before-self-init'}
    )
    foreach($taskProfile in $taskProfiles){if($taskSnapshot[$taskProfile.Release] -ne $taskProfile.Expected){throw "Current release changed: $($taskProfile.Release)"}}
    $taskCp="$taskTools;$taskAsm;$taskMicro"
    Run-Checked javac @('-encoding','UTF-8','--release','7','-cp',$taskCp,'-d',$taskTools,
        'tools/luna/ChibiEntryCompatibilityPatcher.java','tools/luna/EntrySelfInitPatcher.java','tools/luna/ChibiEntryArchiveVerifier.java',
        'fixtures/binary/EntrySelfProtocolBinaryTest.java','fixtures/binary/ChibiEntryLoginBinaryTest.java','fixtures/binary/ChibiEntryProtocolBinaryTest.java',
        'tools/legacy/luna/VerifyAutoTradeSymbols.java','tools/legacy/luna/SenderOpcodeReader.java','tools/legacy/luna/TestAutoTradeRoute.java') (Join-Path $taskBuild 'tools-compile.txt')
    foreach($taskProfile in $taskProfiles){
        $taskJar=Join-Path $taskBuild $taskProfile.File
        $taskCatalog=Join-Path $taskBuild ($taskProfile.Key+'.properties')
        $taskPriorCatalog=Join-Path $taskPrior ($taskProfile.Key+'.properties')
        $taskOriginal=@($taskPriorRecord.Artifacts | Where-Object {$_.Input -eq $taskProfile.Release})
        if($taskOriginal.Count -ne 1){throw 'Original profile not found in prior record'}
        $taskOriginalJar=$taskOriginal[0].Backup
        if((Get-FileHash -LiteralPath $taskOriginalJar).Hash -ne $taskOriginal[0].InputSHA256){throw 'Original backup changed'}
        if($UseStaged){
            $taskText=[IO.File]::ReadAllText($taskCatalog)
            $taskStagedHash=(Get-FileHash -LiteralPath $taskJar).Hash
            if($taskText -notmatch ('(?m)^self\.input\.sha256='+$taskProfile.Expected+'\r?$') -or
                $taskText -notmatch ('(?m)^output\.sha256='+$taskStagedHash+'\r?$')){throw 'Staged hash/catalog mismatch'}
        }else{
            Run-Checked java @('-Xverify:all','-cp',$taskCp,'EntrySelfInitPatcher',$taskProfile.Release,$taskDonor,$taskJar,$taskPriorCatalog,$taskCatalog) (Join-Path $taskBuild ($taskProfile.Key+'-patch.txt'))
        }
        Run-Checked java @('-Xverify:all','-cp',$taskCp,'EntrySelfProtocolBinaryTest',$taskDonor,$taskProfile.Release,$taskJar,$taskCatalog) (Join-Path $taskBuild ($taskProfile.Key+'-self-protocol.txt'))
        Run-Checked java @('-Xverify:all','-cp',$taskCp,'ChibiEntryLoginBinaryTest',$taskDonor,$taskJar,$taskCatalog,$taskOriginalJar) (Join-Path $taskBuild ($taskProfile.Key+'-login.txt'))
        Run-Checked java @('-Xverify:all','-cp',$taskCp,'ChibiEntryProtocolBinaryTest',$taskDonor,$taskOriginalJar,$taskJar,$taskCatalog) (Join-Path $taskBuild ($taskProfile.Key+'-inventory.txt'))
        Run-Checked java @('-Xverify:all','-cp',$taskCp,'ChibiEntryArchiveVerifier',$taskOriginalJar,$taskJar,$taskCatalog,$taskMicro) (Join-Path $taskBuild ($taskProfile.Key+'-archive.txt'))
        Run-Checked java @('-Xverify:all','-cp',$taskCp,'VerifyAutoTradeSymbols',$taskJar,'symbols/autotrade-symbols.properties') (Join-Path $taskBuild ($taskProfile.Key+'-symbols.txt'))
        if($taskProfile.Key -eq 'chibi'){
            Run-Checked java @('-Xverify:all','-cp',$taskCp,'TestAutoTradeRoute',$taskJar,$taskMicro) (Join-Path $taskBuild 'chibi-legacy-route.txt')
        }
        & (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskJar -DependencyDir ([IO.Path]::GetDirectoryName($taskMicro)) -ReportDir (Join-Path $taskBuild ($taskProfile.Key+'-emulator')) |
            Tee-Object -FilePath (Join-Path $taskBuild ($taskProfile.Key+'-emulator.txt'))
    }
    $taskSources=@(rg --files src -g '*.java');$taskFixtures=@(rg --files fixtures/luna -g '*.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',$taskMicro,'-d',$taskSemantic)+$taskSources) (Join-Path $taskBuild 'semantic-compile.txt')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',"$taskSemantic;$taskMicro",'-d',$taskHost)+$taskFixtures) (Join-Path $taskBuild 'semantic-host-compile.txt')
    foreach($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoCombatHostTest','LoginMaterialHostTest')){
        Run-Checked java @('-Xverify:all','-cp',"$taskSemantic;$taskHost;$taskMicro",$taskTest) (Join-Path $taskBuild ($taskTest+'.txt'))
    }
    foreach($taskPath in $taskSnapshot.Keys){if((Get-FileHash -LiteralPath $taskPath).Hash -ne $taskSnapshot[$taskPath]){throw 'Release changed during verification'}}
    $taskRecords=@()
    foreach($taskProfile in $taskProfiles){
        $taskJar=Join-Path $taskBuild $taskProfile.File;$taskHash=(Get-FileHash -LiteralPath $taskJar).Hash;$taskBackup=$null
        if($Publish){
            New-Item -ItemType Directory -Force -Path $taskProfile.BackupDir | Out-Null
            $taskBackup=[IO.Path]::GetFullPath((Join-Path $taskWorkspace (Join-Path $taskProfile.BackupDir ($taskProfile.BackupStem+'-'+$BuildTag+'.jar'))))
            [IO.File]::Copy($taskProfile.Release,$taskBackup,$false)
            if((Get-FileHash -LiteralPath $taskBackup).Hash -ne $taskProfile.Expected){throw 'Backup mismatch'}
            if((Get-FileHash -LiteralPath $taskProfile.Release).Hash -ne $taskProfile.Expected){throw 'Preimage changed before publication'}
            $taskReady=Join-Path $taskBuild ($taskProfile.Key+'-publish-ready.jar');[IO.File]::Copy($taskJar,$taskReady,$false)
            [IO.File]::Replace($taskReady,$taskProfile.Release,(Join-Path $taskBuild ($taskProfile.Key+'-replaced-old.jar')))
            if((Get-FileHash -LiteralPath $taskProfile.Release).Hash -ne $taskHash){throw 'Published hash mismatch'}
        }
        $taskRecords+=[PSCustomObject]@{Release=$taskProfile.Release;PreviousSHA256=$taskProfile.Expected;SHA256=$taskHash;Candidate=$taskJar;Backup=$taskBackup}
    }
    foreach($taskPath in $taskSnapshot.Keys){if($Publish -and $taskProfiles.Release -contains $taskPath){continue};if((Get-FileHash -LiteralPath $taskPath).Hash -ne $taskSnapshot[$taskPath]){throw 'Other endpoint changed'}}
    if((Get-FileHash -LiteralPath $taskGolden).Hash -ne $taskGoldenHash){throw 'Golden changed'}
    [ordered]@{Verified=$true;Published=[bool]$Publish;Artifacts=$taskRecords;DonorSHA256=$taskDonorHash;PriorBuild=$taskPrior;
        SelfOpcode=3;CapacityWire='signed-short';Pages='max(1,(capacity+41)/42)';OriginalFailure='Native capacity/pet/tail slice fails before timestamp';
        NativeSliceCases=16;OtherMethodsUnchanged=$true;ResourcesUnchanged=$true;RMSUnchanged=$true;OtherReleasesUnchanged=$true;GoldenUnchanged=$true;
        Emulator='12 seconds isolated headless startup per artifact';LiveServerTested=$false
    } | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'verified.json')
    Write-Output "ENTRY_SELF_RELEASE=verified; published=$([bool]$Publish)"
}finally{Pop-Location}
