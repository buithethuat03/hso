[CmdletBinding()]
param(
    [string]$InputJar = '..\jar\Hung_By_Son.jar',
    [string]$DependencyDir = '..\..\.codex-deps',
    [string]$BuildTag = ('hung-maze-exclusive-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
)
$ErrorActionPreference = 'Stop'
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskWorkspace
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid BuildTag' }
$taskInput = (Resolve-Path -LiteralPath $InputJar).Path
$taskInputHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskInput).Hash
if ($taskInputHash -ne '387334F942B5534DD3B6ECDD2C3BBDEB638E4060B650C94CC784BDFEB1B08300') { throw 'Use the pinned stationary-dungeon x1 release (or its verified backup)' }
$taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
$taskAsm = (Join-Path $taskDeps 'asm-9.7.jar') + ';' + (Join-Path $taskDeps 'asm-tree-9.7.jar')
$taskMicro = Join-Path $taskDeps 'microemulator.jar'
$taskBase = 'build/hung-dungeon-stationary-20261025-b'
$taskBuild = Join-Path $taskWorkspace ('build/' + $BuildTag)
if (Test-Path -LiteralPath $taskBuild) { throw 'BuildTag exists; use a fresh output' }
New-Item -ItemType Directory -Path $taskBuild, (Join-Path $taskBuild 'tools'), (Join-Path $taskBuild 'support'), (Join-Path $taskBuild 'host') | Out-Null
$taskTools = Join-Path $taskBuild 'tools'
$taskSupport = Join-Path $taskBuild 'support'
$taskHost = Join-Path $taskBuild 'host'
$taskCandidate = Join-Path $taskBuild 'Hung_By_Son.jar'
function Run-Checked {
    param([string]$Program,[string[]]$Arguments,[string]$Log)
    $taskPrior = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $taskLines = @(& $Program @Arguments 2>&1 | ForEach-Object { $_.ToString() })
        $taskExit = $LASTEXITCODE
        $taskLines | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild $Log)
    } finally { $ErrorActionPreference = $taskPrior }
    if ($taskExit -ne 0) { $taskLines | Select-Object -Last 20; throw "$Program failed ($Log)" }
    $taskLines | Select-Object -Last 1
}
$taskToolSources = @('HungMazeExclusivePatcher','HungBySonCopySpecialPatcher','HungBySonModUpLvPatcher','HungBySonExpInfoPack') | ForEach-Object { 'tools/luna/' + $_ + '.java' }
Run-Checked javac (@('-encoding','UTF-8','-cp',$taskAsm,'-d',$taskTools) + $taskToolSources + @('tools/legacy/hsosky/HungBySonFullOfflinePatcher.java')) 'tools-compile.txt'
$taskSources = @(& rg --files src -g '*.java')
Run-Checked javac (@('-encoding','UTF-8','--release','7','-Xlint:-options','-cp',$taskMicro,'-d',$taskSupport) + $taskSources) 'support-compile.txt'
Run-Checked java @('-cp',"$taskTools;$taskAsm",'HungMazeExclusivePatcher',$taskInput,$taskSupport,$taskMicro,(Join-Path $taskBuild 'port-work'),$taskCandidate,(Join-Path $taskBuild 'maze')) 'patch.txt'
$taskHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskCandidate).Hash
foreach ($taskCatalog in @('auto-attack','battlefield','exp','recovery','timing','noclip','dungeon')) {
    $taskText = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $taskBase ($taskCatalog + '.properties'))
    if ($taskText -notmatch ('(?m)^output.sha256=' + $taskInputHash + '\r?$')) { throw "Unpinned baseline catalog $taskCatalog" }
    $taskText = [regex]::Replace($taskText,'(?m)^output.sha256=[^\r\n]*',('output.sha256=' + $taskHash))
    $taskText = [regex]::Replace($taskText,'(?m)^noclip.output.sha256=[^\r\n]*',('noclip.output.sha256=' + $taskHash))
    [IO.File]::WriteAllText((Join-Path $taskBuild ($taskCatalog + '.properties')),$taskText,[Text.UTF8Encoding]::new($false))
}
$taskSemanticTests = @(& rg --files fixtures/luna -g '*.java') + @('fixtures/hung-by-son/HungDungeonPositionHostTest.java')
Run-Checked javac (@('-encoding','UTF-8','--release','7','-Xlint:-options','-cp',"$taskSupport;$taskMicro",'-d',$taskHost) + $taskSemanticTests) 'semantic-tests-compile.txt'
foreach ($taskTest in @('HungDungeonPositionHostTest','LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoAttackHostTest','SpecialAttackHostTest','FarmSpecialAttackHostTest','FarmSpotHostTest','NormalMonstersHostTest','SkillCastStatsHostTest')) {
    Run-Checked java @('-Xverify:all','-cp',"$taskHost;$taskSupport;$taskMicro",$taskTest) ($taskTest + '.txt')
}
$taskBinaryTests = @('HungBySonMazeExclusiveBinaryTest','HungBySonAutoAttackBinaryTest','HungBySonModUpLvBinaryTest','HungBySonCopySpecialBinaryTest','HungBySonSpecialAttackBinaryTest','HungBySonFarmSpecialAttackBinaryTest','HungBySonFullOfflineHostTest','HungBySonBattlefieldBinaryTest','HungBySonAutoAttackPkCorpseHostTest','HungBySonEnhancementWaitRegressionTest','HungBySonEnhancementNpcSessionTest','TradeLocalTest')
$taskBinarySources = @($taskBinaryTests | ForEach-Object { 'fixtures/hung-by-son/' + $_ + '.java' })
Run-Checked javac (@('-encoding','UTF-8','-cp',"$taskCandidate;$taskTools;$taskAsm;$taskMicro",'-d',$taskHost) + $taskBinarySources) 'binary-tests-compile.txt'
$taskHostCp = "$taskHost;$taskTools;$taskAsm;$taskMicro"
$taskAuto = Join-Path $taskBuild 'auto-attack.properties'
$taskDungeon = Join-Path $taskBuild 'maze.properties'
foreach ($taskTest in @('HungBySonModUpLvBinaryTest','HungBySonCopySpecialBinaryTest','HungBySonSpecialAttackBinaryTest','HungBySonFarmSpecialAttackBinaryTest')) {
    Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,$taskTest,$taskCandidate,$taskAuto,$taskDungeon) ($taskTest + '.txt')
}
Copy-Item -LiteralPath (Join-Path $taskBuild 'HungBySonSpecialAttackBinaryTest.txt') -Destination (Join-Path $taskBuild 'special-attack-binary.txt')
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonFullOfflineHostTest',$taskCandidate,'275') 'class-linkage-offline.txt'
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonBattlefieldBinaryTest',$taskCandidate,$taskAuto,(Join-Path $taskBuild 'battlefield.properties')) 'battlefield.txt'
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonAutoAttackPkCorpseHostTest',$taskCandidate,$taskAuto) 'pk-corpse.txt'
foreach ($taskTest in @('HungBySonEnhancementWaitRegressionTest','HungBySonEnhancementNpcSessionTest')) {
    Run-Checked java @('-Xverify:all','-cp',$taskHostCp,$taskTest,$taskCandidate) ($taskTest + '.txt')
}
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'TradeLocalTest',$taskCandidate,$taskMicro) 'trade.txt'
$taskReference = (Resolve-Path -LiteralPath '../jar/backup/Hung_By_Son-before-hung-auto-attack-20261014-final-c.jar').Path
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonAutoAttackBinaryTest',$taskReference,$taskCandidate,(Resolve-Path -LiteralPath '../jar/bachho_final.jar').Path,$taskAuto,$taskMicro,(Join-Path $taskBuild 'auto-attack-binary')) 'auto-attack-native.txt'
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonMazeExclusiveBinaryTest',$taskCandidate,$taskAuto,$taskDungeon,(Join-Path $taskBuild 'maze.properties')) 'HungBySonMazeExclusiveBinaryTest.txt'
& (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskCandidate -DependencyDir $taskDeps -ReportDir (Join-Path $taskBuild 'startup')
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskInput).Hash -ne $taskInputHash) { throw 'Input changed during build' }
[ordered]@{Verified=$true;Published=$false;Input=$taskInput;InputSHA256=$taskInputHash;Candidate=$taskCandidate;SHA256=$taskHash;MazeExclusive=$true;SpecialAttack=$true;AllClassesLinked=$true;SpecialCatalog=(Join-Path $taskBuild 'maze.properties');ClassCount=275;DungeonMap=48;WestNorth='20,20';East='33,20';South='19,33';Multiple='27,26';GoldChestException='map48 only';LiveServerTested=$false;Emulator='isolated headless startup'} | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'verified.json')
Write-Output "VERIFIED=$taskCandidate SHA256=$taskHash"
