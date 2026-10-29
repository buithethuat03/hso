[CmdletBinding()]
param(
    [string]$InputJar = '..\jar\Hung_By_Son.jar',
    [string]$DependencyDir = '',
    [string]$BuildTag = ('hung-noclip-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
)
$ErrorActionPreference = 'Stop'
function Run-Checked {
    param([string]$Program, [string[]]$Arguments, [string]$Log)
    $taskPriorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Program @Arguments 2>&1 | ForEach-Object { $_.ToString() } | Tee-Object -FilePath $Log
        $taskExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $taskPriorPreference }
    if ($taskExit -ne 0) { throw "$Program failed: $taskExit" }
}
function Get-CatalogValue {
    param([string]$Text, [string]$Key)
    $taskMatches = [regex]::Matches($Text, '(?m)^' + [regex]::Escape($Key) + '=([^\r\n]*)')
    if ($taskMatches.Count -ne 1) { throw "Expected one $Key property" }
    return $taskMatches[0].Groups[1].Value
}
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid BuildTag' }
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $taskWorkspace
try {
    $taskInput = (Resolve-Path -LiteralPath $InputJar).Path
    $taskInputHash = (Get-FileHash -LiteralPath $taskInput -Algorithm SHA256).Hash
    if ($taskInputHash -ne '97DB01AD7E5550D4B8B5D747C7D0F870CF21972F03E9ABBEAB757D2DC3AEDACC') { throw 'Unexpected input release hash' }
    if ([string]::IsNullOrWhiteSpace($DependencyDir)) {
        $DependencyDir = if ($env:LUNA_DEPS) { $env:LUNA_DEPS } else { Join-Path $env:LOCALAPPDATA 'Temp/hso-server-list-deps' }
    }
    $taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
    $taskAsm = (Join-Path $taskDeps 'asm-9.7.jar') + ';' + (Join-Path $taskDeps 'asm-tree-9.7.jar')
    $taskMicro = Join-Path $taskDeps 'microemulator.jar'
    foreach ($taskFile in @((Join-Path $taskDeps 'asm-9.7.jar'),(Join-Path $taskDeps 'asm-tree-9.7.jar'),$taskMicro)) {
        if (-not (Test-Path -LiteralPath $taskFile -PathType Leaf)) { throw "Missing dependency $taskFile" }
    }
    $taskJava = (Get-Command java).Source; $taskJavac = (Get-Command javac).Source
    $taskBuild = Join-Path $taskWorkspace (Join-Path 'build' $BuildTag)
    if (Test-Path -LiteralPath $taskBuild) { throw 'BuildTag already used' }
    $taskClasses = Join-Path $taskBuild 'classes'; $taskSemantic = Join-Path $taskBuild 'semantic'; $taskHost = Join-Path $taskBuild 'host'
    New-Item -ItemType Directory -Path $taskBuild,$taskClasses,$taskSemantic,$taskHost | Out-Null
    Run-Checked $taskJavac @('-encoding','UTF-8','--release','7','-cp',$taskAsm,'-d',$taskClasses,
        'tools/luna/HungBySonNoclipPatcher.java','tools/luna/HungBySonNoclipMenuPatch.java','tools/luna/HungBySonParityJarVerifier.java') (Join-Path $taskBuild 'patcher-compile.txt')
    $taskOutput = Join-Path $taskBuild 'Hung_By_Son.jar'; $taskCatalog = Join-Path $taskBuild 'noclip.properties'
    $taskTempoSource = 'symbols/hung-tempo-menu-fixed-20261004.properties'
    Run-Checked $taskJava @('-Xverify:all','-cp',"$taskClasses;$taskAsm",'HungBySonNoclipPatcher',
        $taskInput,$taskTempoSource,$taskOutput,$taskCatalog,(Join-Path $taskBuild 'patch-report.txt')) (Join-Path $taskBuild 'patch-build.txt')
    $taskOutputHash = (Get-FileHash -LiteralPath $taskOutput -Algorithm SHA256).Hash
    $taskCatalogText = [IO.File]::ReadAllText($taskCatalog)
    if ((Get-CatalogValue $taskCatalogText 'noclip.output.sha256') -ne $taskOutputHash) { throw 'Candidate/catalog hash mismatch' }
    $taskAllowed = (Get-CatalogValue $taskCatalogText 'noclip.changed.classes').Split(',')
    if ($taskAllowed.Count -ne 3 -or $taskAllowed[0] -ne 'cs') { throw 'Unexpected changed-class scope' }
    Run-Checked $taskJavac @('-encoding','UTF-8','-cp',"$taskClasses;$taskAsm;$taskMicro",'-d',$taskClasses,
        'fixtures/hung-by-son/HungBySonNoclipCollisionHostTest.java','fixtures/hung-by-son/HungBySonNoclipMenuHostTest.java',
        'fixtures/hung-by-son/HungBySonTempoClickHostTest.java') (Join-Path $taskBuild 'fixture-compile.txt')
    $taskCp = "$taskClasses;$taskAsm;$taskMicro"
    Run-Checked $taskJava @('-Xverify:all','-cp',$taskCp,'HungBySonNoclipCollisionHostTest',$taskInput,$taskOutput,$taskCatalog) (Join-Path $taskBuild 'collision-host-test.txt')
    Run-Checked $taskJava @('-Xverify:all','-cp',$taskCp,'HungBySonNoclipMenuHostTest',$taskOutput,$taskCatalog) (Join-Path $taskBuild 'noclip-menu-test.txt')
    $taskTempoText = [IO.File]::ReadAllText($taskTempoSource)
    $taskTempoText = [regex]::Replace($taskTempoText,'(?m)^output\.sha256=[^\r\n]*',"output.sha256=$taskOutputHash")
    $taskTempoText += "`nvalidation.inputsha=$taskInputHash`n"
    $taskTempoValidation = Join-Path $taskBuild 'tempo-validation.properties'
    [IO.File]::WriteAllText($taskTempoValidation,$taskTempoText,[Text.UTF8Encoding]::new($false))
    Run-Checked $taskJava @('-Xverify:all','-cp',$taskCp,'HungBySonTempoClickHostTest',$taskOutput,$taskTempoValidation) (Join-Path $taskBuild 'native-tempo-clicks.txt')
    Run-Checked $taskJava (@('-Xverify:all','-cp',$taskCp,'HungBySonParityJarVerifier',$taskInput,$taskOutput,$taskMicro,(Join-Path $taskBuild 'manifest.txt')) + $taskAllowed) (Join-Path $taskBuild 'archive-verification.txt')
    $taskSemanticSources = @(rg --files src -g '*.java')
    Run-Checked $taskJavac (@('-encoding','UTF-8','--release','7','-d',$taskSemantic) + $taskSemanticSources) (Join-Path $taskBuild 'semantic-compile.txt')
    $taskLunaFixtures = @(rg --files fixtures/luna -g '*.java')
    Run-Checked $taskJavac (@('-encoding','UTF-8','--release','7','-cp',$taskSemantic,'-d',$taskHost) + $taskLunaFixtures) (Join-Path $taskBuild 'host-compile.txt')
    foreach ($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoCombatHostTest','LoginMaterialHostTest')) {
        Run-Checked $taskJava @('-Xverify:all','-cp',"$taskSemantic;$taskHost",$taskTest) (Join-Path $taskBuild "$taskTest.txt")
    }
    Run-Checked $taskJavac @('-encoding','UTF-8','-cp',"$taskAsm;$taskMicro",'-d',$taskClasses,
        'tools/legacy/hsosky/HungBySonFullOfflinePatcher.java','fixtures/hung-by-son/HungBySonFullOfflineHostTest.java',
        'fixtures/hung-by-son/HungBySonEnhancementWaitRegressionTest.java','fixtures/hung-by-son/HungBySonEnhancementNpcSessionTest.java',
        'fixtures/hung-by-son/TradeLocalTest.java') (Join-Path $taskBuild 'integration-compile.txt')
    foreach ($taskTest in @('HungBySonFullOfflineHostTest','HungBySonEnhancementWaitRegressionTest','HungBySonEnhancementNpcSessionTest','TradeLocalTest')) {
        $taskArguments = @('-Xverify:all','-cp',$taskCp,$taskTest,$taskOutput)
        if ($taskTest -eq 'TradeLocalTest') { $taskArguments += $taskMicro }
        Run-Checked $taskJava $taskArguments (Join-Path $taskBuild "$taskTest.txt")
    }
    & (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskOutput -DependencyDir $taskDeps -ReportDir (Join-Path $taskBuild 'smoke') | Tee-Object -FilePath (Join-Path $taskBuild 'emulator-smoke.txt')
    if ($LASTEXITCODE -ne 0) { throw "Emulator startup failed: $LASTEXITCODE" }
    Write-Output "VERIFIED=$taskOutput; SHA256=$taskOutputHash"
} finally { Pop-Location }
