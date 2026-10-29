[CmdletBinding()]
param(
    [string]$InputJar = '..\jar\ModUpLv.jar',
    [string]$DependencyDir = '',
    [string]$BuildTag = ('moduplv-skill-stats-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
)

# ModUpLv_skill_stats.jar: ModUpLv.jar plus the "Chi tiết tung chiêu" counters/table of
# Hung_By_Son_copy_special_skill (same SkillCastStats), for comparing miss rates. ModUpLv's
# attack logic is untouched; the input file is not replaced.
$ErrorActionPreference = 'Stop'
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskWorkspace
if ([string]::IsNullOrWhiteSpace($DependencyDir)) {
    if (-not [string]::IsNullOrWhiteSpace($env:LUNA_DEPS)) { $DependencyDir = $env:LUNA_DEPS }
    else { $DependencyDir = '..\..\.codex-deps' }
}
$taskExpectedInputHash = 'E282B0AAD121205F9A0CFAF14104C9A9ED281B6838AE22D7FEDD4004E83F997C'

function Run-Checked {
    param([string]$Program, [string[]]$Arguments, [string]$Log)
    $taskPriorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $taskLines = @(& $Program @Arguments 2>&1 | ForEach-Object { $_.ToString() })
        $taskExit = $LASTEXITCODE
        $taskLines | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild $Log)
    } finally { $ErrorActionPreference = $taskPriorPreference }
    if ($taskExit -ne 0) {
        Get-Content -LiteralPath (Join-Path $taskBuild $Log) -Tail 24
        throw "$Program failed with exit code $taskExit ($Log)"
    }
    if ($taskLines.Count -gt 0) { $taskLines | Select-Object -Last 1 }
}

if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid BuildTag' }
$taskInput = (Resolve-Path -LiteralPath $InputJar).Path
$taskInputHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskInput).Hash
if (-not $taskInputHash.Equals($taskExpectedInputHash, [StringComparison]::OrdinalIgnoreCase)) {
    throw "InputJar must be the pinned ModUpLv.jar ($taskExpectedInputHash); got $taskInputHash"
}
$taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
$taskAsmCp = (Join-Path $taskDeps 'asm-9.7.jar') + ';' + (Join-Path $taskDeps 'asm-tree-9.7.jar')
$taskMicro = Join-Path $taskDeps 'microemulator.jar'
foreach ($taskFile in @('tools/luna/ModUpLvSkillStatsPatcher.java', 'tools/luna/HungBySonExpInfoPack.java',
    'tools/luna/moduplv/ModUpLvSkillStats.java', 'tools/luna/moduplv/ModUpLvStatsAccess.java',
    'src/luna/features/SkillCastStats.java', 'src/luna/features/SkillCastLog.java', 'symbols/moduplv-aliases.properties',
    'fixtures/luna/SkillCastStatsHostTest.java', 'fixtures/hung-by-son/ModUpLvSkillStatsUiTest.java',
    'fixtures/hung-by-son/HungBySonExpInfoBinaryTest.java', $taskMicro)) {
    if (-not (Test-Path -LiteralPath $taskFile -PathType Leaf)) { throw "Required file is missing: $taskFile" }
}

$taskBuild = Join-Path $taskWorkspace ('build/' + $BuildTag)
if (Test-Path -LiteralPath $taskBuild) { throw 'BuildTag already exists; use a fresh unique tag' }
New-Item -ItemType Directory -Path $taskBuild, (Join-Path $taskBuild 'support'), (Join-Path $taskBuild 'tools'), (Join-Path $taskBuild 'host') | Out-Null
$taskSupport = Join-Path $taskBuild 'support'
$taskTools = Join-Path $taskBuild 'tools'
$taskHost = Join-Path $taskBuild 'host'
$taskCandidate = Join-Path $taskBuild 'ModUpLv_skill_stats.jar'
$taskCatalog = Join-Path $taskBuild 'skill-stats.properties'

Run-Checked javac @('-encoding', 'UTF-8', '-cp', $taskAsmCp, '-d', $taskTools,
    'tools/luna/ModUpLvSkillStatsPatcher.java', 'tools/luna/HungBySonExpInfoPack.java') 'tools-compile.txt'
Run-Checked javac @('-encoding', 'UTF-8', '--release', '7', '-nowarn', '-d', $taskSupport,
    'src/luna/features/SkillCastStats.java', 'src/luna/features/SkillCastLog.java', 'tools/luna/moduplv/ModUpLvStatsAccess.java', 'tools/luna/moduplv/ModUpLvSkillStats.java') 'support-compile.txt'
Run-Checked java @('-cp', "$taskTools;$taskAsmCp", 'ModUpLvSkillStatsPatcher',
    $taskInput, 'symbols/moduplv-aliases.properties', $taskSupport, $taskCandidate, (Join-Path $taskBuild 'skill-stats')) 'patch.txt'
$taskCandidateHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskCandidate).Hash

Run-Checked javac @('-encoding', 'UTF-8', '--release', '7', '-nowarn', '-cp', $taskSupport, '-d', $taskHost, 'fixtures/luna/SkillCastStatsHostTest.java') 'host-compile.txt'
Run-Checked java @('-Xverify:all', '-cp', "$taskSupport;$taskHost", 'SkillCastStatsHostTest') 'SkillCastStatsHostTest.txt'
Run-Checked javac @('-encoding', 'UTF-8', '-nowarn', '-cp', "$taskCandidate;$taskAsmCp;$taskMicro", '-d', $taskHost,
    'fixtures/hung-by-son/HungBySonExpInfoBinaryTest.java', 'fixtures/hung-by-son/ModUpLvSkillStatsUiTest.java') 'binary-compile.txt'
Run-Checked java @('-Dfile.encoding=UTF-8', '-cp', "$taskHost;$taskMicro", 'ModUpLvSkillStatsUiTest',
    $taskCandidate, 'symbols/moduplv-aliases.properties', $taskCatalog, $taskMicro, (Join-Path $taskBuild 'screens')) 'ModUpLvSkillStatsUiTest.txt'

[ordered]@{
    Verified = $true
    Published = $false
    Variant = 'ModUpLv-skill-stats'
    SkillCastTab = $true
    MissWindowMs = 2000
    AttackLogicUnchanged = $true
    LiveServerTested = $false
    Input = $taskInput
    InputSHA256 = $taskInputHash
    Candidate = $taskCandidate
    SHA256 = $taskCandidateHash
    Catalog = $taskCatalog
} | ConvertTo-Json -Depth 3 | Set-Content -Encoding UTF8 (Join-Path $taskBuild 'verified.json')
Write-Output "VERIFIED=$taskCandidate SHA256=$taskCandidateHash"
