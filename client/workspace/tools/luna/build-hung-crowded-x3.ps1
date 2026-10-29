[CmdletBinding()]
param(
    [string]$InputJar='build\hung-crowded-monsters-20261011-e\Hung_By_Son_crowded_fix.jar',
    [string]$DependencyDir='',
    [string]$BuildTag=('hung-crowded-x3-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
)
$ErrorActionPreference='Stop'
function Run-Checked {
    param([string]$Program,[string[]]$Arguments,[string]$Log)
    $taskPrevious=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        $taskMessages=& $Program @Arguments 2>&1
        $taskExit=$LASTEXITCODE
        $taskMessages | ForEach-Object {$_.ToString()} | Set-Content -Encoding UTF8 -LiteralPath $Log
    } finally {$ErrorActionPreference=$taskPrevious}
    if($taskExit -ne 0){Get-Content -LiteralPath $Log -Tail 16;throw "$Program failed: $taskExit"}
    $taskMessages | Select-Object -Last 1
}
if($BuildTag -notmatch '^[A-Za-z0-9_-]+$'){throw 'Invalid build tag'}
$taskWorkspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $taskWorkspace
try {
    $taskSource=(Resolve-Path -LiteralPath $InputJar).Path
    $taskSourceHash='F806EE5F395B6979057ECF49DA4C10E7313D39A6F6AB23A10D04E88347A4B892'
    if((Get-FileHash -Algorithm SHA256 -LiteralPath $taskSource).Hash -ne $taskSourceHash){throw 'Unexpected crowded-cache source JAR'}
    $taskSourceBuild=Join-Path $taskWorkspace 'build/hung-crowded-monsters-20261011-e'
    $taskSourceProof=Get-Content -Encoding UTF8 -LiteralPath (Join-Path $taskSourceBuild 'verified.json') -Raw | ConvertFrom-Json
    if(-not $taskSourceProof.Verified -or $taskSourceProof.SHA256 -ne $taskSourceHash -or $taskSourceProof.TimingMs -ne 650){throw 'Missing matching source verification'}
    foreach($taskProof in @{
        'HungBySonCrowdedMonsterHostTest.txt'='PASS (45044 checks';
        'HungBySonCrowdedMonsterBinaryTest.txt'='CROWDED_MONSTER_BINARY=PASS';
        'remote-animation-barrier.txt'='PASS (321 checks)';
        'real650-test.txt'='PASS (2502 checks)';
        'LunaSemanticHostTest.txt'='OK -';
        'LunaControllerHostTest.txt'='OK -';
        'LunaRouteHostTest.txt'='OK -'
    }.GetEnumerator()){
        $taskProofText=[IO.File]::ReadAllText((Join-Path $taskSourceBuild $taskProof.Key))
        if(-not $taskProofText.Contains($taskProof.Value)){throw "Source verification incomplete: $($taskProof.Key)"}
    }
    $taskTemplate=(Resolve-Path -LiteralPath '../jar/backup/autotrade_v27_revive05_horse_x3.jar').Path
    $taskTemplateHash='5DC9E9821904617B8A8171F45530DA3E078E6492EC6E37580E160AE6AC1732DC'
    if((Get-FileHash -Algorithm SHA256 -LiteralPath $taskTemplate).Hash -ne $taskTemplateHash){throw 'Unexpected launcher template'}
    if([string]::IsNullOrWhiteSpace($DependencyDir)){
        $DependencyDir=if($env:LUNA_DEPS){$env:LUNA_DEPS}else{Join-Path $taskWorkspace '../../.codex-deps'}
    }
    $taskDeps=(Resolve-Path -LiteralPath $DependencyDir).Path
    $taskAsm=(Join-Path $taskDeps 'asm-9.7.jar')+';'+(Join-Path $taskDeps 'asm-tree-9.7.jar')
    $taskMicro=Join-Path $taskDeps 'microemulator.jar'
    foreach($taskDep in @('asm-9.7.jar','asm-tree-9.7.jar','microemulator.jar')){
        if(-not(Test-Path -LiteralPath (Join-Path $taskDeps $taskDep) -PathType Leaf)){throw "Missing dependency $taskDep"}
    }
    if(-not(Test-Path -LiteralPath 'fixtures/x3/HungBySonCrowdedCacheX3HostTest.java' -PathType Leaf)){
        throw 'Missing crowded-cache X3 regression fixture'
    }
    $taskBuild=Join-Path $taskWorkspace (Join-Path 'build' $BuildTag)
    if(Test-Path -LiteralPath $taskBuild){throw 'Build tag already exists'}
    $taskTools=Join-Path $taskBuild 'tools';$taskSupport=Join-Path $taskBuild 'support';$taskHost=Join-Path $taskBuild 'host'
    $taskRuntime=Join-Path $taskBuild 'runtime-home';$taskScreens=Join-Path $taskBuild 'screenshots'
    New-Item -ItemType Directory -Path $taskTools,$taskSupport,$taskHost,$taskRuntime,$taskScreens | Out-Null
    Run-Checked javac @('-encoding','UTF-8','--release','7','-cp',$taskAsm,'-d',$taskTools,
        'tools/luna/HungBySonX3Packer.java','tools/legacy/luna/HsoChildX3Packer.java',
        'tools/legacy/luna/RepackSupportX3.java') (Join-Path $taskBuild 'tools-compile.txt')
    Copy-Item -LiteralPath 'tools/legacy/luna/x3/X3SlotSupport.java.in' -Destination (Join-Path $taskSupport 'X3SlotSupport.java')
    Run-Checked javac @('-encoding','UTF-8','--release','7','-d',$taskSupport,
        (Join-Path $taskSupport 'X3SlotSupport.java')) (Join-Path $taskBuild 'helper-compile.txt')
    $taskX3=Join-Path $taskBuild 'Hung_By_Son_x3.jar'
    Run-Checked java @('-Xverify:all','-cp',"$taskTools;$taskAsm",'HungBySonX3Packer',
        $taskSource,$taskTemplate,$taskX3,(Join-Path $taskSupport 'X3SlotSupport.class'),
        (Join-Path $taskBuild 'x3-pack-report.txt'),$taskSourceHash) (Join-Path $taskBuild 'x3-pack.txt')
    $taskX3Hash=(Get-FileHash -Algorithm SHA256 -LiteralPath $taskX3).Hash
    $taskCompileCp="$taskTools;$taskAsm;$taskMicro"
    Run-Checked javac @('-encoding','UTF-8','-cp',$taskCompileCp,'-d',$taskHost,
        'fixtures/x3/HungBySonX3HostTest.java','fixtures/x3/HsoChildX3LauncherTest.java') (Join-Path $taskBuild 'fixtures-compile.txt')
    $taskHostCp="$taskHost;$taskCompileCp"
    Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonX3HostTest',
        $taskX3,$taskSource,(Join-Path $taskSourceBuild 'real650-validation.properties')) (Join-Path $taskBuild 'x3-host.txt')
    $taskLauncherArgs=@('-Xverify:all',"-Duser.home=$taskRuntime",'-Djava.awt.headless=true',
        '-cp',"$taskHost;$taskMicro",'HsoChildX3LauncherTest',$taskX3,$taskScreens,'Hung By Son')
    $taskPrevious=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        & java @taskLauncherArgs 1> (Join-Path $taskBuild 'launcher.out.log') 2> (Join-Path $taskBuild 'launcher.err.log')
        $taskLauncherExit=$LASTEXITCODE
    } finally {$ErrorActionPreference=$taskPrevious}
    if($taskLauncherExit -ne 0 -or (Get-Item -LiteralPath (Join-Path $taskBuild 'launcher.err.log')).Length -ne 0){throw 'Launcher test failed or stderr was not empty'}
    if(Select-String -LiteralPath (Join-Path $taskBuild 'launcher.out.log') -Pattern 'Exception|Error|not found|loi load'){throw 'Launcher stdout contains a runtime error'}
    Write-Output 'LAUNCHER=PASS; three live clients, held-* and 1 -> 2 -> 3 -> 1'
    # This extra regression verifies the current repair at runtime in all slots.
    Run-Checked javac @('-encoding','UTF-8','-d',$taskHost,
        'fixtures/x3/HungBySonCrowdedCacheX3HostTest.java') (Join-Path $taskBuild 'crowded-fixture-compile.txt')
    Run-Checked java @('-Xverify:all','-cp',$taskHostCp,'HungBySonCrowdedCacheX3HostTest',
        $taskX3) (Join-Path $taskBuild 'crowded-x3-host.txt')
    if((Get-FileHash -Algorithm SHA256 -LiteralPath $taskSource).Hash -ne $taskSourceHash -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskTemplate).Hash -ne $taskTemplateHash){throw 'Packaging inputs changed'}
    [ordered]@{
        Verified=$true;X3=$taskX3;X3SHA256=$taskX3Hash;Source=$taskSource;SourceSHA256=$taskSourceHash
        TemplateSHA256=$taskTemplateHash;MinimumMs=650;Classes=751;Namespaces=@('default','b/','c/')
        LiveServerTested=$false;Published=$false;Screenshots=$taskScreens
    } | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'verified.json')
    Write-Output "VERIFIED_X3=$taskX3; SHA256=$taskX3Hash"
} finally {Pop-Location}
