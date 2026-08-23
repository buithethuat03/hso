# build.ps1
# GhostMod Build - Official Sequence from claude.md
$ErrorActionPreference = "Stop"
$buildDir = Join-Path (Get-Location) ".ghostmod-build"
if (-not (Test-Path -LiteralPath $buildDir)) {
    New-Item -ItemType Directory -Path $buildDir | Out-Null
}

Write-Host "[*] Compiling GhostMod.java..." -ForegroundColor Cyan
# Using --release 7 for maximum compatibility with J2ME
javac -encoding UTF-8 -cp "microemulator.jar;KnightOnline_402.jar" --release 7 -d $buildDir GhostMod.java
if ($LASTEXITCODE -ne 0) { 
    Write-Error "Failed to compile GhostMod.java"
    exit $LASTEXITCODE 
}

Write-Host "[*] Compiling GhostModPatcher.java..." -ForegroundColor Cyan
javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" -d $buildDir GhostModPatcher.java
if ($LASTEXITCODE -ne 0) { 
    Write-Error "Failed to compile GhostModPatcher.java"
    exit $LASTEXITCODE 
}

Write-Host "[*] Running Patcher..." -ForegroundColor Cyan
java "-Dghostmod.classes.dir=$buildDir" -cp "$buildDir;asm-9.7.jar;asm-tree-9.7.jar" GhostModPatcher
if ($LASTEXITCODE -ne 0) { 
    Write-Error "Patching failed!"
    exit $LASTEXITCODE 
}

Write-Host "[+] SUCCESS! Verified modded JAR was generated (see patcher output for the exact file name)." -ForegroundColor Green
