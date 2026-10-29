[CmdletBinding()]
param(
    [string]$InputJar = '..\jar\Hung_By_Son.jar',
    [string]$ReferenceJar = '..\jar\bachho_final.jar',
    [string]$DependencyDir = '',
    [string]$BuildTag = ('hung-auto-attack-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
)

$ErrorActionPreference = 'Stop'
$taskExpectedInputHash = '188C481E59318B27468F29DE7420C90FE1E9027BD77A22B4710A312FD6BDB2AD'
$taskExpectedDonorHash = 'A8A4165F0E78C7E852444A425CB8C0611BF772A70F6D44B1374091225732FF0E'
$taskExpectedGoldenHash = '06622CD81DC6790871A5863D465D4E352B29A94BB6FD0062C54F7CF9C6C47E0F'
$taskForbiddenClcdRefs = @(
    'java/lang/StringBuilder', 'java/util/Arrays', 'java/util/Collection', 'java/util/Collections',
    'java/util/Iterator', 'java/util/List', 'java/util/ListIterator', 'java/util/Map', 'java/util/Set',
    'java/util/ArrayList', 'java/util/LinkedList', 'java/util/HashMap', 'java/util/LinkedHashMap',
    'java/util/HashSet', 'java/util/LinkedHashSet', 'java/util/TreeMap', 'java/util/TreeSet',
    'java/util/Queue', 'java/util/Deque', 'java/util/PriorityQueue', 'java/util/concurrent/',
    'java/lang/invoke/', 'java/nio/', 'java/time/', 'java/util/function/', 'java/util/stream/'
)

function Run-Checked {
    param([string]$Program, [string[]]$Arguments, [string]$Log)
    $taskPriorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $taskLines = @(& $Program @Arguments 2>&1 | ForEach-Object { $_.ToString() })
        $taskExit = $LASTEXITCODE
        $taskLines | Set-Content -Encoding UTF8 -LiteralPath $Log
    } finally { $ErrorActionPreference = $taskPriorPreference }
    if ($taskExit -ne 0) {
        Get-Content -LiteralPath $Log -Tail 24
        throw "$Program failed with exit code $taskExit"
    }
    if ($taskLines.Count -gt 0) { $taskLines | Select-Object -Last 1 }
}

function Get-PropertyValue {
    param([string]$Text, [string]$Name)
    $taskPattern = '(?m)^' + [regex]::Escape($Name) + '=([^\r\n]*)'
    $taskMatches = [regex]::Matches($Text, $taskPattern)
    if ($taskMatches.Count -ne 1) { throw "Expected exactly one '$Name' property" }
    return $taskMatches[0].Groups[1].Value.Trim()
}

function Set-PropertyLine {
    param([string]$Text, [string]$Name, [string]$Value)
    $taskPattern = '(?m)^' + [regex]::Escape($Name) + '=[^\r\n]*'
    if ([regex]::Matches($Text, $taskPattern).Count -ne 1) { throw "Expected exactly one '$Name' property" }
    return [regex]::Replace($Text, $taskPattern, ($Name + '=' + $Value), 1)
}

function Set-OptionalPropertyLine {
    param([string]$Text, [string]$Name, [string]$Value)
    $taskPattern = '(?m)^' + [regex]::Escape($Name) + '=[^\r\n]*'
    if ([regex]::Matches($Text, $taskPattern).Count -eq 1) {
        return [regex]::Replace($Text, $taskPattern, ($Name + '=' + $Value), 1)
    }
    return $Text
}

function Convert-ClassNameToEntry {
    param([string]$Name)
    $taskValue = $Name.Trim()
    if ([string]::IsNullOrWhiteSpace($taskValue)) { throw 'Empty class name in patch report/catalog' }
    if ($taskValue.EndsWith('.class', [StringComparison]::Ordinal)) { return $taskValue }
    if ($taskValue.Contains('.')) { $taskValue = $taskValue.Replace('.', '/') }
    return ($taskValue + '.class')
}

function Get-ClassList {
    param([string]$Text, [string]$Name)
    $taskValue = Get-PropertyValue $Text $Name
    if ([string]::IsNullOrWhiteSpace($taskValue)) { return @() }
    $taskItems = @($taskValue.Split(',') | ForEach-Object { Convert-ClassNameToEntry $_ })
    if (@($taskItems | Sort-Object -Unique).Count -ne $taskItems.Count) { throw "Duplicate class in '$Name'" }
    return $taskItems
}

function Get-ReportClassList {
    param([string]$ReportPath, [string]$Prefix)
    $taskLines = @(Get-Content -Encoding UTF8 -LiteralPath $ReportPath)
    $taskItems = @($taskLines | Where-Object { $_.StartsWith($Prefix, [StringComparison]::Ordinal) } |
        ForEach-Object { Convert-ClassNameToEntry $_.Substring($Prefix.Length).Trim() })
    if ($taskItems.Count -eq 0) {
        $taskAggregate = if ($Prefix -eq 'CHANGED_CLASS=') { 'CHANGED_CLASSES=' } else { 'ADDED_CLASSES=' }
        $taskAggregateLines = @($taskLines | Where-Object { $_.StartsWith($taskAggregate, [StringComparison]::Ordinal) })
        if ($taskAggregateLines.Count -eq 1) {
            $taskValue = $taskAggregateLines[0].Substring($taskAggregate.Length).Trim()
            if (-not [string]::IsNullOrWhiteSpace($taskValue)) {
                $taskItems = @($taskValue.Split(',') | ForEach-Object { Convert-ClassNameToEntry $_ })
            }
        }
    }
    if (@($taskItems | Sort-Object -Unique).Count -ne $taskItems.Count) { throw "Duplicate '$Prefix' row in patch report" }
    return $taskItems
}

function Get-JarDirectorySnapshot {
    param([string]$Directory)
    $taskSnapshot = [System.Collections.Generic.Dictionary[string, string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($taskJar in @(Get-ChildItem -LiteralPath $Directory -File -Filter '*.jar' | Sort-Object Name)) {
        $taskHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskJar.FullName).Hash
        if ($taskSnapshot.ContainsKey($taskJar.Name)) { throw "Duplicate JAR filename in release directory: $($taskJar.Name)" }
        $taskSnapshot.Add($taskJar.Name, $taskHash)
    }
    return ,$taskSnapshot
}

function Assert-JarDirectorySnapshot {
    param([System.Collections.Generic.Dictionary[string, string]]$Before, [string]$Directory)
    $taskAfter = Get-JarDirectorySnapshot $Directory
    if ($Before.Count -ne $taskAfter.Count) { throw 'JAR endpoint set changed during staging' }
    foreach ($taskName in $Before.Keys) {
        if (-not $taskAfter.ContainsKey($taskName) -or $Before[$taskName] -cne $taskAfter[$taskName]) {
            throw "JAR endpoint changed during staging: $taskName"
        }
    }
    return ,$taskAfter
}

function Get-ZipEntryMap {
    param([System.IO.Compression.ZipArchive]$Archive)
    $taskMap = [System.Collections.Generic.Dictionary[string, System.IO.Compression.ZipArchiveEntry]]::new([StringComparer]::Ordinal)
    foreach ($taskEntry in $Archive.Entries) {
        if ($taskMap.ContainsKey($taskEntry.FullName)) { throw "Duplicate ZIP entry: $($taskEntry.FullName)" }
        $taskMap.Add($taskEntry.FullName, $taskEntry)
    }
    return ,$taskMap
}

function Read-ZipEntryBytes {
    param([System.IO.Compression.ZipArchiveEntry]$Entry)
    $taskMemory = [IO.MemoryStream]::new()
    $taskStream = $Entry.Open()
    try { $taskStream.CopyTo($taskMemory); return ,$taskMemory.ToArray() }
    finally { $taskStream.Dispose(); $taskMemory.Dispose() }
}

function Assert-ArchiveDelta {
    param([string]$SourceJar, [string]$ReferenceJarPath, [string]$CandidateJar, [string]$CatalogPath, [string]$PatchReport, [string]$OutputPath)
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $taskCatalogText = [IO.File]::ReadAllText($CatalogPath)
    $taskReportChanged = @(Get-ReportClassList $PatchReport 'CHANGED_CLASS=')
    $taskReportAdded = @(Get-ReportClassList $PatchReport 'ADDED_CLASS=')
    if ($taskReportChanged.Count -eq 0) { throw 'Patcher report has no changed classes' }
    $taskCatalogChanged = @(Get-ClassList $taskCatalogText 'changed.classes')
    $taskCatalogAdded = @(Get-ClassList $taskCatalogText 'added.classes')
    if (@(Compare-Object ($taskReportChanged | Sort-Object) ($taskCatalogChanged | Sort-Object) -CaseSensitive).Count -ne 0) {
        throw 'Patcher changed-class report differs from catalog'
    }
    if (@(Compare-Object ($taskReportAdded | Sort-Object) ($taskCatalogAdded | Sort-Object) -CaseSensitive).Count -ne 0) {
        throw 'Patcher added-class report differs from catalog'
    }

    $taskBeforeZip = [IO.Compression.ZipFile]::OpenRead($SourceJar)
    $taskAfterZip = [IO.Compression.ZipFile]::OpenRead($CandidateJar)
    try {
        $taskBefore = Get-ZipEntryMap $taskBeforeZip
        $taskAfter = Get-ZipEntryMap $taskAfterZip
        $taskExpectedNames = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($taskName in $taskBefore.Keys) { [void]$taskExpectedNames.Add($taskName) }
        foreach ($taskName in $taskReportAdded) {
            if (-not $taskName.EndsWith('.class', [StringComparison]::Ordinal)) { throw "Added entry is not a class: $taskName" }
            if ($taskBefore.ContainsKey($taskName)) { throw "Reported addition already exists in source: $taskName" }
            [void]$taskExpectedNames.Add($taskName)
        }
        if ($taskAfter.Count -ne ($taskBefore.Count + $taskReportAdded.Count) -or $taskExpectedNames.Count -ne $taskAfter.Count) {
            throw 'Candidate archive topology differs from source plus reported new classes'
        }

        $taskReportedChanged = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($taskName in $taskReportChanged) {
            if (-not $taskBefore.ContainsKey($taskName) -or -not $taskAfter.ContainsKey($taskName)) {
                throw "Reported changed class is absent from source/candidate: $taskName"
            }
            [void]$taskReportedChanged.Add($taskName)
        }

        $taskActualChanged = [System.Collections.Generic.List[string]]::new()
        $taskInputClasses = 0; $taskOutputClasses = 0
        $taskInputResources = 0; $taskOutputResources = 0
        $taskMaximumMajor = 0
        foreach ($taskName in $taskBefore.Keys) {
            $taskOldBytes = Read-ZipEntryBytes $taskBefore[$taskName]
            if (-not $taskAfter.ContainsKey($taskName)) { throw "Source entry missing from candidate: $taskName" }
            $taskNewBytes = Read-ZipEntryBytes $taskAfter[$taskName]
            $taskSame = [HungAutoAttackBuildBytes]::Equal($taskOldBytes, $taskNewBytes)
            if ($taskName.EndsWith('.class', [StringComparison]::Ordinal)) {
                $taskInputClasses++
                $taskMajor = [HungAutoAttackBuildBytes]::Major($taskNewBytes)
                if ($taskMajor -gt 51) { throw "Candidate class exceeds the Java 7 class ceiling: $taskName ($taskMajor)" }
                if ($taskMajor -gt $taskMaximumMajor) { $taskMaximumMajor = $taskMajor }
                if (-not $taskSame) {
                    $taskActualChanged.Add($taskName)
                    if (-not $taskReportedChanged.Contains($taskName)) { throw "Unreported class modification: $taskName" }
                    $taskOldConstants = [System.Collections.Generic.HashSet[string]]::new(
                        [HungAutoAttackBuildBytes]::Utf8Constants($taskOldBytes), [StringComparer]::Ordinal)
                    foreach ($taskConstant in [HungAutoAttackBuildBytes]::Utf8Constants($taskNewBytes)) {
                        if ($taskOldConstants.Contains($taskConstant)) { continue }
                        foreach ($taskBadRef in $taskForbiddenClcdRefs) {
                            if ($taskConstant.IndexOf($taskBadRef, [StringComparison]::Ordinal) -ge 0) {
                                throw "New unsupported CLDC API reference '$taskBadRef' in modified class $taskName"
                            }
                        }
                    }
                } elseif ($taskReportedChanged.Contains($taskName)) { throw "Reported class was byte-identical: $taskName" }
                if ([HungAutoAttackBuildBytes]::Major($taskOldBytes) -ne [HungAutoAttackBuildBytes]::Major($taskNewBytes)) {
                    throw "Existing class version changed: $taskName"
                }
            } else {
                $taskInputResources++
                if (-not $taskSame) { throw "Resource or manifest changed: $taskName" }
            }
        }
        if ($taskActualChanged.Count -ne $taskReportedChanged.Count) { throw 'Patcher changed-class report is incomplete' }
        foreach ($taskName in $taskAfter.Keys) {
            if (-not $taskExpectedNames.Contains($taskName)) { throw "Unexpected candidate entry: $taskName" }
            if ($taskName.EndsWith('.class', [StringComparison]::Ordinal)) { $taskOutputClasses++ }
            else { $taskOutputResources++ }
        }
        if ($taskInputResources -ne $taskOutputResources -or $taskOutputClasses -ne ($taskInputClasses + $taskReportAdded.Count)) {
            throw 'Candidate class/resource counts differ from expected archive delta'
        }

        foreach ($taskName in $taskReportAdded) {
            $taskBytes = Read-ZipEntryBytes $taskAfter[$taskName]
            $taskMajor = [HungAutoAttackBuildBytes]::Major($taskBytes)
            if ($taskMajor -gt 51) { throw "Added class is newer than the CLDC-compatible Java 7 ceiling: $taskName ($taskMajor)" }
            if ($taskMajor -gt $taskMaximumMajor) { $taskMaximumMajor = $taskMajor }
            foreach ($taskConstant in [HungAutoAttackBuildBytes]::Utf8Constants($taskBytes)) {
                foreach ($taskBadRef in $taskForbiddenClcdRefs) {
                    if ($taskConstant.IndexOf($taskBadRef, [StringComparison]::Ordinal) -ge 0) {
                        throw "Unsupported CLDC API reference '$taskBadRef' in $taskName"
                    }
                }
            }
        }

        if ((Get-PropertyValue $taskCatalogText 'input.sha256') -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $SourceJar).Hash -or
            (Get-PropertyValue $taskCatalogText 'donor.sha256') -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $ReferenceJarPath).Hash -or
            (Get-PropertyValue $taskCatalogText 'output.sha256') -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $CandidateJar).Hash) {
            throw 'Patcher catalog hashes do not match the staged files'
        }
        @(
            "SOURCE_SHA256=$((Get-FileHash -Algorithm SHA256 -LiteralPath $SourceJar).Hash)"
            "DONOR_SHA256=$((Get-FileHash -Algorithm SHA256 -LiteralPath $ReferenceJar).Hash)"
            "CANDIDATE_SHA256=$((Get-FileHash -Algorithm SHA256 -LiteralPath $CandidateJar).Hash)"
            "INPUT_ENTRIES=$($taskBefore.Count)"
            "OUTPUT_ENTRIES=$($taskAfter.Count)"
            "INPUT_CLASSES=$taskInputClasses"
            "OUTPUT_CLASSES=$taskOutputClasses"
            "MAX_MAJOR=$taskMaximumMajor"
            "UNCHANGED_RESOURCES=$taskOutputResources"
            "CHANGED_CLASSES=$($taskActualChanged.Count)"
            "ADDED_CLASSES=$($taskReportAdded.Count)"
            'MANIFEST_BYTE_IDENTICAL=true'
            'UNREPORTED_CLASS_CHANGES=false'
            'UNSUPPORTED_CLDC_REFERENCES=false'
        ) | Set-Content -Encoding UTF8 -LiteralPath $OutputPath
    } finally {
        $taskBeforeZip.Dispose()
        $taskAfterZip.Dispose()
    }
}

if (-not ('HungAutoAttackBuildBytes' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Text;
public static class HungAutoAttackBuildBytes {
    public static bool Equal(byte[] a, byte[] b) {
        if (Object.ReferenceEquals(a, b)) return true;
        if (a == null || b == null || a.Length != b.Length) return false;
        for (int i = 0; i < a.Length; i++) if (a[i] != b[i]) return false;
        return true;
    }
    public static int Major(byte[] data) {
        if (data == null || data.Length < 8 || data[0] != 0xca || data[1] != 0xfe || data[2] != 0xba || data[3] != 0xbe)
            throw new InvalidOperationException("Invalid class file header");
        return ((data[6] & 255) << 8) | (data[7] & 255);
    }
    public static string[] Utf8Constants(byte[] data) {
        Major(data);
        int p = 8;
        int count = ((data[p++] & 255) << 8) | (data[p++] & 255);
        var values = new List<string>();
        for (int i = 1; i < count; i++) {
            if (p >= data.Length) throw new InvalidOperationException("Truncated constant pool");
            int tag = data[p++];
            int size;
            switch (tag) {
                case 1:
                    if (p + 2 > data.Length) throw new InvalidOperationException("Truncated UTF8 length");
                    size = ((data[p] & 255) << 8) | (data[p + 1] & 255); p += 2;
                    if (p + size > data.Length) throw new InvalidOperationException("Truncated UTF8 constant");
                    values.Add(Encoding.UTF8.GetString(data, p, size)); p += size; break;
                case 3: case 4: p += 4; break;
                case 5: case 6: p += 8; i++; break;
                case 7: case 8: case 16: case 19: case 20: p += 2; break;
                case 9: case 10: case 11: case 12: case 17: case 18: p += 4; break;
                case 15: p += 3; break;
                default: throw new InvalidOperationException("Unknown constant-pool tag " + tag);
            }
            if (p > data.Length) throw new InvalidOperationException("Truncated constant-pool entry");
        }
        return values.ToArray();
    }
}
'@
}

if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid BuildTag' }
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskBuildRoot = [IO.Path]::GetFullPath((Join-Path $taskWorkspace 'build'))
if ([string]::IsNullOrWhiteSpace($DependencyDir)) {
    $DependencyDir = if ($env:LUNA_DEPS) { $env:LUNA_DEPS } else { Join-Path $taskWorkspace '../../.codex-deps' }
}

Push-Location $taskWorkspace
try {
    $taskInput = (Resolve-Path -LiteralPath $InputJar).Path
    $taskReference = (Resolve-Path -LiteralPath $ReferenceJar).Path
    $taskInputHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskInput).Hash
    $taskReferenceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskReference).Hash
    if ($taskInputHash -ne $taskExpectedInputHash) { throw 'InputJar must be the pinned Hung_By_Son source (188C481E...DB2AD)' }
    if ($taskReferenceHash -ne $taskExpectedDonorHash) { throw 'ReferenceJar must be the pinned Bach Ho donor (A8A4165F...322FF0E)' }
    $taskReleaseJarDir = (Resolve-Path -LiteralPath '..\jar').Path
    $taskReleaseJarSnapshot = Get-JarDirectorySnapshot $taskReleaseJarDir
    $taskGolden = (Resolve-Path -LiteralPath 'inputs/luna_final-golden-baseline.jar').Path
    $taskGoldenHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskGolden).Hash
    if ($taskGoldenHash -ne $taskExpectedGoldenHash) { throw 'Immutable golden baseline hash changed' }

    $taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
    $taskAsm = Join-Path $taskDeps 'asm-9.7.jar'
    $taskAsmTree = Join-Path $taskDeps 'asm-tree-9.7.jar'
    $taskMicro = Join-Path $taskDeps 'microemulator.jar'
    foreach ($taskDependency in @($taskAsm, $taskAsmTree, $taskMicro)) {
        if (-not (Test-Path -LiteralPath $taskDependency -PathType Leaf)) { throw "Missing dependency: $taskDependency" }
    }
    $taskAsmCp = $taskAsm + ';' + $taskAsmTree

    $taskRequiredFiles = @(
        'tools/luna/HungBySonAutoAttackPatcher.java',
        'tools/luna/HungBySonAutoAttackJarVerifier.java',
        'src/luna/api/AutoAttackAccess.java',
        'src/luna/api/AutoAttackSettings.java',
        'src/luna/api/LunaClock.java',
        'src/luna/core/SystemLunaClock.java',
        'src/luna/features/AutoAttackController.java',
        'src/luna/features/AutoAttackRuntime.java',
        'src/luna/features/AutoAttackRmsStore.java',
        'fixtures/luna/AutoAttackHostTest.java',
        'fixtures/hung-by-son/HungBySonAutoAttackBinaryTest.java',
        'fixtures/hung-by-son/HungBySonAutoAttackInputBinaryTest.java'
    )
    foreach ($taskFile in $taskRequiredFiles) {
        if (-not (Test-Path -LiteralPath $taskFile -PathType Leaf)) { throw "Required Auto Attack source/fixture is not ready: $taskFile" }
    }
    $taskAutoAttackFixtureText = [IO.File]::ReadAllText('fixtures/hung-by-son/HungBySonAutoAttackBinaryTest.java')
    if ([regex]::IsMatch($taskAutoAttackFixtureText, '\bTODO\b')) {
        throw 'Auto Attack binary fixture still contains unfinished assertions; staging cannot be verified'
    }

    $taskBuild = [IO.Path]::GetFullPath((Join-Path $taskBuildRoot $BuildTag))
    if (-not $taskBuild.StartsWith($taskBuildRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Build stage escaped workspace/build'
    }
    if (Test-Path -LiteralPath $taskBuild) { throw 'BuildTag already exists; use a fresh unique tag' }
    $taskTools = Join-Path $taskBuild 'tools'
    $taskSupport = Join-Path $taskBuild 'support'
    $taskSemantic = Join-Path $taskBuild 'semantic'
    $taskSemanticHost = Join-Path $taskBuild 'semantic-host'
    $taskHost = Join-Path $taskBuild 'host'
    $taskRuntimeSupport = Join-Path $taskBuild 'runtime-support'
    $taskScreenshots = Join-Path $taskBuild 'screenshots'
    New-Item -ItemType Directory -Path $taskBuild, $taskTools, $taskSupport, $taskSemantic,
        $taskSemanticHost, $taskHost, $taskRuntimeSupport, $taskScreenshots | Out-Null

    $taskJavac = (Get-Command javac -ErrorAction Stop).Source
    $taskJava = (Get-Command java -ErrorAction Stop).Source
    $taskPatchToolSources = @('tools/luna/HungBySonAutoAttackPatcher.java', 'tools/luna/HungBySonAutoAttackJarVerifier.java')
    foreach ($taskToolSource in @('tools/luna/HungAutoAttackNativeBuilder.java',
        'tools/luna/HungAutoAttackBridgeGenerator.java', 'tools/luna/HungBySonAutoLoginDialogPatcher.java',
        'tools/legacy/hsosky/HungBySonFullOfflinePatcher.java')) {
        if (-not (Test-Path -LiteralPath $taskToolSource -PathType Leaf)) { throw "Required verifier/bridge source is missing: $taskToolSource" }
        $taskPatchToolSources += $taskToolSource
    }
    Run-Checked $taskJavac (@('-encoding', 'UTF-8', '--release', '7', '-cp', $taskAsmCp, '-d', $taskTools) + $taskPatchToolSources) `
        (Join-Path $taskBuild 'tools-compile.txt')

    $taskSupportSources = @(
        'src/luna/api/AutoAttackAccess.java', 'src/luna/api/AutoAttackSettings.java', 'src/luna/api/LunaClock.java',
        'src/luna/core/SystemLunaClock.java', 'src/luna/features/AutoAttackController.java',
        'src/luna/features/AutoAttackRuntime.java', 'src/luna/features/AutoAttackRmsStore.java',
        'src/luna/features/MonsterCacheSupport.java'
    )
    Run-Checked $taskJavac (@('-encoding', 'UTF-8', '--release', '7', '-cp', "$taskInput;$taskMicro", '-d', $taskSupport) + $taskSupportSources) `
        (Join-Path $taskBuild 'support-compile.txt')

    $taskCandidate = Join-Path $taskBuild 'Hung_By_Son_auto_attack.jar'
    $taskCatalog = Join-Path $taskBuild 'auto-attack.properties'
    $taskPatchReport = Join-Path $taskBuild 'auto-attack-patch-report.txt'
    Run-Checked $taskJava @('-Xverify:all', '-cp', "$taskTools;$taskAsmCp", 'HungBySonAutoAttackPatcher',
        $taskInput, $taskReference, $taskSupport, $taskCandidate, $taskCatalog, $taskPatchReport, $taskExpectedInputHash) `
        (Join-Path $taskBuild 'patch.txt')
    if (-not (Test-Path -LiteralPath $taskCandidate -PathType Leaf)) { throw 'Patcher did not create a staged candidate JAR' }
    $taskCandidateHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskCandidate).Hash
    if ((Get-PropertyValue ([IO.File]::ReadAllText($taskCatalog)) 'native.scope.inverse') -cne 'PASS') {
        throw 'Patcher did not certify the native-method inverse'
    }
    if (-not (Select-String -LiteralPath $taskPatchReport -Pattern '^PASS saved-class structural inverse:' -Quiet)) {
        throw 'Patch report lacks the native structural-inverse proof'
    }
    Assert-ArchiveDelta $taskInput $taskReference $taskCandidate $taskCatalog $taskPatchReport (Join-Path $taskBuild 'archive-boundary.txt')

    $taskExpCatalogText = [IO.File]::ReadAllText('symbols/hung-exp-native-notices-single-20261013.properties')
    $taskExpCatalogText = Set-PropertyLine $taskExpCatalogText 'output.sha256' $taskCandidateHash
    $taskExpCatalog = Join-Path $taskBuild 'exp-native-notices-validation.properties'
    [IO.File]::WriteAllText($taskExpCatalog, $taskExpCatalogText, [Text.UTF8Encoding]::new($false))

    $taskMonsterSourceCatalog = 'build/hung-exp-native-notices-20261013-a/single-validation.properties'
    if (-not (Test-Path -LiteralPath $taskMonsterSourceCatalog -PathType Leaf)) { throw 'Missing latest maintained single monster-validation catalog' }
    $taskMonsterCatalogText = [IO.File]::ReadAllText($taskMonsterSourceCatalog)
    $taskMonsterCatalogText = Set-PropertyLine $taskMonsterCatalogText 'output.sha256' $taskCandidateHash
    $taskMonsterCatalog = Join-Path $taskBuild 'single-validation.properties'
    [IO.File]::WriteAllText($taskMonsterCatalog, $taskMonsterCatalogText, [Text.UTF8Encoding]::new($false))

    $taskTimingText = [IO.File]::ReadAllText('build/hung-real650-x3-20261006-b/real650.properties')
    $taskTimingText = Set-PropertyLine $taskTimingText 'input.sha256' $taskInputHash
    $taskTimingText = Set-PropertyLine $taskTimingText 'output.sha256' $taskCandidateHash
    $taskAttackCatalogText = [IO.File]::ReadAllText($taskCatalog)
    foreach ($taskKey in @('request.tag.field', 'native.requestDispatch.owner',
        'native.requestDispatch.method', 'native.requestDispatch.descriptor',
        'hook.request.lock.owner', 'hook.request.lock.method', 'hook.request.lock.descriptor',
        'native.requestMinimum.owner', 'native.requestMinimum.method', 'native.requestMinimum.descriptor',
        'native.farmAutoMinimum.owner', 'native.farmAutoMinimum.method', 'native.farmAutoMinimum.descriptor',
        'request.farmOrigin.field', 'native.farmChoose.owner', 'native.farmChoose.method', 'native.farmChoose.descriptor')) {
        $taskValue = Get-PropertyValue $taskAttackCatalogText $taskKey
        $taskTimingText = Set-OptionalPropertyLine $taskTimingText $taskKey $taskValue
        if (-not [regex]::IsMatch($taskTimingText, '(?m)^' + [regex]::Escape($taskKey) + '=')) {
            $taskTimingText += "`n$taskKey=$taskValue`n"
        }
    }
    $taskTimingCatalog = Join-Path $taskBuild 'real650-validation.properties'
    [IO.File]::WriteAllText($taskTimingCatalog, $taskTimingText, [Text.UTF8Encoding]::new($false))

    $taskNoclipText = [IO.File]::ReadAllText('symbols/hung-noclip-20261004.properties')
    $taskNoclipText = Set-PropertyLine $taskNoclipText 'output.sha256' $taskCandidateHash
    $taskNoclipText = Set-PropertyLine $taskNoclipText 'noclip.output.sha256' $taskCandidateHash
    $taskNoclipText = Set-OptionalPropertyLine $taskNoclipText 'validation.inputsha' $taskInputHash
    $taskNoclipCatalog = Join-Path $taskBuild 'noclip-validation.properties'
    [IO.File]::WriteAllText($taskNoclipCatalog, $taskNoclipText, [Text.UTF8Encoding]::new($false))

    $taskSupportZip = [IO.Compression.ZipFile]::OpenRead($taskCandidate)
    try {
        foreach ($taskEntry in $taskSupportZip.Entries) {
            $taskName = $taskEntry.FullName
            $taskEligible = $taskName.EndsWith('.class', [StringComparison]::Ordinal) -and
                ($taskName.StartsWith('luna/api/', [StringComparison]::Ordinal) -or
                 $taskName.StartsWith('luna/features/', [StringComparison]::Ordinal) -or
                 $taskName -ceq 'luna/generated/InvestigateStorage.class' -or
                 $taskName -ceq 'luna/generated/ExpEstimateSharedRmsStore.class')
            if (-not $taskEligible) { continue }
            $taskDestination = [IO.Path]::GetFullPath((Join-Path $taskRuntimeSupport $taskName))
            if (-not $taskDestination.StartsWith($taskRuntimeSupport + [IO.Path]::DirectorySeparatorChar,
                [StringComparison]::OrdinalIgnoreCase)) { throw 'Support extraction escaped the build directory' }
            $taskParent = Split-Path -Parent $taskDestination
            New-Item -ItemType Directory -Path $taskParent -Force | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($taskEntry, $taskDestination, $false)
        }
    } finally { $taskSupportZip.Dispose() }
    foreach ($taskRequiredClass in @('luna/api/MonsterCacheAccess.class', 'luna/features/MonsterCacheSupport.class',
        'luna/generated/InvestigateStorage.class', 'luna/generated/ExpEstimateSharedRmsStore.class')) {
        if (-not (Test-Path -LiteralPath (Join-Path $taskRuntimeSupport $taskRequiredClass) -PathType Leaf)) {
            throw "Required current runtime support is absent: $taskRequiredClass"
        }
    }

    $taskBinarySources = @(
        'fixtures/hung-by-son/HungBySonAutoAttackBinaryTest.java',
        'fixtures/hung-by-son/HungBySonAutoAttackInputBinaryTest.java',
        'fixtures/hung-by-son/HungBySonMonsterCacheHostTest.java',
        'fixtures/hung-by-son/HungBySonStaleTargetHostTest.java',
        'fixtures/hung-by-son/HungBySonMonsterSelectionHostTest.java',
        'fixtures/hung-by-son/HungBySonRealTimeCombatHostTest.java',
        'fixtures/hung-by-son/HungBySonCrowdedMonsterHostTest.java',
        'fixtures/hung-by-son/HungBySonCrowdedMonsterBinaryTest.java',
        'fixtures/hung-by-son/HungBySonInvestigateBinaryTest.java',
        'fixtures/hung-by-son/HungBySonInvestigateHudBinaryTest.java',
        'fixtures/hung-by-son/HungBySonMonsterSelectionBinaryTest.java',
        'fixtures/hung-by-son/HungBySonMonsterRecoveryBinaryTest.java',
        'fixtures/hung-by-son/HungBySonStaleTargetBinaryTest.java',
        'fixtures/hung-by-son/HungBySonNoclipMenuHostTest.java',
        'fixtures/hung-by-son/HungBySonNoclipCollisionHostTest.java',
        'fixtures/hung-by-son/HungBySonTempoClickHostTest.java',
        'fixtures/hung-by-son/HungBySonExpBinaryTest.java',
        'fixtures/hung-by-son/HungBySonRemoteAnimationBarrierTest.java',
        'fixtures/hung-by-son/HungBySonFullOfflineHostTest.java',
        'fixtures/hung-by-son/HungBySonEnhancementWaitRegressionTest.java',
        'fixtures/hung-by-son/HungBySonEnhancementNpcSessionTest.java',
        'fixtures/hung-by-son/HungBySonAutoLoginDialogHostTest.java',
        'fixtures/hung-by-son/TradeLocalTest.java'
    )
    Run-Checked $taskJavac (@('-encoding', 'UTF-8', '-cp', "$taskCandidate;$taskTools;$taskAsmCp;$taskMicro", '-d', $taskHost) + $taskBinarySources) `
        (Join-Path $taskBuild 'binary-fixtures-compile.txt')

    $taskSemanticSources = @(& rg --files src -g '*.java' | Where-Object {
        $_ -notin @('src/luna/api/ExpEstimateStore.java', 'src/luna/features/ExpEstimateSupport.java',
            'src/luna/ui/ExpEstimateNoticeLayout.java')
    })
    if ($taskSemanticSources.Count -eq 0) { throw 'No semantic sources were found' }
    Run-Checked $taskJavac (@('-encoding', 'UTF-8', '--release', '7', '-cp', $taskMicro, '-d', $taskSemantic) + $taskSemanticSources) `
        (Join-Path $taskBuild 'semantic-compile.txt')
    $taskSemanticFixtures = @(& rg --files fixtures/luna -g '*.java')
    if ($taskSemanticFixtures.Count -eq 0) { throw 'No semantic fixtures were found' }
    Run-Checked $taskJavac (@('-encoding', 'UTF-8', '--release', '7', '-cp', "$taskSemantic;$taskMicro", '-d', $taskSemanticHost) + $taskSemanticFixtures) `
        (Join-Path $taskBuild 'semantic-fixtures-compile.txt')
    $taskExpSemantic = Join-Path $taskBuild 'exp-semantic'
    $taskExpHost = Join-Path $taskBuild 'exp-semantic-host'
    New-Item -ItemType Directory -Path $taskExpSemantic, $taskExpHost | Out-Null
    Run-Checked $taskJavac @('-encoding', 'UTF-8', '--release', '7', '-d', $taskExpSemantic,
        'src/luna/api/ExpEstimateStore.java', 'src/luna/features/ExpEstimateSupport.java',
        'src/luna/ui/ExpPercentInterpolator.java', 'src/luna/features/ExpRateSupport.java',
        'src/luna/ui/ExpEstimateNoticeLayout.java') `
        (Join-Path $taskBuild 'exp-semantic-compile.txt')
    Run-Checked $taskJavac @('-encoding', 'UTF-8', '--release', '7', '-cp', $taskExpSemantic, '-d', $taskExpHost,
        'fixtures/hung-by-son/ExpEstimateHostTest.java') (Join-Path $taskBuild 'exp-semantic-fixture-compile.txt')

    $taskHostCp = "$taskHost;$taskTools;$taskAsmCp;$taskMicro"
    $taskLegacyHostCp = "$taskHost;$taskRuntimeSupport;$taskAsmCp;$taskMicro"
    $taskSemanticCp = "$taskSemantic;$taskSemanticHost;$taskMicro"
    $taskJarVerifierReport = Join-Path $taskBuild 'auto-attack-jar-verifier-report.txt'
    Run-Checked $taskJava @('-Xverify:all', '-cp', "$taskTools;$taskAsmCp;$taskMicro", 'HungBySonAutoAttackJarVerifier',
        $taskInput, $taskCandidate, $taskMicro, $taskCatalog, $taskJarVerifierReport) `
        (Join-Path $taskBuild 'class-link-and-resource-check.txt')
    if (-not (Test-Path -LiteralPath $taskJarVerifierReport -PathType Leaf) -or
        -not (Select-String -LiteralPath $taskJarVerifierReport -Pattern '^PASS\b' -Quiet)) {
        throw 'HungBySonAutoAttackJarVerifier report lacks its class-link/resource PASS marker'
    }
    $taskAdapterLinkLog = Join-Path $taskBuild 'native-adapter-link.txt'
    Run-Checked $taskJava @('-Xverify:all', '-cp', "$taskTools;$taskAsmCp;$taskMicro", 'HungAutoAttackNativeBuilder',
        '--link', $taskCandidate, $taskMicro) $taskAdapterLinkLog
    if (-not (Select-String -LiteralPath $taskAdapterLinkLog -Pattern '^LINK_PASS classes=[0-9]+$' -Quiet)) {
        throw 'HungAutoAttackNativeBuilder did not certify generated native adapter linkage'
    }

    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonAutoAttackBinaryTest',
        $taskInput, $taskCandidate, $taskReference, $taskCatalog, $taskMicro, $taskScreenshots) `
        (Join-Path $taskBuild 'auto-attack-binary.txt')
    $taskAutoAttackBinaryLines = @(Get-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'auto-attack-binary.txt'))
    foreach ($taskMarker in @('NATIVE_PATHS_VERIFIED=true', 'RMS_LIFECYCLE_VERIFIED=true',
        'HUD_SNAPSHOTS_VERIFIED=true', 'REMOTE_BARRIER_VERIFIED=true',
        'SHARED_FARM_DELAY_VERIFIED=true', 'MANUAL_MOVEMENT_PRIORITY_VERIFIED=true',
        'MOVEMENT_RESUME_100MS_VERIFIED=true', 'INVENTORY_AUTO_ATTACK_VERIFIED=true')) {
        if ($taskAutoAttackBinaryLines -cnotcontains $taskMarker) {
            throw "Auto Attack binary fixture lacks required completion marker: $taskMarker"
        }
    }
    foreach ($taskHudSnapshot in @('auto-attack-hud-240.png', 'auto-attack-hud-450.png')) {
        $taskHudPath = Join-Path $taskScreenshots $taskHudSnapshot
        if (-not (Test-Path -LiteralPath $taskHudPath -PathType Leaf) -or
            (Get-Item -LiteralPath $taskHudPath).Length -lt 256) {
            throw "Auto Attack HUD snapshot is missing or empty: $taskHudSnapshot"
        }
    }
    $taskNativeInputReportDir = Join-Path $taskBuild 'native-input-events'
    New-Item -ItemType Directory -Path $taskNativeInputReportDir | Out-Null
    $taskNativeInputLog = Join-Path $taskBuild 'native-input-events.txt'
    # MicroEmulator bundles ASM 3; keep host ASM 9 out of this UI worker's runtime classpath.
    $taskNativeInputCp = "$taskHost;$taskMicro"
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskNativeInputCp, 'HungBySonAutoAttackInputBinaryTest',
        $taskCandidate, $taskCatalog, $taskMicro, $taskNativeInputReportDir) $taskNativeInputLog
    $taskNativeInputLines = @(Get-Content -Encoding UTF8 -LiteralPath $taskNativeInputLog)
    if ($taskNativeInputLines -cnotcontains 'NATIVE_INPUT_EVENTS_VERIFIED=true') {
        throw 'Native input fixture lacks NATIVE_INPUT_EVENTS_VERIFIED=true'
    }
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskSemanticCp, 'AutoAttackHostTest') (Join-Path $taskBuild 'AutoAttackHostTest.txt')
    foreach ($taskTest in @('LunaSemanticHostTest', 'LunaControllerHostTest', 'LunaRouteHostTest', 'AutoCombatHostTest', 'LoginMaterialHostTest')) {
        Run-Checked $taskJava @('-Xverify:all', '-cp', $taskSemanticCp, $taskTest) (Join-Path $taskBuild "$taskTest.txt")
    }
    Run-Checked $taskJava @('-Xverify:all', '-cp', "$taskExpSemantic;$taskExpHost", 'ExpEstimateHostTest') `
        (Join-Path $taskBuild 'ExpEstimateHostTest.txt')

    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskLegacyHostCp, 'HungBySonMonsterCacheHostTest', $taskCandidate) `
        (Join-Path $taskBuild 'HungBySonMonsterCacheHostTest.txt')
    if (-not (Select-String -LiteralPath (Join-Path $taskBuild 'HungBySonMonsterCacheHostTest.txt') `
        -Pattern '^MAP48_CACHE_DISABLED_VERIFIED=true$' -Quiet)) { throw 'Missing map48 cache exclusion result' }
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskLegacyHostCp, 'HungBySonStaleTargetHostTest', $taskCandidate) `
        (Join-Path $taskBuild 'HungBySonStaleTargetHostTest.txt')
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskLegacyHostCp, 'HungBySonMonsterSelectionHostTest', $taskCandidate) `
        (Join-Path $taskBuild 'HungBySonMonsterSelectionHostTest.txt')
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskLegacyHostCp, 'HungBySonCrowdedMonsterHostTest', $taskCandidate) `
        (Join-Path $taskBuild 'HungBySonCrowdedMonsterHostTest.txt')
    foreach ($taskTest in @('HungBySonInvestigateHudBinaryTest', 'HungBySonMonsterSelectionBinaryTest',
        'HungBySonMonsterRecoveryBinaryTest', 'HungBySonStaleTargetBinaryTest', 'HungBySonCrowdedMonsterBinaryTest')) {
        $taskTestCatalog = $taskMonsterCatalog
        $taskTestArguments = @('-Xverify:all', '-cp', $taskLegacyHostCp, $taskTest, $taskCandidate, $taskTestCatalog, $taskMicro)
        Run-Checked $taskJava $taskTestArguments (Join-Path $taskBuild "$taskTest.txt")
    }
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonRealTimeCombatHostTest',
        $taskInput, $taskCandidate, $taskTimingCatalog, 'symbols/hung-tempo-menu-fixed-20261004.properties', $taskMicro, '--auto-attack-regression', $taskCatalog) `
        (Join-Path $taskBuild 'real650-preserved-timing.txt')
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonTempoClickHostTest', $taskCandidate, $taskNoclipCatalog) `
        (Join-Path $taskBuild 'tempo-menu-clicks.txt')
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonNoclipMenuHostTest', $taskCandidate, $taskNoclipCatalog) `
        (Join-Path $taskBuild 'noclip-menu.txt')
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonNoclipCollisionHostTest',
        $taskInput, $taskCandidate, $taskNoclipCatalog) (Join-Path $taskBuild 'noclip-collision.txt')
    $taskCandidateClassCount = [int](Get-PropertyValue ([IO.File]::ReadAllText((Join-Path $taskBuild 'archive-boundary.txt'))) 'OUTPUT_CLASSES')
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonFullOfflineHostTest',
        $taskCandidate, [string]$taskCandidateClassCount) (Join-Path $taskBuild 'full-offline.txt')
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonEnhancementWaitRegressionTest', $taskCandidate) `
        (Join-Path $taskBuild 'enhancement-wait.txt')
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonEnhancementNpcSessionTest', $taskCandidate) `
        (Join-Path $taskBuild 'enhancement-npc-session.txt')
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskHostCp, 'TradeLocalTest', $taskCandidate, $taskMicro) `
        (Join-Path $taskBuild 'trade-local.txt')
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonAutoLoginDialogHostTest',
        $taskInput, $taskCandidate, $taskMicro, (Join-Path $taskBuild 'autologin-dialog-current.txt'), '--current-baseline') `
        (Join-Path $taskBuild 'autologin-dialog-test.txt')
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonRemoteAnimationBarrierTest',
        $taskCandidate, $taskMonsterCatalog, $taskInput, '--current-input') (Join-Path $taskBuild 'remote-animation-barrier-current.txt')
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonExpBinaryTest',
        $taskCandidate, $taskExpCatalog, $taskMicro, $taskScreenshots) (Join-Path $taskBuild 'exp-native-binary.txt')

    & (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskCandidate -DependencyDir $taskDeps `
        -ReportDir (Join-Path $taskBuild 'single-emulator-smoke') |
        Tee-Object -FilePath (Join-Path $taskBuild 'single-emulator-smoke.txt')
    if ($LASTEXITCODE -ne 0) { throw "Isolated emulator startup failed: $LASTEXITCODE" }

    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskInput).Hash -ne $taskExpectedInputHash -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskReference).Hash -ne $taskExpectedDonorHash -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskGolden).Hash -ne $taskExpectedGoldenHash) {
        throw 'Pinned source, donor, or golden baseline changed during staging'
    }
    $taskReleaseJarSnapshotAfter = Assert-JarDirectorySnapshot $taskReleaseJarSnapshot $taskReleaseJarDir
    $taskBoundaryText = [IO.File]::ReadAllText((Join-Path $taskBuild 'archive-boundary.txt'))
    $taskMaximumMajor = [int](Get-PropertyValue $taskBoundaryText 'MAX_MAJOR')
    if (Test-Path -LiteralPath (Join-Path $taskBuild 'verified.json')) { throw 'Refusing to overwrite verified.json' }
    $taskChanged = @(Get-ClassList ([IO.File]::ReadAllText($taskCatalog)) 'changed.classes')
    $taskAdded = @(Get-ClassList ([IO.File]::ReadAllText($taskCatalog)) 'added.classes')
    [ordered]@{
        Verified = $true
        Published = $false
        LiveServerTested = $false
        SingleCandidate = $taskCandidate
        SingleCandidateSHA256 = $taskCandidateHash
        SingleSourceInput = $taskInput
        SingleSourceInputSHA256 = $taskInputHash
        DonorReference = $taskReference
        DonorReferenceSHA256 = $taskReferenceHash
        ChangedClasses = $taskChanged
        AddedClasses = $taskAdded
        ArchiveBoundary = Join-Path $taskBuild 'archive-boundary.txt'
        JarLinkAndDescriptorReport = $taskJarVerifierReport
        NativeAdapterLinkReport = $taskAdapterLinkLog
        AllClassesLinked = $true
        MaximumClassMajor = $taskMaximumMajor
        AddedClassCLDCConstantPoolChecked = $taskAdded.Count
        NativePathsVerified = $true
        RmsLifecycleVerified = $true
        HudSnapshotsVerified = $true
        HudSnapshots = @('auto-attack-hud-240.png', 'auto-attack-hud-450.png')
        NativeInputEventsVerified = $true
        NativeInputEventsReport = $taskNativeInputLog
        SharedFarmDelayVerified = $true
        ManualMovementPriorityVerified = $true
        MovementResume100msVerified = $true
        InventoryAutoAttackVerified = $true
        Map48CacheDisabledVerified = $true
        RemoteBarrierVerified = $true
        EXPNativeHooksRegression = 'passed on staged candidate; validation catalog is a build-local copy'
        RuntimeCadence = 'Shared configured real-time delay for Farm and Auto Attack; manual/remote requests retain 650ms'
        AutoAttackSettings = 'mode/delay persisted independently; startup OFF; two-way Farm handoff tested by binary fixture'
        SingleRegressionTests = @(
            'HungBySonAutoAttackBinaryTest', 'HungBySonAutoAttackInputBinaryTest',
            'HungBySonMonsterCacheHostTest', 'HungBySonStaleTargetHostTest',
            'HungBySonMonsterSelectionHostTest', 'HungBySonCrowdedMonsterHostTest',
            'HungBySonInvestigateHudBinaryTest', 'HungBySonMonsterSelectionBinaryTest',
            'HungBySonMonsterRecoveryBinaryTest', 'HungBySonStaleTargetBinaryTest', 'HungBySonCrowdedMonsterBinaryTest',
            'HungBySonRemoteAnimationBarrierTest --current-input', 'HungBySonFullOfflineHostTest',
            'HungBySonEnhancementWaitRegressionTest', 'HungBySonEnhancementNpcSessionTest', 'TradeLocalTest',
            'HungBySonAutoLoginDialogHostTest --current-baseline',
            'HungBySonRealTimeCombatHostTest --auto-attack-regression', 'HungBySonTempoClickHostTest',
            'HungBySonNoclipMenuHostTest', 'HungBySonNoclipCollisionHostTest', 'HungBySonExpBinaryTest',
            'HungBySonAutoAttackJarVerifier (all class links, descriptors, and resource parity)',
            'HungAutoAttackNativeBuilder --link (generated adapter linkage/access)'
        )
        SemanticRegressionTests = @(
            'AutoAttackHostTest', 'ExpEstimateHostTest', 'LunaSemanticHostTest', 'LunaControllerHostTest',
            'LunaRouteHostTest', 'AutoCombatHostTest', 'LoginMaterialHostTest'
        )
        EmulatorStartup = '12 seconds, isolated memory RMS, empty stderr; no account login'
        DependencyDir = $taskDeps
        ReleaseJarSnapshotUnchanged = $true
        AllOtherEndpointsPreserved = $true
        ReleaseJarEndpoints = @($taskReleaseJarSnapshotAfter.Keys | Sort-Object | ForEach-Object {
            [ordered]@{ Name = $_; SHA256 = $taskReleaseJarSnapshotAfter[$_] }
        })
    } | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'verified.json')
    Write-Output "VERIFIED_SINGLE=$taskCandidate; SHA256=$taskCandidateHash; STAGE_ONLY=true"
} finally { Pop-Location }
