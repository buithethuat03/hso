[CmdletBinding()]
param(
    [string]$InputJar = '..\jar\Hung_By_Son.jar',
    [string]$ReferenceJar = '..\jar\Hso.jar',
    [Parameter(Mandatory=$true)][string]$DependencyDir,
    [string]$BuildTag = ('chibi-up-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')),
    [switch]$Publish,
    [switch]$ResumePublication
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
    if ($taskExit -ne 0) { throw "$Program failed: $taskExit" }
}
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid build tag' }
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $taskWorkspace
try {
    $taskInput = (Resolve-Path -LiteralPath $InputJar).Path
    $taskReference = (Resolve-Path -LiteralPath $ReferenceJar).Path
    $taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
    $taskAsm = (Join-Path $taskDeps 'asm-9.7.jar') + ';' + (Join-Path $taskDeps 'asm-tree-9.7.jar')
    $taskMicro = Join-Path $taskDeps 'microemulator.jar'
    $taskGolden = (Resolve-Path -LiteralPath 'inputs/luna_final-golden-baseline.jar').Path
    $taskGoldenHash = (Get-FileHash -LiteralPath $taskGolden).Hash
    if ($taskGoldenHash -ne '06622CD81DC6790871A5863D465D4E352B29A94BB6FD0062C54F7CF9C6C47E0F') { throw 'Golden baseline mismatch' }
    $taskSnapshot = @{}
    Get-ChildItem -LiteralPath '../jar' -Filter '*.jar' -File | ForEach-Object { $taskSnapshot[$_.Name] = (Get-FileHash -LiteralPath $_.FullName).Hash }
    $taskBuild = Join-Path $taskWorkspace (Join-Path 'build' $BuildTag)
    if ((Test-Path -LiteralPath $taskBuild) -and -not $ResumePublication) { throw 'Build tag already exists' }
    if ($ResumePublication -and -not (Test-Path -LiteralPath $taskBuild)) { throw 'No staged build to resume' }
    if ($ResumePublication -and -not $Publish) { throw 'ResumePublication requires Publish' }
    $taskTools = Join-Path $taskBuild 'tools'; $taskHost = Join-Path $taskBuild 'host'
    $taskSemantic = Join-Path $taskBuild 'semantic'; $taskSemanticHost = Join-Path $taskBuild 'semantic-host'
    New-Item -ItemType Directory -Force -Path $taskTools,$taskHost,$taskSemantic,$taskSemanticHost | Out-Null
    $taskJar = Join-Path $taskBuild 'chibi_up.jar'; $taskCatalog = Join-Path $taskBuild 'chibi.properties'
    $taskCachedJar = Join-Path $taskBuild 'chibi-cached-base.jar'; $taskCachedCatalog = Join-Path $taskBuild 'chibi-cached-base.properties'
    if (-not $ResumePublication) {
    Run-Checked javac @('-encoding','UTF-8','--release','7','-cp',$taskAsm,'-d',$taskTools,
        'tools/luna/HungBySonChildLoginPatcher.java','tools/luna/HsoChildSingleServerPatcher.java','tools/luna/ChibiUpPatcher.java',
        'tools/legacy/hsosky/HungBySonFullOfflinePatcher.java','tools/luna/ChibiDisableMonsterCachePatcher.java') (Join-Path $taskBuild 'tools-compile.txt')
    Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskAsm",'ChibiUpPatcher',$taskInput,$taskReference,$taskCachedJar,$taskCachedCatalog) (Join-Path $taskBuild 'patch.txt')
    Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskAsm",'ChibiDisableMonsterCachePatcher',$taskCachedJar,$taskCachedCatalog,$taskJar,$taskCatalog,(Join-Path $taskBuild 'no-monster-cache-boundary.txt')) (Join-Path $taskBuild 'no-monster-cache-patch.txt')
    $taskCp = "$taskHost;$taskTools;$taskAsm;$taskMicro"
    Run-Checked javac @('-encoding','UTF-8','-cp',$taskCp,'-d',$taskHost,
        'fixtures/hung-by-son/ChibiLoginBinaryTest.java','fixtures/hung-by-son/ChibiStorageBinaryTest.java',
        'fixtures/hung-by-son/ChibiArchiveServerTest.java','fixtures/hung-by-son/ChibiNoMonsterCacheTest.java',
        'fixtures/hung-by-son/HungBySonMonsterRecoveryBinaryTest.java',
        'fixtures/hung-by-son/HungBySonFullOfflineHostTest.java','fixtures/hung-by-son/HungBySonEnhancementWaitRegressionTest.java',
        'fixtures/hung-by-son/HungBySonEnhancementNpcSessionTest.java','fixtures/hung-by-son/TradeLocalTest.java') (Join-Path $taskBuild 'fixtures-compile.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'ChibiLoginBinaryTest',$taskReference,$taskJar,$taskCatalog) (Join-Path $taskBuild 'login-parity.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'ChibiStorageBinaryTest',$taskInput,$taskJar,(Join-Path $taskBuild 'storage-report.properties'),$taskMicro) (Join-Path $taskBuild 'storage-isolation.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'ChibiArchiveServerTest',$taskInput,$taskJar,$taskCatalog,$taskMicro) (Join-Path $taskBuild 'archive-server.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'ChibiNoMonsterCacheTest',$taskCachedJar,$taskJar,'symbols/hung-investigate-corpse-20261006.properties',$taskMicro) (Join-Path $taskBuild 'native-monster-lifecycle.txt')
    foreach ($taskTest in @('HungBySonFullOfflineHostTest','HungBySonEnhancementWaitRegressionTest','HungBySonEnhancementNpcSessionTest','TradeLocalTest')) {
        $taskArguments = @('-Xverify:all','-cp',$taskCp,$taskTest,$taskJar)
        if ($taskTest -eq 'HungBySonFullOfflineHostTest') { $taskArguments += '259' }
        if ($taskTest -eq 'TradeLocalTest') { $taskArguments += $taskMicro }
        Run-Checked java $taskArguments (Join-Path $taskBuild ($taskTest + '.txt'))
    }
    $taskSources = @(rg --files src -g '*.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',$taskMicro,'-d',$taskSemantic) + $taskSources) (Join-Path $taskBuild 'semantic-compile.txt')
    $taskFixtures = @(rg --files fixtures/luna -g '*.java') + @('fixtures/hung-by-son/ExpEstimateHostTest.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',"$taskSemantic;$taskMicro",'-d',$taskSemanticHost) + $taskFixtures) (Join-Path $taskBuild 'semantic-host-compile.txt')
    foreach ($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoCombatHostTest','LoginMaterialHostTest','AutoAttackHostTest','ExpEstimateHostTest')) {
        Run-Checked java @('-Xverify:all','-cp',"$taskSemantic;$taskSemanticHost;$taskMicro",$taskTest) (Join-Path $taskBuild ($taskTest + '.txt'))
    }
    & (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskJar -DependencyDir $taskDeps -ReportDir (Join-Path $taskBuild 'emulator') |
        Tee-Object -FilePath (Join-Path $taskBuild 'emulator-smoke.txt')
    } else {
        # Resume only the publication of an unchanged artifact after all checks passed.
        if ((Get-FileHash -LiteralPath $taskInput).Hash -ne 'BCF304BB6245CDBB6F48D5888404F8186E4E0895E2DCDAF608C68D53FEADA6E2' -or
            (Get-FileHash -LiteralPath $taskReference).Hash -ne '24B6563FA347F63245AF2432EC215367FFDEFE33830C46E111CDAA91E9B4DDAD' -or
            (Get-FileHash -LiteralPath $taskCachedJar).Hash -ne '8B0F9551165EA4A48BBC96C6ED82B9C6F56424113B3EDC51584B49BA28FCCE5E') { throw 'Resume input hash mismatch' }
        $taskResumeHash = (Get-FileHash -LiteralPath $taskJar).Hash
        if ([IO.File]::ReadAllText($taskCatalog) -notmatch ('(?m)^output\.sha256=' + $taskResumeHash + '\r?$') -or
            [IO.File]::ReadAllText((Join-Path $taskBuild 'no-monster-cache-boundary.txt')) -notmatch ('(?m)^OUTPUT_SHA256=' + $taskResumeHash + '\r?$')) { throw 'Resume candidate/report hash mismatch' }
        $taskPassedLogs = @{
            'login-parity.txt'='CHIBI_LOGIN_PACKET=PASS'; 'storage-isolation.txt'='CHIBI_STORAGE_BINARY=PASS';
            'archive-server.txt'='CHIBI_ARCHIVE_SERVER=PASS'; 'native-monster-lifecycle.txt'='ChibiNoMonsterCacheTest PASS';
            'HungBySonFullOfflineHostTest.txt'='ALL PASS'; 'HungBySonEnhancementWaitRegressionTest.txt'='WAIT REGRESSION ALL PASS';
            'HungBySonEnhancementNpcSessionTest.txt'='NPC SESSION ALL PASS'; 'TradeLocalTest.txt'='ALL PASS';
            'LunaSemanticHostTest.txt'='OK'; 'LunaControllerHostTest.txt'='OK'; 'LunaRouteHostTest.txt'='OK';
            'AutoCombatHostTest.txt'='OK'; 'LoginMaterialHostTest.txt'='OK';
            'AutoAttackHostTest.txt'='PASS'; 'ExpEstimateHostTest.txt'='PASS'; 'emulator-smoke.txt'='EMULATOR_STARTUP=PASS'
        }
        foreach ($taskLog in $taskPassedLogs.Keys) {
            if ([IO.File]::ReadAllText((Join-Path $taskBuild $taskLog)) -notmatch $taskPassedLogs[$taskLog]) { throw "Missing successful check: $taskLog" }
        }
        if ((Get-Item -LiteralPath (Join-Path $taskBuild 'emulator/emulator-stderr.txt')).Length -ne 0) { throw 'Emulator stderr is not empty' }
        Write-Output "RESUME_PUBLICATION=all staged checks passed; unchanged candidate $taskResumeHash"
    }
    foreach ($taskName in $taskSnapshot.Keys) {
        if ((Get-FileHash -LiteralPath (Join-Path '../jar' $taskName)).Hash -ne $taskSnapshot[$taskName]) { throw "Unrelated release changed: $taskName" }
    }
    if ((Get-FileHash -LiteralPath $taskGolden).Hash -ne $taskGoldenHash) { throw 'Golden changed' }
    $taskHash = (Get-FileHash -LiteralPath $taskJar).Hash
    $taskRelease = [IO.Path]::GetFullPath((Join-Path $taskWorkspace '../jar/chibi_up.jar'))
    $taskBackup = $null
    if ($Publish) {
        if (Test-Path -LiteralPath $taskRelease) {
            $taskBackup = Join-Path $taskWorkspace ("../jar/backup/chibi_up-before-$BuildTag.jar")
            if (Test-Path -LiteralPath $taskBackup) {
                if (-not $ResumePublication) { throw 'Backup exists' }
            } else { Copy-Item -LiteralPath $taskRelease -Destination $taskBackup }
            if ((Get-FileHash -LiteralPath $taskBackup).Hash -ne $taskSnapshot['chibi_up.jar']) { throw 'Backup mismatch' }
        }
        $taskPending = Join-Path $taskBuild 'publish-pending.jar'
        Copy-Item -LiteralPath $taskJar -Destination $taskPending
        if (Test-Path -LiteralPath $taskRelease) { [IO.File]::Replace($taskPending,$taskRelease,[NullString]::Value) }
        else { [IO.File]::Move($taskPending,$taskRelease) }
        if ((Get-FileHash -LiteralPath $taskRelease).Hash -ne $taskHash) { throw 'Published hash mismatch' }
    }
    [ordered]@{ Verified=$true; Published=[bool]$Publish; Candidate=$taskJar; SHA256=$taskHash;
        Source=$taskInput; Reference=$taskReference; Release=$taskRelease; Backup=$taskBackup;
        Suite='HSO CHIBI'; RMSPrefix='HSO_CHIBI_'; Server='103.77.173.232:19129';
        FullLoginPayloadParity=$true; FreshDataIsolation=$true; AllClassesLinked=$true;
        MonsterCacheEnabled=$false; MonsterCacheReadWrite=$false; MonsterCorpseRetention=$false; MonsterLifecycle='native';
        SemanticControllerRouteTests=$true; NativeLocalTradeRegression=$true;
        Emulator='12 second isolated headless startup'; LiveServerTested=$false;
        ExistingReleasesUnchanged=$true; GoldenUnchanged=$true
    } | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'verified.json')
    Write-Output "CHIBI_VERIFIED=$taskJar; SHA256=$taskHash; PUBLISHED=$Publish"
} finally { Pop-Location }
