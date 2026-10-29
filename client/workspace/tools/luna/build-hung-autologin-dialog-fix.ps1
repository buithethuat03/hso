[CmdletBinding()]
param(
    [string]$InputJar = '..\jar\Hung_By_Son.jar',
    [string]$DependencyDir = '',
    [string]$BuildTag = ('hung-autologin-dialog-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
)

$ErrorActionPreference = 'Stop'

function Run-Checked {
    param([string]$Program, [string[]]$Arguments, [string]$Log)
    $taskPreviousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Program @Arguments 2>&1 | ForEach-Object { $_.ToString() } | Tee-Object -FilePath $Log
        $taskExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $taskPreviousPreference }
    if ($taskExit -ne 0) { throw "$Program failed: $taskExit" }
}

function Get-PaddedObfuscatedName {
    param([string]$Suffix)
    if ($Suffix.Length -gt 100) { throw 'Obfuscated member suffix is longer than 100 characters' }
    return $Suffix.PadLeft(100, [char]'i')
}

function Get-PropertyValue {
    param([string]$Text, [string]$Key)
    $taskPattern = '(?m)^' + [regex]::Escape($Key) + '=([^\r\n]*)'
    $taskMatches = [regex]::Matches($Text, $taskPattern)
    if ($taskMatches.Count -ne 1) { throw "Expected exactly one $Key property" }
    return $taskMatches[0].Groups[1].Value
}

if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid BuildTag' }

$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $taskWorkspace
try {
    $taskInput = (Resolve-Path -LiteralPath $InputJar).Path
    $taskExpectedInputHash = 'D6F2489636C66EAE3AD1A959BB6FF4E428EE4BD16740EE2BBE22D575354DCFE1'
    $taskInputHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskInput).Hash
    if ($taskInputHash -ne $taskExpectedInputHash) {
        throw "Unexpected Hung input SHA-256: $taskInputHash"
    }

    if ([string]::IsNullOrWhiteSpace($DependencyDir)) {
        if (-not [string]::IsNullOrWhiteSpace($env:LUNA_DEPS)) {
            $DependencyDir = $env:LUNA_DEPS
        } elseif (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
            $DependencyDir = Join-Path $env:LOCALAPPDATA 'Temp\hso-server-list-deps'
        } else {
            throw 'Pass -DependencyDir or set LUNA_DEPS'
        }
    }
    $taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
    $taskAsmJar = Join-Path $taskDeps 'asm-9.7.jar'
    $taskAsmTreeJar = Join-Path $taskDeps 'asm-tree-9.7.jar'
    $taskMicro = Join-Path $taskDeps 'microemulator.jar'
    foreach ($taskDependency in @($taskAsmJar, $taskAsmTreeJar, $taskMicro)) {
        if (-not (Test-Path -LiteralPath $taskDependency -PathType Leaf)) {
            throw "Missing dependency: $taskDependency"
        }
    }

    $taskJava = (Get-Command java -ErrorAction Stop).Source
    $taskJavac = (Get-Command javac -ErrorAction Stop).Source
    $taskAsm = $taskAsmJar + ';' + $taskAsmTreeJar

    $taskBuild = Join-Path $taskWorkspace (Join-Path 'build' $BuildTag)
    if (Test-Path -LiteralPath $taskBuild) { throw 'BuildTag already used' }
    $taskClasses = Join-Path $taskBuild 'classes'
    $taskSemantic = Join-Path $taskBuild 'semantic'
    $taskHost = Join-Path $taskBuild 'host'
    New-Item -ItemType Directory -Path $taskBuild,$taskClasses,$taskSemantic,$taskHost | Out-Null

    $taskPatcherSources = @(
        'tools/luna/HungBySonAutoLoginDialogPatcher.java',
        'tools/luna/HungBySonParityJarVerifier.java'
    )
    Run-Checked $taskJavac (@('-encoding','UTF-8','--release','7','-cp',"$taskAsm;$taskMicro",'-d',$taskClasses) + $taskPatcherSources) `
        (Join-Path $taskBuild 'patcher-compile.txt')
    $taskHostSources = @(
        'fixtures/hung-by-son/HungBySonAutoLoginDialogHostTest.java',
        'fixtures/hung-by-son/HungBySonTempoClickHostTest.java'
    )
    Run-Checked $taskJavac (@('-encoding','UTF-8','-cp',"$taskClasses;$taskAsm;$taskMicro",'-d',$taskClasses) + $taskHostSources) `
        (Join-Path $taskBuild 'fixture-compile.txt')

    $taskOutput = Join-Path $taskBuild 'Hung_By_Son.jar'
    $taskDialogCatalog = Join-Path $taskBuild 'autologin-dialog.properties'
    $taskDialogReport = Join-Path $taskBuild 'autologin-dialog-report.txt'
    Run-Checked $taskJava @('-Xverify:all','-cp',"$taskClasses;$taskAsm",'HungBySonAutoLoginDialogPatcher',
        $taskInput,$taskOutput,$taskDialogCatalog,$taskDialogReport) (Join-Path $taskBuild 'patcher-build.txt')
    $taskOutputHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskOutput).Hash
    if ((Get-PropertyValue ([IO.File]::ReadAllText($taskDialogCatalog)) 'input.sha256') -ne $taskExpectedInputHash) {
        throw 'Patcher catalog input hash does not match the pinned release'
    }
    if ((Get-PropertyValue ([IO.File]::ReadAllText($taskDialogCatalog)) 'output.sha256') -ne $taskOutputHash) {
        throw 'Patcher catalog output hash does not match the staged JAR'
    }

    $taskAllowedClass = Get-PaddedObfuscatedName 'lliiiill'
    if ((Get-PropertyValue ([IO.File]::ReadAllText($taskDialogCatalog)) 'changed.classes') -ne $taskAllowedClass) {
        throw 'Auto Login patcher changed-class scope is not the single audited class'
    }

    Run-Checked $taskJava @('-Xverify:all','-cp',"$taskClasses;$taskAsm;$taskMicro",'HungBySonAutoLoginDialogHostTest',
        $taskInput,$taskOutput,$taskMicro,(Join-Path $taskBuild 'autologin-dialog-host-test.txt')) `
        (Join-Path $taskBuild 'autologin-dialog-host-test.log')

    $taskTempoSourcePath = Join-Path $taskWorkspace 'symbols/hung-tempo-menu-fixed-20261004.properties'
    $taskTempoSource = [IO.File]::ReadAllText($taskTempoSourcePath, [Text.Encoding]::UTF8)
    if ((Get-PropertyValue $taskTempoSource 'input.sha256') -ne '5878D588F1CED165EE7045AAB656EBECD043FAA64556CC4CD317387D44C4727C') {
        throw 'Unexpected native tempo-click fixture provenance'
    }
    if ((Get-PropertyValue $taskTempoSource 'output.sha256') -ne $taskExpectedInputHash) {
        throw 'Tempo-click source catalog is not pinned to this Auto Login input'
    }
    if ([regex]::Matches($taskTempoSource, '(?m)^validation\.inputsha=').Count -ne 0) {
        throw 'Source tempo catalog already contains validation.inputsha'
    }
    $taskOutputProperty = [regex]::new('(?m)^output\.sha256=[^\r\n]*')
    if ($taskOutputProperty.Matches($taskTempoSource).Count -ne 1) {
        throw 'Expected exactly one output.sha256 line in the tempo-click source catalog'
    }
    $taskTempoValidation = $taskOutputProperty.Replace($taskTempoSource, "output.sha256=$taskOutputHash", 1)
    $taskLineEnding = if ($taskTempoValidation.Contains("`r`n")) { "`r`n" } else { "`n" }
    if (-not $taskTempoValidation.EndsWith("`n")) { $taskTempoValidation += $taskLineEnding }
    $taskTempoValidation += "validation.inputsha=$taskInputHash" + $taskLineEnding
    $taskTempoValidationPath = Join-Path $taskBuild 'tempo-validation.properties'
    [IO.File]::WriteAllText($taskTempoValidationPath,$taskTempoValidation,[Text.UTF8Encoding]::new($false))
    Run-Checked $taskJava @('-Xverify:all','-cp',"$taskClasses;$taskAsm;$taskMicro",'HungBySonTempoClickHostTest',
        $taskOutput,$taskTempoValidationPath) (Join-Path $taskBuild 'native-menu-clicks.txt')

    Run-Checked $taskJava @('-Xverify:all','-cp',"$taskClasses;$taskAsm",'HungBySonParityJarVerifier',
        $taskInput,$taskOutput,$taskMicro,(Join-Path $taskBuild 'manifest.txt'),$taskAllowedClass) `
        (Join-Path $taskBuild 'archive-verification.txt')

    $taskSemanticSources = @(rg --files src -g '*.java')
    if ($taskSemanticSources.Count -eq 0) { throw 'No semantic Java sources found' }
    Run-Checked $taskJavac (@('-encoding','UTF-8','--release','7','-d',$taskSemantic) + $taskSemanticSources) `
        (Join-Path $taskBuild 'semantic-compile.txt')
    $taskLunaFixtures = @(rg --files fixtures/luna -g '*.java')
    if ($taskLunaFixtures.Count -eq 0) { throw 'No Luna host fixtures found' }
    Run-Checked $taskJavac (@('-encoding','UTF-8','--release','7','-cp',$taskSemantic,'-d',$taskHost) + $taskLunaFixtures) `
        (Join-Path $taskBuild 'host-compile.txt')
    foreach ($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoCombatHostTest','LoginMaterialHostTest')) {
        Run-Checked $taskJava @('-Xverify:all','-cp',"$taskSemantic;$taskHost",$taskTest) (Join-Path $taskBuild "$taskTest.txt")
    }

    $taskRegressionSources = @(
        'tools/legacy/hsosky/HungBySonFullOfflinePatcher.java',
        'fixtures/hung-by-son/HungBySonFullOfflineHostTest.java',
        'fixtures/hung-by-son/HungBySonEnhancementWaitRegressionTest.java',
        'fixtures/hung-by-son/HungBySonEnhancementNpcSessionTest.java',
        'fixtures/hung-by-son/TradeLocalTest.java'
    )
    Run-Checked $taskJavac (@('-encoding','UTF-8','-cp',"$taskAsm;$taskMicro",'-d',$taskClasses) + $taskRegressionSources) `
        (Join-Path $taskBuild 'integration-compile.txt')
    foreach ($taskTest in @('HungBySonFullOfflineHostTest','HungBySonEnhancementWaitRegressionTest',
        'HungBySonEnhancementNpcSessionTest','TradeLocalTest')) {
        $taskTestArgs = @('-Xverify:all','-cp',"$taskClasses;$taskAsm;$taskMicro",$taskTest,$taskOutput)
        if ($taskTest -eq 'TradeLocalTest') { $taskTestArgs += $taskMicro }
        Run-Checked $taskJava $taskTestArgs (Join-Path $taskBuild "$taskTest.txt")
    }

    & (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskOutput -DependencyDir $taskDeps `
        -ReportDir (Join-Path $taskBuild 'smoke') | Tee-Object -FilePath (Join-Path $taskBuild 'emulator-smoke.txt')
    if ($LASTEXITCODE -ne 0) { throw "Headless emulator smoke failed: $LASTEXITCODE" }
    Write-Output "Verified staged JAR: $taskOutput"
    Write-Output "SHA-256: $taskOutputHash"
} finally { Pop-Location }
