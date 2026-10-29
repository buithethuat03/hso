[CmdletBinding()]
param(
    [string]$InputJar = '..\jar\Hung_By_Son.jar',
    [string]$DependencyDir = '',
    [string]$BuildTag = ('hung-exp-percent-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')),
    [switch]$Publish
)
$ErrorActionPreference = 'Stop'
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskWorkspace
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid build tag' }
if (-not $DependencyDir) { $DependencyDir = $env:LUNA_DEPS }
if (-not $DependencyDir) { throw 'Supply external dependencies with -DependencyDir or LUNA_DEPS' }
$taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
$taskMicro = Join-Path $taskDeps 'microemulator.jar'
$taskAsm = Join-Path $taskDeps 'asm-9.7.jar'
$taskTree = Join-Path $taskDeps 'asm-tree-9.7.jar'
foreach ($taskDependency in @($taskMicro, $taskAsm, $taskTree)) {
    if (-not (Test-Path -LiteralPath $taskDependency -PathType Leaf)) { throw "Missing $taskDependency" }
}
$taskInput = (Resolve-Path -LiteralPath $InputJar).Path
$taskExpected = 'BCF304BB6245CDBB6F48D5888404F8186E4E0895E2DCDAF608C68D53FEADA6E2'
if ((Get-FileHash -LiteralPath $taskInput -Algorithm SHA256).Hash -ne $taskExpected) { throw 'Unexpected x1 source hash' }
$taskBuild = Join-Path $taskWorkspace ('build/' + $BuildTag)
if (Test-Path -LiteralPath $taskBuild) { throw 'Use a fresh build tag' }
$taskSupport = Join-Path $taskBuild 'support'
$taskTools = Join-Path $taskBuild 'tools'
$taskSemantic = Join-Path $taskBuild 'semantic'
$taskHost = Join-Path $taskBuild 'host'
New-Item -ItemType Directory -Path $taskBuild,$taskSupport,$taskTools,$taskSemantic,$taskHost | Out-Null
$taskCandidate = Join-Path $taskBuild 'Hung_By_Son.jar'
$taskCatalog = Join-Path $taskBuild 'percent-symbols.properties'
$taskAsmCp = "$taskAsm;$taskTree"
function Run-Checked {
    param([string]$Program, [string[]]$Arguments, [string]$Log)
    $taskPriorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $taskOutput = & $Program @Arguments 2>&1
        $taskExit = $LASTEXITCODE
        $taskOutput | ForEach-Object { $_.ToString() } | Set-Content -LiteralPath (Join-Path $taskBuild $Log) -Encoding UTF8
    } finally { $ErrorActionPreference = $taskPriorPreference }
    if ($taskExit -ne 0) { $taskOutput | Select-Object -Last 15; throw "$Program failed, see $Log" }
    $taskOutput | Select-Object -Last 1
}
Run-Checked 'javac' @('-encoding','UTF-8','--release','7','-d',$taskSupport,
    'src/luna/api/ExpEstimateStore.java','src/luna/features/ExpEstimateSupport.java',
    'src/luna/ui/ExpPercentInterpolator.java') 'support-compile.txt'
Run-Checked 'javac' @('-encoding','UTF-8','-cp',$taskAsmCp,'-d',$taskTools,
    'tools/luna/HungBySonExpPercentPatcher.java') 'tools-compile.txt'
Run-Checked 'java' @('-Xverify:all','-cp',"$taskTools;$taskAsmCp",'HungBySonExpPercentPatcher',
    $taskInput,$taskSupport,$taskCandidate,'symbols/hung-exp-percent-base-single-20261019.properties',
    $taskCatalog,(Join-Path $taskBuild 'patch-report.txt')) 'patch.txt'
$taskSemanticSources = @(& rg --files src -g '*.java')
Run-Checked 'javac' (@('-encoding','UTF-8','--release','7','-cp',$taskMicro,'-d',$taskSemantic) + $taskSemanticSources) 'semantic-compile.txt'
$taskFixtures = @(& rg --files fixtures/luna -g '*.java') + @(
    'fixtures/hung-by-son/ExpEstimateHostTest.java',
    'fixtures/hung-by-son/ExpPercentInterpolatorHostTest.java',
    'fixtures/hung-by-son/ExpPercentIntegrationHostTest.java')
Run-Checked 'javac' (@('-encoding','UTF-8','--release','7','-cp',"$taskSemantic;$taskMicro",'-d',$taskHost) + $taskFixtures) 'host-compile.txt'
foreach ($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest',
        'AutoCombatHostTest','LoginMaterialHostTest','AutoAttackHostTest')) {
    Run-Checked 'java' @('-Xverify:all','-cp',"$taskSemantic;$taskHost;$taskMicro",$taskTest) ($taskTest + '.txt')
}
# The EXP tests execute the classes embedded in this exact candidate, not the semantic output.
foreach ($taskTest in @('ExpEstimateHostTest','ExpPercentInterpolatorHostTest','ExpPercentIntegrationHostTest')) {
    Run-Checked 'java' @('-Xverify:all','-cp',"$taskCandidate;$taskHost;$taskMicro",$taskTest) ($taskTest + '.txt')
}
Run-Checked 'javac' @('-encoding','UTF-8','-cp',"$taskAsmCp;$taskMicro",'-d',$taskHost,
    'fixtures/hung-by-son/HungBySonExpBinaryTest.java') 'binary-compile.txt'
Run-Checked 'java' @('-Xverify:all','-cp',"$taskHost;$taskAsmCp;$taskMicro",'HungBySonExpBinaryTest',
    $taskCandidate,$taskCatalog,$taskMicro,(Join-Path $taskBuild 'screenshots')) 'exp-native-binary.txt'
& (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskCandidate -DependencyDir $taskDeps `
    -ReportDir (Join-Path $taskBuild 'startup')
if (-not $?) { throw 'Startup smoke failed' }
$taskHash = (Get-FileHash -LiteralPath $taskCandidate -Algorithm SHA256).Hash
$taskProof = [ordered]@{
    Verified=$true; Published=$false; Variant='x1'; Decimals=3; LiveServerTested=$false
    Input=$taskInput; InputSHA256=$taskExpected; Candidate=$taskCandidate; SHA256=$taskHash
    DependencyDir=$taskDeps; Catalog=$taskCatalog; NativeInverseVerified=$true
    AllClassesLinked=$true; NativeExpAndRmsTested=$true; EmulatorStartupSeconds=12
    SemanticTests=@('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest',
        'AutoCombatHostTest','LoginMaterialHostTest','AutoAttackHostTest','ExpEstimateHostTest',
        'ExpPercentInterpolatorHostTest','ExpPercentIntegrationHostTest')
}
$taskProof | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskBuild 'verified.json') -Encoding UTF8
if ($Publish) {
    & (Join-Path $PSScriptRoot 'publish-hung-exp-percent.ps1') -StageBuild $taskBuild
}
Write-Output "VERIFIED=$taskCandidate SHA256=$taskHash"
