[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$SingleStage,[Parameter(Mandatory=$true)][string]$X3Stage)
$ErrorActionPreference='Stop'
$taskWorkspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskWorkspace
$taskSingleStage=(Resolve-Path -LiteralPath $SingleStage).Path
$taskX3Stage=(Resolve-Path -LiteralPath $X3Stage).Path
$taskBuildRoot=Join-Path $taskWorkspace 'build'
foreach($taskPath in @($taskSingleStage,$taskX3Stage)){
    if(-not $taskPath.StartsWith($taskBuildRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Stage must be inside workspace/build'}
}
$taskSingleProof=Get-Content -Raw -Encoding UTF8 (Join-Path $taskSingleStage 'verified.json') | ConvertFrom-Json
$taskX3Proof=Get-Content -Raw -Encoding UTF8 (Join-Path $taskX3Stage 'verified.json') | ConvertFrom-Json
if(-not $taskSingleProof.Verified -or -not $taskSingleProof.Battlefield -or -not $taskX3Proof.Verified -or -not $taskX3Proof.X3ChecksPassed -or -not $taskX3Proof.BattlefieldAllSlotsVerified){throw 'Both verified stages required'}
if($taskSingleProof.SHA256 -cne $taskX3Proof.SingleInputSHA256){throw 'X3 is not built from the verified single'}
$taskSingle=(Resolve-Path -LiteralPath $taskSingleProof.Candidate).Path
$taskX3=(Resolve-Path -LiteralPath $taskX3Proof.X3Candidate).Path
if((Get-FileHash $taskSingle).Hash -cne $taskSingleProof.SHA256 -or (Get-FileHash $taskX3).Hash -cne $taskX3Proof.X3CandidateSHA256){throw 'Candidate bytes changed after testing'}
if(-not (Test-Path (Join-Path $taskX3Stage 'battlefield-all-slots.txt'))){throw 'Missing feature validation of all three X3 slots'}
if(-not (Select-String -LiteralPath (Join-Path $taskX3Stage 'battlefield-all-slots.txt') -Pattern '^BATTLEFIELD_X3_VERIFIED=true$' -Quiet)){throw 'X3 feature check did not pass'}
$taskJarRoot=[IO.Path]::GetFullPath((Join-Path $taskWorkspace '../jar'))
$taskOldSingle=Join-Path $taskJarRoot 'Hung_By_Son.jar'
$taskOldX3=Join-Path $taskJarRoot 'Hung_By_Son_x3.jar'
$taskSinglePreimage='3CB0DBA1EC67F0687057AEB1533C025A7858D85DC11B2A246097A029C053FCCF'
$taskX3Preimage='9A91554743E22884DC7DA5C660B668D69F199C7ED632683C58EDA51E97B79F57'
if((Get-FileHash $taskOldSingle).Hash -cne $taskSinglePreimage -or (Get-FileHash $taskOldX3).Hash -cne $taskX3Preimage){throw 'Release preimage changed; refusing replacement'}
$taskOther=@{}
foreach($taskFile in Get-ChildItem -LiteralPath $taskJarRoot -File -Filter '*.jar'){
    if($taskFile.Name -notin @('Hung_By_Son.jar','Hung_By_Son_x3.jar')){$taskOther[$taskFile.FullName]=(Get-FileHash $taskFile.FullName).Hash}
}
$taskGolden=Join-Path $taskWorkspace 'inputs/luna_final-golden-baseline.jar'
$taskGoldenHash=(Get-FileHash $taskGolden).Hash
$taskBackupRoot=Join-Path $taskJarRoot 'backup'
New-Item -ItemType Directory -Force -Path $taskBackupRoot | Out-Null
$taskStamp=[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')
$taskBackupSingle=Join-Path $taskBackupRoot ('Hung_By_Son-before-battlefield-'+$taskStamp+'.jar')
$taskBackupX3=Join-Path $taskBackupRoot ('Hung_By_Son_x3-before-battlefield-'+$taskStamp+'.jar')
Copy-Item -LiteralPath $taskOldSingle -Destination $taskBackupSingle
Copy-Item -LiteralPath $taskOldX3 -Destination $taskBackupX3
if((Get-FileHash $taskBackupSingle).Hash -cne $taskSinglePreimage -or (Get-FileHash $taskBackupX3).Hash -cne $taskX3Preimage){throw 'Backup verification failed'}
Copy-Item -LiteralPath $taskSingle -Destination $taskOldSingle -Force
Copy-Item -LiteralPath $taskX3 -Destination $taskOldX3 -Force
if((Get-FileHash $taskOldSingle).Hash -cne $taskSingleProof.SHA256 -or (Get-FileHash $taskOldX3).Hash -cne $taskX3Proof.X3CandidateSHA256){throw 'Published bytes mismatch'}
foreach($taskPath in $taskOther.Keys){if((Get-FileHash $taskPath).Hash -cne $taskOther[$taskPath]){throw 'Another endpoint changed'}}
if((Get-FileHash $taskGolden).Hash -cne $taskGoldenHash){throw 'Golden baseline changed'}
$taskReceipt=[ordered]@{Published=$true;SingleSHA256=$taskSingleProof.SHA256;X3SHA256=$taskX3Proof.X3CandidateSHA256;
    SingleBackup=$taskBackupSingle;X3Backup=$taskBackupX3;SinglePreimageSHA256=$taskSinglePreimage;X3PreimageSHA256=$taskX3Preimage;
    OtherEndpointsUnchanged=$true;GoldenUnchanged=$true;LiveServerTested=$false}
$taskReceipt | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $taskSingleStage 'published.json')
$taskReceipt | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $taskX3Stage 'published.json')
Write-Output ('PUBLISHED_SINGLE='+$taskOldSingle)
Write-Output ('PUBLISHED_X3='+$taskOldX3)
Write-Output ('BACKUP_SINGLE='+$taskBackupSingle)
Write-Output ('BACKUP_X3='+$taskBackupX3)
