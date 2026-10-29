[CmdletBinding()]
param(
    [string]$InputJar = '..\jar\Hung_By_Son.jar',
    [string]$DependencyDir = '..\..\.codex-deps',
    [switch]$SlotFix,
    [switch]$ReturnScroll,
    [switch]$FarmScrollMatch,
    [string]$BuildTag = ('hung-equipment-filters-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
)
$ErrorActionPreference = 'Stop'
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskWorkspace
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid BuildTag' }
$taskInput = (Resolve-Path -LiteralPath $InputJar).Path
$taskInputHash = (Get-FileHash -LiteralPath $taskInput -Algorithm SHA256).Hash
if (($SlotFix -and $ReturnScroll) -or ($SlotFix -and $FarmScrollMatch) -or ($ReturnScroll -and $FarmScrollMatch)) { throw 'Select only one patch mode' }
$taskScrollMode = $ReturnScroll -or $FarmScrollMatch
$taskExpectedInput = if ($FarmScrollMatch) { '2F07C9183C568D929F29BC2FE5B12FBD73FBB36CFEA36C48C86CE22A2E6651AF' } elseif ($ReturnScroll) { '6F1E025C754DDE82F9D479DB1E623C9B680B419A64084C2ED24B983FF9D00819' } elseif ($SlotFix) { '92EF9789FC89BBA7A037F08A6A2A9A4CA9073068A2F820802D04CE84A501BE2D' } else { 'F07535B4B0A7B71A896F9F5DBE9C9871C846B92827EA0273910CEC704EBBF600' }
if ($taskInputHash -ne $taskExpectedInput) { throw 'Use the pinned Hung x1 input for this mode' }
$taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
$taskAsm = (Join-Path $taskDeps 'asm-9.7.jar') + ';' + (Join-Path $taskDeps 'asm-tree-9.7.jar')
$taskMicro = Join-Path $taskDeps 'microemulator.jar'
$taskBuild = Join-Path $taskWorkspace ('build/' + $BuildTag)
if (Test-Path -LiteralPath $taskBuild) { throw 'Use a fresh BuildTag' }
New-Item -ItemType Directory -Path $taskBuild,(Join-Path $taskBuild 'tools'),(Join-Path $taskBuild 'support'),(Join-Path $taskBuild 'host') | Out-Null
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
Run-Checked javac @('-encoding','UTF-8','-cp',$taskAsm,'-d',$taskTools,
    'tools/luna/HungEquipmentFiltersPatcher.java','tools/luna/HungEquipmentSlotFixPatcher.java','tools/luna/HungSaleReturnScrollPatcher.java','tools/luna/HungFarmScrollMatchFixPatcher.java','tools/luna/HungAutoAttackNativeBuilder.java','tools/luna/HungBySonExpInfoPack.java',
    'tools/legacy/hsosky/HungBySonFullOfflinePatcher.java') 'tools-compile.txt'
$taskSources = @(& rg --files src -g '*.java')
Run-Checked javac (@('-encoding','UTF-8','--release','7','-Xlint:-options','-cp',$taskMicro,'-d',$taskSupport) + $taskSources) 'support-compile.txt'
$taskPatcher = if ($FarmScrollMatch) { 'HungFarmScrollMatchFixPatcher' } elseif ($ReturnScroll) { 'HungSaleReturnScrollPatcher' } elseif ($SlotFix) { 'HungEquipmentSlotFixPatcher' } else { 'HungEquipmentFiltersPatcher' }
Run-Checked java @('-cp',"$taskTools;$taskAsm",$taskPatcher,$taskInput,$taskSupport,$taskCandidate,(Join-Path $taskBuild 'equipment-filters.properties')) 'patch.txt'
$taskHash = (Get-FileHash -LiteralPath $taskCandidate -Algorithm SHA256).Hash
$taskBase = if ($FarmScrollMatch) { 'build/hung-sale-scroll-20261025-b' } elseif ($ReturnScroll) { 'build/hung-equipment-slots-20261025-a' } elseif ($SlotFix) { 'build/hung-equipment-filters-20261025-c' } else { 'build/hung-dungeon-daily-limit-20261025-c' }
foreach ($taskCatalog in @('auto-attack','battlefield','exp','recovery','timing','noclip','dungeon','maze')) {
    $taskText = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $taskBase ($taskCatalog + '.properties'))
    if ($taskText -notmatch ('(?m)^output.sha256=' + $taskInputHash + '\r?$')) { throw "Unpinned catalog $taskCatalog" }
    $taskText = [regex]::Replace($taskText,'(?m)^output.sha256=[^\r\n]*',('output.sha256=' + $taskHash))
    $taskText = [regex]::Replace($taskText,'(?m)^noclip.output.sha256=[^\r\n]*',('noclip.output.sha256=' + $taskHash))
    [IO.File]::WriteAllText((Join-Path $taskBuild ($taskCatalog + '.properties')),$taskText,[Text.UTF8Encoding]::new($false))
}
$taskSemanticTests = @(& rg --files fixtures/luna -g '*.java')
Run-Checked javac (@('-encoding','UTF-8','--release','7','-Xlint:-options','-cp',"$taskSupport;$taskMicro",'-d',$taskHost) + $taskSemanticTests) 'semantic-tests-compile.txt'
Run-Checked java @('-Xverify:all','-cp',"$taskHost;$taskSupport;$taskMicro",'EquipmentFilterPolicyHostTest') 'policy-test.txt'
if ($taskScrollMode) { Run-Checked java @('-Xverify:all','-cp',"$taskHost;$taskSupport;$taskMicro",'SaleReturnScrollHostTest') 'sale-return-scroll-policy.txt' }
foreach ($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoAttackHostTest','SpecialAttackHostTest','FarmSpecialAttackHostTest','FarmSpotHostTest','NormalMonstersHostTest','SkillCastStatsHostTest')) {
    Run-Checked java @('-Xverify:all','-cp',"$taskHost;$taskSupport;$taskMicro",$taskTest) ($taskTest + '.txt')
}
$taskBinaryTests = @('HungEquipmentFiltersBinaryTest','HungBySonMazeExclusiveBinaryTest','HungBySonAutoAttackBinaryTest',
    'HungBySonModUpLvBinaryTest','HungBySonCopySpecialBinaryTest','HungBySonSpecialAttackBinaryTest','HungBySonFarmSpecialAttackBinaryTest',
    'HungBySonFullOfflineHostTest','HungBySonBattlefieldBinaryTest','HungBySonAutoAttackPkCorpseHostTest',
    'HungBySonEnhancementWaitRegressionTest','HungBySonEnhancementNpcSessionTest','TradeLocalTest','HungBySonExpInfoBinaryTest','HungEquipmentFiltersUiTest')
if ($taskScrollMode) { $taskBinaryTests += 'HungSaleReturnScrollBinaryTest' }
$taskBinarySources = @($taskBinaryTests | ForEach-Object { 'fixtures/hung-by-son/' + $_ + '.java' })
Run-Checked javac (@('-encoding','UTF-8','-cp',"$taskCandidate;$taskTools;$taskAsm;$taskMicro",'-d',$taskHost) + $taskBinarySources) 'binary-tests-compile.txt'
$taskHostCp = "$taskHost;$taskTools;$taskAsm;$taskMicro"
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungEquipmentFiltersBinaryTest',$taskCandidate) 'equipment-filters-binary.txt'
if ($taskScrollMode) { Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungSaleReturnScrollBinaryTest',$taskCandidate) 'sale-return-scroll-binary.txt' }
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-Djava.awt.headless=true','-Dsun.reflect.inflationThreshold=10000','-cp',"$taskHost;$taskMicro",'HungEquipmentFiltersUiTest',$taskCandidate,(Join-Path $taskBuild 'screenshots')) 'equipment-filters-ui.txt'
$taskAuto = Join-Path $taskBuild 'auto-attack.properties'
$taskDungeon = Join-Path $taskBuild 'maze.properties'
foreach ($taskTest in @('HungBySonModUpLvBinaryTest','HungBySonCopySpecialBinaryTest','HungBySonSpecialAttackBinaryTest','HungBySonFarmSpecialAttackBinaryTest')) {
    Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,$taskTest,$taskCandidate,$taskAuto,$taskDungeon) ($taskTest + '.txt')
}
Copy-Item -LiteralPath (Join-Path $taskBuild 'HungBySonSpecialAttackBinaryTest.txt') -Destination (Join-Path $taskBuild 'special-attack-binary.txt')
$taskExpectedClasses = if ($taskScrollMode) { 280 } else { 278 }
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonFullOfflineHostTest',$taskCandidate,[string]$taskExpectedClasses) 'class-linkage-offline.txt'
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonBattlefieldBinaryTest',$taskCandidate,$taskAuto,(Join-Path $taskBuild 'battlefield.properties')) 'battlefield.txt'
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonAutoAttackPkCorpseHostTest',$taskCandidate,$taskAuto) 'pk-corpse.txt'
foreach ($taskTest in @('HungBySonEnhancementWaitRegressionTest','HungBySonEnhancementNpcSessionTest')) {
    Run-Checked java @('-Xverify:all','-cp',$taskHostCp,$taskTest,$taskCandidate) ($taskTest + '.txt')
}
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'TradeLocalTest',$taskCandidate,$taskMicro) 'trade.txt'
$taskReference = (Resolve-Path -LiteralPath '../jar/backup/Hung_By_Son-before-hung-auto-attack-20261014-final-c.jar').Path
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonAutoAttackBinaryTest',$taskReference,$taskCandidate,
    (Resolve-Path -LiteralPath '../jar/bachho_final.jar').Path,$taskAuto,$taskMicro,(Join-Path $taskBuild 'auto-attack-binary')) 'auto-attack-native.txt'
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonMazeExclusiveBinaryTest',$taskCandidate,$taskAuto,$taskDungeon,$taskDungeon) 'maze-binary.txt'
& (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskCandidate -DependencyDir $taskDeps -ReportDir (Join-Path $taskBuild 'startup')
if ((Get-FileHash -LiteralPath $taskInput -Algorithm SHA256).Hash -ne $taskInputHash) { throw 'Input changed during build' }
[ordered]@{Verified=$true;Published=$false;Input=$taskInput;InputSHA256=$taskInputHash;Candidate=$taskCandidate;SHA256=$taskHash;
    ClassCount=$taskExpectedClasses;ChangedNativeClasses=$(if ($FarmScrollMatch) {0} elseif ($ReturnScroll) {1} elseif ($SlotFix) {0} else {2});AddedClasses=$(if ($FarmScrollMatch) {0} elseif ($ReturnScroll) {2} elseif ($SlotFix) {0} else {3});SlotFix=[bool]$SlotFix;ReturnScroll=[bool]$ReturnScroll;FarmScrollMatch=[bool]$FarmScrollMatch;EquipmentGroups='Strength,Dexterity,Vitality,Spirit';
    FullBagNoCandidate='disable before travel; defer cleanup during existing sale cycle';LiveServerTested=$false} |
    ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'verified.json')
Write-Output "VERIFIED=$taskCandidate SHA256=$taskHash"
