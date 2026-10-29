[CmdletBinding()]
param(
    [string]$InputJar = '..\jar\Hung_By_Son.jar',
    [string]$DependencyDir = '',
    [string]$BuildTag = ('hung-auto500-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
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
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid BuildTag' }
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $taskWorkspace
try {
    $taskInput = (Resolve-Path -LiteralPath $InputJar).Path
    if ((Get-FileHash -LiteralPath $taskInput).Hash -ne 'D917DA2442C8AED8A67F4D30BD5EC2F462B3883D0319827ACA684EAF05204005') { throw 'Unexpected 400ms release' }
    if ([string]::IsNullOrWhiteSpace($DependencyDir)) {
        $DependencyDir = if ($env:LUNA_DEPS) { $env:LUNA_DEPS } else { Join-Path $env:LOCALAPPDATA 'Temp/hso-server-list-deps' }
    }
    $taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
    $taskAsm = (Join-Path $taskDeps 'asm-9.7.jar') + ';' + (Join-Path $taskDeps 'asm-tree-9.7.jar')
    $taskMicro = Join-Path $taskDeps 'microemulator.jar'
    foreach ($taskFile in @((Join-Path $taskDeps 'asm-9.7.jar'),(Join-Path $taskDeps 'asm-tree-9.7.jar'),$taskMicro)) {
        if (-not (Test-Path -LiteralPath $taskFile -PathType Leaf)) { throw "Missing dependency $taskFile" }
    }
    $taskBuild = Join-Path $taskWorkspace (Join-Path 'build' $BuildTag)
    if (Test-Path -LiteralPath $taskBuild) { throw 'BuildTag already used' }
    $taskClasses = Join-Path $taskBuild 'classes'; $taskSemantic = Join-Path $taskBuild 'semantic'; $taskHost = Join-Path $taskBuild 'host'
    New-Item -ItemType Directory -Path $taskBuild,$taskClasses,$taskSemantic,$taskHost | Out-Null
    $taskCp = "$taskClasses;$taskAsm;$taskMicro"
    $taskTools = @('tools/luna/HungBySonAutoGatePatcher.java','tools/luna/HungBySonParityJarVerifier.java',
        'tools/legacy/hsosky/HungBySonFullOfflinePatcher.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',"$taskAsm;$taskMicro",'-d',$taskClasses) + $taskTools) (Join-Path $taskBuild 'tools-compile.txt')
    $taskSources = @('fixtures/hung-by-son/HungBySonAutoGateHostTest.java','fixtures/hung-by-son/HungBySonTempoClickHostTest.java',
        'fixtures/hung-by-son/HungBySonNoclipMenuHostTest.java','fixtures/hung-by-son/HungBySonNoclipCollisionHostTest.java',
        'fixtures/hung-by-son/HungBySonFullOfflineHostTest.java',
        'fixtures/hung-by-son/HungBySonEnhancementWaitRegressionTest.java','fixtures/hung-by-son/HungBySonEnhancementNpcSessionTest.java',
        'fixtures/hung-by-son/TradeLocalTest.java')
    Run-Checked javac (@('-encoding','UTF-8','-cp',$taskCp,'-d',$taskClasses) + $taskSources) (Join-Path $taskBuild 'compile.txt')
    $taskOutput = Join-Path $taskBuild 'Hung_By_Son.jar'; $taskCatalog = Join-Path $taskBuild 'combat.properties'
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonAutoGatePatcher',$taskInput,$taskOutput,
        'symbols/hung-bachho-combat-20261004.properties',$taskCatalog) (Join-Path $taskBuild 'patch.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonAutoGateHostTest',$taskInput,$taskOutput,$taskCatalog,$taskMicro) (Join-Path $taskBuild 'auto-gate-test.txt')
    $taskOutputHash = (Get-FileHash -LiteralPath $taskOutput).Hash
    $taskChanged = (Get-Content $taskCatalog | Where-Object { $_ -match '^changed\.classes=' }).Substring('changed.classes='.Length)
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonParityJarVerifier',$taskInput,$taskOutput,$taskMicro,
        (Join-Path $taskBuild 'manifest.txt'),$taskChanged) (Join-Path $taskBuild 'archive-verification.txt')
    $taskNoclipCatalog = Join-Path $taskBuild 'noclip-validation.properties'
    $taskNoclipText = [IO.File]::ReadAllText('symbols/hung-noclip-20261004.properties')
    $taskNoclipText = [regex]::Replace($taskNoclipText,'(?m)^(noclip\.)?output\.sha256=[^\r\n]*', ('${1}output.sha256=' + $taskOutputHash))
    [IO.File]::WriteAllText($taskNoclipCatalog,$taskNoclipText,[Text.UTF8Encoding]::new($false))
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonTempoClickHostTest',$taskOutput,$taskNoclipCatalog) (Join-Path $taskBuild 'tempo-clicks.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonNoclipMenuHostTest',$taskOutput,$taskNoclipCatalog) (Join-Path $taskBuild 'noclip-menu.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonNoclipCollisionHostTest',
        '../jar/backup/Hung_By_Son-before-noclip-menu-20261004.jar',$taskOutput,$taskNoclipCatalog) (Join-Path $taskBuild 'noclip-collision.txt')
    foreach ($taskTest in @('HungBySonFullOfflineHostTest','HungBySonEnhancementWaitRegressionTest','HungBySonEnhancementNpcSessionTest','TradeLocalTest')) {
        $taskArguments = @('-Xverify:all','-cp',$taskCp,$taskTest,$taskOutput)
        if ($taskTest -eq 'TradeLocalTest') { $taskArguments += $taskMicro }
        Run-Checked java $taskArguments (Join-Path $taskBuild "$taskTest.txt")
    }
    $taskSemanticSources = @(rg --files src -g '*.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-d',$taskSemantic) + $taskSemanticSources) (Join-Path $taskBuild 'semantic-compile.txt')
    $taskLunaFixtures = @(rg --files fixtures/luna -g '*.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',$taskSemantic,'-d',$taskHost) + $taskLunaFixtures) (Join-Path $taskBuild 'host-compile.txt')
    foreach ($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoCombatHostTest','LoginMaterialHostTest')) {
        Run-Checked java @('-Xverify:all','-cp',"$taskSemantic;$taskHost",$taskTest) (Join-Path $taskBuild "$taskTest.txt")
    }
    & (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskOutput -DependencyDir $taskDeps -ReportDir (Join-Path $taskBuild 'smoke') | Tee-Object -FilePath (Join-Path $taskBuild 'emulator-smoke.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Emulator startup failed' }
    Write-Output "VERIFIED=$taskOutput; SHA256=$taskOutputHash"
} finally { Pop-Location }
