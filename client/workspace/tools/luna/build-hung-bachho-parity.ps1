[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$InputJar,
    [Parameter(Mandatory=$true)][string]$ReferenceJar,
    [Parameter(Mandatory=$true)][string]$DependencyDir,
    [string]$BuildTag = ('hung-bachho-parity-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss'))
)
$ErrorActionPreference = 'Stop'
function Run-Checked {
    param([string]$Program, [string[]]$Arguments, [string]$Log)
    & $Program @Arguments | Tee-Object -FilePath $Log
    if ($LASTEXITCODE -ne 0) { throw "$Program failed: $LASTEXITCODE" }
}
function Read-ChangedClasses {
    param([string]$Catalog)
    $taskLine = @(Get-Content -LiteralPath $Catalog | Where-Object { $_ -match '^changed\.classes=' })
    if ($taskLine.Count -ne 1) { throw "Missing modification scope: $Catalog" }
    return $taskLine[0].Substring('changed.classes='.Length).Split(',')
}
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid BuildTag' }
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $taskWorkspace
try {
    $taskInput = (Resolve-Path -LiteralPath $InputJar).Path
    $taskReference = (Resolve-Path -LiteralPath $ReferenceJar).Path
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskInput).Hash -ne 'EA401D771635723AD2EE2BF3EB5FE9A7D5F490559DE6F97983BD1F107A5659AC') {
        throw 'Unexpected Hung input; use the audited pre-parity backup'
    }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskReference).Hash -ne '2BE9C4DB203EBAD94DB20B21D1F3DD0F4B611C90756D738DF44272BF2C2CF840') { throw 'Unexpected Bạch Hổ reference' }
    $taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
    $taskAsm = (Join-Path $taskDeps 'asm-9.7.jar') + ';' + (Join-Path $taskDeps 'asm-tree-9.7.jar')
    $taskMicro = Join-Path $taskDeps 'microemulator.jar'
    foreach ($taskFile in @((Join-Path $taskDeps 'asm-9.7.jar'),(Join-Path $taskDeps 'asm-tree-9.7.jar'),$taskMicro)) {
        if (-not (Test-Path -LiteralPath $taskFile -PathType Leaf)) { throw "Missing dependency: $taskFile" }
    }
    $taskBuild = Join-Path $taskWorkspace (Join-Path 'build' $BuildTag)
    if (Test-Path -LiteralPath $taskBuild) { throw 'BuildTag already used' }
    $taskClasses = Join-Path $taskBuild 'classes'
    $taskSemantic = Join-Path $taskBuild 'semantic'
    $taskHost = Join-Path $taskBuild 'host'
    New-Item -ItemType Directory -Path $taskBuild,$taskClasses,$taskSemantic,$taskHost | Out-Null
    $taskSources = @('tools/luna/HungBySonBachHoTempoPatcher.java','tools/luna/HungBySonBachHoCombatPatcher.java',
        'tools/luna/HungBySonParityJarVerifier.java','tools/luna/HungBySonTempoMenuFixPatcher.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',"$taskAsm;$taskMicro",'-d',$taskClasses) + $taskSources) (Join-Path $taskBuild 'compile.txt')
    Run-Checked javac @('-encoding','UTF-8','-cp',"$taskAsm;$taskMicro",'-d',$taskClasses,
        'fixtures/hung-by-son/HungBySonBachHoParityHostTest.java','fixtures/hung-by-son/HungBySonTempoClickHostTest.java') (Join-Path $taskBuild 'fixture-compile.txt')
    $taskTempo = Join-Path $taskBuild 'tempo.jar'
    $taskOutput = Join-Path $taskBuild 'Hung_By_Son.jar'
    $taskCombatStage = Join-Path $taskBuild 'combat.jar'
    $taskTempoCatalog = Join-Path $taskBuild 'tempo.properties'
    $taskCombatCatalog = Join-Path $taskBuild 'combat.properties'
    $taskFixedTempoCatalog = Join-Path $taskBuild 'tempo-fixed.properties'
    Run-Checked java @('-Xverify:all','-cp',"$taskClasses;$taskAsm",'HungBySonBachHoTempoPatcher',
        $taskInput,$taskReference,$taskTempo,(Join-Path $taskBuild 'tempo-report.txt'),$taskTempoCatalog) (Join-Path $taskBuild 'tempo-build.txt')
    Run-Checked java @('-Xverify:all','-cp',"$taskClasses;$taskAsm",'HungBySonBachHoCombatPatcher',
        $taskTempo,$taskReference,$taskCombatStage,$taskCombatCatalog,(Join-Path $taskBuild 'combat-report.txt')) (Join-Path $taskBuild 'combat-build.txt')
    Run-Checked java @('-Xverify:all','-cp',"$taskClasses;$taskAsm",'HungBySonTempoMenuFixPatcher',
        $taskCombatStage,$taskTempoCatalog,$taskOutput,$taskFixedTempoCatalog,(Join-Path $taskBuild 'tempo-fix-report.txt')) (Join-Path $taskBuild 'tempo-fix-build.txt')
    Run-Checked java @('-Xverify:all','-cp',"$taskClasses;$taskAsm;$taskMicro",'HungBySonBachHoParityHostTest',
        $taskReference,$taskOutput,$taskDeps,$taskCombatCatalog,$taskFixedTempoCatalog) (Join-Path $taskBuild 'differential-tests.txt')
    Run-Checked java @('-Xverify:all','-cp',"$taskClasses;$taskAsm;$taskMicro",'HungBySonTempoClickHostTest',
        $taskOutput,$taskFixedTempoCatalog) (Join-Path $taskBuild 'native-menu-clicks.txt')
    $taskAllowed = @((Read-ChangedClasses $taskTempoCatalog) + (Read-ChangedClasses $taskCombatCatalog) + (Read-ChangedClasses $taskFixedTempoCatalog) | Select-Object -Unique)
    Run-Checked java (@('-Xverify:all','-cp',"$taskClasses;$taskAsm",'HungBySonParityJarVerifier',
        $taskInput,$taskOutput,$taskMicro,(Join-Path $taskBuild 'manifest.txt')) + $taskAllowed) (Join-Path $taskBuild 'archive-verification.txt')
    $taskSemanticSources = @(rg --files src -g '*.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-d',$taskSemantic) + $taskSemanticSources) (Join-Path $taskBuild 'semantic-compile.txt')
    $taskFixtures = @(rg --files fixtures/luna -g '*.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',$taskSemantic,'-d',$taskHost) + $taskFixtures) (Join-Path $taskBuild 'host-compile.txt')
    foreach ($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoCombatHostTest','LoginMaterialHostTest')) {
        Run-Checked java @('-Xverify:all','-cp',"$taskSemantic;$taskHost",$taskTest) (Join-Path $taskBuild "$taskTest.txt")
    }
    $taskRegressionSources = @('tools/legacy/hsosky/HungBySonFullOfflinePatcher.java',
        'fixtures/hung-by-son/HungBySonFullOfflineHostTest.java',
        'fixtures/hung-by-son/HungBySonEnhancementWaitRegressionTest.java',
        'fixtures/hung-by-son/HungBySonEnhancementNpcSessionTest.java','fixtures/hung-by-son/TradeLocalTest.java')
    Run-Checked javac (@('-encoding','UTF-8','-cp',"$taskAsm;$taskMicro",'-d',$taskClasses) + $taskRegressionSources) (Join-Path $taskBuild 'integration-compile.txt')
    foreach ($taskTest in @('HungBySonFullOfflineHostTest','HungBySonEnhancementWaitRegressionTest','HungBySonEnhancementNpcSessionTest','TradeLocalTest')) {
        $taskRegressionArgs = @('-Xverify:all','-cp',"$taskClasses;$taskAsm;$taskMicro",$taskTest,$taskOutput)
        if ($taskTest -eq 'TradeLocalTest') { $taskRegressionArgs += $taskMicro }
        Run-Checked java $taskRegressionArgs (Join-Path $taskBuild "$taskTest.txt")
    }
    & (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskOutput -DependencyDir $taskDeps -ReportDir (Join-Path $taskBuild 'smoke')
    Write-Output "Verified staged JAR: $taskOutput"
} finally { Pop-Location }
