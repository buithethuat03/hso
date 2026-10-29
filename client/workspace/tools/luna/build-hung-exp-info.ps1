[CmdletBinding()]
param(
    [string]$InputJar = '..\jar\Hung_By_Son.jar',
    [string]$DependencyDir = '',
    [string]$BuildTag = ('hung-exp-info-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')),
    [switch]$Publish
)
$ErrorActionPreference = 'Stop'
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskWorkspace
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid build tag' }
if (-not $DependencyDir) { $DependencyDir = $env:LUNA_DEPS }
if (-not $DependencyDir) { throw 'Supply -DependencyDir or LUNA_DEPS' }
$taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
$taskMicro = Join-Path $taskDeps 'microemulator.jar'
$taskAsm = Join-Path $taskDeps 'asm-9.7.jar'
$taskTree = Join-Path $taskDeps 'asm-tree-9.7.jar'
foreach ($taskDependency in @($taskMicro,$taskAsm,$taskTree)) {
    if (-not (Test-Path -LiteralPath $taskDependency -PathType Leaf)) { throw "Missing $taskDependency" }
}
$taskInput = (Resolve-Path -LiteralPath $InputJar).Path
$taskExpected = '38CBF77190184F479645EF44E9EA74898C19ACB0227C16E245DB6834D83877DE'
if ((Get-FileHash -LiteralPath $taskInput -Algorithm SHA256).Hash -ne $taskExpected) { throw 'Unexpected x1 source hash' }
$taskBuild = Join-Path $taskWorkspace ('build/' + $BuildTag)
if (Test-Path -LiteralPath $taskBuild) { throw 'Use a fresh build tag' }
$taskSupport = Join-Path $taskBuild 'support'
$taskTools = Join-Path $taskBuild 'tools'
$taskSemantic = Join-Path $taskBuild 'semantic'
$taskHost = Join-Path $taskBuild 'host'
New-Item -ItemType Directory -Path $taskBuild,$taskSupport,$taskTools,$taskSemantic,$taskHost | Out-Null
$taskCandidate = Join-Path $taskBuild 'Hung_By_Son.jar'
$taskUi = Join-Path $taskBuild 'native-ui.jar'
$taskUiCatalog = Join-Path $taskBuild 'ui-symbols.properties'
$taskCatalog = Join-Path $taskBuild 'info-symbols.properties'
$taskAsmCp = "$taskAsm;$taskTree"
function Run-Checked {
    param([string]$Program,[string[]]$Arguments,[string]$Log)
    $taskPrior = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $taskOutput = & $Program @Arguments 2>&1
        $taskExit = $LASTEXITCODE
        $taskOutput | ForEach-Object {$_.ToString()} | Set-Content -LiteralPath (Join-Path $taskBuild $Log) -Encoding UTF8
    } finally { $ErrorActionPreference = $taskPrior }
    if ($taskExit -ne 0) { $taskOutput | Select-Object -Last 16; throw "$Program failed, see $Log" }
    $taskOutput | Select-Object -Last 1
}
Run-Checked 'javac' @('-encoding','UTF-8','-cp',$taskAsmCp,'-d',$taskTools,
    'tools/luna/HungBySonExpInfoUiBuilder.java','tools/luna/HungBySonExpInfoPack.java') 'tools-compile.txt'
Run-Checked 'java' @('-Xverify:all','-cp',"$taskTools;$taskAsmCp",'HungBySonExpInfoUiBuilder',
    $taskInput,$taskUi,$taskUiCatalog,(Join-Path $taskBuild 'ui-patch-report.txt')) 'ui-patch.txt'
Run-Checked 'javac' @('-encoding','UTF-8','--release','7','-d',$taskSupport,
    'src/luna/api/ExpEstimateStore.java','src/luna/features/ExpEstimateSupport.java',
    'src/luna/features/ExpRateSupport.java','src/luna/ui/ExpPercentInterpolator.java') 'support-compile.txt'
Run-Checked 'java' @('-Xverify:all','-cp',"$taskTools;$taskAsmCp",'HungBySonExpInfoPack',
    $taskUi,$taskSupport,$taskCandidate,$taskUiCatalog,'symbols/hung-exp-info-base-single-20261019.properties',
    $taskCatalog,(Join-Path $taskBuild 'archive-boundary.txt'),$taskInput) 'pack.txt'
$taskSemanticSources = @(& rg --files src -g '*.java')
Run-Checked 'javac' (@('-encoding','UTF-8','--release','7','-cp',$taskMicro,'-d',$taskSemantic) + $taskSemanticSources) 'semantic-compile.txt'
$taskFixtures = @(& rg --files fixtures/luna -g '*.java') + @(
    'fixtures/hung-by-son/ExpEstimateHostTest.java','fixtures/hung-by-son/ExpPercentInterpolatorHostTest.java',
    'fixtures/hung-by-son/ExpPercentIntegrationHostTest.java','fixtures/hung-by-son/ExpRateHostTest.java',
    'fixtures/hung-by-son/ExpInfoHostTest.java')
Run-Checked 'javac' (@('-encoding','UTF-8','--release','7','-cp',"$taskSemantic;$taskMicro",'-d',$taskHost) + $taskFixtures) 'host-compile.txt'
foreach ($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest',
        'AutoCombatHostTest','LoginMaterialHostTest','AutoAttackHostTest')) {
    Run-Checked 'java' @('-Xverify:all','-cp',"$taskSemantic;$taskHost;$taskMicro",$taskTest) ($taskTest+'.txt')
}
foreach ($taskTest in @('ExpEstimateHostTest','ExpPercentInterpolatorHostTest','ExpPercentIntegrationHostTest',
        'ExpRateHostTest','ExpInfoHostTest')) {
    Run-Checked 'java' @('-Xverify:all','-cp',"$taskCandidate;$taskHost;$taskMicro",$taskTest) ($taskTest+'.txt')
}
Run-Checked 'javac' @('-encoding','UTF-8','-cp',"$taskAsmCp;$taskMicro",'-d',$taskHost,
    'fixtures/hung-by-son/HungBySonExpBinaryTest.java','fixtures/hung-by-son/HungBySonExpInfoBinaryTest.java') 'binary-compile.txt'
foreach ($taskTest in @('HungBySonExpBinaryTest','HungBySonExpInfoBinaryTest')) {
    $taskScreens = Join-Path $taskBuild $taskTest
    Run-Checked 'java' @('-Xverify:all','-cp',"$taskHost;$taskAsmCp;$taskMicro",$taskTest,
        $taskCandidate,$taskCatalog,$taskMicro,$taskScreens) ($taskTest+'.txt')
}
& (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskCandidate -DependencyDir $taskDeps `
    -ReportDir (Join-Path $taskBuild 'startup')
if (-not $?) { throw 'Startup smoke failed' }
$taskHash = (Get-FileHash -LiteralPath $taskCandidate -Algorithm SHA256).Hash
[ordered]@{
    Verified=$true;Published=$false;Variant='x1';Decimals=3;InfoEnabled=$true;LiveServerTested=$false
    Input=$taskInput;InputSHA256=$taskExpected;Candidate=$taskCandidate;SHA256=$taskHash
    DependencyDir=$taskDeps;Catalog=$taskCatalog;NativeInverseVerified=$true;NativeHudByteIdentical=$true
    AllClassesLinked=$true;NativeExpAndRmsTested=$true;NativeInfoInputAndConfirmationTested=$true
    EmulatorStartupSeconds=12;RateMinimumMillis=300000
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskBuild 'verified.json') -Encoding UTF8
if ($Publish) { & (Join-Path $PSScriptRoot 'publish-hung-exp-info.ps1') -StageBuild $taskBuild }
Write-Output "VERIFIED=$taskCandidate SHA256=$taskHash"
