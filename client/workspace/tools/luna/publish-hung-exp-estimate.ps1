[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$StageBuild,[switch]$Resume)
$ErrorActionPreference='Stop'
$taskWorkspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskBuildRoot=[IO.Path]::GetFullPath((Join-Path $taskWorkspace 'build'))
$taskBuild=(Resolve-Path -LiteralPath $StageBuild).Path
if(-not $taskBuild.StartsWith($taskBuildRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){
    throw 'Publication stage must be inside workspace/build'
}
$taskTag=Split-Path -Leaf $taskBuild
if($taskTag -notmatch '^[A-Za-z0-9_-]+$'){throw 'Invalid publication tag'}
$taskProof=Get-Content -LiteralPath (Join-Path $taskBuild 'verified.json') -Encoding UTF8 -Raw | ConvertFrom-Json
if(-not $taskProof.Verified -or $taskProof.Published -or $taskProof.X3Slots -ne 3){throw 'Missing complete unpublished verification'}
$taskJarRoot=[IO.Path]::GetFullPath((Join-Path $taskWorkspace '../jar'))
$taskBackupRoot=Join-Path $taskJarRoot 'backup'
$taskPublishRoot=Join-Path $taskBuild 'publish-stage'
if(Test-Path -LiteralPath (Join-Path $taskBuild 'publish.json')){throw 'This stage already has a publication record'}
if((Test-Path -LiteralPath $taskPublishRoot) -and -not $Resume){throw 'Publication staging directory already exists'}
$taskSingleBeforeHash=if($taskProof.ReleaseSingleBeforeHash){$taskProof.ReleaseSingleBeforeHash}else{$taskProof.SingleInputSHA256}
$taskX3BeforeHash=if($taskProof.ReleaseX3BeforeHash){$taskProof.ReleaseX3BeforeHash}else{$taskProof.X3InputSHA256}
$taskJobs=@(
    @{Name='Hung_By_Son.jar';Source=$taskProof.Single;NewHash=$taskProof.SingleSHA256;OldHash=$taskSingleBeforeHash;SourceInputHash=$taskProof.SingleInputSHA256},
    @{Name='Hung_By_Son_x3.jar';Source=$taskProof.X3;NewHash=$taskProof.X3SHA256;OldHash=$taskX3BeforeHash;SourceInputHash=$taskProof.X3InputSHA256}
)
foreach($taskJob in $taskJobs){
    $taskSource=(Resolve-Path -LiteralPath $taskJob.Source).Path
    if(-not $taskSource.StartsWith($taskBuild+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Candidate escaped the verified stage'}
    $taskJob.Source=$taskSource
    $taskJob.Target=Join-Path $taskJarRoot $taskJob.Name
    $taskJob.Backup=Join-Path $taskBackupRoot (($taskJob.Name -replace '\.jar$','')+'-before-'+$taskTag+'.jar')
    $taskJob.Staged=Join-Path $taskPublishRoot $taskJob.Name
    $taskJob.AtomicBackup=Join-Path $taskPublishRoot ($taskJob.Name+'.atomic-old')
    if((Get-FileHash -LiteralPath $taskJob.Source -Algorithm SHA256).Hash -ne $taskJob.NewHash){throw 'Verified candidate changed'}
    if((Get-FileHash -LiteralPath $taskJob.Target -Algorithm SHA256).Hash -ne $taskJob.OldHash){throw 'Release changed since verification'}
    if(Test-Path -LiteralPath $taskJob.Backup){
        if(-not $Resume -or (Get-FileHash -LiteralPath $taskJob.Backup -Algorithm SHA256).Hash -ne $taskJob.OldHash){throw 'Backup path already exists or does not match'}
    }
}
$taskOther=@{}
Get-ChildItem -LiteralPath $taskJarRoot -File -Filter '*.jar' | Where-Object {$_.Name -notin @('Hung_By_Son.jar','Hung_By_Son_x3.jar')} | ForEach-Object{
    $taskOther[$_.FullName]=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
}
$taskGolden=Join-Path $taskWorkspace 'inputs/luna_final-golden-baseline.jar'
$taskGoldenHash=(Get-FileHash -LiteralPath $taskGolden -Algorithm SHA256).Hash
if(-not(Test-Path -LiteralPath $taskPublishRoot)){New-Item -ItemType Directory -Path $taskPublishRoot | Out-Null}
if(-not(Test-Path -LiteralPath $taskBackupRoot)){New-Item -ItemType Directory -Path $taskBackupRoot | Out-Null}
foreach($taskJob in $taskJobs){
    if(-not(Test-Path -LiteralPath $taskJob.Backup)){Copy-Item -LiteralPath $taskJob.Target -Destination $taskJob.Backup}
    if((Get-FileHash -LiteralPath $taskJob.Backup -Algorithm SHA256).Hash -ne $taskJob.OldHash){throw 'Backup hash mismatch'}
    if(-not(Test-Path -LiteralPath $taskJob.Staged)){Copy-Item -LiteralPath $taskJob.Source -Destination $taskJob.Staged}
    if((Get-FileHash -LiteralPath $taskJob.Staged -Algorithm SHA256).Hash -ne $taskJob.NewHash){throw 'Staged copy hash mismatch'}
}
foreach($taskJob in $taskJobs){
    if((Get-FileHash -LiteralPath $taskJob.Target -Algorithm SHA256).Hash -ne $taskJob.OldHash){throw 'Release changed before replacement'}
    if(Test-Path -LiteralPath $taskJob.AtomicBackup){throw 'Atomic backup path already exists'}
    [IO.File]::Replace($taskJob.Staged,$taskJob.Target,$taskJob.AtomicBackup)
    if((Get-FileHash -LiteralPath $taskJob.AtomicBackup -Algorithm SHA256).Hash -ne $taskJob.OldHash){throw 'Atomic backup mismatch'}
    if((Get-FileHash -LiteralPath $taskJob.Target -Algorithm SHA256).Hash -ne $taskJob.NewHash){throw 'Published hash mismatch'}
}
foreach($taskPath in $taskOther.Keys){if((Get-FileHash -LiteralPath $taskPath -Algorithm SHA256).Hash -ne $taskOther[$taskPath]){throw 'Unrelated endpoint changed'}}
if((Get-FileHash -LiteralPath $taskGolden -Algorithm SHA256).Hash -ne $taskGoldenHash){throw 'Golden changed'}
[ordered]@{
    Published=$true;VerifiedStage=$taskBuild;Jobs=$taskJobs
    OtherEndpointsUnchanged=$true;GoldenUnchanged=$true;LiveServerTested=$false
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $taskBuild 'publish.json') -Encoding UTF8
$taskJobs | ForEach-Object {Write-Output ('PUBLISHED='+$_.Target+' SHA256='+$_.NewHash+' BACKUP='+$_.Backup)}
