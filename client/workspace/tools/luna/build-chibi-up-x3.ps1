[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$DependencyDir,
    [string]$BuildTag = ('chibi-up-x3-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')),
    [switch]$Publish
)
$ErrorActionPreference = 'Stop'
function Run-Checked {
    param([string]$Program,[string[]]$Arguments,[string]$Log)
    $taskPrevious = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $taskLines = @(& $Program @Arguments 2>&1 | ForEach-Object { $_.ToString() })
        $taskExit = $LASTEXITCODE
        $taskLines | Set-Content -Encoding UTF8 -LiteralPath $Log
    } finally { $ErrorActionPreference = $taskPrevious }
    if ($taskExit -ne 0) { Get-Content -LiteralPath $Log -Tail 16; throw "$Program failed: $taskExit" }
    $taskLines | Select-Object -Last 1
}
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid build tag' }
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $taskWorkspace
try {
    $taskSource = (Resolve-Path -LiteralPath '../jar/chibi_up.jar').Path
    $taskSourceHash = '34BCFBF74DB570954DFA563E931D98F84DB9796EE018FB75570EC2EC2472E500'
    if ((Get-FileHash -LiteralPath $taskSource).Hash -ne $taskSourceHash) { throw 'Unexpected CHIBI source' }
    $taskProofDir = 'build/chibi-no-cache-release-20261016'
    $taskProof = Get-Content -Encoding UTF8 -Raw -LiteralPath "$taskProofDir/verified.json" | ConvertFrom-Json
    if (-not $taskProof.Verified -or $taskProof.SHA256 -ne $taskSourceHash -or $taskProof.MonsterCacheEnabled -ne $false) { throw 'Missing matching no-cache source proof' }
    $taskTemplate = (Resolve-Path -LiteralPath '../jar/backup/autotrade_v27_revive05_horse_x3.jar').Path
    $taskTemplateHash = (Get-FileHash -LiteralPath $taskTemplate).Hash
    $taskGolden = (Resolve-Path -LiteralPath 'inputs/luna_final-golden-baseline.jar').Path
    $taskGoldenHash = (Get-FileHash -LiteralPath $taskGolden).Hash
    if ($taskGoldenHash -ne '06622CD81DC6790871A5863D465D4E352B29A94BB6FD0062C54F7CF9C6C47E0F') { throw 'Golden mismatch' }
    $taskSnapshot = @{}
    Get-ChildItem -LiteralPath '../jar' -File -Filter '*.jar' | ForEach-Object { $taskSnapshot[$_.Name] = (Get-FileHash -LiteralPath $_.FullName).Hash }
    $taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
    foreach ($taskName in @('asm-9.7.jar','asm-tree-9.7.jar','microemulator.jar')) {
        if (-not (Test-Path -LiteralPath (Join-Path $taskDeps $taskName) -PathType Leaf)) { throw "Missing $taskName" }
    }
    $taskAsm = (Join-Path $taskDeps 'asm-9.7.jar') + ';' + (Join-Path $taskDeps 'asm-tree-9.7.jar')
    $taskMicro = Join-Path $taskDeps 'microemulator.jar'
    $taskBuild = Join-Path $taskWorkspace "build/$BuildTag"
    if (Test-Path -LiteralPath $taskBuild) { throw 'Use a fresh build tag' }
    $taskTools = Join-Path $taskBuild 'tools'; $taskSupport = Join-Path $taskBuild 'support'
    $taskHost = Join-Path $taskBuild 'host'; $taskRuntime = Join-Path $taskBuild 'runtime-home'
    $taskSemantic = Join-Path $taskBuild 'semantic'; $taskSemanticHost = Join-Path $taskBuild 'semantic-host'
    New-Item -ItemType Directory -Path $taskTools,$taskSupport,$taskHost,$taskRuntime,$taskSemantic,$taskSemanticHost | Out-Null
    Run-Checked javac @('-encoding','UTF-8','--release','7','-cp',$taskAsm,'-d',$taskTools,
        'tools/luna/HungBySonX3Packer.java','tools/legacy/luna/HsoChildX3Packer.java',
        'tools/legacy/luna/RepackSupportX3.java') "$taskBuild/tools-compile.txt"
    Copy-Item -LiteralPath 'tools/legacy/luna/x3/X3SlotSupport.java.in' -Destination "$taskSupport/X3SlotSupport.java"
    Run-Checked javac @('-encoding','UTF-8','--release','7','-d',$taskSupport,"$taskSupport/X3SlotSupport.java") "$taskBuild/helper-compile.txt"
    $taskJar = Join-Path $taskBuild 'chibi_up_x3.jar'
    Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskAsm",'HungBySonX3Packer',
        $taskSource,$taskTemplate,$taskJar,"$taskSupport/X3SlotSupport.class","$taskBuild/x3-pack-report.txt",$taskSourceHash,'CHIBI') "$taskBuild/x3-pack.txt"
    $taskCp = "$taskHost;$taskTools;$taskAsm;$taskMicro"
    Run-Checked javac @('-encoding','UTF-8','-cp',$taskCp,'-d',$taskHost,
        'fixtures/x3/ChibiX3HostTest.java','fixtures/x3/HsoChildX3LauncherTest.java',
        'fixtures/hung-by-son/ChibiLoginBinaryTest.java','fixtures/hung-by-son/ChibiNoMonsterCacheTest.java',
        'fixtures/hung-by-son/HungBySonMonsterRecoveryBinaryTest.java','fixtures/hung-by-son/TradeLocalTest.java') "$taskBuild/fixtures-compile.txt"
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'ChibiX3HostTest',$taskJar,$taskSource,$taskTemplate) "$taskBuild/x3-host.txt"
    # Execute source native regressions again; complete packed namespace parity binds all three copies to this source.
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'ChibiLoginBinaryTest','../jar/Hso.jar',$taskSource,"$taskProofDir/chibi.properties") "$taskBuild/source-login.txt"
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'ChibiNoMonsterCacheTest',"$taskProofDir/chibi-cached-base.jar",$taskSource,'symbols/hung-investigate-corpse-20261006.properties',$taskMicro) "$taskBuild/source-native-monster.txt"
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'TradeLocalTest',$taskSource,$taskMicro) "$taskBuild/source-native-trade.txt"
    $taskSources = @(rg --files src -g '*.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',$taskMicro,'-d',$taskSemantic) + $taskSources) "$taskBuild/semantic-compile.txt"
    $taskFixtures = @(rg --files fixtures/luna -g '*.java') + @('fixtures/hung-by-son/ExpEstimateHostTest.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',"$taskSemantic;$taskMicro",'-d',$taskSemanticHost) + $taskFixtures) "$taskBuild/semantic-fixtures-compile.txt"
    foreach ($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoCombatHostTest','LoginMaterialHostTest','AutoAttackHostTest','ExpEstimateHostTest')) {
        Run-Checked java @('-Xverify:all','-cp',"$taskSemantic;$taskSemanticHost;$taskMicro",$taskTest) "$taskBuild/$taskTest.txt"
    }
    $taskPrevious = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & java '-Xverify:all' "-Duser.home=$taskRuntime" '-Djava.awt.headless=true' '-cp' "$taskHost;$taskMicro" 'HsoChildX3LauncherTest' $taskJar "$taskBuild/screenshots" 'HSO CHIBI' 1> "$taskBuild/launcher.out.log" 2> "$taskBuild/launcher.err.log"
        $taskExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $taskPrevious }
    if ($taskExit -ne 0 -or (Get-Item -LiteralPath "$taskBuild/launcher.err.log").Length -ne 0) { throw 'Launcher test failed or stderr was not empty' }
    if (Select-String -LiteralPath "$taskBuild/launcher.out.log" -Pattern 'Exception|Error|not found|loi load') { throw 'Launcher runtime error' }
    if (-not (Select-String -LiteralPath "$taskBuild/launcher.out.log" -SimpleMatch 'PASS: one native launcher')) { throw 'Launcher did not complete' }
    foreach ($taskName in $taskSnapshot.Keys) {
        if ((Get-FileHash -LiteralPath (Join-Path '../jar' $taskName)).Hash -ne $taskSnapshot[$taskName]) { throw "Existing release changed: $taskName" }
    }
    if ((Get-FileHash -LiteralPath $taskGolden).Hash -ne $taskGoldenHash -or (Get-FileHash -LiteralPath $taskTemplate).Hash -ne $taskTemplateHash) { throw 'Golden or template changed' }
    $taskHash = (Get-FileHash -LiteralPath $taskJar).Hash
    $taskRelease = [IO.Path]::GetFullPath((Join-Path $taskWorkspace '../jar/chibi_up_x3.jar'))
    $taskBackup = $null
    if ($Publish) {
        if (Test-Path -LiteralPath $taskRelease) {
            $taskBackup = Join-Path $taskWorkspace "../jar/backup/chibi_up_x3-before-$BuildTag.jar"
            if (Test-Path -LiteralPath $taskBackup) { throw 'Backup already exists' }
            Copy-Item -LiteralPath $taskRelease -Destination $taskBackup
            if ((Get-FileHash -LiteralPath $taskBackup).Hash -ne $taskSnapshot['chibi_up_x3.jar']) { throw 'Backup mismatch' }
        }
        $taskPending = Join-Path $taskBuild 'publish-pending.jar'
        Copy-Item -LiteralPath $taskJar -Destination $taskPending
        if (Test-Path -LiteralPath $taskRelease) { [IO.File]::Replace($taskPending,$taskRelease,[NullString]::Value) }
        else { [IO.File]::Move($taskPending,$taskRelease) }
        if ((Get-FileHash -LiteralPath $taskRelease).Hash -ne $taskHash) { throw 'Published hash mismatch' }
    }
    [ordered]@{ Verified=$true; Published=[bool]$Publish; Candidate=$taskJar; Release=$taskRelease; Backup=$taskBackup;
        SHA256=$taskHash; SourceSHA256=$taskSourceHash; TemplateSHA256=$taskTemplateHash;
        Suite='HSO CHIBI X3'; Vendor='HSO_CHIBI_X3'; Classes=800; MonsterCacheEnabled=$false;
        SlotRMSPrefixes=@('aHSO_CHIBI_','bHSO_CHIBI_','cHSO_CHIBI_'); SharedExp='aHSO_CHIBI_hbs_exp_levels_v2';
        Emulator='Three live clients; held-*; 1->2->3->1'; LiveServerTested=$false;
        ExistingReleasesUnchanged=$true; GoldenUnchanged=$true
    } | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -LiteralPath "$taskBuild/verified.json"
    Write-Output "CHIBI_X3_VERIFIED=$taskJar; SHA256=$taskHash; PUBLISHED=$Publish"
} finally { Pop-Location }
