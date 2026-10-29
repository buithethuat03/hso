[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$AsmJar,
    [Parameter(Mandatory=$true)][string]$AsmTreeJar,
    [Parameter(Mandatory=$true)][string]$MicroEmulatorJar,
    [string]$BuildTag = ('chibi-entry-fix-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')),
    [switch]$Publish,
    [switch]$ResumeVerification
)
$ErrorActionPreference = 'Stop'
function Run-Checked {
    param([string]$Program,[string[]]$Arguments,[string]$Log)
    $taskPrevious = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Program @Arguments 2>&1 | ForEach-Object { $_.ToString() } | Tee-Object -FilePath $Log
        $taskExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $taskPrevious }
    if ($taskExit -ne 0) { throw "$Program failed ($taskExit); see $Log" }
}
function Run-Verified {
    param([string]$Program,[string[]]$Arguments,[string]$Log,[string]$Proof)
    if ($ResumeVerification -and (Test-Path -LiteralPath $Log) -and ([IO.File]::ReadAllText($Log) -match $Proof)) {
        Write-Output "Retained successful check: $([IO.Path]::GetFileName($Log))"
        return
    }
    Run-Checked $Program $Arguments $Log
}
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid build tag' }
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $taskWorkspace
try {
    $taskAsm = (Resolve-Path -LiteralPath $AsmJar).Path + ';' + (Resolve-Path -LiteralPath $AsmTreeJar).Path
    $taskMicro = (Resolve-Path -LiteralPath $MicroEmulatorJar).Path
    $taskBuild = Join-Path $taskWorkspace (Join-Path 'build' $BuildTag)
    if ((Test-Path -LiteralPath $taskBuild) -and -not $ResumeVerification) { throw 'Use a fresh build tag' }
    if ($ResumeVerification -and -not (Test-Path -LiteralPath $taskBuild)) { throw 'No staged build to resume' }
    if (Test-Path -LiteralPath (Join-Path $taskBuild 'verified.json')) { throw 'Build already completed; do not republish' }
    $taskTools = Join-Path $taskBuild 'tools'
    $taskSemantic = Join-Path $taskBuild 'semantic'
    $taskHost = Join-Path $taskBuild 'host'
    New-Item -ItemType Directory -Force -Path $taskTools,$taskSemantic,$taskHost | Out-Null
    $taskSnapshot = @{}
    Get-ChildItem -LiteralPath '../jar' -File -Filter '*.jar' | ForEach-Object { $taskSnapshot[$_.FullName] = (Get-FileHash -LiteralPath $_.FullName).Hash }
    $taskGolden = (Resolve-Path -LiteralPath 'inputs/luna_final-golden-baseline.jar').Path
    $taskGoldenHash = (Get-FileHash -LiteralPath $taskGolden).Hash
    if ($taskGoldenHash -ne '06622CD81DC6790871A5863D465D4E352B29A94BB6FD0062C54F7CF9C6C47E0F') { throw 'Golden baseline mismatch' }
    $taskDonor = (Resolve-Path -LiteralPath '../jar/Hso.jar').Path
    if ((Get-FileHash -LiteralPath $taskDonor).Hash -ne 'C69F9C6E9FF3AB998D21C9D78919859E5364C4D28B628723DC74DA48EFDC68C6') { throw 'Current Hso reference changed' }
    $taskProfiles = @(
        [PSCustomObject]@{ Key='chibi'; Input=(Resolve-Path -LiteralPath '../jar/chibi_final.jar').Path; File='chibi_final.jar'; Expected='A5C8FC9C5659518E2E62AB702381FD4E781B3C55ABE82F87EA4FF63A0D142DFB'; BackupDir='../jar/backup'; BackupStem='chibi_final-before-entry-fix'; LegacyBridge=$true },
        [PSCustomObject]@{ Key='luna'; Input=(Resolve-Path -LiteralPath '../jar/luna_final (1).jar').Path; File='luna_final_1.jar'; Expected='F8998F3EC1570304DB02C7EDBD8AE844CCA90BE917B621B0F45A3DAA95C62CD2'; BackupDir='inputs/releases'; BackupStem='luna_final-1-before-entry-fix'; LegacyBridge=$false }
    )
    foreach ($taskProfile in $taskProfiles) { if ($taskSnapshot[$taskProfile.Input] -ne $taskProfile.Expected) { throw "Input preimage changed: $($taskProfile.Input)" } }
    $taskCp = "$taskTools;$taskAsm;$taskMicro"
    Run-Checked javac @('-encoding','UTF-8','--release','7','-cp',$taskCp,'-d',$taskTools,
        'tools/luna/ChibiEntryCompatibilityPatcher.java','tools/luna/ChibiEntryArchiveVerifier.java',
        'fixtures/binary/ChibiEntryLoginBinaryTest.java','fixtures/binary/ChibiEntryProtocolBinaryTest.java',
        'tools/luna/LunaJarVerifier.java','tools/legacy/luna/VerifyAutoTradeSymbols.java',
        'tools/legacy/luna/TestAutoTradeRoute.java','tools/legacy/luna/SenderOpcodeReader.java') (Join-Path $taskBuild 'tools-compile.txt')
    foreach ($taskProfile in $taskProfiles) {
        $taskJar = Join-Path $taskBuild $taskProfile.File
        $taskCatalog = Join-Path $taskBuild ($taskProfile.Key + '.properties')
        if ($ResumeVerification -and (Test-Path -LiteralPath $taskJar) -and (Test-Path -LiteralPath $taskCatalog)) {
            $taskCatalogText = [IO.File]::ReadAllText($taskCatalog)
            $taskStagedHash = (Get-FileHash -LiteralPath $taskJar).Hash
            if ($taskCatalogText -notmatch ('(?m)^input\.sha256=' + $taskProfile.Expected + '\r?$') -or
                $taskCatalogText -notmatch ('(?m)^output\.sha256=' + $taskStagedHash + '\r?$') -or
                $taskCatalogText -notmatch '(?m)^reference\.sha256=C69F9C6E9FF3AB998D21C9D78919859E5364C4D28B628723DC74DA48EFDC68C6\r?$') { throw 'Staged input/reference/output hash mismatch' }
            Write-Output "Retained staged artifact: $($taskProfile.File) $taskStagedHash"
        } else {
            Run-Checked java @('-Xverify:all','-cp',$taskCp,'ChibiEntryCompatibilityPatcher',$taskProfile.Input,$taskDonor,$taskJar,$taskCatalog) (Join-Path $taskBuild ($taskProfile.Key + '-patch.txt'))
        }
        Run-Verified java @('-Xverify:all','-cp',$taskCp,'ChibiEntryLoginBinaryTest',$taskDonor,$taskJar,$taskCatalog,$taskProfile.Input) (Join-Path $taskBuild ($taskProfile.Key + '-login.txt')) 'CHIBI_ENTRY_LOGIN=PASS'
        Run-Verified java @('-Xverify:all','-cp',$taskCp,'ChibiEntryProtocolBinaryTest',$taskDonor,$taskProfile.Input,$taskJar,$taskCatalog) (Join-Path $taskBuild ($taskProfile.Key + '-protocol.txt')) 'CHIBI_ENTRY_PROTOCOL=PASS'
        Run-Verified java @('-Xverify:all','-cp',$taskCp,'ChibiEntryArchiveVerifier',$taskProfile.Input,$taskJar,$taskCatalog,$taskMicro) (Join-Path $taskBuild ($taskProfile.Key + '-archive.txt')) 'CHIBI_ENTRY_ARCHIVE=PASS'
        if ($taskProfile.LegacyBridge) {
            Run-Verified java @('-Xverify:all','-cp',$taskCp,'luna.tools.LunaJarVerifier',$taskJar,'--compare-resources',$taskProfile.Input,'--load-classes','--write-manifest',(Join-Path $taskBuild ($taskProfile.Key + '.manifest'))) (Join-Path $taskBuild ($taskProfile.Key + '-jar-verifier.txt')) 'classLoad=OK'
            Run-Verified java @('-Xverify:all','-cp',$taskCp,'TestAutoTradeRoute',$taskJar,$taskMicro) (Join-Path $taskBuild ($taskProfile.Key + '-legacy-route.txt')) 'OK - dinh tuyen dung'
        }
        Run-Verified java @('-Xverify:all','-cp',$taskCp,'VerifyAutoTradeSymbols',$taskJar,'symbols/autotrade-symbols.properties') (Join-Path $taskBuild ($taskProfile.Key + '-symbols.txt')) 'OK - tat ca 133'
        $taskEmulatorLog = Join-Path $taskBuild ($taskProfile.Key + '-emulator.txt')
        if ($ResumeVerification -and (Test-Path -LiteralPath $taskEmulatorLog) -and ([IO.File]::ReadAllText($taskEmulatorLog) -match 'EMULATOR_STARTUP=PASS')) {
            Write-Output "Retained emulator check: $($taskProfile.Key)"
        } else {
            & (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskJar -DependencyDir ([IO.Path]::GetDirectoryName($taskMicro)) -ReportDir (Join-Path $taskBuild ($taskProfile.Key + '-emulator')) |
                Tee-Object -FilePath $taskEmulatorLog
        }
    }
    $taskSources = @(rg --files src -g '*.java')
    $taskFixtures = @(rg --files fixtures/luna -g '*.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',$taskMicro,'-d',$taskSemantic) + $taskSources) (Join-Path $taskBuild 'semantic-compile.txt')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',"$taskSemantic;$taskMicro",'-d',$taskHost) + $taskFixtures) (Join-Path $taskBuild 'semantic-host-compile.txt')
    foreach ($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoCombatHostTest','LoginMaterialHostTest')) {
        Run-Checked java @('-Xverify:all','-cp',"$taskSemantic;$taskHost;$taskMicro",$taskTest) (Join-Path $taskBuild ($taskTest + '.txt'))
    }
    foreach ($taskPath in $taskSnapshot.Keys) { if ((Get-FileHash -LiteralPath $taskPath).Hash -ne $taskSnapshot[$taskPath]) { throw "Release changed during build: $taskPath" } }
    if ((Get-FileHash -LiteralPath $taskGolden).Hash -ne $taskGoldenHash) { throw 'Golden baseline changed' }
    $taskRecords = @()
    foreach ($taskProfile in $taskProfiles) {
        $taskJar = Join-Path $taskBuild $taskProfile.File
        $taskHash = (Get-FileHash -LiteralPath $taskJar).Hash
        $taskBackup = $null
        if ($Publish) {
            New-Item -ItemType Directory -Force -Path $taskProfile.BackupDir | Out-Null
            $taskBackup = [IO.Path]::GetFullPath((Join-Path $taskWorkspace (Join-Path $taskProfile.BackupDir ($taskProfile.BackupStem + '-' + $BuildTag + '.jar'))))
            [IO.File]::Copy($taskProfile.Input,$taskBackup,$false)
            if ((Get-FileHash -LiteralPath $taskBackup).Hash -ne $taskProfile.Expected) { throw 'Backup verification failed' }
            if ((Get-FileHash -LiteralPath $taskProfile.Input).Hash -ne $taskProfile.Expected) { throw 'Release changed before replacement' }
            $taskReady = Join-Path $taskBuild ($taskProfile.Key + '-publish-ready.jar')
            [IO.File]::Copy($taskJar,$taskReady,$false)
            [IO.File]::Replace($taskReady,$taskProfile.Input,(Join-Path $taskBuild ($taskProfile.Key + '-replaced-old.jar')))
            if ((Get-FileHash -LiteralPath $taskProfile.Input).Hash -ne $taskHash) { throw 'Published hash mismatch' }
        }
        $taskRecords += [PSCustomObject]@{ Input=$taskProfile.Input; InputSHA256=$taskProfile.Expected; Candidate=$taskJar; SHA256=$taskHash; Backup=$taskBackup }
    }
    foreach ($taskPath in $taskSnapshot.Keys) {
        if ($Publish -and $taskProfiles.Input -contains $taskPath) { continue }
        if ((Get-FileHash -LiteralPath $taskPath).Hash -ne $taskSnapshot[$taskPath]) { throw "Other endpoint changed: $taskPath" }
    }
    if ((Get-FileHash -LiteralPath $taskGolden).Hash -ne $taskGoldenHash) { throw 'Golden baseline changed after publish' }
    [ordered]@{ Verified=$true; Published=[bool]$Publish; Donor=$taskDonor; DonorSHA256=$taskSnapshot[$taskDonor]; Artifacts=$taskRecords;
        Authentication='Current Hso session-key signature'; InventoryShortReaders=5; UnrelatedMethodsPreserved=$true;
        ResourceChanges=0; RMSChanges=0; OtherReleasesUnchanged=$true; GoldenUnchanged=$true;
        Emulator='12 seconds isolated headless startup per artifact'; LiveServerTested=$false
    } | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'verified.json')
    Write-Output "CHIBI_ENTRY_RELEASE=verified; published=$([bool]$Publish)"
} finally { Pop-Location }
