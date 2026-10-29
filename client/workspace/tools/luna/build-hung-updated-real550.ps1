[CmdletBinding()]
param(
    [string]$InputJar = '..\jar\backup\Hung_By_Son_updated-before-real550-20261006.jar',
    [string]$DependencyDir = '',
    [string]$BuildTag = ('hung-updated-real550-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
)
$ErrorActionPreference = 'Stop'
function Run-Checked {
    param([string]$Program,[string[]]$Arguments,[string]$Log)
    $taskPrevious = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $taskMessages = & $Program @Arguments 2>&1
        $taskExit = $LASTEXITCODE
        $taskMessages | ForEach-Object { $_.ToString() } | Set-Content -Encoding UTF8 -LiteralPath $Log
    } finally { $ErrorActionPreference = $taskPrevious }
    if ($taskExit -ne 0) { Get-Content -LiteralPath $Log -Tail 15; throw "$Program failed ($taskExit)" }
    $taskMessages | Select-Object -Last 1
}
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid BuildTag' }
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $taskWorkspace
try {
    $taskInput = (Resolve-Path -LiteralPath $InputJar).Path
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskInput).Hash -ne '2AF569F85FE379BD18BF770C55799CE95CAA25796F60CC13B0E36AAFA42B92E2') { throw 'Unexpected pre-real550 Updated JAR' }
    if ([string]::IsNullOrWhiteSpace($DependencyDir)) { $DependencyDir = if ($env:LUNA_DEPS) { $env:LUNA_DEPS } else { Join-Path $env:LOCALAPPDATA 'Temp/hso-server-list-deps' } }
    $taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
    $taskAsm = (Join-Path $taskDeps 'asm-9.7.jar') + ';' + (Join-Path $taskDeps 'asm-tree-9.7.jar')
    $taskMicro = Join-Path $taskDeps 'microemulator.jar'
    $taskBuild = Join-Path $taskWorkspace (Join-Path 'build' $BuildTag)
    if (Test-Path -LiteralPath $taskBuild) { throw 'BuildTag already used' }
    $taskTools = Join-Path $taskBuild 'tools'; $taskHost = Join-Path $taskBuild 'host'; $taskSupport = Join-Path $taskBuild 'support'
    $taskSemantic = Join-Path $taskBuild 'semantic'; $taskSemanticHost = Join-Path $taskBuild 'semantic-host'
    New-Item -ItemType Directory -Path $taskTools,$taskHost,$taskSupport,$taskSemantic,$taskSemanticHost | Out-Null
    Run-Checked javac @('-encoding','UTF-8','--release','7','-cp',$taskAsm,'-d',$taskTools,
        'tools/luna/HungBySonNativeTimingPatcher.java','tools/luna/HungBySonRealTimeCombatPatcher.java',
        'tools/luna/HungBySonParityJarVerifier.java','tools/luna/HungBySonInvestigatePatcher.java',
        'tools/luna/HungBySonMonsterSelectionPatch.java','tools/luna/HungBySonStaleTargetPatch.java',
        'tools/legacy/hsosky/HungBySonFullOfflinePatcher.java') (Join-Path $taskBuild 'tools-compile.txt')
    $taskOutput = Join-Path $taskBuild 'Hung_By_Son_updated.jar'; $taskTiming = Join-Path $taskBuild 'real550.properties'
    Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskAsm",'HungBySonRealTimeCombatPatcher',
        $taskInput,'symbols/hung-investigate-match-update-20261006.properties',$taskOutput,$taskTiming) (Join-Path $taskBuild 'patch.txt')
    $taskHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskOutput).Hash
    # Share only support/API/storage classes; native access bridges remain in each fixture's isolated loader.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $taskZip = [IO.Compression.ZipFile]::OpenRead($taskOutput)
    try {
        foreach ($taskEntry in $taskZip.Entries) {
            if (-not $taskEntry.FullName.StartsWith('luna/') -or -not $taskEntry.FullName.EndsWith('.class')) { continue }
            if ($taskEntry.FullName.StartsWith('luna/generated/') -and $taskEntry.FullName -ne 'luna/generated/InvestigateStorage.class') { continue }
            $taskDestination = Join-Path $taskSupport $taskEntry.FullName
            New-Item -ItemType Directory -Path (Split-Path -Parent $taskDestination) -Force | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($taskEntry,$taskDestination,$false)
        }
    } finally { $taskZip.Dispose() }
    $taskFixtureNames = @('HungBySonRealTimeCombatHostTest','HungBySonMonsterCacheHostTest','HungBySonMonsterSelectionHostTest',
        'HungBySonInvestigateBinaryTest','HungBySonMonsterSelectionBinaryTest','HungBySonInvestigateHudBinaryTest',
        'HungBySonMonsterRecoveryBinaryTest','HungBySonStaleTargetHostTest','HungBySonStaleTargetBinaryTest',
        'HungBySonTempoClickHostTest','HungBySonNoclipMenuHostTest','HungBySonNoclipCollisionHostTest',
        'HungBySonFullOfflineHostTest','HungBySonEnhancementWaitRegressionTest','HungBySonEnhancementNpcSessionTest','TradeLocalTest')
    $taskFixtureFiles = @($taskFixtureNames | ForEach-Object { "fixtures/hung-by-son/$_.java" })
    $taskCp = "$taskHost;$taskSupport;$taskTools;$taskAsm;$taskMicro"
    Run-Checked javac (@('-encoding','UTF-8','-cp',"$taskCp;$taskOutput",'-d',$taskHost) + $taskFixtureFiles) (Join-Path $taskBuild 'fixtures-compile.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonRealTimeCombatHostTest',
        $taskInput,$taskOutput,$taskTiming,'symbols/hung-tempo-menu-fixed-20261004.properties',$taskMicro) (Join-Path $taskBuild 'real550-test.txt')
    $taskAllowed = (Get-Content $taskTiming | Where-Object { $_ -match '^changed\.classes=' }).Substring('changed.classes='.Length).Split(',')
    Run-Checked java (@('-Xverify:all','-cp',"$taskTools;$taskAsm",'HungBySonParityJarVerifier',
        $taskInput,$taskOutput,$taskMicro,(Join-Path $taskBuild 'boundary.txt')) + $taskAllowed) (Join-Path $taskBuild 'boundary-log.txt')
    $taskValidation = Join-Path $taskBuild 'investigate-validation.properties'
    $taskText = [IO.File]::ReadAllText('symbols/hung-investigate-long-session-20261006.properties')
    $taskText = [regex]::Replace($taskText,'(?m)^output\.sha256=[^\r\n]*',('output.sha256='+$taskHash))
    $taskText = [regex]::Replace($taskText,'(?m)^auto\.delay\.ms=[^\r\n]*','auto.delay.ms=550')
    [IO.File]::WriteAllText($taskValidation,$taskText,[Text.UTF8Encoding]::new($false))
    $taskNoclip = Join-Path $taskBuild 'noclip-validation.properties'
    $taskText = [IO.File]::ReadAllText('symbols/hung-noclip-20261004.properties')
    $taskText = [regex]::Replace($taskText,'(?m)^(noclip\.)?output\.sha256=[^\r\n]*',('${1}output.sha256='+$taskHash))
    [IO.File]::WriteAllText($taskNoclip,$taskText,[Text.UTF8Encoding]::new($false))
    foreach ($taskTest in @('HungBySonMonsterCacheHostTest','HungBySonMonsterSelectionHostTest','HungBySonStaleTargetHostTest')) {
        Run-Checked java @('-Xverify:all','-cp',$taskCp,$taskTest,$taskOutput) (Join-Path $taskBuild "$taskTest.txt")
    }
    foreach ($taskTest in @('HungBySonMonsterSelectionBinaryTest','HungBySonInvestigateHudBinaryTest','HungBySonMonsterRecoveryBinaryTest','HungBySonStaleTargetBinaryTest')) {
        Run-Checked java @('-Xverify:all','-cp',$taskCp,$taskTest,$taskOutput,$taskValidation,$taskMicro) (Join-Path $taskBuild "$taskTest.txt")
    }
    foreach ($taskTest in @('HungBySonTempoClickHostTest','HungBySonNoclipMenuHostTest')) {
        Run-Checked java @('-Xverify:all','-cp',$taskCp,$taskTest,$taskOutput,$taskNoclip) (Join-Path $taskBuild "$taskTest.txt")
    }
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'HungBySonNoclipCollisionHostTest',
        '../jar/backup/Hung_By_Son-before-noclip-menu-20261004.jar',$taskOutput,$taskNoclip) (Join-Path $taskBuild 'collision.txt')
    foreach ($taskTest in @('HungBySonFullOfflineHostTest','HungBySonEnhancementWaitRegressionTest','HungBySonEnhancementNpcSessionTest','TradeLocalTest')) {
        $taskArguments = @('-Xverify:all','-cp',$taskCp,$taskTest,$taskOutput)
        if ($taskTest -eq 'HungBySonFullOfflineHostTest') { $taskArguments += '242' }
        if ($taskTest -eq 'TradeLocalTest') { $taskArguments += $taskMicro }
        Run-Checked java $taskArguments (Join-Path $taskBuild "$taskTest.txt")
    }
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-d',$taskSemantic) + @(rg --files src -g '*.java')) (Join-Path $taskBuild 'semantic-compile.txt')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',$taskSemantic,'-d',$taskSemanticHost) + @(rg --files fixtures/luna -g '*.java')) (Join-Path $taskBuild 'semantic-fixtures-compile.txt')
    foreach ($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoCombatHostTest','LoginMaterialHostTest')) {
        Run-Checked java @('-Xverify:all','-cp',"$taskSemantic;$taskSemanticHost",$taskTest) (Join-Path $taskBuild "$taskTest.txt")
    }
    & (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskOutput -DependencyDir $taskDeps -ReportDir (Join-Path $taskBuild 'smoke')
    [ordered]@{Verified=$true;Candidate=$taskOutput;SHA256=$taskHash;InputSHA256=(Get-FileHash -Algorithm SHA256 -LiteralPath $taskInput).Hash;LiveServerTested=$false} |
        ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'verified.json')
    Write-Output "VERIFIED=$taskOutput; SHA256=$taskHash"
} finally { Pop-Location }
