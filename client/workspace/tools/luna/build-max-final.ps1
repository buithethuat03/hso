[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$DependencyDir,
    [string]$BuildTag = ('max-final-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')),
    [switch]$Publish
)
$ErrorActionPreference = 'Stop'
function Run-Checked {
    param([string]$Program,[string[]]$Arguments,[string]$Log)
    $taskPrevious = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Program @Arguments 2>&1 | ForEach-Object { $_.ToString() } | Tee-Object -FilePath $Log
        $taskExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $taskPrevious }
    if ($taskExit -ne 0) { throw "$Program failed: $taskExit" }
}
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid build tag' }
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $taskWorkspace
try {
    $taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
    $taskAsm = (Join-Path $taskDeps 'asm-9.7.jar') + ';' + (Join-Path $taskDeps 'asm-tree-9.7.jar')
    $taskMicro = Join-Path $taskDeps 'microemulator.jar'
    $taskBuild = Join-Path $taskWorkspace (Join-Path 'build' $BuildTag)
    if (Test-Path -LiteralPath $taskBuild) { throw 'Use a fresh build tag' }
    $taskSnapshot = @{}
    Get-ChildItem -LiteralPath '../jar' -File -Filter '*.jar' | ForEach-Object { $taskSnapshot[$_.FullName] = (Get-FileHash -LiteralPath $_.FullName).Hash }
    $taskGolden = (Resolve-Path -LiteralPath 'inputs/luna_final-golden-baseline.jar').Path
    $taskGoldenHash = (Get-FileHash -LiteralPath $taskGolden).Hash
    $taskTools = Join-Path $taskBuild 'tools'
    $taskSupport = Join-Path $taskBuild 'support'
    $taskSemantic = Join-Path $taskBuild 'semantic'
    $taskHost = Join-Path $taskBuild 'host'
    New-Item -ItemType Directory -Path $taskTools,$taskSupport,$taskSemantic,$taskHost | Out-Null
    $taskJar = Join-Path $taskBuild 'max_final.jar'
    $taskCatalog = Join-Path $taskBuild 'max.properties'
    $taskBase = Join-Path $taskBuild 'max-endpoint-base.jar'
    $taskBaseCatalog = Join-Path $taskBuild 'max-base.properties'
    Run-Checked javac @('-encoding','UTF-8','--release','7','-cp',$taskMicro,'-d',$taskSupport,'src/luna/core/MaxRecordStores.java') (Join-Path $taskBuild 'support-compile.txt')
    $taskCp = "$taskTools;$taskAsm;$taskMicro"
    Run-Checked javac @('-encoding','UTF-8','--release','7','-cp',$taskCp,'-d',$taskTools,'tools/luna/MaxVariantPatcher.java','tools/luna/MaxLegacyProtocolPatcher.java','tools/luna/LunaJarVerifier.java','fixtures/binary/MaxVariantBinaryTest.java','fixtures/binary/MaxCharacterProtocolTest.java','tools/legacy/luna/TestAutoTradeRoute.java','tools/legacy/luna/SenderOpcodeReader.java','tools/legacy/luna/VerifyAutoTradeSymbols.java') (Join-Path $taskBuild 'tools-compile.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'MaxVariantPatcher','../jar/chibi_final.jar','../jar/HsoMaxMod.jar',$taskBase,$taskSupport,$taskBaseCatalog) (Join-Path $taskBuild 'patch.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'MaxLegacyProtocolPatcher',$taskBase,'../jar/HsoMaxMod.jar',$taskJar,$taskBaseCatalog,$taskCatalog) (Join-Path $taskBuild 'legacy-protocol-patch.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'MaxCharacterProtocolTest','../jar/HsoMaxMod.jar',$taskBase,$taskJar) (Join-Path $taskBuild 'character-protocol-test.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'MaxVariantBinaryTest','../jar/chibi_final.jar','../jar/HsoMaxMod.jar',$taskJar,$taskCatalog) (Join-Path $taskBuild 'binary-test.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'luna.tools.LunaJarVerifier',$taskJar,'--compare-resources','../jar/chibi_final.jar','--allow-resource','META-INF/MANIFEST.MF','--load-classes','--write-manifest',(Join-Path $taskBuild 'max.manifest')) (Join-Path $taskBuild 'jar-verifier.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'VerifyAutoTradeSymbols',$taskJar,'symbols/autotrade-symbols.properties') (Join-Path $taskBuild 'symbol-verifier.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskCp,'TestAutoTradeRoute',$taskJar,$taskMicro) (Join-Path $taskBuild 'legacy-route-test.txt')
    $taskSources = @(rg --files src -g '*.java')
    $taskFixtures = @(rg --files fixtures/luna -g '*.java')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',$taskMicro,'-d',$taskSemantic) + $taskSources) (Join-Path $taskBuild 'semantic-compile.txt')
    Run-Checked javac (@('-encoding','UTF-8','--release','7','-cp',$taskSemantic,'-d',$taskHost) + $taskFixtures) (Join-Path $taskBuild 'host-compile.txt')
    foreach ($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoCombatHostTest','LoginMaterialHostTest')) {
        Run-Checked java @('-Xverify:all','-cp',"$taskSemantic;$taskHost",$taskTest) (Join-Path $taskBuild ($taskTest+'.txt'))
    }
    & "$PSScriptRoot/run-hung-bachho-emulator-smoke.ps1" -Jar $taskJar -DependencyDir $taskDeps -ReportDir (Join-Path $taskBuild 'emulator') | Tee-Object -FilePath (Join-Path $taskBuild 'emulator-smoke.txt')
    foreach ($taskPath in $taskSnapshot.Keys) { if ((Get-FileHash -LiteralPath $taskPath).Hash -ne $taskSnapshot[$taskPath]) { throw "Existing release changed: $taskPath" } }
    if ((Get-FileHash -LiteralPath $taskGolden).Hash -ne $taskGoldenHash) { throw 'Golden baseline changed' }
    $taskHash = (Get-FileHash -LiteralPath $taskJar).Hash
    $taskRelease = [IO.Path]::GetFullPath((Join-Path $taskWorkspace '../jar/max_final.jar'))
    $taskBackup = $null
    if ($Publish) {
        if (Test-Path -LiteralPath $taskRelease) {
            $taskOldHash = (Get-FileHash -LiteralPath $taskRelease).Hash
            if ($taskOldHash -ne $taskHash) {
                if ($taskOldHash -ne 'F66EE6FA7DCB998704348B4FC60A04C468CA5F9FB759770180A23A602E9D4DE8') { throw 'Unexpected MAX preimage; preserve user changes' }
                New-Item -ItemType Directory -Path '../jar/backup' -Force | Out-Null
                $taskBackup = [IO.Path]::GetFullPath((Join-Path $taskWorkspace ("../jar/backup/max_final-before-"+$BuildTag+'.jar')))
                [IO.File]::Copy($taskRelease,$taskBackup,$false)
                if ((Get-FileHash -LiteralPath $taskBackup).Hash -ne $taskOldHash) { throw 'Backup hash mismatch' }
                $taskReady = Join-Path $taskBuild 'publish-ready.jar'
                [IO.File]::Copy($taskJar,$taskReady,$false)
                [IO.File]::Replace($taskReady,$taskRelease,(Join-Path $taskBuild 'replaced-old.jar'))
            }
        } else { [IO.File]::Copy($taskJar,$taskRelease,$false) }
        if ((Get-FileHash -LiteralPath $taskRelease).Hash -ne $taskHash) { throw 'Published hash mismatch' }
    }
    [ordered]@{ Verified=$true; Published=[bool]$Publish; Candidate=$taskJar; Release=$taskRelease; Backup=$taskBackup; SHA256=$taskHash;
        Source='chibi_final.jar'; Donor='HsoMaxMod.jar'; Suite='HSO MAX'; ServerName='HsoMax'; Endpoint='sv.hsomax.com:19129';
        LoginOpcode=1; LoginVersion='2.5.8'; RMSPrefix='HSO_MAX_'; FullLoginPacketCases=5; LinkedClasses=217;
        AppearanceOpcodes=@(3,5,15); NativeAppearanceCases=15; FullSelfBodyTail=$true;
        Emulator='12 second isolated headless startup'; LiveServerTested=$false; OtherReleasesUnchanged=$true; GoldenUnchanged=$true
    } | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'verified.json')
    Write-Output "MAX_RELEASE=verified; published=$([bool]$Publish); SHA256=$taskHash"
} finally { Pop-Location }
