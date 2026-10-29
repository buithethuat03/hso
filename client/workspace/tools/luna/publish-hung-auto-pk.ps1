[CmdletBinding()]
param([string]$BuildTag = 'hung-auto-pk-20261025-g',[switch]$RangeCenterFix,[switch]$PreviewFix,[switch]$Radius400,[switch]$MapObjectsFix,[switch]$PkLines365,[switch]$PkSerpent,[switch]$LootMenu,[switch]$MapResetRace,[switch]$MountGuard,[switch]$FogOda,[switch]$FogRoute,[switch]$EnhancePort,[switch]$EnhancePopup,[switch]$Stamina,[switch]$BuffSwap,[switch]$HpOrder,[switch]$TargetLock,[switch]$CacheFocus,[switch]$SelectionObjects,[switch]$RollingExp,[switch]$EconomyInfo,[switch]$GoldIdentityFix)
$ErrorActionPreference = 'Stop'
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskWorkspace
if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid BuildTag' }
if(([int][bool]$RangeCenterFix + [int][bool]$PreviewFix + [int][bool]$Radius400 + [int][bool]$MapObjectsFix + [int][bool]$PkLines365 + [int][bool]$PkSerpent + [int][bool]$LootMenu + [int][bool]$MapResetRace + [int][bool]$MountGuard + [int][bool]$FogOda + [int][bool]$FogRoute + [int][bool]$EnhancePort + [int][bool]$EnhancePopup + [int][bool]$Stamina + [int][bool]$BuffSwap + [int][bool]$HpOrder + [int][bool]$TargetLock) -gt 1){throw 'Select one incremental patch mode'}
$taskExpectedInput = if($TargetLock){'E52366BDB974761D106955F3E051346EA513A5D73ABC410D3649ACB5297CDE97'}elseif($HpOrder){'A215437818175041B179C5CA841BA36F0EC7ADBFA6A4EB7533CB490931E0EF2F'}elseif($BuffSwap){'14E2AD66697A67472C7FA90D1786822B98AF787598143C258EED748A7DD8D038'}elseif($Stamina){'95D1C5CA51588B86FA305642B0416A73042E614EED9219ACC89EEB0D8665F7D1'}elseif($EnhancePopup){'BC8A910363F65FB1785F7D5A2995ECB9301E3B02615C1E9CD11F9A9E5E94D9D2'}elseif($EnhancePort){'C9E5073D6B0978873D6A712C803027951D7E3CE8EDA289AF3803DFC51246C9F7'}elseif($FogRoute){'206D2781D8DC22B20DA78ADEC540B19A15ECBC7DBF685FB80E4FC2D0427D4082'}elseif($FogOda){'4A83A53621FAFE241A34C1A396C52D5FF7F36294BC3DF19D3366985C905730D6'}elseif($MountGuard){'BF1BB40C7433733FE8416326018B4821D1526BB6BE1B5CA7B992020CE1170647'}elseif($MapResetRace){'2EB9258B2F68805E7F90B74AEAF88DA3CAF691707500B3DF48280FCE10A33C7E'}elseif($LootMenu){'896F3B6919C168A3D92FD5455F5DDEA3F0C32D0FA88B3E1A477A2DC55FCF79D8'}elseif($PkSerpent){'A698173091FE7424B86652B59641551869F676AAD5B103AE2E512C38F4B6D3EB'}elseif($PkLines365){'0227180CBD4839C468F2AAA466E4F3555A67663181DC21F32419D5817CD98E01'}elseif($MapObjectsFix){'721671B5C0762A85CFEF8F09F02E94CEF2111137FD30F74EBD445B4E54AEB7AB'}elseif($Radius400){'C2F902196E743316DDD842A87E7A5BA31849197FFE9746D26CE0423C42D3A751'}elseif($PreviewFix){'D2E1A3318F082F4BF7EEAB4BE06BDB6F4BAED85156899734238032C5022B43B0'}elseif($RangeCenterFix){'3AE3D8126E4041F96F19DD6336C1BEE5F5824477946E6A706C83A7119A8A1B46'}else{'E8A95AEBF84055D9137276792C7BD4D6E2468B21FF51A8B3C48E57CD8ACC2CFA'}
if($GoldIdentityFix){if($EconomyInfo){throw 'Select GoldIdentityFix alone'};$EconomyInfo=$true}
if($EconomyInfo){if($RollingExp -or $SelectionObjects -or $CacheFocus){throw 'Select EconomyInfo alone'};$RollingExp=$true}
if($RollingExp){if($SelectionObjects -or $CacheFocus){throw 'Select RollingExp alone'};$SelectionObjects=$true}
if($SelectionObjects){if($CacheFocus){throw 'Select SelectionObjects alone'};$CacheFocus=$true}
if($CacheFocus){
    if($TargetLock -or $HpOrder -or $BuffSwap -or $Stamina -or $EnhancePopup -or $EnhancePort -or $FogRoute -or $FogOda -or $MountGuard -or $MapResetRace -or $LootMenu -or $PkSerpent -or $PkLines365 -or $MapObjectsFix -or $Radius400 -or $PreviewFix -or $RangeCenterFix){throw 'Select CacheFocus alone'}
    $taskExpectedInput = 'B597B3B7588D5E5692B688A456E2B11B3B7A0A0A4B82EAF0DBAEEFAE0E2B4DBC'
    $TargetLock = $true
}
if($SelectionObjects){$taskExpectedInput='5E16E5EAFC1BE81DE19318894F1F4F94787B405635431962964AF09844766E1F'}
if($RollingExp){$taskExpectedInput='9C45CB4C061E1A358FFA2A4C5A8BD90E055A075B70BE791F2C97190600CAEDE2'}
if($EconomyInfo){$taskExpectedInput='AA16DCCA0DA0A48DB0C0F39088D402D40390FCC15B779C50142BCF731B10A03C'}
if($GoldIdentityFix){$taskExpectedInput='855A7F66891DFBC0F6A64ABC00A148BB2BBE90D57EF9C416D1C3D85EB1F13A58'}
$taskBuild = Join-Path $taskWorkspace ('build/' + $BuildTag)
$taskProof = Join-Path $taskBuild 'verified.json'
if (-not (Test-Path -LiteralPath $taskProof -PathType Leaf)) { throw 'Missing build verification record' }
$taskVerified = Get-Content -Raw -Encoding UTF8 -LiteralPath $taskProof | ConvertFrom-Json
if ([bool]$taskVerified.CacheFocus -ne [bool]$CacheFocus) { throw 'Verification belongs to a different cache/focus patch mode' }
if ([bool]$taskVerified.SelectionObjects -ne [bool]$SelectionObjects) { throw 'Verification belongs to a different selection patch mode' }
if ([bool]$taskVerified.RollingExp -ne [bool]$RollingExp) { throw 'Verification belongs to a different rolling EXP patch mode' }
if ([bool]$taskVerified.EconomyInfo -ne [bool]$EconomyInfo) { throw 'Verification belongs to a different economy information patch mode' }
if ([bool]$taskVerified.GoldIdentityFix -ne [bool]$GoldIdentityFix) { throw 'Verification belongs to a different gold identity patch mode' }
$taskCandidate = [IO.Path]::GetFullPath([string]$taskVerified.Candidate)
$taskRelease = [IO.Path]::GetFullPath((Join-Path $taskWorkspace '../jar/Hung_By_Son.jar'))
$taskBackup = [IO.Path]::GetFullPath((Join-Path $taskWorkspace ('../jar/backup/Hung_By_Son-before-' + $BuildTag + '.jar')))
if (-not $taskVerified.Verified -or $taskVerified.Published -or $taskVerified.BuildTag -ne $BuildTag -or $taskVerified.InputSHA256 -ne $taskExpectedInput) { throw 'Missing, stale, or mismatched verification record' }
if ([bool]$taskVerified.RangeCenterFix -ne [bool]$RangeCenterFix) { throw 'Verification belongs to a different patch mode' }
if ([bool]$taskVerified.PreviewFix -ne [bool]$PreviewFix) { throw 'Verification belongs to a different preview patch mode' }
if ([bool]$taskVerified.Radius400 -ne [bool]$Radius400) { throw 'Verification belongs to a different radius patch mode' }
if ([bool]$taskVerified.MapObjectsFix -ne [bool]$MapObjectsFix) { throw 'Verification belongs to a different map objects patch mode' }
if ([bool]$taskVerified.PkLines365 -ne [bool]$PkLines365) { throw 'Verification belongs to a different PK lines patch mode' }
if ([bool]$taskVerified.PkSerpent -ne [bool]$PkSerpent) { throw 'Verification belongs to a different PK Xa nu patch mode' }
if ([bool]$taskVerified.LootMenu -ne [bool]$LootMenu) { throw 'Verification belongs to a different loot menu patch mode' }
if ([bool]$taskVerified.MapResetRace -ne [bool]$MapResetRace) { throw 'Verification belongs to a different map reset race patch mode' }
if ([bool]$taskVerified.MountGuard -ne [bool]$MountGuard) { throw 'Verification belongs to a different mount guard patch mode' }
if ([bool]$taskVerified.FogOda -ne [bool]$FogOda) { throw 'Verification belongs to a different Mrs Oda patch mode' }
if ([bool]$taskVerified.FogRoute -ne [bool]$FogRoute) { throw 'Verification belongs to a different fog route patch mode' }
if ([bool]$taskVerified.EnhancePort -ne [bool]$EnhancePort) { throw 'Verification belongs to a different enhance port patch mode' }
if ([bool]$taskVerified.EnhancePopup -ne [bool]$EnhancePopup) { throw 'Verification belongs to a different enhance popup patch mode' }
if ([bool]$taskVerified.Stamina -ne [bool]$Stamina) { throw 'Verification belongs to a different stamina patch mode' }
if ([bool]$taskVerified.BuffSwap -ne [bool]$BuffSwap) { throw 'Verification belongs to a different buff swap patch mode' }
if ([bool]$taskVerified.HpOrder -ne [bool]$HpOrder) { throw 'Verification belongs to a different PK hp order patch mode' }
if ([bool]$taskVerified.TargetLock -ne [bool]$TargetLock) { throw 'Verification belongs to a different PK target lock patch mode' }
$taskExpectedCandidate = [IO.Path]::GetFullPath((Join-Path $taskBuild 'Hung_By_Son.jar'))
if ($taskCandidate -ne $taskExpectedCandidate) { throw 'Candidate path does not match this BuildTag' }
if (-not (Test-Path -LiteralPath $taskCandidate -PathType Leaf) -or (Get-FileHash -Algorithm SHA256 -LiteralPath $taskCandidate).Hash -ne $taskVerified.SHA256) { throw 'Candidate changed after verification' }
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskRelease).Hash -ne $taskExpectedInput) { throw 'Release changed; inspect before publishing' }
if ((Test-Path -LiteralPath $taskBackup) -and (Get-FileHash -Algorithm SHA256 -LiteralPath $taskBackup).Hash -ne $taskExpectedInput) { throw 'Existing backup does not match the pinned preimage' }

$taskRequiredLogs = @(
    'tools-compile.txt','support-compile.txt','semantic-tests-compile.txt','binary-tests-compile.txt','patch.txt','auto-pk-report.txt',
    'equipment-filters-binary.txt','sale-return-scroll-binary.txt','equipment-filters-ui.txt',
    'auto-pk-binary.txt','auto-pk-range-ui.txt','auto-pk-world-range.txt','auto-pk-cadence.txt','auto-attack-native.txt','maze-binary.txt','battlefield.txt','pk-corpse.txt',
    'HungBySonModUpLvBinaryTest.txt','HungBySonCopySpecialBinaryTest.txt','HungBySonSpecialAttackBinaryTest.txt',
    'HungBySonFarmSpecialAttackBinaryTest.txt','HungBySonEnhancementWaitRegressionTest.txt',
    'HungBySonEnhancementNpcSessionTest.txt','trade.txt','exp-info-binary.txt','class-linkage-offline.txt',
    'auto-attack-linkage.txt','EquipmentFilterPolicyHostTest.txt','SaleReturnScrollHostTest.txt','AutoPkTargetPolicyHostTest.txt',
    'LunaSemanticHostTest.txt','LunaControllerHostTest.txt','LunaRouteHostTest.txt','AutoAttackHostTest.txt',
    'SpecialAttackHostTest.txt','FarmSpecialAttackHostTest.txt','FarmSpotHostTest.txt','NormalMonstersHostTest.txt',
    'SkillCastStatsHostTest.txt','startup/emulator-run.json'
)
if(-not $RangeCenterFix){$taskRequiredLogs += 'auto-pk-menu-states.txt'}
if($MapObjectsFix){$taskRequiredLogs += 'map-objects-binary.txt'}
if($MapResetRace){$taskRequiredLogs += 'map-reset-race.txt'}
if($MountGuard){$taskRequiredLogs += 'mount-guard.txt'}
if($EnhancePort -or $EnhancePopup -or $Stamina -or $BuffSwap -or $HpOrder -or $TargetLock){$taskRequiredLogs += 'enhance-port-binary.txt'}
if($Stamina -or $BuffSwap -or $HpOrder -or $TargetLock){$taskRequiredLogs += 'stamina-binary.txt'}
if($BuffSwap -or $HpOrder -or $TargetLock){$taskRequiredLogs += 'buff-swap-binary.txt'}
foreach ($taskLog in $taskRequiredLogs) {
    if (-not (Test-Path -LiteralPath (Join-Path $taskBuild $taskLog) -PathType Leaf)) { throw "Missing check $taskLog" }
}
if($CacheFocus -and -not (Select-String -LiteralPath (Join-Path $taskBuild 'cache-objects-host.txt') -Pattern '^PASS HungBySonMonsterCacheHostTest|^HungBySonMonsterCacheHostTest PASS|^PASS .*262' -Quiet)) { throw 'Cache objects regression did not pass' }
if($SelectionObjects -and -not (Select-String -LiteralPath (Join-Path $taskBuild 'selection-objects-host.txt') -Pattern '^HungBySonMonsterSelectionHostTest PASS' -Quiet)){throw 'Selection objects regression did not pass'}
if($RollingExp){
    foreach($taskExpTest in @('ExpRateHostTest','ExpInfoHostTest','ExpEstimateHostTest','ExpPercentIntegrationHostTest','ExpPercentInterpolatorHostTest')){
        if(-not (Select-String -LiteralPath (Join-Path $taskBuild ($taskExpTest+'.txt')) -Pattern ($taskExpTest+'.*passed|'+$taskExpTest+' PASS') -Quiet)){throw "Missing rolling EXP check: $taskExpTest"}
    }
}

$taskOtherHashes = @{}
if($EconomyInfo){
    foreach($taskEconomyCheck in @{'GoldRateHostTest.txt'='GoldRateHostTest PASS';'gold-tracking-binary.txt'='HUNG_GOLD_TRACKING_BINARY_TEST=PASS';'inventory-info-binary.txt'='HungBySonInventoryInfoBinaryTest PASS';'economy-tabs-ui.txt'='HungBySonSkillStatsUiTest PASS'}.GetEnumerator()){
        if(-not (Select-String -LiteralPath (Join-Path $taskBuild $taskEconomyCheck.Key) -Pattern $taskEconomyCheck.Value -Quiet)){throw ('Missing economy check: '+$taskEconomyCheck.Key)}
    }
}
foreach ($taskJar in Get-ChildItem -LiteralPath (Split-Path $taskRelease) -Filter '*.jar' -File) {
    if ($taskJar.FullName -ne $taskRelease) { $taskOtherHashes[$taskJar.FullName] = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskJar.FullName).Hash }
}
$taskGolden = (Resolve-Path -LiteralPath 'inputs/luna_final-golden-baseline.jar').Path
$taskGoldenHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskGolden).Hash

New-Item -ItemType Directory -Force -Path (Split-Path $taskBackup) | Out-Null
if (-not (Test-Path -LiteralPath $taskBackup)) { Copy-Item -LiteralPath $taskRelease -Destination $taskBackup }
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskBackup).Hash -ne $taskExpectedInput) { throw 'Backup verification failed' }
$taskStageDir = Join-Path $taskBuild 'publish-stage'
New-Item -ItemType Directory -Force -Path $taskStageDir | Out-Null
$taskStage = Join-Path $taskStageDir 'Hung_By_Son.jar'
if (Test-Path -LiteralPath $taskStage) { throw 'Publish stage already exists; use a fresh verified BuildTag' }
Copy-Item -LiteralPath $taskCandidate -Destination $taskStage
[IO.File]::Replace($taskStage,$taskRelease,[NullString]::Value)
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskRelease).Hash -ne $taskVerified.SHA256) { throw 'Published hash mismatch' }
foreach ($taskPath in $taskOtherHashes.Keys) {
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskPath).Hash -ne $taskOtherHashes[$taskPath]) { throw "Other endpoint changed: $taskPath" }
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskGolden).Hash -ne $taskGoldenHash) { throw 'Golden baseline changed' }

$taskVerified.Published = $true
$taskVerified | Add-Member -NotePropertyName Backup -NotePropertyValue $taskBackup
$taskVerified | Add-Member -NotePropertyName BackupSHA256 -NotePropertyValue $taskExpectedInput
$taskVerified | Add-Member -NotePropertyName OtherEndpointsUnchanged -NotePropertyValue $true
$taskVerified | Add-Member -NotePropertyName GoldenBaselineSHA256 -NotePropertyValue $taskGoldenHash
$taskVerified | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath $taskProof
Write-Output "PUBLISHED=$taskRelease SHA256=$($taskVerified.SHA256) BACKUP=$taskBackup OTHER_ENDPOINTS_UNCHANGED=$($taskOtherHashes.Count) GOLDEN=$taskGoldenHash"
