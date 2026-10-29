[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$InputJar,
    [Parameter(Mandatory=$true)][string]$DependencyDir,
    [string]$BuildTag = ("bachho-upgrades-" + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss'))
)
$ErrorActionPreference = 'Stop'
function Run-Checked {
    param([string]$Program, [string[]]$Arguments)
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed: $LASTEXITCODE" }
}
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid BuildTag' }
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
Push-Location $taskWorkspace
try {
    $taskInput = (Resolve-Path -LiteralPath $InputJar).Path
    $taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
    $taskAsm = (Join-Path $taskDeps 'asm-9.7.jar') + ';' + (Join-Path $taskDeps 'asm-tree-9.7.jar')
    $taskMicro = Join-Path $taskDeps 'microemulator.jar'
    foreach ($taskFile in @((Join-Path $taskDeps 'asm-9.7.jar'), (Join-Path $taskDeps 'asm-tree-9.7.jar'), $taskMicro)) {
        if (-not (Test-Path -LiteralPath $taskFile -PathType Leaf)) { throw "Missing dependency: $taskFile" }
    }
    $taskBuild = Join-Path $taskWorkspace (Join-Path 'build' $BuildTag)
    if (Test-Path -LiteralPath $taskBuild) { throw 'BuildTag already used; choose a new tag' }
    $taskTools = Join-Path $taskBuild 'tools'
    $taskClasses = Join-Path $taskBuild 'classes'
    $taskTests = Join-Path $taskBuild 'test'
    $taskGenerated = Join-Path $taskBuild 'BachHoGeneratedAutomationBridge.java'
    $taskStage = Join-Path $taskBuild 'noclip.jar'
    $taskOutput = Join-Path $taskBuild 'bachho_final.jar'
    $taskCatalog = Join-Path $taskBuild 'automation.properties'
    New-Item -ItemType Directory -Path $taskBuild,$taskTools,$taskClasses,$taskTests | Out-Null
    $taskToolSources = @('tools/luna/BachHoNoclipDefaultPatcher.java', 'tools/luna/BachHoAutomationPatcher.java',
        'tools/luna/LunaJarVerifier.java', 'tools/legacy/luna/SenderOpcodeReader.java', 'tools/legacy/luna/VerifyAutoTradeSymbols.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',$taskAsm,'-d',$taskTools) + $taskToolSources)
    $taskSemanticSources = @(rg --files src -g '*.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-d',$taskClasses) + $taskSemanticSources)
    Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskAsm",'luna.tools.BachHoNoclipDefaultPatcher',
        $taskInput,$taskStage,'symbols/bachho-ghost-20261003.properties',
        (Join-Path $taskBuild 'noclip.properties'),(Join-Path $taskBuild 'noclip-patch.txt'))
    Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskAsm",'luna.tools.BachHoAutomationPatcher','--prepare',
        $taskStage,'symbols/bachho-combat-position-20261003.properties','tools/luna/BachHoAutomationBridge.java.in',$taskGenerated,$taskCatalog)
    Run-Checked javac @('-encoding','UTF-8','--release','7','-cp',"$taskStage;$taskClasses;$taskMicro",'-d',$taskClasses,$taskGenerated)
    Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskAsm",'luna.tools.BachHoAutomationPatcher','--patch',
        $taskStage,$taskOutput,$taskClasses,$taskCatalog)
    $taskHostFixtures = @(rg --files fixtures/luna -g '*.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',$taskClasses,'-d',$taskTests) + $taskHostFixtures)
    foreach ($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoCombatHostTest','LoginMaterialHostTest')) {
        Run-Checked java @('-Xverify:all','-cp',"$taskClasses;$taskTests",$taskTest)
    }
    Run-Checked javac @('-encoding','UTF-8','--release','7','-cp',$taskAsm,'-d',$taskTests,
        'fixtures/binary/BachHoAutomationBinaryTest.java','fixtures/binary/BachHoNoclipDefaultHostTest.java','tools/legacy/luna/TestAutoTradeRoute.java')
    Run-Checked java @('-Xverify:all','-cp',"$taskTests;$taskAsm;$taskMicro",'luna.tools.BachHoAutomationBinaryTest',$taskStage,$taskOutput,$taskCatalog)
    Run-Checked java @('-Xverify:all','-cp',"$taskTests;$taskAsm;$taskMicro",'BachHoNoclipDefaultHostTest',$taskOutput)
    Run-Checked java @('-Xverify:all','-cp',"$taskTests;$taskMicro",'TestAutoTradeRoute',$taskOutput,$taskMicro,'--noclip-off')
    Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskAsm",'VerifyAutoTradeSymbols',$taskOutput,'symbols/autotrade-symbols.properties')
    Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskMicro",'luna.tools.LunaJarVerifier',$taskOutput,
        '--load-classes','--compare-resources',$taskInput,'--write-manifest',(Join-Path $taskBuild 'manifest.txt'))
    Write-Output "Verified staged JAR: $taskOutput"
    Write-Output 'Run the isolated emulator smoke check before publication. This script does not replace endpoint releases.'
} finally { Pop-Location }
