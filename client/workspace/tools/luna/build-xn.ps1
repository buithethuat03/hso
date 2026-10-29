[CmdletBinding()]
param(
    [Parameter(Mandatory=$true,Position=0)][string]$InputJar,
    [Parameter(Mandatory=$true,Position=1)][ValidateRange(2,26)][int]$Count,
    [string]$OutputJar,
    [string]$Name,
    [string]$DependencyDir=$env:LUNA_DEPS
)
$ErrorActionPreference='Stop'
$taskTimer=[Diagnostics.Stopwatch]::StartNew()
$taskWorkspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskCache=Join-Path $taskWorkspace 'build/xn-cache'
$taskDepsConfig=Join-Path $taskCache 'dependencies.json'
function Invoke-XnTool([string]$Program,[string[]]$ToolArgs) {
    & $Program @ToolArgs
    if($LASTEXITCODE -ne 0){throw "$Program failed ($LASTEXITCODE); no release published"}
}
function Get-XnDependency([string]$FileName,[string[]]$Roots) {
    foreach($taskRoot in $Roots){
        if($taskRoot -and (Test-Path -LiteralPath (Join-Path $taskRoot $FileName))){return (Resolve-Path -LiteralPath (Join-Path $taskRoot $FileName)).Path}
    }
    throw "Missing $FileName. Set LUNA_DEPS or pass -DependencyDir once; paths will be cached."
}
$taskInput=(Resolve-Path -LiteralPath $InputJar).Path
if([IO.Path]::GetExtension($taskInput) -ine '.jar'){throw 'Input must be a JAR'}
$taskBase=[IO.Path]::GetFileNameWithoutExtension($taskInput) -replace '(?i)[_-]x1$',''
if(-not $OutputJar){$OutputJar=Join-Path ([IO.Path]::GetDirectoryName($taskInput)) ($taskBase+'_x'+$Count+'.jar')}
$taskOutput=[IO.Path]::GetFullPath($OutputJar)
$taskGolden=[IO.Path]::GetFullPath((Join-Path $taskWorkspace 'inputs/luna_final-golden-baseline.jar'))
if($taskOutput -ieq $taskInput -or $taskOutput -ieq $taskGolden){throw 'Output must not overwrite input or the golden baseline'}
if(-not $Name){$Name=($taskBase -replace '_',' ').ToUpperInvariant()+' X'+$Count}
$taskTemplate=Join-Path $taskWorkspace '../jar/backup/autotrade_v27_revive05_horse_x3.jar'
if(-not(Test-Path -LiteralPath $taskTemplate)){throw "Missing pinned launcher: $taskTemplate"}
New-Item -ItemType Directory -Force -Path $taskCache | Out-Null
$taskDeps=$null
if(-not $DependencyDir -and (Test-Path -LiteralPath $taskDepsConfig)){
    $taskDeps=Get-Content -Raw -LiteralPath $taskDepsConfig | ConvertFrom-Json
    foreach($taskPath in @($taskDeps.asm,$taskDeps.tree)){if(-not(Test-Path -LiteralPath $taskPath)){$taskDeps=$null;break}}
}
if(-not $taskDeps){
    # External dependency caches only. No download, recursive scan or repo copy.
    $taskRoots=@($DependencyDir)
    if(-not $DependencyDir){$taskRoots+=@(Get-ChildItem -LiteralPath $env:TEMP -Directory -Filter 'hso-*-deps-*' | Sort-Object LastWriteTime -Descending | ForEach-Object FullName)}
    $taskDeps=[pscustomobject]@{
        asm=(Get-XnDependency 'asm-9.7.jar' $taskRoots)
        tree=(Get-XnDependency 'asm-tree-9.7.jar' $taskRoots)
    }
    $taskDeps | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath $taskDepsConfig
}
$taskJava=(Get-Command java -ErrorAction Stop).Source
$taskJavac=(Get-Command javac -ErrorAction Stop).Source
$taskSources=@('tools/legacy/luna/RepackSupportX3.java','tools/legacy/luna/HsoChildX3Packer.java','tools/luna/MultiClientPacker.java','tools/legacy/luna/x3/X3SlotSupport.java.in') | ForEach-Object {Join-Path $taskWorkspace $_}
$taskFingerprint=(@($taskSources)+@($taskDeps.asm,$taskDeps.tree) | ForEach-Object {(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash}) -join ':'
$taskFingerprint+=':'+$taskJavac
$taskHashProvider=[Security.Cryptography.SHA256]::Create()
try{$taskKey=([BitConverter]::ToString($taskHashProvider.ComputeHash([Text.Encoding]::UTF8.GetBytes($taskFingerprint)))).Replace('-','').Substring(0,20)}finally{$taskHashProvider.Dispose()}
$taskCompiled=Join-Path $taskCache $taskKey
$taskAsm=$taskDeps.asm+';'+$taskDeps.tree
if(-not(Test-Path -LiteralPath (Join-Path $taskCompiled 'ready'))){
    New-Item -ItemType Directory -Force -Path $taskCompiled | Out-Null
    Copy-Item -LiteralPath $taskSources[3] -Destination (Join-Path $taskCompiled 'X3SlotSupport.java')
    Invoke-XnTool $taskJavac (@('-encoding','UTF-8','--release','7','-nowarn','-cp',$taskAsm,'-d',$taskCompiled)+$taskSources[0..2])
    Invoke-XnTool $taskJavac @('-encoding','UTF-8','--release','7','-nowarn','-d',$taskCompiled,(Join-Path $taskCompiled 'X3SlotSupport.java'))
    [IO.File]::WriteAllText((Join-Path $taskCompiled 'ready'),$taskKey)
}
$taskBuild=Join-Path $taskWorkspace ('build/xn-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,6))
New-Item -ItemType Directory -Path $taskBuild | Out-Null
$taskStaged=Join-Path $taskBuild ([IO.Path]::GetFileName($taskOutput))
$taskReport=Join-Path $taskBuild 'pack-report.txt'
Invoke-XnTool $taskJava @('-cp',($taskCompiled+';'+$taskAsm),'MultiClientPacker',$taskInput,$taskTemplate,$taskStaged,(Join-Path $taskCompiled 'X3SlotSupport.class'),$taskReport,[string]$Count,$Name)
$taskHash=(Get-FileHash -LiteralPath $taskStaged).Hash
$taskReportText=[IO.File]::ReadAllText($taskReport)
if($taskReportText -notmatch ('(?m)^output\.sha256='+$taskHash+'\r?$')){throw 'Pack report checksum mismatch'}
$taskOutputDir=[IO.Path]::GetDirectoryName($taskOutput)
New-Item -ItemType Directory -Force -Path $taskOutputDir | Out-Null
$taskBackup=$null
if(Test-Path -LiteralPath $taskOutput){
    $taskBackupDir=Join-Path $taskOutputDir 'backup'
    if([IO.Path]::GetFileName($taskOutput) -like 'luna*'){$taskBackupDir=Join-Path $taskWorkspace 'inputs/releases'}
    New-Item -ItemType Directory -Force -Path $taskBackupDir | Out-Null
    $taskBackup=Join-Path $taskBackupDir ([IO.Path]::GetFileNameWithoutExtension($taskOutput)+'-before-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')+'.jar')
    [IO.File]::Copy($taskOutput,$taskBackup,$false)
    if((Get-FileHash -LiteralPath $taskBackup).Hash -ne (Get-FileHash -LiteralPath $taskOutput).Hash){throw 'Backup checksum mismatch'}
}
[IO.File]::Copy($taskStaged,$taskOutput,$true)
if((Get-FileHash -LiteralPath $taskOutput).Hash -ne $taskHash){throw 'Published checksum mismatch; staged output and backup retained'}
$taskRecord="`r`n### x$Count fast package - $([DateTime]::Now.ToString('yyyy-MM-dd HH:mm:ss'))`r`n`r`n"
$taskRecord+='- Output: `'+$taskOutput+'`; SHA-256 `'+$taskHash+'`.'+"`r`n"
$taskRecord+='- Source: `'+$taskInput+'`; SHA-256 `'+(Get-FileHash -LiteralPath $taskInput).Hash+'`.'+"`r`n"
$taskRecord+='- Report: `'+$taskReport+'`. Archive/input/resource checks only; emulator and live login not rerun.'+"`r`n"
if($taskOutputDir -ieq [IO.Path]::GetFullPath((Join-Path $taskWorkspace '../jar'))){
    [IO.File]::AppendAllText((Join-Path $taskWorkspace 'docs/RELEASES.md'),$taskRecord,(New-Object Text.UTF8Encoding($false)))
}
Write-Output ('OUTPUT='+$taskOutput)
Write-Output ('REPORT='+$taskReport)
if($taskBackup){Write-Output ('BACKUP='+$taskBackup)}
Write-Output ('DONE in '+$taskTimer.Elapsed.TotalSeconds.ToString('0.0')+' seconds; fast archive checks only')
