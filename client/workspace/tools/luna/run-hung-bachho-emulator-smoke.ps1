[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Jar,
    [Parameter(Mandatory=$true)][string]$DependencyDir,
    [Parameter(Mandatory=$true)][string]$ReportDir
)
$ErrorActionPreference = 'Stop'
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskJar = (Resolve-Path -LiteralPath $Jar).Path
$taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
$taskReport = [IO.Path]::GetFullPath($ReportDir)
if (-not $taskReport.StartsWith($taskWorkspace + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Emulator output must stay within the client workspace'
}
$taskMicro = Join-Path $taskDeps 'microemulator.jar'
if (-not (Test-Path -LiteralPath $taskMicro -PathType Leaf)) { throw 'MicroEmulator dependency is missing' }
New-Item -ItemType Directory -Force -Path $taskReport | Out-Null
$taskHome = Join-Path $taskReport 'emulator-home'
if (Test-Path -LiteralPath $taskHome) { throw 'Use a fresh emulator report directory' }
New-Item -ItemType Directory -Path $taskHome | Out-Null
$taskOut = Join-Path $taskReport 'emulator-stdout.txt'
$taskErr = Join-Path $taskReport 'emulator-stderr.txt'
$taskArguments = @('-Duser.home="' + $taskHome + '"', '-cp', '"' + $taskMicro + '"',
    'org.microemu.app.Headless', '"' + $taskJar + '"', '--rms', 'memory')
$taskProcess = Start-Process -FilePath (Get-Command java).Source -ArgumentList $taskArguments `
    -WindowStyle Hidden -PassThru -RedirectStandardOutput $taskOut -RedirectStandardError $taskErr
try {
    $taskExited = $taskProcess.WaitForExit(12000)
    $taskRecord = [ordered]@{ ProcessId=$taskProcess.Id; ObservedSeconds=12; EndedEarly=$taskExited;
        Jar=$taskJar; SHA256=(Get-FileHash -Algorithm SHA256 -LiteralPath $taskJar).Hash;
        Scope='isolated headless startup with memory RMS; no account login' }
    $taskRecord | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskReport 'emulator-run.json')
    if ($taskExited) { throw 'Emulator terminated during startup observation' }
} finally {
    if (-not $taskProcess.HasExited) { Stop-Process -Id $taskProcess.Id; $taskProcess.WaitForExit() }
    $taskProcess.Dispose()
}
$taskErrors = @(Select-String -LiteralPath $taskOut,$taskErr -Pattern 'Exception|VerifyError|LinkageError|NoClassDefFoundError|Error during')
if ($taskErrors.Count -gt 0) { $taskErrors; throw 'Emulator startup recorded an error' }
if ((Get-Item -LiteralPath $taskErr).Length -ne 0) { Get-Content -LiteralPath $taskErr; throw 'Emulator stderr is not empty' }
Write-Output 'EMULATOR_STARTUP=PASS (12 seconds, isolated memory RMS, empty stderr)'
