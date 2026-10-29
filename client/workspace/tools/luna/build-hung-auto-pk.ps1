[CmdletBinding()]
param(
    [string]$InputJar = '..\jar\Hung_By_Son.jar',
    [string]$DependencyDir = '..\..\.codex-deps',
    [switch]$RangeCenterFix,
    [switch]$PreviewFix,
    [switch]$Radius400,
    [switch]$MapObjectsFix,
    [switch]$PkLines365,
    [switch]$PkSerpent,
    [switch]$LootMenu,
    [switch]$MapResetRace,
    [switch]$MountGuard,
    [switch]$FogOda,
    [switch]$FogRoute,
    [switch]$EnhancePort,
    [switch]$EnhancePopup,
    [switch]$Stamina,
    [switch]$BuffSwap,
    [switch]$HpOrder,
    [switch]$TargetLock,
    [switch]$CacheFocus,
    [switch]$SelectionObjects,
    [switch]$RollingExp,
    [switch]$EconomyInfo,
    [switch]$GoldIdentityFix,
    [string]$BuildTag = ('hung-auto-pk-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
)
$ErrorActionPreference = 'Stop'
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskWorkspace
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid BuildTag' }

if(([int][bool]$RangeCenterFix + [int][bool]$PreviewFix + [int][bool]$Radius400 + [int][bool]$MapObjectsFix + [int][bool]$PkLines365 + [int][bool]$PkSerpent + [int][bool]$LootMenu + [int][bool]$MapResetRace + [int][bool]$MountGuard + [int][bool]$FogOda + [int][bool]$FogRoute + [int][bool]$EnhancePort + [int][bool]$EnhancePopup + [int][bool]$Stamina + [int][bool]$BuffSwap + [int][bool]$HpOrder + [int][bool]$TargetLock) -gt 1){throw 'Select one incremental patch mode'}
$taskExpectedInput = if($CacheFocus){'B597B3B7588D5E5692B688A456E2B11B3B7A0A0A4B82EAF0DBAEEFAE0E2B4DBC'}elseif($TargetLock){'E52366BDB974761D106955F3E051346EA513A5D73ABC410D3649ACB5297CDE97'}elseif($HpOrder){'A215437818175041B179C5CA841BA36F0EC7ADBFA6A4EB7533CB490931E0EF2F'}elseif($BuffSwap){'14E2AD66697A67472C7FA90D1786822B98AF787598143C258EED748A7DD8D038'}elseif($Stamina){'95D1C5CA51588B86FA305642B0416A73042E614EED9219ACC89EEB0D8665F7D1'}elseif($EnhancePopup){'BC8A910363F65FB1785F7D5A2995ECB9301E3B02615C1E9CD11F9A9E5E94D9D2'}elseif($EnhancePort){'C9E5073D6B0978873D6A712C803027951D7E3CE8EDA289AF3803DFC51246C9F7'}elseif($FogRoute){'206D2781D8DC22B20DA78ADEC540B19A15ECBC7DBF685FB80E4FC2D0427D4082'}elseif($FogOda){'4A83A53621FAFE241A34C1A396C52D5FF7F36294BC3DF19D3366985C905730D6'}elseif($MountGuard){'BF1BB40C7433733FE8416326018B4821D1526BB6BE1B5CA7B992020CE1170647'}elseif($MapResetRace){'2EB9258B2F68805E7F90B74AEAF88DA3CAF691707500B3DF48280FCE10A33C7E'}elseif($LootMenu){'896F3B6919C168A3D92FD5455F5DDEA3F0C32D0FA88B3E1A477A2DC55FCF79D8'}elseif($PkSerpent){'A698173091FE7424B86652B59641551869F676AAD5B103AE2E512C38F4B6D3EB'}elseif($PkLines365){'0227180CBD4839C468F2AAA466E4F3555A67663181DC21F32419D5817CD98E01'}elseif($MapObjectsFix){'721671B5C0762A85CFEF8F09F02E94CEF2111137FD30F74EBD445B4E54AEB7AB'}elseif($Radius400){'C2F902196E743316DDD842A87E7A5BA31849197FFE9746D26CE0423C42D3A751'}elseif($PreviewFix){'D2E1A3318F082F4BF7EEAB4BE06BDB6F4BAED85156899734238032C5022B43B0'}elseif($RangeCenterFix){'3AE3D8126E4041F96F19DD6336C1BEE5F5824477946E6A706C83A7119A8A1B46'}else{'E8A95AEBF84055D9137276792C7BD4D6E2468B21FF51A8B3C48E57CD8ACC2CFA'}
if($SelectionObjects){
    if($CacheFocus){throw 'Select SelectionObjects alone'}
    $CacheFocus=$true
    $taskExpectedInput='5E16E5EAFC1BE81DE19318894F1F4F94787B405635431962964AF09844766E1F'
}
if($RollingExp){
    if($SelectionObjects -or $CacheFocus){throw 'Select RollingExp alone'}
    $SelectionObjects=$true
    $CacheFocus=$true
    $taskExpectedInput='9C45CB4C061E1A358FFA2A4C5A8BD90E055A075B70BE791F2C97190600CAEDE2'
}
if($GoldIdentityFix){if($EconomyInfo){throw 'Select GoldIdentityFix alone'};$EconomyInfo=$true}
if($EconomyInfo){
    if($RollingExp -or $SelectionObjects -or $CacheFocus){throw 'Select EconomyInfo alone'}
    $RollingExp=$true;$SelectionObjects=$true;$CacheFocus=$true
    $taskExpectedInput='AA16DCCA0DA0A48DB0C0F39088D402D40390FCC15B779C50142BCF731B10A03C'
}
if($GoldIdentityFix){$taskExpectedInput='855A7F66891DFBC0F6A64ABC00A148BB2BBE90D57EF9C416D1C3D85EB1F13A58'}
$taskInput = (Resolve-Path -LiteralPath $InputJar).Path
$taskInputHash = (Get-FileHash -LiteralPath $taskInput -Algorithm SHA256).Hash
if ($taskInputHash -ne $taskExpectedInput) { throw 'Use the pinned current Hung x1 input' }
$taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
$taskAsm = (Join-Path $taskDeps 'asm-9.7.jar') + ';' + (Join-Path $taskDeps 'asm-tree-9.7.jar')
$taskMicro = Join-Path $taskDeps 'microemulator.jar'
foreach ($taskDependency in @((Join-Path $taskDeps 'asm-9.7.jar'),(Join-Path $taskDeps 'asm-tree-9.7.jar'),$taskMicro)) {
    if (-not (Test-Path -LiteralPath $taskDependency -PathType Leaf)) { throw "Missing dependency: $taskDependency" }
}

$taskBuild = Join-Path $taskWorkspace ('build/' + $BuildTag)
if (Test-Path -LiteralPath $taskBuild) { throw 'Use a fresh BuildTag' }
New-Item -ItemType Directory -Path $taskBuild,(Join-Path $taskBuild 'tools'),(Join-Path $taskBuild 'support'),(Join-Path $taskBuild 'host'),(Join-Path $taskBuild 'work'),(Join-Path $taskBuild 'screenshots') | Out-Null
$taskTools = Join-Path $taskBuild 'tools'
$taskSupport = Join-Path $taskBuild 'support'
$taskHost = Join-Path $taskBuild 'host'
$taskWork = Join-Path $taskBuild 'work'
$taskCandidate = Join-Path $taskBuild 'Hung_By_Son.jar'
$taskAutoCatalog = Join-Path $taskBuild 'auto-attack.properties'
$taskMazeCatalog = Join-Path $taskBuild 'maze.properties'
$taskAutoPkStem = Join-Path $taskBuild 'auto-pk'
$taskBase = Join-Path $taskWorkspace $(if($TargetLock){'build/hung-auto-pk-hp-order-20261027-c'}elseif($HpOrder){'build/hung-buff-swap-20261027-a'}elseif($BuffSwap){'build/hung-stamina-20261027-a'}elseif($Stamina){'build/hung-enhance-popup-20261027-a'}elseif($EnhancePopup){'build/hung-enhance-port-20261027-b'}elseif($EnhancePort){'build/hung-fog-route-20261027-a'}elseif($FogRoute){'build/hung-fog-oda-20261026-a'}elseif($FogOda){'build/hung-mount-guard-20261026-a'}elseif($MountGuard){'build/hung-map-reset-race-20261026-a'}elseif($MapResetRace){'build/hung-loot-menu-20261026-b'}elseif($LootMenu){'build/hung-auto-pk-serpent-20261026-a'}elseif($PkSerpent){'build/hung-auto-pk-lines365-20261026-a'}elseif($PkLines365){'build/hung-map-objects-20261026-b'}elseif($MapObjectsFix){'build/hung-auto-pk-radius400-20261026-a'}elseif($Radius400){'build/hung-auto-pk-preview-20261026-b'}elseif($PreviewFix){'build/hung-auto-pk-center-20261025-c'}elseif($RangeCenterFix){'build/hung-auto-pk-20261025-g'}else{'build/hung-sale-scroll-farm-match-20261025-a'})
if($CacheFocus){
    if($TargetLock -or $HpOrder -or $BuffSwap -or $Stamina -or $EnhancePopup -or $EnhancePort -or $FogRoute -or $FogOda -or $MountGuard -or $MapResetRace -or $LootMenu -or $PkSerpent -or $PkLines365 -or $MapObjectsFix -or $Radius400 -or $PreviewFix -or $RangeCenterFix){throw 'Select CacheFocus alone'}
    $taskBase = Join-Path $taskWorkspace 'build/hung-auto-pk-target-lock-20261027-b'
    # This incremental profile inherits all target-lock regression gates.
    $TargetLock = $true
}
if($SelectionObjects){$taskBase=Join-Path $taskWorkspace 'build/hung-cache-focus-20261028-final'}
if($RollingExp){$taskBase=Join-Path $taskWorkspace 'build/hung-selection-objects-20261028-b'}
if($EconomyInfo){$taskBase=Join-Path $taskWorkspace 'build/hung-exp-rolling-fine-20261028-final'}
if($GoldIdentityFix){$taskBase=Join-Path $taskWorkspace 'build/hung-economy-info-20261029-b'}
$taskBaseProofPath = Join-Path $taskBase 'verified.json'
if (-not (Test-Path -LiteralPath $taskBaseProofPath -PathType Leaf)) { throw 'Missing pinned base build verification record' }
$taskBaseProof = Get-Content -Raw -Encoding UTF8 -LiteralPath $taskBaseProofPath | ConvertFrom-Json
if (-not $taskBaseProof.Verified -or $taskBaseProof.SHA256 -ne $taskExpectedInput) { throw 'Base build is not verified for the pinned input' }
$taskBaseCandidate = (Resolve-Path -LiteralPath $taskBaseProof.Candidate).Path
if ((Get-FileHash -LiteralPath $taskBaseCandidate -Algorithm SHA256).Hash -ne $taskExpectedInput) { throw 'Base candidate changed' }

function Run-Checked {
    param([string]$Program,[string[]]$Arguments,[string]$Log)
    $taskPriorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $taskLines = @(& $Program @Arguments 2>&1 | ForEach-Object { $_.ToString() })
        $taskExit = $LASTEXITCODE
        [IO.File]::WriteAllLines((Join-Path $taskBuild $Log),[string[]]$taskLines,[Text.UTF8Encoding]::new($false))
    } finally { $ErrorActionPreference = $taskPriorPreference }
    if ($taskExit -ne 0) { $taskLines | Select-Object -Last 20; throw "$Program failed ($Log)" }
    $taskLines | Select-Object -Last 1
}

function Set-PropertyLine {
    param([string]$Text,[string]$Key,[string]$Value)
    $taskPattern = '(?m)^' + [regex]::Escape($Key) + '=.*$'
    $taskMatches = [regex]::Matches($Text,$taskPattern)
    if ($taskMatches.Count -gt 1) { throw "Duplicate catalog property: $Key" }
    if ($taskMatches.Count -eq 1) { return [regex]::Replace($Text,$taskPattern,($Key + '=' + $Value)) }
    $taskTrimmed = [regex]::Replace($Text,'(?:\r?\n)+$','')
    return $taskTrimmed + "`r`n$Key=$Value`r`n"
}

function Remove-PropertyLine {
    param([string]$Text,[string]$Key)
    $taskPattern = '(?m)^' + [regex]::Escape($Key) + '=.*(?:\r?\n|$)'
    $taskMatches = [regex]::Matches($Text,'(?m)^' + [regex]::Escape($Key) + '=.*$')
    if ($taskMatches.Count -gt 1) { throw "Duplicate catalog property: $Key" }
    return [regex]::Replace($Text,$taskPattern,'')
}

$taskCatalogNames = @('auto-attack','battlefield','exp','recovery','timing','noclip','dungeon','maze')
foreach ($taskCatalog in $taskCatalogNames) {
    $taskSourceCatalog = Join-Path $taskBase ($taskCatalog + '.properties')
    if (-not (Test-Path -LiteralPath $taskSourceCatalog -PathType Leaf)) { throw "Missing base catalog: $taskCatalog" }
    $taskText = Get-Content -Raw -Encoding UTF8 -LiteralPath $taskSourceCatalog
    if ($taskText -notmatch ('(?m)^output\.sha256=' + $taskExpectedInput + '\r?$')) { throw "Unpinned base catalog: $taskCatalog" }
    $taskText = [regex]::Replace($taskText,'(?m)^output\.sha256=[^\r\n]*',('output.sha256=' + $taskInputHash))
    if ($taskCatalog -eq 'noclip') {
        if ($taskText -notmatch ('(?m)^noclip\.output\.sha256=' + $taskExpectedInput + '\r?$')) { throw 'Unpinned noclip output catalog' }
        $taskText = [regex]::Replace($taskText,'(?m)^noclip\.output\.sha256=[^\r\n]*',('noclip.output.sha256=' + $taskInputHash))
    }
    $taskText = Set-PropertyLine $taskText 'auto.pk.upgrade' 'true'
    if(-not $RangeCenterFix){$taskText = Set-PropertyLine $taskText 'auto.pk.preview.selected' 'true'}
    if($PkLines365 -or $PkSerpent -or $LootMenu -or $MapResetRace -or $MountGuard -or $FogOda -or $FogRoute -or $EnhancePort -or $EnhancePopup -or $Stamina -or $BuffSwap -or $HpOrder -or $TargetLock){$taskText = Set-PropertyLine $taskText 'auto.pk.radius' '365'; $taskText = Set-PropertyLine $taskText 'auto.pk.lines' 'true'}
    if($LootMenu -or $MapResetRace -or $MountGuard -or $FogOda -or $FogRoute -or $EnhancePort -or $EnhancePopup -or $Stamina -or $BuffSwap -or $HpOrder -or $TargetLock){$taskText = Set-PropertyLine $taskText 'loot.menu' 'true'}
    if($MapResetRace -or $MountGuard -or $FogOda -or $FogRoute -or $EnhancePort -or $EnhancePopup -or $Stamina -or $BuffSwap -or $HpOrder -or $TargetLock){$taskText = Set-PropertyLine $taskText 'map.reset.race' 'true'}
    if($MountGuard -or $FogOda -or $FogRoute -or $EnhancePort -or $EnhancePopup -or $Stamina -or $BuffSwap -or $HpOrder -or $TargetLock){$taskText = Set-PropertyLine $taskText 'mount.guard' 'true'}
    if($FogOda -or $FogRoute -or $EnhancePort -or $EnhancePopup -or $Stamina -or $BuffSwap -or $HpOrder -or $TargetLock){$taskText = Set-PropertyLine $taskText 'fog.oda' 'true'}
    if($FogRoute -or $EnhancePort -or $EnhancePopup -or $Stamina -or $BuffSwap -or $HpOrder -or $TargetLock){$taskText = Set-PropertyLine $taskText 'fog.route' 'true'}
    if($EnhancePort -or $EnhancePopup -or $Stamina -or $BuffSwap -or $HpOrder -or $TargetLock){$taskText = Set-PropertyLine $taskText 'enhance.port' 'true'}
    if($EnhancePopup -or $Stamina -or $BuffSwap -or $HpOrder -or $TargetLock){$taskText = Set-PropertyLine $taskText 'enhance.popup' 'true'}
    if($Stamina -or $BuffSwap -or $HpOrder -or $TargetLock){$taskText = Set-PropertyLine $taskText 'stamina' 'true'}
    if($BuffSwap -or $HpOrder -or $TargetLock){$taskText = Set-PropertyLine $taskText 'buff.swap' 'true'}
    if($HpOrder -or $TargetLock){$taskText = Set-PropertyLine $taskText 'auto.pk.hp.order' 'true'}
    if($TargetLock){$taskText = Set-PropertyLine $taskText 'auto.pk.target.lock' 'true'}
    if($CacheFocus){$taskText = Set-PropertyLine $taskText 'auto.pk.focus.lines' 'true';$taskText = Set-PropertyLine $taskText 'cache.exclude.objects' 'true'}
    if($SelectionObjects){$taskText = Set-PropertyLine $taskText 'selection.exclude.objects' 'true'}
    if($RollingExp){$taskText = Set-PropertyLine $taskText 'exp.rate.window.seconds' '300';$taskText = Set-PropertyLine $taskText 'exp.rate.bucket.ms' '1000';$taskText = Set-PropertyLine $taskText 'exp.hud.fine.numerator' 'true'}
    if($EconomyInfo){
        $taskText = Set-PropertyLine $taskText 'exp.rate.window.seconds' '900'
        $taskText = Set-PropertyLine $taskText 'exp.rate.minimum.seconds' '300'
        $taskText = Set-PropertyLine $taskText 'gold.rate.window.seconds' '900'
        $taskText = Set-PropertyLine $taskText 'gold.info.tab' 'true'
        $taskText = Set-PropertyLine $taskText 'inventory.info.modes' '3'
    }
    if($PkSerpent -or $LootMenu -or $MapResetRace -or $MountGuard -or $FogOda -or $FogRoute -or $EnhancePort -or $EnhancePopup -or $Stamina -or $BuffSwap -or $HpOrder -or $TargetLock){$taskText = Set-PropertyLine $taskText 'auto.pk.serpent' 'true'}
    elseif($Radius400 -or (-not $RangeCenterFix -and -not $PreviewFix)){$taskText = Set-PropertyLine $taskText 'auto.pk.radius' '400'}
    $taskText = Remove-PropertyLine $taskText 'normal.monsters.action'
    [IO.File]::WriteAllText((Join-Path $taskBuild ($taskCatalog + '.properties')),$taskText,[Text.UTF8Encoding]::new($false))
}

Run-Checked javac @('-encoding','UTF-8','-cp',$taskAsm,'-d',$taskTools,
    'tools/luna/HungBySonAutoPkPatcher.java','tools/luna/HungMapObjectsPatcher.java','tools/luna/HungAutoPkRadiusPatcher.java','tools/luna/HungAutoPkLinesPatcher.java','tools/luna/HungAutoPkSerpentPatcher.java','tools/luna/HungLootMenuPatcher.java','tools/luna/HungMapResetRacePatcher.java','tools/luna/HungMountGuardPatcher.java','tools/luna/HungFogOdaPatcher.java','tools/luna/HungFogRoutePatcher.java','tools/luna/HungEnhancePortPatcher.java','tools/luna/HungEnhancePopupPatcher.java','tools/luna/HungStaminaPatcher.java','tools/luna/HungBuffSwapPatcher.java','tools/luna/HungAutoPkHpOrderPatcher.java','tools/luna/HungAutoPkTargetLockPatcher.java','tools/luna/HungAutoPkPreviewFixPatcher.java','tools/luna/HungAutoPkRangeFixPatcher.java','tools/luna/HungAutoPkRangeHook.java','tools/luna/HungBySonCopySpecialPatcher.java',
    'tools/luna/HungBySonExpInfoPack.java','tools/luna/HungBySonModUpLvPatcher.java','tools/luna/HungAutoAttackNativeBuilder.java',
    'tools/legacy/hsosky/HungBySonFullOfflinePatcher.java') 'tools-compile.txt'
if($CacheFocus){Run-Checked javac @('-encoding','UTF-8','-cp',"$taskTools;$taskAsm",'-d',$taskTools,'tools/luna/HungCacheFocusPatcher.java') 'cache-focus-tools-compile.txt'}
if($SelectionObjects){Run-Checked javac @('-encoding','UTF-8','-cp',"$taskTools;$taskAsm",'-d',$taskTools,'tools/luna/HungSelectionObjectsPatcher.java') 'selection-objects-tools-compile.txt'}
if($RollingExp){Run-Checked javac @('-encoding','UTF-8','-cp',"$taskTools;$taskAsm",'-d',$taskTools,'tools/luna/HungExpRollingPatcher.java') 'rolling-exp-tools-compile.txt'}
if($EconomyInfo){Run-Checked javac @('-encoding','UTF-8','-cp',"$taskTools;$taskAsm",'-d',$taskTools,'tools/luna/HungEconomyInfoPatcher.java','tools/luna/HungGoldTrackingPatch.java','tools/luna/HungInventoryInfoPatch.java','tools/luna/HungGoldIdentityPatcher.java') 'economy-info-tools-compile.txt'}
$taskSources = @(& rg --files src -g '*.java')
if ($taskSources.Count -eq 0) { throw 'No support sources found' }
Run-Checked javac (@('-encoding','UTF-8','--release','7','-Xlint:-options','-cp',$taskMicro,'-d',$taskSupport) + $taskSources) 'support-compile.txt'
$taskPatcher=if($TargetLock){'HungAutoPkTargetLockPatcher'}elseif($HpOrder){'HungAutoPkHpOrderPatcher'}elseif($BuffSwap){'HungBuffSwapPatcher'}elseif($Stamina){'HungStaminaPatcher'}elseif($EnhancePopup){'HungEnhancePopupPatcher'}elseif($EnhancePort){'HungEnhancePortPatcher'}elseif($FogRoute){'HungFogRoutePatcher'}elseif($FogOda){'HungFogOdaPatcher'}elseif($MountGuard){'HungMountGuardPatcher'}elseif($MapResetRace){'HungMapResetRacePatcher'}elseif($LootMenu){'HungLootMenuPatcher'}elseif($PkSerpent){'HungAutoPkSerpentPatcher'}elseif($PkLines365){'HungAutoPkLinesPatcher'}elseif($MapObjectsFix){'HungMapObjectsPatcher'}elseif($Radius400){'HungAutoPkRadiusPatcher'}elseif($PreviewFix){'HungAutoPkPreviewFixPatcher'}elseif($RangeCenterFix){'HungAutoPkRangeFixPatcher'}else{'HungBySonAutoPkPatcher'}
if($CacheFocus){$taskPatcher='HungCacheFocusPatcher'}
if($SelectionObjects){$taskPatcher='HungSelectionObjectsPatcher'}
if($RollingExp){$taskPatcher='HungExpRollingPatcher'}
if($EconomyInfo){$taskPatcher='HungEconomyInfoPatcher'}
if($GoldIdentityFix){$taskPatcher='HungGoldIdentityPatcher'}
Run-Checked java @('-cp',"$taskTools;$taskAsm",$taskPatcher,$taskInput,$taskSupport,$taskMicro,$taskWork,$taskCandidate,$taskAutoPkStem) 'patch.txt'

$taskHash = (Get-FileHash -LiteralPath $taskCandidate -Algorithm SHA256).Hash
foreach ($taskCatalog in $taskCatalogNames) {
    $taskPath = Join-Path $taskBuild ($taskCatalog + '.properties')
    $taskText = Get-Content -Raw -Encoding UTF8 -LiteralPath $taskPath
    if ($taskText -notmatch ('(?m)^output\.sha256=' + $taskInputHash + '\r?$')) { throw "Catalog changed before output rebind: $taskCatalog" }
    $taskText = [regex]::Replace($taskText,'(?m)^output\.sha256=[^\r\n]*',('output.sha256=' + $taskHash))
    if ($taskCatalog -eq 'noclip') {
        if ($taskText -notmatch ('(?m)^noclip\.output\.sha256=' + $taskInputHash + '\r?$')) { throw 'Noclip source hash changed before output rebind' }
        $taskText = [regex]::Replace($taskText,'(?m)^noclip\.output\.sha256=[^\r\n]*',('noclip.output.sha256=' + $taskHash))
    }
    [IO.File]::WriteAllText($taskPath,$taskText,[Text.UTF8Encoding]::new($false))
}
foreach ($taskCatalog in $taskCatalogNames) {
    $taskText = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $taskBuild ($taskCatalog + '.properties'))
    if ($taskText -notmatch ('(?m)^output\.sha256=' + $taskHash + '\r?$')) { throw "Catalog output hash mismatch: $taskCatalog" }
    if ($taskText -notmatch '(?m)^auto\.pk\.upgrade=true\r?$') { throw "Auto PK marker missing: $taskCatalog" }
}
$taskAutoPkCatalog = Join-Path $taskBuild 'auto-pk.properties'
$taskAutoPkReport = Join-Path $taskBuild 'auto-pk-report.txt'
foreach ($taskArtifact in @($taskAutoPkCatalog,$taskAutoPkReport)) {
    if (-not (Test-Path -LiteralPath $taskArtifact -PathType Leaf)) { throw "Patcher output missing: $taskArtifact" }
}
$taskGeneratedCatalog = Get-Content -Raw -Encoding UTF8 -LiteralPath $taskAutoPkCatalog
if ($taskGeneratedCatalog -notmatch ('(?m)^output\.sha256=' + $taskHash + '\r?$')) { throw 'Generated Auto PK catalog does not match candidate' }
if ($taskGeneratedCatalog -notmatch '(?m)^auto\.pk\.upgrade=true\r?$') { throw 'Generated Auto PK catalog is not enabled' }
if ($taskGeneratedCatalog -match '(?m)^normal\.monsters\.action=') { throw 'Hidden normal-monsters action remains in Auto PK catalog' }
if ($taskGeneratedCatalog -match '(?m)^auto\.pk\.class\.count=(\d+)\r?$') {
    $taskCatalogClassCount = [int]$Matches[1]
} else { throw 'Generated Auto PK class count missing' }
if ($taskGeneratedCatalog -match '(?m)^auto\.pk\.changed\.classes=(.*)\r?$') {
    $taskChangedClasses = $Matches[1]
} else { throw 'Generated Auto PK changed-class list missing' }

$taskJarEntries = @(& jar tf $taskCandidate 2>&1 | ForEach-Object { $_.ToString() })
$taskJarExit = $LASTEXITCODE
if ($taskJarExit -ne 0) { throw 'Could not list candidate archive' }
$taskJarEntries | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'candidate-entries.txt')
$taskClassCount = @($taskJarEntries | Where-Object { $_.EndsWith('.class',[StringComparison]::Ordinal) }).Count
$taskBaseJarEntries = @(& jar tf $taskBaseCandidate 2>&1 | ForEach-Object { $_.ToString() })
if ($LASTEXITCODE -ne 0) { throw 'Could not list pinned base archive' }
$taskBaseClassCount = @($taskBaseJarEntries | Where-Object { $_.EndsWith('.class',[StringComparison]::Ordinal) }).Count
if ($taskClassCount -ne $taskCatalogClassCount) { throw "Catalog/JAR class count mismatch: $taskCatalogClassCount != $taskClassCount" }
if ($GoldIdentityFix) { if($taskClassCount -ne $taskBaseClassCount){throw 'Gold identity fix adds no classes'} }
elseif ($EconomyInfo) { if($taskClassCount -ne $taskBaseClassCount + 2){throw 'Economy adds exactly GoldRateSupport and HungInventoryInfo'} }
elseif ($EnhancePort) { if($taskClassCount -ne $taskBaseClassCount + 1){throw 'Enhance port adds exactly HungBySonEnhance'} }
elseif ($Stamina) { if($taskClassCount -ne $taskBaseClassCount + 1){throw 'Stamina adds exactly HungBySonStamina'} }
elseif ($BuffSwap) { if($taskClassCount -ne $taskBaseClassCount + 1){throw 'Buff swap adds exactly HungBySonBuffSwap'} }
elseif ($RangeCenterFix -or $PreviewFix -or $Radius400 -or $MapObjectsFix -or $PkLines365 -or $PkSerpent -or $LootMenu -or $MapResetRace -or $MountGuard -or $FogOda -or $FogRoute -or $EnhancePopup -or $HpOrder -or $TargetLock) { if($taskClassCount -ne $taskBaseClassCount){throw 'Incremental fixes must preserve class count'} }
elseif ($taskClassCount -le $taskBaseClassCount) { throw 'Auto PK candidate did not add its required support classes' }

$taskSemanticTests = @(& rg --files fixtures/luna -g '*.java')
Run-Checked javac (@('-encoding','UTF-8','--release','7','-Xlint:-options','-cp',"$taskSupport;$taskMicro",'-d',$taskHost) + $taskSemanticTests) 'semantic-tests-compile.txt'
foreach ($taskTest in @('EquipmentFilterPolicyHostTest','SaleReturnScrollHostTest','AutoPkTargetPolicyHostTest')) {
    Run-Checked java @('-Xverify:all','-cp',"$taskHost;$taskSupport;$taskMicro",$taskTest) ($taskTest + '.txt')
}
foreach ($taskTest in @('LunaSemanticHostTest','LunaControllerHostTest','LunaRouteHostTest','AutoAttackHostTest','SpecialAttackHostTest','FarmSpecialAttackHostTest','FarmSpotHostTest','NormalMonstersHostTest','SkillCastStatsHostTest')) {
    Run-Checked java @('-Xverify:all','-cp',"$taskHost;$taskSupport;$taskMicro",$taskTest) ($taskTest + '.txt')
}

$taskBinaryTests = @('HungEquipmentFiltersBinaryTest','HungBySonMazeExclusiveBinaryTest','HungBySonAutoAttackBinaryTest',
    'HungBySonModUpLvBinaryTest','HungBySonCopySpecialBinaryTest','HungBySonSpecialAttackBinaryTest','HungBySonFarmSpecialAttackBinaryTest',
    'HungBySonFullOfflineHostTest','HungBySonBattlefieldBinaryTest','HungBySonAutoAttackPkCorpseHostTest',
    'HungBySonEnhancementWaitRegressionTest','HungBySonEnhancementNpcSessionTest','TradeLocalTest','HungBySonExpInfoBinaryTest','HungSaleReturnScrollBinaryTest',
    'HungEquipmentFiltersUiTest','HungBySonAutoPkBinaryTest','HungBySonAutoPkRangeUiTest','HungBySonAutoPkCadenceTest','HungBySonAutoPkWorldRangeTest','HungBySonAutoPkMenuStateTest')
$taskBinarySources = @($taskBinaryTests | ForEach-Object { 'fixtures/hung-by-son/' + $_ + '.java' })
if($CacheFocus){$taskBinarySources += 'fixtures/hung-by-son/HungBySonMonsterCacheHostTest.java'}
if($SelectionObjects){$taskBinarySources += 'fixtures/hung-by-son/HungBySonMonsterSelectionHostTest.java'}
if($RollingExp){$taskBinarySources += @('fixtures/hung-by-son/ExpRateHostTest.java','fixtures/hung-by-son/ExpInfoHostTest.java','fixtures/hung-by-son/ExpEstimateHostTest.java','fixtures/hung-by-son/ExpPercentIntegrationHostTest.java','fixtures/hung-by-son/ExpPercentInterpolatorHostTest.java')}
if($EconomyInfo){$taskBinarySources += @('fixtures/hung-by-son/GoldRateHostTest.java','fixtures/hung-by-son/HungGoldTrackingBinaryTest.java','fixtures/hung-by-son/HungBySonInventoryInfoBinaryTest.java','fixtures/hung-by-son/HungBySonSkillStatsUiTest.java')}
if($MapObjectsFix){$taskBinarySources += 'fixtures/hung-by-son/HungBySonMapObjectLoadingTest.java'}
if($MapResetRace){$taskBinarySources += 'fixtures/hung-by-son/HungBySonMapResetRaceTest.java'}
if($MountGuard){$taskBinarySources += 'fixtures/hung-by-son/HungBySonMountGuardTest.java'}
if($EnhancePort -or $EnhancePopup -or $Stamina -or $BuffSwap -or $HpOrder -or $TargetLock){$taskBinarySources += 'fixtures/hung-by-son/HungBySonEnhanceBinaryTest.java'}
if($Stamina -or $BuffSwap -or $HpOrder -or $TargetLock){$taskBinarySources += 'fixtures/hung-by-son/HungBySonStaminaBinaryTest.java'}
if($BuffSwap -or $HpOrder -or $TargetLock){$taskBinarySources += 'fixtures/hung-by-son/HungBySonBuffSwapBinaryTest.java'}
Run-Checked javac (@('-encoding','UTF-8','-cp',"$taskCandidate;$taskSupport;$taskTools;$taskAsm;$taskMicro",'-d',$taskHost) + $taskBinarySources) 'binary-tests-compile.txt'
$taskHostCp = "$taskHost;$taskTools;$taskAsm;$taskMicro"
if($CacheFocus){Run-Checked java @('-Xverify:all','-cp',"$taskHost;$taskCandidate;$taskMicro",'HungBySonMonsterCacheHostTest',$taskCandidate) 'cache-objects-host.txt'}
if($SelectionObjects){Run-Checked java @('-Xverify:all','-cp',"$taskHost;$taskCandidate;$taskMicro",'HungBySonMonsterSelectionHostTest',$taskCandidate) 'selection-objects-host.txt'}
if($RollingExp){
    foreach($taskExpTest in @('ExpRateHostTest','ExpInfoHostTest','ExpEstimateHostTest','ExpPercentIntegrationHostTest','ExpPercentInterpolatorHostTest')){
        Run-Checked java @('-Xverify:all','-cp',"$taskHost;$taskCandidate;$taskMicro",$taskExpTest) ($taskExpTest + '.txt')
    }
}
if($EconomyInfo){
    Run-Checked java @('-Xverify:all','-cp',"$taskHost;$taskCandidate;$taskMicro",'GoldRateHostTest') 'GoldRateHostTest.txt'
    Run-Checked java @('-Xverify:all','-cp',"$taskHostCp;$taskCandidate",'HungGoldTrackingBinaryTest',$taskCandidate,$taskAutoPkCatalog,$taskMicro) 'gold-tracking-binary.txt'
    Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonInventoryInfoBinaryTest',$taskCandidate,$taskAutoPkCatalog,$taskMicro,(Join-Path $taskBuild 'inventory-preview')) 'inventory-info-binary.txt'
    Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8',('-Dmicroemulator.jar='+$taskMicro),'-cp',$taskHostCp,'HungBySonSkillStatsUiTest',$taskCandidate,(Join-Path $taskBuild 'exp.properties'),$taskMicro,(Join-Path $taskBuild 'economy-screenshots')) 'economy-tabs-ui.txt'
}
if($MapObjectsFix){
    Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonMapObjectLoadingTest',$taskInput,$taskCandidate) 'map-objects-binary.txt'
    if((Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'map-objects-binary.txt')) -match '(?m)^(Exception in thread|java\.[a-z.]+Exception|\s+at [a-z])'){
        throw 'Unexpected runtime stack trace in the map-object regression'
    }
}
if($MapResetRace){
    Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonMapResetRaceTest',$taskInput,$taskCandidate) 'map-reset-race.txt'
    if((Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'map-reset-race.txt')) -match '(?m)^(Exception in thread|java\.[a-z.]+Exception|\s+at [a-z])'){
        throw 'Unexpected runtime stack trace in the map reset race regression'
    }
}
if($MountGuard){
    Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonMountGuardTest',$taskInput,$taskCandidate) 'mount-guard.txt'
    if((Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'mount-guard.txt')) -match '(?m)^(Exception in thread|java\.[a-z.]+Exception|\s+at [a-z])'){
        throw 'Unexpected runtime stack trace in the mount guard regression'
    }
}
if($EnhancePort -or $EnhancePopup -or $Stamina -or $BuffSwap -or $HpOrder -or $TargetLock){
    Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonEnhanceBinaryTest',$taskCandidate,$taskAutoCatalog,(Join-Path $taskBuild 'auto-pk.properties')) 'enhance-port-binary.txt'
    if((Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'enhance-port-binary.txt')) -notmatch '(?m)^HungBySonEnhanceBinaryTest PASS checks=\d+'){throw 'Enhance port fixture did not finish'}
}
if($Stamina -or $BuffSwap -or $HpOrder -or $TargetLock){
    Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonStaminaBinaryTest',$taskCandidate,$taskAutoCatalog,(Join-Path $taskBuild 'auto-pk.properties')) 'stamina-binary.txt'
    if((Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'stamina-binary.txt')) -notmatch '(?m)^HungBySonStaminaBinaryTest PASS checks=\d+'){throw 'Stamina fixture did not finish'}
}
if($BuffSwap -or $HpOrder -or $TargetLock){
    Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonBuffSwapBinaryTest',$taskCandidate,$taskAutoCatalog,(Join-Path $taskBuild 'auto-pk.properties')) 'buff-swap-binary.txt'
    if((Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'buff-swap-binary.txt')) -notmatch '(?m)^HungBySonBuffSwapBinaryTest PASS checks=\d+'){throw 'Buff swap fixture did not finish'}
}
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungEquipmentFiltersBinaryTest',$taskCandidate) 'equipment-filters-binary.txt'
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungSaleReturnScrollBinaryTest',$taskCandidate) 'sale-return-scroll-binary.txt'
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-Djava.awt.headless=true','-Dsun.reflect.inflationThreshold=10000','-cp',"$taskHost;$taskMicro",'HungEquipmentFiltersUiTest',$taskCandidate,(Join-Path $taskBuild 'screenshots')) 'equipment-filters-ui.txt'
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonFullOfflineHostTest',$taskCandidate,[string]$taskClassCount) 'class-linkage-offline.txt'
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonBattlefieldBinaryTest',$taskCandidate,$taskAutoCatalog,(Join-Path $taskBuild 'battlefield.properties')) 'battlefield.txt'
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonAutoAttackPkCorpseHostTest',$taskCandidate,$taskAutoCatalog) 'pk-corpse.txt'
foreach ($taskTest in @('HungBySonEnhancementWaitRegressionTest','HungBySonEnhancementNpcSessionTest')) {
    Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,$taskTest,$taskCandidate) ($taskTest + '.txt')
}
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'TradeLocalTest',$taskCandidate,$taskMicro) 'trade.txt'
$taskReference = (Resolve-Path -LiteralPath '../jar/backup/Hung_By_Son-before-hung-auto-attack-20261014-final-c.jar').Path
$taskDonor = (Resolve-Path -LiteralPath '../jar/bachho_final.jar').Path
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonAutoAttackBinaryTest',$taskReference,$taskCandidate,$taskDonor,$taskAutoCatalog,$taskMicro,(Join-Path $taskBuild 'auto-attack-binary')) 'auto-attack-native.txt'
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonMazeExclusiveBinaryTest',$taskCandidate,$taskAutoCatalog,$taskMazeCatalog,$taskMazeCatalog) 'maze-binary.txt'
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonAutoPkBinaryTest',$taskCandidate,$taskAutoCatalog,$taskMazeCatalog) 'auto-pk-binary.txt'
if(-not $RangeCenterFix){Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonAutoPkMenuStateTest',$taskCandidate,$taskAutoCatalog) 'auto-pk-menu-states.txt'}
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8',('-Dmicroemulator.jar=' + $taskMicro),'-cp',$taskHostCp,'HungBySonAutoPkRangeUiTest',$taskCandidate,$taskAutoCatalog,(Join-Path $taskBuild 'pk-range-screenshots')) 'auto-pk-range-ui.txt'
if ((Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'auto-pk-range-ui.txt')) -match 'Exception|AssertionError|error MIDlet') { throw 'Unexpected emulator error in the range UI fixture' }
$taskOldRange=(Resolve-Path 'build/hung-auto-pk-20261025-g/Hung_By_Son.jar').Path
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8',('-Dmicroemulator.jar=' + $taskMicro),'-cp',$taskHostCp,'HungBySonAutoPkWorldRangeTest',$taskCandidate,$taskOldRange,$taskAutoPkCatalog,(Join-Path $taskBuild 'pk-world-range-screenshots')) 'auto-pk-world-range.txt'
if ((Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'auto-pk-world-range.txt')) -match 'error MIDlet|Exception in thread') { throw 'Unexpected error in world-camera regression' }
$taskCadenceSource=if($RangeCenterFix -or $PreviewFix -or $Radius400 -or $MapObjectsFix -or $PkLines365 -or $PkSerpent -or $LootMenu -or $MapResetRace -or $MountGuard -or $FogOda -or $FogRoute -or $EnhancePort -or $EnhancePopup -or $Stamina -or $BuffSwap -or $HpOrder -or $TargetLock){(Resolve-Path '../jar/backup/Hung_By_Son-before-hung-auto-pk-20261025-g.jar').Path}else{$taskInput}
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonAutoPkCadenceTest',$taskCadenceSource,$taskCandidate,$taskAutoCatalog) 'auto-pk-cadence.txt'
foreach ($taskTest in @('HungBySonModUpLvBinaryTest','HungBySonCopySpecialBinaryTest','HungBySonSpecialAttackBinaryTest','HungBySonFarmSpecialAttackBinaryTest')) {
    Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,$taskTest,$taskCandidate,$taskAutoCatalog,$taskMazeCatalog) ($taskTest + '.txt')
}
Copy-Item -LiteralPath (Join-Path $taskBuild 'HungBySonSpecialAttackBinaryTest.txt') -Destination (Join-Path $taskBuild 'special-attack-binary.txt')
Run-Checked java @('-Xverify:all','-Dfile.encoding=UTF-8','-cp',$taskHostCp,'HungBySonExpInfoBinaryTest',$taskCandidate,(Join-Path $taskBuild 'exp.properties'),$taskMicro,(Join-Path $taskBuild 'exp-info-screenshots')) 'exp-info-binary.txt'
Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungAutoAttackNativeBuilder','--link',$taskCandidate,$taskMicro) 'auto-attack-linkage.txt'
& (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskCandidate -DependencyDir $taskDeps -ReportDir (Join-Path $taskBuild 'startup')
if ((Get-FileHash -LiteralPath $taskInput -Algorithm SHA256).Hash -ne $taskInputHash) { throw 'Input changed during build' }

[ordered]@{
    Verified=$true; Published=$false; Input=$taskInput; InputSHA256=$taskInputHash; Candidate=$taskCandidate; SHA256=$taskHash
    ClassCount=$taskClassCount; BaseClassCount=$taskBaseClassCount; AddedClasses=($taskClassCount - $taskBaseClassCount); CacheFocus=[bool]$CacheFocus; SelectionObjects=[bool]$SelectionObjects; RollingExp=[bool]$RollingExp; EconomyInfo=[bool]$EconomyInfo; GoldIdentityFix=[bool]$GoldIdentityFix
    ChangedClasses=$taskChangedClasses; AutoPkCatalog=$taskAutoPkCatalog; BuildTag=$BuildTag; LiveServerTested=$false; RangeCenterFix=[bool]$RangeCenterFix; PreviewFix=[bool]$PreviewFix; Radius400=[bool]$Radius400; MapObjectsFix=[bool]$MapObjectsFix; PkLines365=[bool]$PkLines365; PkSerpent=[bool]$PkSerpent; LootMenu=[bool]$LootMenu; MapResetRace=[bool]$MapResetRace; MountGuard=[bool]$MountGuard; FogOda=[bool]$FogOda; FogRoute=[bool]$FogRoute; EnhancePort=[bool]$EnhancePort; EnhancePopup=[bool]$EnhancePopup; Stamina=[bool]$Stamina; BuffSwap=[bool]$BuffSwap; HpOrder=[bool]$HpOrder; TargetLock=[bool]$TargetLock
} | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'verified.json')
Write-Output "VERIFIED=$taskCandidate SHA256=$taskHash CLASSES=$taskClassCount ADDED=$($taskClassCount - $taskBaseClassCount)"
