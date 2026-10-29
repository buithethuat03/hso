<#
  Canonical Luna source -> JAR build.

  Run from client/workspace:
      .\tools\luna\build-source.ps1 -DependencyDir C:\path\to\luna-deps

  The script never overwrites a JAR.  It compiles semantic source and host
  fixtures, generates the ABI facade and legacy binary bridge, patches a new
  output, then runs the release verifiers. Only a verified JAR is published to
  client/jar; build files and the manifest remain in client/workspace/build.
  Dependencies are intentionally external to keep the repository source-first.
#>
[CmdletBinding()]
param(
    [string]$OutputJar = "",
    [string]$DependencyDir = "",
    [switch]$EnterOnly
)

$ErrorActionPreference = "Stop"

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$Program,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw ("{0} failed with exit code {1}" -f $Program, $LASTEXITCODE)
    }
}

$jarRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$releaseDir = [IO.Path]::GetFullPath((Join-Path $jarRoot "..\jar"))
Push-Location $jarRoot
try {
    if (-not (Test-Path -LiteralPath $releaseDir -PathType Container)) {
        throw "Release directory does not exist: $releaseDir"
    }
    $unexpectedReleaseEntries = @(Get-ChildItem -LiteralPath $releaseDir -Force | Where-Object { $_.PSIsContainer -or $_.Extension -ine ".jar" })
    if ($unexpectedReleaseEntries.Count -ne 0) {
        throw "Release directory contains non-JAR content: $releaseDir"
    }
    if ([string]::IsNullOrWhiteSpace($DependencyDir)) {
        $DependencyDir = $env:LUNA_DEPS
    }
    if ([string]::IsNullOrWhiteSpace($DependencyDir)) {
        throw "Missing external dependencies. Pass -DependencyDir <folder> or set LUNA_DEPS. Required: asm-9.7.jar, asm-tree-9.7.jar, microemulator.jar"
    }
    $dependencyPath = [IO.Path]::GetFullPath($DependencyDir)
    if (-not (Test-Path -LiteralPath $dependencyPath -PathType Container)) {
        throw "Dependency directory does not exist: $dependencyPath"
    }
    $asmJar = Join-Path $dependencyPath "asm-9.7.jar"
    $asmTreeJar = Join-Path $dependencyPath "asm-tree-9.7.jar"
    $microemulatorJar = Join-Path $dependencyPath "microemulator.jar"
    foreach ($dependency in @($asmJar, $asmTreeJar, $microemulatorJar)) {
        if (-not (Test-Path -LiteralPath $dependency -PathType Leaf)) {
            throw "Missing required dependency: $dependency"
        }
    }
    $asmClasspath = "$asmJar;$asmTreeJar"

    if ([string]::IsNullOrWhiteSpace($OutputJar)) {
        $buildTag = Get-Date -Format "yyyyMMdd-HHmmss"
        $OutputJar = "luna-4.0.2-refactor-$buildTag.jar"
    }
    if ($OutputJar -ne [IO.Path]::GetFileName($OutputJar) -or [IO.Path]::GetExtension($OutputJar) -ine ".jar") {
        throw "OutputJar must be a .jar filename without a directory: $OutputJar"
    }
    $outputPath = Join-Path $releaseDir $OutputJar
    if (Test-Path -LiteralPath $outputPath) {
        throw "Refusing to overwrite existing JAR: $outputPath"
    }
    if (Test-Path -LiteralPath (Join-Path $jarRoot (Join-Path "inputs/releases" $OutputJar))) {
        throw "OutputJar was used by an archived release: $OutputJar"
    }
    $outputName = [IO.Path]::GetFileNameWithoutExtension($outputPath)
    $stagedOutput = Join-Path (Join-Path $jarRoot "build/releases") $OutputJar
    if (Test-Path -LiteralPath $stagedOutput) {
        throw "Refusing to overwrite staged JAR: $stagedOutput"
    }
    $manifestPath = Join-Path (Get-Location) (Join-Path "build" ($outputName + ".manifest"))
    if (Test-Path -LiteralPath $manifestPath) {
        throw "Refusing to overwrite existing manifest: $manifestPath"
    }

    $legacyLunaDir = "tools/legacy/luna"
    $autoTradeSymbols = "symbols/autotrade-symbols.properties"
    $baselineJar = "inputs/luna_final-golden-baseline.jar"
    $generatedFacade = "build/generated/HSOAutoTradeSupport.java"
    $generatedCompileJar = "build/generated/.autotrade-compile.jar"
    $routeTestSource = "tools/legacy/luna/TestAutoTradeRoute.java"
    $autoTradeOutput = Join-Path (Join-Path (Get-Location) "build/generated") ($outputName + ".autotrade.jar")
    $nativeAutoOutput = Join-Path (Join-Path (Get-Location) "build/generated") ($outputName + ".native-auto.jar")

    # Javac does not remove classes for deleted source files. Clean only the
    # generated compilation directories so a removed feature cannot survive in
    # the next JAR as a stale class.
    foreach ($generatedDir in @("build/semantic", "build/test", "build/tools", "build/generated")) {
        if (Test-Path -LiteralPath $generatedDir) {
            Remove-Item -LiteralPath $generatedDir -Recurse -Force
        }
    }
    New-Item -ItemType Directory -Force build/semantic,build/test,build/tools,build/generated,build/releases | Out-Null

    $semanticSources = @(rg --files src | Where-Object { $_ -like '*.java' })
    $fixtureSources = @(rg --files fixtures/luna | Where-Object { $_ -like '*.java' })
    $toolSources = @(rg --files tools/luna | Where-Object { $_ -like '*.java' })
    if ($semanticSources.Count -eq 0) { throw "No semantic Java source found under src" }
    if ($fixtureSources.Count -eq 0) { throw "No Luna fixture source found" }
    if ($toolSources.Count -eq 0) { throw "No Luna tool source found" }

    Write-Host "[1/9] Compile semantic source" -ForegroundColor Cyan
    $compileSemantic = @("-encoding", "UTF-8", "--release", "7", "-d", "build/semantic") + $semanticSources
    Invoke-Checked javac $compileSemantic

    Write-Host "[2/9] Compile and run host fixtures" -ForegroundColor Cyan
    $compileFixtures = @("-encoding", "UTF-8", "--release", "7", "-cp", "build/semantic", "-d", "build/test") + $fixtureSources
    Invoke-Checked javac $compileFixtures
    Invoke-Checked java @("-Xverify:all", "-cp", "build/semantic;build/test", "LunaSemanticHostTest")
    Invoke-Checked java @("-Xverify:all", "-cp", "build/semantic;build/test", "LunaControllerHostTest")
    Invoke-Checked java @("-Xverify:all", "-cp", "build/semantic;build/test", "LunaRouteHostTest")

    Write-Host "[3/9] Compile and verify Luna tools" -ForegroundColor Cyan
    $compileTools = @("-encoding", "UTF-8", "--release", "7", "-cp", $asmClasspath, "-d", "build/tools") + $toolSources
    Invoke-Checked javac $compileTools
    Invoke-Checked java @("-Xverify:all", "-cp", "build/tools", "luna.tools.LunaBridgeGenerator")
    Invoke-Checked java @("-Xverify:all", "-cp", "build/tools", "luna.tools.LunaSymbolCatalogVerifier")

    Write-Host "[4/9] Verify all binary symbols" -ForegroundColor Cyan
    Invoke-Checked javac @("-encoding", "UTF-8", "--release", "7", "-cp", $asmClasspath, "-d", "build/tools", "$legacyLunaDir/SenderOpcodeReader.java", "$legacyLunaDir/VerifyAutoTradeSymbols.java")
    Invoke-Checked java @("-Xverify:all", "-cp", "build/tools;$asmClasspath", "VerifyAutoTradeSymbols", $baselineJar, $autoTradeSymbols)

    Write-Host "[5/9] Generate hook facade and legacy bridge" -ForegroundColor Cyan
    Invoke-Checked javac @("-encoding", "UTF-8", "-d", "build/tools", "$legacyLunaDir/GenAutoTradeSupport.java")
    Invoke-Checked java @("-Xverify:all", "-cp", "build/tools", "GenAutoTradeSupport", "$legacyLunaDir/HSOAutoTradeSupport.java.in", $autoTradeSymbols, $generatedFacade)
    Invoke-Checked javac @("-encoding", "UTF-8", "--release", "7", "-cp", "$baselineJar;$microemulatorJar;build/semantic", "-d", "build/generated", "build/generated/LunaGeneratedLegacyHooks.java")

    Write-Host "[6/9] Compile support and patcher" -ForegroundColor Cyan
    Invoke-Checked javac @("-encoding", "UTF-8", "--release", "7", "-cp", "$baselineJar;$microemulatorJar", "-d", "build/generated", "$legacyLunaDir/HSOSKYRuntimeSupport.java")
    Invoke-Checked javac @("-encoding", "UTF-8", "--release", "7", "-cp", $asmClasspath, "-d", "build/tools", "$legacyLunaDir/MakeAutoTradeCompileJar.java", "$legacyLunaDir/AutoTradePatcher.java")
    Invoke-Checked java @("-Xverify:all", "-cp", "build/tools;$asmClasspath", "MakeAutoTradeCompileJar", $baselineJar, $generatedCompileJar, $autoTradeSymbols)
    Invoke-Checked javac @("-encoding", "UTF-8", "--release", "7", "-cp", "$generatedCompileJar;$microemulatorJar;build/semantic", "-d", "build/generated", $generatedFacade)
    $patcherProperties = @(
        "-Dluna.generated.dir=build/generated",
        "-Dluna.support.source=$generatedFacade",
        "-Dluna.support.template=$legacyLunaDir/HSOAutoTradeSupport.java.in",
        "-Dluna.runtime.source=$legacyLunaDir/HSOSKYRuntimeSupport.java",
        "-Dluna.symbols=$autoTradeSymbols"
    )
    Invoke-Checked java (@("-Xverify:all") + $patcherProperties + @("-cp", "build/tools;$asmClasspath", "AutoTradePatcher", $baselineJar, $autoTradeOutput))
    $xpPopupInput = $autoTradeOutput
    # Keep the native "Tự động" menu available.  The one-shot ENTER hook in
    # legacy-hook.template is the login/world-entry cleanup path; blocking
    # MENUBOX.show here also blocks the real auto menu when the player opens it.
    # Keep -EnterOnly as a backwards-compatible no-op for old build commands.
    Write-Host "[6a/9] Keep native Tự động menu; use one-shot ENTER login hook (500ms)" -ForegroundColor Cyan
    $xpPopupInput = $autoTradeOutput
    Write-Host "[6b/9] Patch XP popup presentation from the versioned symbol catalog" -ForegroundColor Cyan
    Invoke-Checked java @(
        "-Xverify:all",
        "-Dluna.symbols=$autoTradeSymbols",
        "-cp", "build/tools;$asmClasspath",
        "luna.tools.XpPopupPatcher",
        $xpPopupInput,
        $stagedOutput
    )

    Write-Host "[7/9] Verify JAR structure and resources" -ForegroundColor Cyan
    Invoke-Checked java @("-Xverify:all", "-cp", "build/tools;$microemulatorJar", "luna.tools.LunaJarVerifier", $stagedOutput, "--load-classes", "--compare-resources", $baselineJar, "--allow-resource", "x1/mfont/tahoma_14b_green.png", "--allow-resource", "mfont/tahoma_14b", "--write-manifest", $manifestPath)

    Write-Host "[8/9] Verify legacy route invariants" -ForegroundColor Cyan
    Invoke-Checked javac @("-encoding", "UTF-8", "--release", "7", "-d", "build/test", $routeTestSource)
    Invoke-Checked java @("-Xverify:all", "-cp", "build/test;$microemulatorJar", "TestAutoTradeRoute", $stagedOutput, $microemulatorJar)

    Write-Host "[9/9] Publish verified release" -ForegroundColor Cyan
    $archiveDir = Join-Path $jarRoot "inputs/releases"
    New-Item -ItemType Directory -Force -Path $archiveDir | Out-Null
    $oldReleases = @(Get-ChildItem -LiteralPath $releaseDir -File -Filter "*.jar")
    $archived = @()
    foreach ($oldRelease in $oldReleases) {
        $archivePath = Join-Path $archiveDir $oldRelease.Name
        if (Test-Path -LiteralPath $archivePath) {
            throw "Archive already contains $archivePath; publish stopped before moving releases"
        }
    }
    try {
        foreach ($oldRelease in $oldReleases) {
            $archivePath = Join-Path $archiveDir $oldRelease.Name
            Move-Item -LiteralPath $oldRelease.FullName -Destination $archivePath
            $archived += @{ From = $oldRelease.FullName; To = $archivePath }
        }
        Move-Item -LiteralPath $stagedOutput -Destination $outputPath
    } catch {
        foreach ($item in $archived) {
            Move-Item -LiteralPath $item.To -Destination $item.From
        }
        throw
    }
    $published = @(Get-ChildItem -LiteralPath $releaseDir -Force)
    if ($published.Count -ne 1 -or $published[0].FullName -ine $outputPath) {
        throw "Release directory must contain only the new JAR: $releaseDir"
    }
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $outputPath).Hash
    Write-Host ("output={0}" -f $outputPath)
    Write-Host ("sha256={0}" -f $hash)
    Write-Host ("manifest={0}" -f $manifestPath)
} finally {
    Pop-Location
}
