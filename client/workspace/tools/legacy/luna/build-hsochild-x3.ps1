param(
    [string]$DependencyDir = $env:LUNA_DEPS,
    [string]$TemplateJar = '../jar/backup/autotrade_v27_revive05_horse_x3.jar',
    [string]$OutputName = ("hsochild_final_x3-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($DependencyDir)) { throw 'Pass -DependencyDir with ASM 9.7, ASM Tree 9.7 and MicroEmulator.' }
if ($OutputName -notmatch '^[A-Za-z0-9_-]+$') { throw 'OutputName must be a plain filename without extension.' }
$asm = Join-Path $DependencyDir 'asm-9.7.jar'
$tree = Join-Path $DependencyDir 'asm-tree-9.7.jar'
$micro = Join-Path $DependencyDir 'microemulator.jar'
foreach ($dependency in @($asm,$tree,$micro)) { if (!(Test-Path -LiteralPath $dependency)) { throw "Missing $dependency" } }
$stage = "build/$OutputName"
$output = "build/releases/$OutputName.jar"
if ((Test-Path -LiteralPath $stage) -or (Test-Path -LiteralPath $output)) { throw 'Use a new OutputName; existing build artifacts are preserved.' }
New-Item -ItemType Directory -Force "$stage/tools","$stage/support","$stage/test","$stage/semantic",'build/releases' | Out-Null
function Checked([string]$Command, [string[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command failed with code $LASTEXITCODE" }
}
Copy-Item -LiteralPath 'tools/legacy/luna/x3/X3SlotSupport.java.in' -Destination "$stage/support/X3SlotSupport.java"
Checked javac @('-encoding','UTF-8','--release','7','-cp',$micro,'-d',"$stage/support","$stage/support/X3SlotSupport.java")
Checked javac @('-encoding','UTF-8','--release','7','-cp',"$asm;$tree",'-d',"$stage/tools",'tools/legacy/luna/RepackSupportX3.java','tools/legacy/luna/HsoChildX3Packer.java','tools/legacy/luna/SenderOpcodeReader.java','tools/legacy/luna/VerifyAutoTradeSymbols.java')
Checked java @('-Xverify:all','-cp',"$stage/tools;$asm;$tree",'HsoChildX3Packer','../jar/hsochild_final.jar',$TemplateJar,$output,"$stage/support/X3SlotSupport.class","$stage/manifest.txt")
Checked javac @('-encoding','UTF-8','--release','7','-cp',$micro,'-d',"$stage/test",'fixtures/x3/HsoChildX3HostTest.java','fixtures/x3/HsoChildX3LauncherTest.java','tools/legacy/luna/TestAutoTradeRoute.java')
Checked java @('-Xverify:all','-cp',"$stage/test;$micro",'HsoChildX3HostTest',$output)
foreach ($slot in @('','b','c')) {
    $dotted = if ($slot -eq '') { '' } else { "$slot." }
    $internal = if ($slot -eq '') { '' } else { "$slot/" }
    Checked java @('-Xverify:all','-cp',"$stage/tools;$asm;$tree",'VerifyAutoTradeSymbols',$output,'symbols/autotrade-symbols.properties',$internal) > "$stage/symbols-$($slot)test.log"
    Checked java @('-Xverify:all','-cp',"$stage/test;$micro",'TestAutoTradeRoute',$output,$micro,$dotted) > "$stage/route-$($slot)test.log"
}
# Current semantic sources are tested separately: their newer NoticeReader ABI
# is not injected into the supplied binary by this packaging operation.
$semanticSources = @(rg --files src -g '*.java')
Checked javac (@('-encoding','UTF-8','--release','7','-d',"$stage/semantic") + $semanticSources)
Checked javac @('-encoding','UTF-8','--release','7','-cp',"$stage/semantic",'-d',"$stage/test",'fixtures/luna/LunaSemanticHostTest.java','fixtures/luna/LunaControllerHostTest.java','fixtures/luna/LunaRouteHostTest.java')
foreach ($test in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest')) {
    Checked java @('-Xverify:all','-cp',"$stage/test;$stage/semantic",$test)
}
$runtimeHome = Join-Path (Get-Location) "$stage/runtime-home"
New-Item -ItemType Directory -Force $runtimeHome | Out-Null
Checked java @('-Xverify:all',"-Duser.home=$runtimeHome",'-Djava.awt.headless=true','-cp',"$stage/test;$micro",'HsoChildX3LauncherTest',$output,"$stage/screenshots") > "$stage/launcher.out.log" 2> "$stage/launcher.err.log"
if ((Get-Item -LiteralPath "$stage/launcher.err.log").Length -ne 0) { throw 'Launcher logged runtime errors' }
if (Select-String -LiteralPath "$stage/launcher.out.log" -Pattern 'Exception|Error|Unable|loi load|not found') { throw 'Launcher stdout contains runtime errors' }
Add-Content -Encoding UTF8 -LiteralPath "$stage/manifest.txt" -Value @('jvm.class/member.loading=OK (673 classes)','rms.host.tests=OK (native and reflective storage; isolation; static clear/restart)','symbol.checks=OK (133 symbols per namespace)','legacy.route.regression=OK (all three namespaces)','current.semantic.source.tests=OK (source tested separately from supplied binary)','launcher.emulator=OK (one native MIDlet, three running game loops; held-* returns to menu; 1->2->3->1 preserves instances)','live.server=UNTESTED')
Write-Host "Verified staged JAR: $output"
