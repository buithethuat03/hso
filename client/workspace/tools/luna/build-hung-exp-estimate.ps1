[CmdletBinding()]
param(
    [string]$SingleInputJar = 'build\hung-crowded-monsters-20261011-e\Hung_By_Son_crowded_fix.jar',
    [string]$X3InputJar = '..\jar\backup\Hung_By_Son_x3-before-hung-exp-estimate-20261013-e.jar',
    [string]$ExpectedSingleInputHash = 'F806EE5F395B6979057ECF49DA4C10E7313D39A6F6AB23A10D04E88347A4B892',
    [string]$ExpectedX3InputHash = '9D8966C322871B8F16180B69F0C7A6EFD905CA7BAAE0F5C9B3F4B07B35AA3A1E',
    [string]$ReleaseSingleBeforeHash = 'D94DE96E2BA6A660B55CB11CF0D0CBAC7EA4F5B81FB6BD4032EC3D9E9F0129EA',
    [string]$ReleaseX3BeforeHash = 'ABC5CEFD7EEFEB639EF1EFEA7ADBCD1CC7633BD4FB82FDCBFB97E1EDF2BF109D',
    [string]$DependencyDir = '',
    [string]$BuildTag = ('hung-exp-estimate-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
)

$ErrorActionPreference = 'Stop'

function Run-Checked {
    param([string]$Program, [string[]]$Arguments, [string]$Log)
    $taskPrevious = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $taskMessages = & $Program @Arguments 2>&1
        $taskExit = $LASTEXITCODE
        $taskMessages | ForEach-Object { $_.ToString() } | Set-Content -Encoding UTF8 -LiteralPath $Log
    } finally {
        $ErrorActionPreference = $taskPrevious
    }
    if ($taskExit -ne 0) {
        Get-Content -LiteralPath $Log -Tail 20
        throw "$Program failed: $taskExit"
    }
    $taskMessages | Select-Object -Last 1
}

function Set-PropertyLine {
    param([string]$Text, [string]$Name, [string]$Value)
    $pattern = '(?m)^' + [regex]::Escape($Name) + '=[^\r\n]*'
    if ([regex]::Matches($Text, $pattern).Count -ne 1) { throw "Expected exactly one $Name property" }
    return [regex]::Replace($Text, $pattern, ($Name + '=' + $Value), 1)
}

function Get-ReportClasses {
    param([string]$ReportPath, [string]$Prefix)
    $items = @(
        Get-Content -Encoding UTF8 -LiteralPath $ReportPath |
            Where-Object { $_.StartsWith($Prefix, [StringComparison]::Ordinal) } |
            ForEach-Object { $_.Substring($Prefix.Length).Trim() }
    )
    if ($items.Count -eq 0) { throw "Patcher report contains no $Prefix entries: $ReportPath" }
    return $items
}

function Convert-ClassNameToEntry {
    param([string]$Name)
    $value = $Name.Trim()
    if ([string]::IsNullOrWhiteSpace($value)) { throw 'Empty class name in report or catalog' }
    if ($value.EndsWith('.class', [StringComparison]::Ordinal)) { return $value }
    if ($value.Contains('.')) { $value = $value.Replace('.', '/') }
    return ($value + '.class')
}

function Read-ClassProperty {
    param([System.Collections.Specialized.OrderedDictionary]$Properties, [string]$Key)
    $value = [string]$Properties[$Key]
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Missing catalog property: $Key" }
    return @($value.Split(',') | ForEach-Object { Convert-ClassNameToEntry $_ })
}

function Get-ZipEntryMap {
    param([System.IO.Compression.ZipArchive]$Archive)
    $map = [System.Collections.Generic.Dictionary[string, System.IO.Compression.ZipArchiveEntry]]::new([StringComparer]::Ordinal)
    foreach ($entry in $Archive.Entries) {
        if ($map.ContainsKey($entry.FullName)) { throw "Duplicate archive entry: $($entry.FullName)" }
        $map.Add($entry.FullName, $entry)
    }
    return ,$map
}

function Read-ZipEntryBytes {
    param([System.IO.Compression.ZipArchiveEntry]$Entry)
    $memory = [IO.MemoryStream]::new()
    $stream = $Entry.Open()
    try { $stream.CopyTo($memory); return ,$memory.ToArray() }
    finally { $stream.Dispose(); $memory.Dispose() }
}

function Assert-ArchiveDelta {
    param(
        [string]$InputJar,
        [string]$CandidateJar,
        [string]$CatalogPath,
        [string]$ReportPath,
        [string]$ReportOut,
        [ValidateSet('single', 'x3')][string]$Variant
    )
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $reportChanged = @(Get-ReportClasses $ReportPath 'CHANGED_CLASS=' | ForEach-Object { Convert-ClassNameToEntry $_ })
    $reportAdded = @(Get-ReportClasses $ReportPath 'ADDED_CLASS=' | ForEach-Object { Convert-ClassNameToEntry $_ })
    if (@($reportChanged | Sort-Object -Unique).Count -ne $reportChanged.Count) { throw 'Duplicate CHANGED_CLASS in patcher report' }
    if (@($reportAdded | Sort-Object -Unique).Count -ne $reportAdded.Count) { throw 'Duplicate ADDED_CLASS in patcher report' }
    $expectedAddedCount = if ($Variant -eq 'single') { 7 } else { 19 }
    if ($reportAdded.Count -ne $expectedAddedCount) {
        throw "Unexpected $Variant added class count: expected $expectedAddedCount, got $($reportAdded.Count)"
    }
    $sharedStoreEntry = 'luna/generated/ExpEstimateSharedRmsStore.class'
    if (@($reportAdded | Where-Object { $_ -ceq $sharedStoreEntry }).Count -ne 1) {
        throw "Patcher report must add exactly one root shared EXP RMS store for $Variant"
    }

    $catalog = [ordered]@{}
    Get-Content -Encoding UTF8 -LiteralPath $CatalogPath | ForEach-Object {
        $line = $_.Trim()
        if ($line.Length -gt 0 -and -not $line.StartsWith('#') -and -not $line.StartsWith('!')) {
            $split = $line.IndexOf('=')
            if ($split -lt 1) { $split = $line.IndexOf(':') }
            if ($split -lt 1) { throw "Malformed catalog line: $line" }
            $catalog[$line.Substring(0, $split).Trim()] = $line.Substring($split + 1).Trim()
        }
    }
    $catalogChanged = @(Read-ClassProperty $catalog 'changed.classes')
    $catalogAdded = @(Read-ClassProperty $catalog 'added.classes')
    if (@($catalogChanged | Sort-Object -Unique).Count -ne $catalogChanged.Count) { throw 'Duplicate changed.classes entry in catalog' }
    if (@($catalogAdded | Sort-Object -Unique).Count -ne $catalogAdded.Count) { throw 'Duplicate added.classes entry in catalog' }
    $changedDiff = @(Compare-Object ($reportChanged | Sort-Object) ($catalogChanged | Sort-Object) -CaseSensitive)
    $addedDiff = @(Compare-Object ($reportAdded | Sort-Object) ($catalogAdded | Sort-Object) -CaseSensitive)
    if ($changedDiff.Count -ne 0) { throw 'Patcher report CHANGED_CLASS list differs from catalog changed.classes' }
    if ($addedDiff.Count -ne 0) { throw 'Patcher report ADDED_CLASS list differs from catalog added.classes' }

    $inputArchive = [IO.Compression.ZipFile]::OpenRead($InputJar)
    $outputArchive = [IO.Compression.ZipFile]::OpenRead($CandidateJar)
    try {
        $before = Get-ZipEntryMap $inputArchive
        $after = Get-ZipEntryMap $outputArchive
        $expectedNames = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($name in $before.Keys) { [void]$expectedNames.Add($name) }
        foreach ($name in $reportAdded) {
            if ($before.ContainsKey($name)) { throw "Reported added class already exists in input: $name" }
            [void]$expectedNames.Add($name)
        }
        if ($before.Count -ne ($after.Count - $reportAdded.Count)) { throw 'Archive entry count does not match the reported additions' }
        if ($expectedNames.Count -ne $after.Count) { throw 'Archive entries differ from the input plus reported class additions' }
        foreach ($name in $after.Keys) {
            if (-not $expectedNames.Contains($name)) { throw "Unexpected archive entry: $name" }
        }
        if (-not $after.ContainsKey($sharedStoreEntry)) { throw 'Root shared EXP RMS store is absent from candidate' }
        $adapterPrefixes = if ($Variant -eq 'single') { @('') } else { @('', 'b/', 'c/') }
        foreach ($prefix in $adapterPrefixes) {
            $adapterEntry = $prefix + 'luna/generated/ExpEstimateRmsStore.class'
            if (-not $after.ContainsKey($adapterEntry)) { throw "Per-slot EXP RMS adapter is absent: $adapterEntry" }
        }
        foreach ($prefix in @('b/', 'c/')) {
            if ($after.ContainsKey($prefix + $sharedStoreEntry)) {
                throw "Shared EXP RMS store must exist only in the root namespace, found $prefix$sharedStoreEntry"
            }
        }

        $reportedChanged = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($name in $reportChanged) {
            if (-not $before.ContainsKey($name)) { throw "Reported changed class is absent from input: $name" }
            if (-not $after.ContainsKey($name)) { throw "Reported changed class is absent from output: $name" }
            [void]$reportedChanged.Add($name)
        }
        $inputClassCount = 0
        $outputClassCount = 0
        $inputResourceCount = 0
        $outputResourceCount = 0
        $actualChanged = [System.Collections.Generic.List[string]]::new()
        foreach ($name in $before.Keys) {
            $isClass = $name.EndsWith('.class', [StringComparison]::Ordinal)
            if ($isClass) { $inputClassCount++ } else { $inputResourceCount++ }
            $oldBytes = Read-ZipEntryBytes $before[$name]
            $newBytes = Read-ZipEntryBytes $after[$name]
            $same = [LunaBuildBytes]::Equal($oldBytes, $newBytes)
            if ($isClass) {
                if (-not $same) {
                    $actualChanged.Add($name)
                    if (-not $reportedChanged.Contains($name)) { throw "Unreported class modification: $name" }
                } elseif ($reportedChanged.Contains($name)) {
                    throw "Reported class was byte-identical: $name"
                }
                if ($Variant -eq 'x3' -and ($name.StartsWith('lib/', [StringComparison]::Ordinal) -or
                    $name.StartsWith('MrQuyet/', [StringComparison]::Ordinal)) -and -not $same) {
                    throw "X3 launcher wrapper class changed: $name"
                }
            } elseif (-not $same) {
                throw "Resource changed: $name"
            }
        }
        foreach ($name in $after.Keys) {
            if ($name.EndsWith('.class', [StringComparison]::Ordinal)) { $outputClassCount++ }
            else { $outputResourceCount++ }
        }
        if ($actualChanged.Count -ne $reportedChanged.Count) { throw 'Not all reported class modifications were observed in the archive' }
        if ($outputClassCount -ne ($inputClassCount + $reportAdded.Count)) { throw 'Class count differs from input plus reported additions' }
        if ($outputResourceCount -ne $inputResourceCount) { throw 'Resource count changed' }
        $embeddedExpClasses = @($reportAdded | Where-Object {
            ($_.StartsWith('luna/', [StringComparison]::Ordinal) -or
             $_.StartsWith('b/luna/', [StringComparison]::Ordinal) -or
             $_.StartsWith('c/luna/', [StringComparison]::Ordinal)) -and
            $_.IndexOf('ExpEstimate', [StringComparison]::Ordinal) -ge 0 -and
            $_.EndsWith('.class', [StringComparison]::Ordinal)
        })
        if ($embeddedExpClasses.Count -eq 0) { throw 'Patcher report has no embedded EXP classes to check for CLDC references' }
        $forbiddenExpRefs = @(
            'java/lang/StringBuilder', 'java/util/Arrays', 'java/util/Collection',
            'java/util/Collections', 'java/util/Iterator', 'java/util/List',
            'java/util/ListIterator', 'java/util/Map', 'java/util/Set',
            'java/util/ArrayList', 'java/util/LinkedList', 'java/util/HashMap',
            'java/util/LinkedHashMap', 'java/util/HashSet', 'java/util/LinkedHashSet',
            'java/util/TreeMap', 'java/util/TreeSet', 'java/util/Queue',
            'java/util/Deque', 'java/util/PriorityQueue', 'java/util/concurrent/'
        )
        foreach ($className in $embeddedExpClasses) {
            $constants = [LunaBuildBytes]::Utf8Constants((Read-ZipEntryBytes $after[$className]))
            foreach ($constant in $constants) {
                foreach ($forbidden in $forbiddenExpRefs) {
                    if ($constant.IndexOf($forbidden, [StringComparison]::Ordinal) -ge 0) {
                        throw "Unsupported CLDC reference '$forbidden' in embedded EXP class $className"
                    }
                }
            }
        }
        if (-not $catalog.Contains('input.sha256') -or $catalog['input.sha256'] -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $InputJar).Hash) {
            throw 'Catalog input.sha256 does not match input JAR'
        }
        if (-not $catalog.Contains('output.sha256') -or $catalog['output.sha256'] -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $CandidateJar).Hash) {
            throw 'Catalog output.sha256 does not match candidate JAR'
        }

        $expectedSlots = if ($Variant -eq 'single') { '1' } else { '3' }
        if (-not $catalog.Contains('slots') -or $catalog['slots'] -cne $expectedSlots) {
            throw "Unexpected $Variant slot count: '$($catalog['slots'])'"
        }
        if ($catalog.Contains('namespaces')) {
            $namespaceValue = [string]$catalog['namespaces']
            if ($Variant -eq 'single' -and $namespaceValue -cne 'root' -and $namespaceValue -cne '') {
                throw "Unexpected single-client namespace catalog value: '$($catalog['namespaces'])'"
            }
            if ($Variant -eq 'x3' -and $namespaceValue -cne ',b/,c/') {
                throw "Unexpected X3 namespace catalog value: '$($catalog['namespaces'])'"
            }
        }
        foreach ($setting in @{
            'exp.cache.key' = 'level'
            'exp.cache.version' = '2'
            'exp.cache.shared.slots' = 'true'
            'exp.cache.store' = 'hbs_exp_levels_v2'
            'exp.cache.shared.owner' = 'luna/generated/ExpEstimateSharedRmsStore'
        }.GetEnumerator()) {
            if (-not $catalog.Contains($setting.Key) -or $catalog[$setting.Key] -cne $setting.Value) {
                throw "Unexpected shared EXP catalog setting $($setting.Key): '$($catalog[$setting.Key])'"
            }
        }
        $namespacePrefixes = if ($Variant -eq 'single') { @('') } else { @('', 'b/', 'c/') }
        for ($slot = 0; $slot -lt [int]$expectedSlots; $slot++) {
            $prefix = $namespacePrefixes[$slot]
            foreach ($suffix in @('hud.owner', 'hud.method', 'hud.descriptor', 'receiver.owner',
                'snapshot.method', 'snapshot.descriptor',
                'xp.method', 'xp.descriptor', 'entity.owner', 'main.owner', 'main.field', 'main.descriptor',
                'progress.field', 'level.field', 'id.field', 'bridge.owner', 'font.owner',
                'font.draw.method', 'font.draw.descriptor', 'graphics.owner', 'lower.icon.y',
                'notice.method', 'notice.descriptor', 'notice.base.field', 'notice.fields',
                'notice.hide.owner', 'notice.hide.field', 'notice.hooks', 'notice.native.unchanged')) {
                $key = "slot.$slot.$suffix"
                if (-not $catalog.Contains($key) -or [string]::IsNullOrWhiteSpace([string]$catalog[$key])) {
                    throw "Catalog missing feature link key: $key"
                }
            }
            foreach ($suffix in @('hud.owner', 'receiver.owner', 'entity.owner', 'main.owner', 'bridge.owner', 'notice.hide.owner')) {
                $key = "slot.$slot.$suffix"
                if (-not ([string]$catalog[$key]).StartsWith($prefix, [StringComparison]::Ordinal)) {
                    throw "Catalog owner $key is outside namespace '$prefix'"
                }
            }
            if ($catalog["slot.$slot.bridge.owner"] -cne ($prefix + 'luna/generated/ExpEstimateBridge')) {
                throw "Unexpected EXP bridge owner for namespace '$prefix'"
            }
            if ($catalog["slot.$slot.lower.icon.y"] -cne '59') { throw "Unexpected lower icon position for slot $slot" }
            if ($catalog["slot.$slot.notice.hooks"] -cne '0') { throw "Unexpected notice hook count for slot $slot" }
            if ($catalog["slot.$slot.notice.native.unchanged"] -cne 'true') {
                throw "Native notice method was not proven byte-identical for slot $slot"
            }
            $noticeFields = @(([string]$catalog["slot.$slot.notice.fields"]).Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_.Length -gt 0 })
            if ($noticeFields.Count -ne 3 -or @($noticeFields | Sort-Object -Unique).Count -ne 3) {
                throw "Notice catalog must identify three unique text fields for slot $slot"
            }
        }
        foreach ($setting in @{
            'frame.height.before' = '42'
            'frame.height.after' = '56'
            'frame.upper.width' = '96'
            'frame.width.after' = '96'
            'frame.rectangular' = 'true'
            'estimate.y.offset' = '38'
            'notice.position' = 'native'
        }.GetEnumerator()) {
            if (-not $catalog.Contains($setting.Key) -or $catalog[$setting.Key] -cne $setting.Value) {
                throw "Unexpected catalog setting $($setting.Key): '$($catalog[$setting.Key])'"
            }
        }

        $wrapperStatus = if ($Variant -eq 'x3') { 'true' } else { 'not-applicable' }
        @(
            "VARIANT=$Variant"
            "INPUT_ENTRIES=$($before.Count)"
            "OUTPUT_ENTRIES=$($after.Count)"
            "INPUT_CLASSES=$inputClassCount"
            "OUTPUT_CLASSES=$outputClassCount"
            "INPUT_RESOURCES=$inputResourceCount"
            "OUTPUT_RESOURCES=$outputResourceCount"
            "NAMESPACE_PREFIXES=$(if ($Variant -eq 'single') { 'root' } else { 'root,b/,c/' })"
            "CHANGED_CLASSES=$($reportedChanged.Count)"
            "ADDED_CLASSES=$($reportAdded.Count)"
            "EMBEDDED_EXP_CLASSES_CLDC_CHECKED=$($embeddedExpClasses.Count)"
            'UNSUPPORTED_CLDC_REFERENCES=false'
            'NOTICE_POSITION=native'
            'NOTICE_HOOKS=0'
            'NATIVE_NOTICE_UNCHANGED=true'
            'RESOURCES_BYTE_IDENTICAL=true'
            'UNREPORTED_CLASSES_BYTE_IDENTICAL=true'
            "X3_WRAPPER_CLASSES_BYTE_IDENTICAL=$wrapperStatus"
        ) | Set-Content -Encoding UTF8 -LiteralPath $ReportOut
    } finally {
        $inputArchive.Dispose()
        $outputArchive.Dispose()
    }
}

if (-not ('LunaBuildBytes' -as [type])) {
    Add-Type -TypeDefinition @'
public static class LunaBuildBytes {
    public static bool Equal(byte[] first, byte[] second) {
        if (first == second) return true;
        if (first == null || second == null || first.Length != second.Length) return false;
        for (int i = 0; i < first.Length; i++) if (first[i] != second[i]) return false;
        return true;
    }
    public static string[] Utf8Constants(byte[] data) {
        if (data == null || data.Length < 10 || data[0] != 0xca || data[1] != 0xfe || data[2] != 0xba || data[3] != 0xbe)
            throw new System.InvalidOperationException("Invalid classfile while checking CLDC references");
        int position = 8;
        int count = (data[position++] << 8) | data[position++];
        var values = new System.Collections.Generic.List<string>();
        for (int index = 1; index < count; index++) {
            if (position >= data.Length) throw new System.InvalidOperationException("Truncated class constant pool");
            int tag = data[position++];
            int size;
            switch (tag) {
                case 1:
                    if (position + 2 > data.Length) throw new System.InvalidOperationException("Truncated UTF8 constant length");
                    size = (data[position] << 8) | data[position + 1]; position += 2;
                    if (position + size > data.Length) throw new System.InvalidOperationException("Truncated UTF8 constant");
                    values.Add(System.Text.Encoding.UTF8.GetString(data, position, size)); position += size;
                    break;
                case 3: case 4: size = 4; position += size; break;
                case 5: case 6: size = 8; position += size; index++; break;
                case 7: case 8: case 16: case 19: case 20: position += 2; break;
                case 9: case 10: case 11: case 12: case 17: case 18: position += 4; break;
                case 15: position += 3; break;
                default: throw new System.InvalidOperationException("Unknown class constant pool tag " + tag);
            }
            if (position > data.Length) throw new System.InvalidOperationException("Truncated class constant pool entry");
        }
        return values.ToArray();
    }
}
'@
}

if ($BuildTag -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid build tag' }
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ([string]::IsNullOrWhiteSpace($DependencyDir)) {
    $DependencyDir = if ($env:LUNA_DEPS) { $env:LUNA_DEPS } else { Join-Path $taskWorkspace '../../.codex-deps' }
}

Push-Location $taskWorkspace
try {
    $taskSingleInput = (Resolve-Path -LiteralPath $SingleInputJar).Path
    $taskX3Input = (Resolve-Path -LiteralPath $X3InputJar).Path
    $taskSingleInputHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskSingleInput).Hash
    $taskX3InputHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskX3Input).Hash
    foreach ($taskHashParam in @{
        ExpectedSingleInputHash = $ExpectedSingleInputHash
        ExpectedX3InputHash = $ExpectedX3InputHash
        ReleaseSingleBeforeHash = $ReleaseSingleBeforeHash
        ReleaseX3BeforeHash = $ReleaseX3BeforeHash
    }.GetEnumerator()) {
        if ($taskHashParam.Value -notmatch '^[A-Fa-f0-9]{64}$') { throw "Invalid SHA-256 parameter: $($taskHashParam.Key)" }
    }
    if ($taskSingleInputHash -ne $ExpectedSingleInputHash) {
        throw 'SingleInputJar does not match ExpectedSingleInputHash'
    }
    if ($taskX3InputHash -ne $ExpectedX3InputHash) {
        throw 'Unexpected pre-EXP crowded X3 source JAR'
    }

    $taskReleaseSingle = (Resolve-Path -LiteralPath '..\jar\Hung_By_Son.jar').Path
    $taskReleaseX3 = (Resolve-Path -LiteralPath '..\jar\Hung_By_Son_x3.jar').Path
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskReleaseSingle).Hash -ne $ReleaseSingleBeforeHash) {
        throw 'Single release does not match ReleaseSingleBeforeHash'
    }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskReleaseX3).Hash -ne $ReleaseX3BeforeHash) {
        throw 'X3 release does not match ReleaseX3BeforeHash'
    }

    $taskCrowdedSourceBuild = 'build/hung-crowded-monsters-20261011-e'
    $taskCrowdedSourceInput = (Resolve-Path -LiteralPath (Join-Path $taskCrowdedSourceBuild 'Hung_By_Son_crowded_fix.jar')).Path
    $taskCrowdedSourceHash = 'F806EE5F395B6979057ECF49DA4C10E7313D39A6F6AB23A10D04E88347A4B892'
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskCrowdedSourceInput).Hash -ne $taskCrowdedSourceHash) {
        throw 'Crowded source candidate hash changed'
    }
    $taskCrowdedSourceProof = Get-Content -Encoding UTF8 -LiteralPath (Join-Path $taskCrowdedSourceBuild 'verified.json') -Raw | ConvertFrom-Json
    if (-not $taskCrowdedSourceProof.Verified -or $taskCrowdedSourceProof.SHA256 -ne $taskCrowdedSourceHash -or
        $taskCrowdedSourceProof.TimingMs -ne 650) {
        throw 'Crowded single source proof does not match the pinned 650ms candidate'
    }
    $taskCrowdedSourcePredecessorHash = [string]$taskCrowdedSourceProof.InputSHA256
    $taskCrowdedPredecessor = (Resolve-Path -LiteralPath '..\jar\backup\Hung_By_Son-before-hung-exp-estimate-20261013-e.jar').Path
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskCrowdedPredecessor).Hash -ne $taskCrowdedSourcePredecessorHash) {
        throw 'Crowded source predecessor backup does not match its verification record'
    }
    foreach ($taskSourceProof in @{
        'HungBySonCrowdedMonsterHostTest.txt' = 'PASS (45044 checks'
        'HungBySonCrowdedMonsterBinaryTest.txt' = 'CROWDED_MONSTER_BINARY=PASS; checks=27'
        'remote-animation-barrier.txt' = 'PASS (321 checks'
        'real650-test.txt' = 'PASS (2502 checks'
        'LunaSemanticHostTest.txt' = 'OK -'
        'LunaControllerHostTest.txt' = 'OK -'
        'LunaRouteHostTest.txt' = 'OK -'
    }.GetEnumerator()) {
        $taskProofPath = Join-Path $taskCrowdedSourceBuild $taskSourceProof.Key
        if (-not (Test-Path -LiteralPath $taskProofPath -PathType Leaf) -or
            -not [IO.File]::ReadAllText($taskProofPath).Contains($taskSourceProof.Value)) {
            throw "Crowded single source evidence is incomplete: $($taskSourceProof.Key)"
        }
    }
    $taskCrowdedSourceValidation = Join-Path $taskCrowdedSourceBuild 'crowded-monsters-validation.properties'
    $taskCrowdedValidationText = [IO.File]::ReadAllText($taskCrowdedSourceValidation)
    if ([regex]::Match($taskCrowdedValidationText, '(?m)^input\.sha256=([^\r\n]*)').Groups[1].Value -ne $taskCrowdedSourcePredecessorHash -or
        [regex]::Match($taskCrowdedValidationText, '(?m)^output\.sha256=([^\r\n]*)').Groups[1].Value -ne $taskCrowdedSourceHash) {
        throw 'Crowded source validation catalog does not match its pinned input/output'
    }

    $taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
    $taskAsm = Join-Path $taskDeps 'asm-9.7.jar'
    $taskAsmTree = Join-Path $taskDeps 'asm-tree-9.7.jar'
    $taskMicro = Join-Path $taskDeps 'microemulator.jar'
    foreach ($taskDependency in @($taskAsm, $taskAsmTree, $taskMicro)) {
        if (-not (Test-Path -LiteralPath $taskDependency -PathType Leaf)) { throw "Missing dependency: $taskDependency" }
    }
    $taskAsmCp = $taskAsm + ';' + $taskAsmTree

    foreach ($taskFile in @(
        'tools/luna/HungBySonExpPatcher.java',
        'tools/legacy/luna/RepackSupportX3.java',
        'src/luna/api/ExpEstimateStore.java',
        'src/luna/features/ExpEstimateSupport.java',
        'src/luna/ui/ExpEstimateNoticeLayout.java',
        'tools/luna/templates/ExpEstimateRmsStore.java.in',
        'tools/luna/templates/ExpEstimateSharedRmsStore.java.in',
        'fixtures/hung-by-son/ExpEstimateHostTest.java',
        'fixtures/hung-by-son/HungBySonExpBinaryTest.java'
    )) {
        if (-not (Test-Path -LiteralPath $taskFile -PathType Leaf)) { throw "Required ExpEstimate source or fixture is not ready: $taskFile" }
    }
    foreach ($taskTest in @(
        'HungBySonMonsterCacheHostTest', 'HungBySonStaleTargetHostTest', 'HungBySonMonsterSelectionHostTest',
        'HungBySonRealTimeCombatHostTest', 'HungBySonInvestigateHudBinaryTest', 'HungBySonMonsterSelectionBinaryTest',
        'HungBySonMonsterRecoveryBinaryTest', 'HungBySonStaleTargetBinaryTest', 'HungBySonCrowdedMonsterHostTest',
        'HungBySonCrowdedMonsterBinaryTest', 'HungBySonRemoteAnimationBarrierTest', 'HungBySonCrowdedCacheX3HostTest',
        'HsoChildX3LauncherTest'
    )) {
        $taskFixture = if ($taskTest -eq 'HungBySonCrowdedCacheX3HostTest' -or $taskTest -eq 'HsoChildX3LauncherTest') {
            "fixtures/x3/$taskTest.java"
        } else { "fixtures/hung-by-son/$taskTest.java" }
        if (-not (Test-Path -LiteralPath $taskFixture -PathType Leaf)) { throw "Missing regression fixture: $taskFixture" }
    }

    $taskBuild = Join-Path $taskWorkspace (Join-Path 'build' $BuildTag)
    if (Test-Path -LiteralPath $taskBuild) { throw 'Build tag already exists; choose a fresh BuildTag' }
    $taskTools = Join-Path $taskBuild 'tools'
    $taskCompiled = Join-Path $taskBuild 'compiled-support'
    $taskGeneratedSrc = Join-Path $taskBuild 'generated-src'
    $taskHost = Join-Path $taskBuild 'host'
    $taskSemantic = Join-Path $taskBuild 'semantic'
    $taskSemanticHost = Join-Path $taskBuild 'semantic-host'
    $taskRuntime = Join-Path $taskBuild 'x3-runtime-home'
    $taskRuntimeSupport = Join-Path $taskBuild 'runtime-support'
    $taskSingleScreens = Join-Path $taskBuild 'single-screenshots'
    $taskX3Screens = Join-Path $taskBuild 'x3-screenshots'
    New-Item -ItemType Directory -Path $taskTools, $taskCompiled, $taskGeneratedSrc, $taskHost, $taskSemantic,
        $taskSemanticHost, $taskRuntime, $taskRuntimeSupport, $taskSingleScreens, $taskX3Screens | Out-Null

    Run-Checked javac @('-encoding', 'UTF-8', '--release', '7', '-cp', $taskAsmCp, '-d', $taskTools,
        'tools/luna/HungBySonExpPatcher.java', 'tools/legacy/luna/RepackSupportX3.java') (Join-Path $taskBuild 'tools-compile.txt')

    $taskGeneratedPackage = Join-Path $taskGeneratedSrc 'luna/generated'
    New-Item -ItemType Directory -Path $taskGeneratedPackage | Out-Null
    $taskGeneratedBridge = Join-Path $taskGeneratedPackage 'ExpEstimateRmsStore.java'
    Copy-Item -LiteralPath 'tools/luna/templates/ExpEstimateRmsStore.java.in' -Destination $taskGeneratedBridge
    $taskGeneratedSharedStore = Join-Path $taskGeneratedPackage 'ExpEstimateSharedRmsStore.java'
    Copy-Item -LiteralPath 'tools/luna/templates/ExpEstimateSharedRmsStore.java.in' -Destination $taskGeneratedSharedStore
    $taskSupportSources = @('src/luna/api/ExpEstimateStore.java', 'src/luna/features/ExpEstimateSupport.java',
        'src/luna/ui/ExpEstimateNoticeLayout.java', $taskGeneratedBridge, $taskGeneratedSharedStore)
    $taskSupportCp = $taskSingleInput + ';' + $taskMicro
    Run-Checked javac (@('-encoding', 'UTF-8', '--release', '7', '-cp', $taskSupportCp, '-d', $taskCompiled) + $taskSupportSources) (Join-Path $taskBuild 'support-compile.txt')

    $taskSingleCandidate = Join-Path $taskBuild 'Hung_By_Son.jar'
    $taskX3Candidate = Join-Path $taskBuild 'Hung_By_Son_x3.jar'
    $taskSingleCatalog = Join-Path $taskBuild 'single.properties'
    $taskX3Catalog = Join-Path $taskBuild 'x3.properties'
    $taskSinglePatchReport = Join-Path $taskBuild 'single-patch-report.txt'
    $taskX3PatchReport = Join-Path $taskBuild 'x3-patch-report.txt'
    Run-Checked java @('-Xverify:all', '-cp', "$taskTools;$taskAsmCp", 'HungBySonExpPatcher',
        $taskSingleInput, $taskCompiled, $taskSingleCandidate, $taskSingleCatalog, $taskSinglePatchReport, $taskSingleInputHash) (Join-Path $taskBuild 'single-patch.txt')
    Run-Checked java @('-Xverify:all', '-cp', "$taskTools;$taskAsmCp", 'HungBySonExpPatcher',
        $taskX3Input, $taskCompiled, $taskX3Candidate, $taskX3Catalog, $taskX3PatchReport, $taskX3InputHash) (Join-Path $taskBuild 'x3-patch.txt')
    $taskSingleHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskSingleCandidate).Hash
    $taskX3Hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskX3Candidate).Hash
    Assert-ArchiveDelta $taskSingleInput $taskSingleCandidate $taskSingleCatalog $taskSinglePatchReport (Join-Path $taskBuild 'single-archive-boundary.txt') 'single'
    Assert-ArchiveDelta $taskX3Input $taskX3Candidate $taskX3Catalog $taskX3PatchReport (Join-Path $taskBuild 'x3-archive-boundary.txt') 'x3'

    # Legacy fixtures resolve Luna APIs through the host classloader. Extract
    # only those support packages; the full candidate must stay in each fixture's
    # isolated loader so native game classes receive their instrumentation.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $taskSupportZip = [IO.Compression.ZipFile]::OpenRead($taskSingleCandidate)
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
                [StringComparison]::OrdinalIgnoreCase)) { throw 'Runtime-support extraction escaped the build directory' }
            New-Item -ItemType Directory -Path (Split-Path -Parent $taskDestination) -Force | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($taskEntry, $taskDestination, $false)
        }
    } finally { $taskSupportZip.Dispose() }
    foreach ($taskRequiredClass in @('luna/api/MonsterCacheAccess.class', 'luna/features/MonsterCacheSupport.class',
        'luna/generated/InvestigateStorage.class', 'luna/generated/ExpEstimateSharedRmsStore.class')) {
        if (-not (Test-Path -LiteralPath (Join-Path $taskRuntimeSupport $taskRequiredClass) -PathType Leaf)) {
            throw "Required legacy runtime support is absent: $taskRequiredClass"
        }
    }

    $taskSingleValidation = Join-Path $taskBuild 'single-validation.properties'
    $taskValidationText = $taskCrowdedValidationText
    $taskValidationText = Set-PropertyLine $taskValidationText 'input.sha256' $taskSingleInputHash
    $taskValidationText = Set-PropertyLine $taskValidationText 'output.sha256' $taskSingleHash
    [IO.File]::WriteAllText($taskSingleValidation, $taskValidationText, [Text.UTF8Encoding]::new($false))

    $taskTiming = Join-Path $taskBuild 'real650-validation.properties'
    $taskTimingSource = 'build/hung-real650-x3-20261006-b/real650.properties'
    if (-not (Test-Path -LiteralPath $taskTimingSource -PathType Leaf)) { throw 'Missing pinned real650 timing catalog' }
    $taskTimingText = [IO.File]::ReadAllText($taskTimingSource)
    $taskTimingText = Set-PropertyLine $taskTimingText 'input.sha256' $taskSingleInputHash
    $taskTimingText = Set-PropertyLine $taskTimingText 'output.sha256' $taskSingleHash
    [IO.File]::WriteAllText($taskTiming, $taskTimingText, [Text.UTF8Encoding]::new($false))

    $taskFixtureFiles = @(
        'fixtures/hung-by-son/HungBySonMonsterCacheHostTest.java', 'fixtures/hung-by-son/HungBySonStaleTargetHostTest.java',
        'fixtures/hung-by-son/HungBySonMonsterSelectionHostTest.java', 'fixtures/hung-by-son/HungBySonRealTimeCombatHostTest.java',
        'fixtures/hung-by-son/HungBySonCrowdedMonsterHostTest.java', 'fixtures/hung-by-son/HungBySonCrowdedMonsterBinaryTest.java',
        'fixtures/hung-by-son/HungBySonRemoteAnimationBarrierTest.java',
        'fixtures/hung-by-son/HungBySonInvestigateBinaryTest.java', 'fixtures/hung-by-son/HungBySonInvestigateHudBinaryTest.java',
        'fixtures/hung-by-son/HungBySonMonsterSelectionBinaryTest.java', 'fixtures/hung-by-son/HungBySonMonsterRecoveryBinaryTest.java',
        'fixtures/hung-by-son/HungBySonStaleTargetBinaryTest.java',
        'fixtures/x3/HungBySonCrowdedCacheX3HostTest.java', 'fixtures/x3/HsoChildX3LauncherTest.java'
    )
    $taskFixtureCp = "$taskSingleCandidate;$taskAsmCp;$taskMicro"
    Run-Checked javac @('-encoding', 'UTF-8', '-cp', "$taskCompiled;$taskMicro", '-d', $taskHost,
        'fixtures/hung-by-son/ExpEstimateHostTest.java') (Join-Path $taskBuild 'ExpEstimateHostTest-compile.txt')
    Run-Checked javac @('-encoding', 'UTF-8', '-cp', "$taskAsmCp;$taskMicro", '-d', $taskHost,
        'fixtures/hung-by-son/HungBySonExpBinaryTest.java') (Join-Path $taskBuild 'ExpBinaryTest-compile.txt')
    Run-Checked javac (@('-encoding', 'UTF-8', '-cp', $taskFixtureCp, '-d', $taskHost) + $taskFixtureFiles) (Join-Path $taskBuild 'fixtures-compile.txt')
    $taskHostCp = "$taskHost;$taskAsmCp;$taskMicro"
    $taskLegacyHostCp = "$taskHost;$taskRuntimeSupport;$taskAsmCp;$taskMicro"
    $taskSupportHostCp = "$taskHost;$taskCompiled;$taskMicro"

    Run-Checked java @('-Xverify:all', '-cp', $taskSupportHostCp, 'ExpEstimateHostTest') (Join-Path $taskBuild 'ExpEstimateHostTest.txt')
    foreach ($taskTest in @('HungBySonMonsterCacheHostTest', 'HungBySonStaleTargetHostTest',
        'HungBySonMonsterSelectionHostTest', 'HungBySonCrowdedMonsterHostTest')) {
        Run-Checked java @('-Xverify:all', '-cp', $taskLegacyHostCp, $taskTest, $taskSingleCandidate) (Join-Path $taskBuild "$taskTest.txt")
    }
    foreach ($taskTest in @('HungBySonInvestigateHudBinaryTest', 'HungBySonMonsterSelectionBinaryTest',
        'HungBySonMonsterRecoveryBinaryTest', 'HungBySonStaleTargetBinaryTest', 'HungBySonCrowdedMonsterBinaryTest')) {
        Run-Checked java @('-Xverify:all', '-cp', $taskLegacyHostCp, $taskTest, $taskSingleCandidate, $taskSingleValidation, $taskMicro) (Join-Path $taskBuild "$taskTest.txt")
    }
    Run-Checked java @('-Xverify:all', '-cp', $taskLegacyHostCp, 'HungBySonRemoteAnimationBarrierTest',
        $taskCrowdedSourceInput, $taskCrowdedSourceValidation, $taskCrowdedPredecessor) (Join-Path $taskBuild 'remote-animation-barrier.txt')
    Run-Checked java @('-Xverify:all', '-cp', $taskLegacyHostCp, 'HungBySonRealTimeCombatHostTest',
        $taskSingleInput, $taskSingleCandidate, $taskTiming, 'symbols/hung-tempo-menu-fixed-20261004.properties', $taskMicro, '--preserved-timing') (Join-Path $taskBuild 'real650-preserved-timing.txt')
    Run-Checked java @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonExpBinaryTest',
        $taskSingleCandidate, $taskSingleCatalog, $taskMicro, $taskSingleScreens) (Join-Path $taskBuild 'single-exp-binary.txt')

    $taskSemanticSources = @(& rg --files src -g '*.java' | Where-Object {
        $_ -ne 'src/luna/api/ExpEstimateStore.java' -and
        $_ -ne 'src/luna/features/ExpEstimateSupport.java' -and
        $_ -ne 'src/luna/ui/ExpEstimateNoticeLayout.java'
    })
    if ($taskSemanticSources.Count -eq 0) { throw 'No semantic Java sources were found' }
    Run-Checked javac (@('-encoding', 'UTF-8', '--release', '7', '-d', $taskSemantic) + $taskSemanticSources) (Join-Path $taskBuild 'semantic-compile.txt')
    $taskSemanticTests = @(& rg --files fixtures/luna -g '*.java')
    if ($taskSemanticTests.Count -eq 0) { throw 'No Luna semantic fixtures were found' }
    Run-Checked javac (@('-encoding', 'UTF-8', '--release', '7', '-cp', $taskSemantic, '-d', $taskSemanticHost) + $taskSemanticTests) (Join-Path $taskBuild 'semantic-host-compile.txt')
    foreach ($taskTest in @('LunaSemanticHostTest', 'LunaControllerHostTest', 'LunaRouteHostTest', 'AutoCombatHostTest', 'LoginMaterialHostTest')) {
        Run-Checked java @('-Xverify:all', '-cp', "$taskSemantic;$taskSemanticHost", $taskTest) (Join-Path $taskBuild "$taskTest.txt")
    }

    & (Join-Path $PSScriptRoot 'run-hung-bachho-emulator-smoke.ps1') -Jar $taskSingleCandidate -DependencyDir $taskDeps -ReportDir (Join-Path $taskBuild 'single-emulator-smoke') |
        Tee-Object -FilePath (Join-Path $taskBuild 'single-emulator-smoke.txt')

    Run-Checked java @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonCrowdedCacheX3HostTest', $taskX3Candidate) (Join-Path $taskBuild 'crowded-cache-x3.txt')
    $taskLauncherArgs = @('-Xverify:all', "-Duser.home=$taskRuntime", '-Djava.awt.headless=true', '-cp', "$taskHost;$taskMicro",
        'HsoChildX3LauncherTest', $taskX3Candidate, $taskX3Screens, 'Hung By Son')
    $taskPrevious = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & java @taskLauncherArgs 1> (Join-Path $taskBuild 'x3-launcher.out.log') 2> (Join-Path $taskBuild 'x3-launcher.err.log')
        $taskLauncherExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $taskPrevious }
    if ($taskLauncherExit -ne 0 -or (Get-Item -LiteralPath (Join-Path $taskBuild 'x3-launcher.err.log')).Length -ne 0) {
        throw 'X3 launcher test failed or stderr was not empty'
    }
    if (Select-String -LiteralPath (Join-Path $taskBuild 'x3-launcher.out.log') -Pattern 'Exception|Error|not found|loi load') {
        throw 'X3 launcher stdout contains a runtime error'
    }
    Run-Checked java @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonExpBinaryTest',
        $taskX3Candidate, $taskX3Catalog, $taskMicro, $taskX3Screens) (Join-Path $taskBuild 'x3-exp-binary.txt')

    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskSingleInput).Hash -ne $ExpectedSingleInputHash -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskX3Input).Hash -ne $ExpectedX3InputHash -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskCrowdedSourceInput).Hash -ne $taskCrowdedSourceHash -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskReleaseSingle).Hash -ne $ReleaseSingleBeforeHash -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskReleaseX3).Hash -ne $ReleaseX3BeforeHash -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskCrowdedPredecessor).Hash -ne $taskCrowdedSourcePredecessorHash) {
        throw 'Pinned release inputs changed during staging'
    }
    if (Test-Path -LiteralPath (Join-Path $taskBuild 'verified.json')) { throw 'Refusing to overwrite verified.json' }
    [ordered]@{
        Verified = $true
        Single = $taskSingleCandidate
        SingleSHA256 = $taskSingleHash
        SingleSourceInput = $taskSingleInput
        SingleSourceInputSHA256 = $taskSingleInputHash
        SingleInputSHA256 = $taskSingleInputHash
        ExpectedSingleInputHash = $ExpectedSingleInputHash
        ReleaseSingleBefore = $taskReleaseSingle
        ReleaseSingleBeforeHash = $ReleaseSingleBeforeHash
        X3 = $taskX3Candidate
        X3SHA256 = $taskX3Hash
        X3SourceInput = $taskX3Input
        X3SourceInputSHA256 = $taskX3InputHash
        X3InputSHA256 = $taskX3InputHash
        ExpectedX3InputHash = $ExpectedX3InputHash
        ReleaseX3Before = $taskReleaseX3
        ReleaseX3BeforeHash = $ReleaseX3BeforeHash
        CrowdedSourceInput = $taskCrowdedSourceInput
        CrowdedSourceInputSHA256 = $taskCrowdedSourceHash
        CrowdedSourcePredecessorSHA256 = $taskCrowdedSourcePredecessorHash
        RemoteAnimationBarrierSourceSHA256 = $taskCrowdedSourceHash
        RemoteAnimationBarrierBaselineSHA256 = $taskCrowdedSourcePredecessorHash
        SingleChangedClasses = @(Get-ReportClasses $taskSinglePatchReport 'CHANGED_CLASS=')
        SingleAddedClasses = @(Get-ReportClasses $taskSinglePatchReport 'ADDED_CLASS=')
        X3ChangedClasses = @(Get-ReportClasses $taskX3PatchReport 'CHANGED_CLASS=')
        X3AddedClasses = @(Get-ReportClasses $taskX3PatchReport 'ADDED_CLASS=')
        RuntimeGate = '650ms preserved; entity/request timing checked byte-identical'
        EstimatePersistence = [ordered]@{
            CacheKey = 'level'
            CacheVersion = 2
            SharedAcrossSlots = $true
            Store = 'hbs_exp_levels_v2'
            SharedOwner = 'luna/generated/ExpEstimateSharedRmsStore'
        }
        NativeNoticePosition = 'native'
        NoticeHooks = 0
        NativeNoticeUnchanged = $true
        X3Slots = 3
        Published = $false
        LiveServerTested = $false
        SingleScreenshots = $taskSingleScreens
        X3Screenshots = $taskX3Screens
        LegacyRuntimeSupport = $taskRuntimeSupport
        SingleRegressionTests = @(
            'HungBySonMonsterCacheHostTest', 'HungBySonStaleTargetHostTest', 'HungBySonMonsterSelectionHostTest',
            'HungBySonCrowdedMonsterHostTest', 'HungBySonInvestigateHudBinaryTest',
            'HungBySonMonsterSelectionBinaryTest', 'HungBySonMonsterRecoveryBinaryTest',
            'HungBySonStaleTargetBinaryTest', 'HungBySonCrowdedMonsterBinaryTest',
            'HungBySonRemoteAnimationBarrierTest (crowded source F806 baseline 8B)',
            'HungBySonRealTimeCombatHostTest --preserved-timing'
        )
        X3RegressionTests = @('HungBySonCrowdedCacheX3HostTest', 'HsoChildX3LauncherTest', 'HungBySonExpBinaryTest')
        SemanticRegressionTests = @('LunaSemanticHostTest', 'LunaControllerHostTest', 'LunaRouteHostTest', 'AutoCombatHostTest', 'LoginMaterialHostTest')
        DependencyDir = $taskDeps
    } | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'verified.json')
    Write-Output "VERIFIED_SINGLE=$taskSingleCandidate; SHA256=$taskSingleHash"
    Write-Output "VERIFIED_X3=$taskX3Candidate; SHA256=$taskX3Hash"
} finally {
    Pop-Location
}
