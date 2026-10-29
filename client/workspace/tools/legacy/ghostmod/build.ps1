# Historical GhostMod/client pipeline.
# The canonical maintained build is ..\..\luna\build-source.ps1.
# This script is reference-only because its legacy input JARs are not retained.
param(
    [string]$ServerIp = "127.0.0.1",
    [string]$ServerName = "HSO FREE"
)

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

Write-Host "[*] Running GhostMod Patcher..." -ForegroundColor Cyan
java "-Dghostmod.classes.dir=$buildDir" -cp "$buildDir;asm-9.7.jar;asm-tree-9.7.jar" GhostModPatcher
if ($LASTEXITCODE -ne 0) { 
    Write-Error "Patching failed!"
    exit $LASTEXITCODE 
}

Write-Host "[*] Compiling ClientRepointPatcher.java..." -ForegroundColor Cyan
javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" -d $buildDir ClientRepointPatcher.java
if ($LASTEXITCODE -ne 0) { 
    Write-Error "Failed to compile ClientRepointPatcher.java"
    exit $LASTEXITCODE 
}

Write-Host "[*] Repointing KnightOnline clients ($ServerIp)..." -ForegroundColor Cyan
java -cp "$buildDir;asm-9.7.jar;asm-tree-9.7.jar" ClientRepointPatcher KnightOnline_402.jar KnightOnline_402_Local.jar $ServerIp
java -cp "$buildDir;asm-9.7.jar;asm-tree-9.7.jar" ClientRepointPatcher KnightOnline_402_Modded.jar KnightOnline_402_Modded_Local.jar $ServerIp

# Build unified client.jar
Write-Host "[*] Compiling BuildClientPatcher.java..." -ForegroundColor Cyan
javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" -d $buildDir BuildClientPatcher.java
if ($LASTEXITCODE -ne 0) { 
    Write-Error "Failed to compile BuildClientPatcher.java"
    exit $LASTEXITCODE 
}

Write-Host "[*] Building unified client.jar ($ServerIp)..." -ForegroundColor Cyan
java -cp "$buildDir;asm-9.7.jar;asm-tree-9.7.jar" BuildClientPatcher HSOSKY_MOD_AUTO_ENHANCE_GOLD_ZONECHAT_V9.jar client.jar $ServerIp 19129
java -cp "$buildDir;asm-9.7.jar;asm-tree-9.7.jar" BuildClientPatcher HSOSKY_MOD_AUTO_ENHANCE_GOLD_ZONECHAT_V9.jar HSOSKY_MOD_AUTO_ENHANCE_GOLD_ZONECHAT_V9_Local.jar $ServerIp 19129

Copy-Item -Path "client.jar" -Destination "..\client.jar" -Force

Write-Host "[+] SUCCESS! Generated all unified client JARs" -ForegroundColor Green
