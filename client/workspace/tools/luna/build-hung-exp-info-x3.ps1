[CmdletBinding()]
param(
    [string]$SingleInputJar = '..\jar\Hung_By_Son.jar',
    [string]$SingleVerifiedJson = 'build\hung-exp-svg-20261019-b\verified.json',
    [string]$SingleCatalog = 'build\hung-map48-inventory-resume100-20261014-d\auto-attack.properties',
    [string]$SingleTimingCatalog = 'build\hung-map48-inventory-resume100-20261014-d\real650-validation.properties',
    [string]$TemplateJar = '..\jar\backup\autotrade_v27_revive05_horse_x3.jar',
    [string]$X3ReleaseJar = '..\jar\Hung_By_Son_x3.jar',
    [string]$ExpectedSingleSourceHash = '3CB0DBA1EC67F0687057AEB1533C025A7858D85DC11B2A246097A029C053FCCF',
    [string]$ExpectedX3ReleaseHash = '4997CF94549554D4A2E70EF50692659A000D0C0D6413A13360D3625077F12796',
    [string]$ExpectedTemplateHash = '5DC9E9821904617B8A8171F45530DA3E078E6492EC6E37580E160AE6AC1732DC',
    [string]$DependencyDir = '',
    [string]$BuildTag = ('hung-exp-info-x3-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')),
    [switch]$StageFromVerifiedCandidate,
    [int]$ExpectedX3ClassCount = 812
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskGoldenHash = '06622CD81DC6790871A5863D465D4E352B29A94BB6FD0062C54F7CF9C6C47E0F'
$taskTemplateHash = '5DC9E9821904617B8A8171F45530DA3E078E6492EC6E37580E160AE6AC1732DC'
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
        if (Test-Path -LiteralPath $Log) { Get-Content -LiteralPath $Log -Tail 24 }
        throw "$Program failed with exit code $taskExit"
    }
    if ($taskLines.Count -gt 0) { $taskLines | Select-Object -Last 1 }
}

function Read-Properties {
    param([string]$Path)
    $taskProperties = @{}
    foreach ($taskLine in Get-Content -Encoding UTF8 -LiteralPath $Path) {
        $taskText = $taskLine.Trim()
        if ($taskText.Length -eq 0 -or $taskText.StartsWith('#') -or $taskText.StartsWith('!')) { continue }
        $taskSplit = $taskText.IndexOf('=')
        if ($taskSplit -lt 1) { $taskSplit = $taskText.IndexOf(':') }
        if ($taskSplit -lt 1) { throw "Malformed property in $Path`: $taskText" }
        $taskKey = $taskText.Substring(0, $taskSplit).Trim()
        if ($taskProperties.ContainsKey($taskKey)) { throw "Duplicate property '$taskKey' in $Path" }
        $taskProperties[$taskKey] = $taskText.Substring($taskSplit + 1).Trim()
    }
    return $taskProperties
}

function Get-RequiredProperty {
    param([hashtable]$Properties, [string]$Name)
    if (-not $Properties.ContainsKey($Name) -or [string]::IsNullOrWhiteSpace([string]$Properties[$Name])) {
        throw "Missing required property '$Name'"
    }
    return [string]$Properties[$Name]
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
    return ($Text.TrimEnd() + "`n" + $Name + '=' + $Value + "`n")
}

function Convert-ClassNameToEntry {
    param([string]$Name)
    $taskValue = $Name.Trim()
    if ([string]::IsNullOrWhiteSpace($taskValue)) { throw 'Empty class name in Auto Attack catalog' }
    if ($taskValue.EndsWith('.class', [StringComparison]::Ordinal)) { return $taskValue }
    if ($taskValue.Contains('.')) { $taskValue = $taskValue.Replace('.', '/') }
    return ($taskValue + '.class')
}

function Get-JarDirectorySnapshot {
    param([string]$Directory)
    $taskSnapshot = [System.Collections.Generic.Dictionary[string, string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($taskJar in @(Get-ChildItem -LiteralPath $Directory -File -Filter '*.jar' | Sort-Object Name)) {
        $taskHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskJar.FullName).Hash
        if ($taskSnapshot.ContainsKey($taskJar.Name)) { throw "Duplicate JAR filename: $($taskJar.Name)" }
        $taskSnapshot.Add($taskJar.Name, $taskHash)
    }
    return ,$taskSnapshot
}

function Assert-JarDirectorySnapshot {
    param([System.Collections.Generic.Dictionary[string, string]]$Before, [string]$Directory)
    $taskAfter = Get-JarDirectorySnapshot $Directory
    if ($Before.Count -ne $taskAfter.Count) { throw 'Release endpoint inventory changed during X3 staging' }
    foreach ($taskName in $Before.Keys) {
        if (-not $taskAfter.ContainsKey($taskName) -or $Before[$taskName] -cne $taskAfter[$taskName]) {
            throw "Release endpoint changed during X3 staging: $taskName"
        }
    }
    return ,$taskAfter
}

function Get-ZipEntryMap {
    param([System.IO.Compression.ZipArchive]$Archive)
    $taskMap = [System.Collections.Generic.Dictionary[string, System.IO.Compression.ZipArchiveEntry]]::new([StringComparer]::Ordinal)
    foreach ($taskEntry in $Archive.Entries) {
        if ($taskMap.ContainsKey($taskEntry.FullName)) { throw "Duplicate archive entry: $($taskEntry.FullName)" }
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

function Assert-X3Archive {
    param([string]$SourceJar, [string]$TemplatePath, [string]$CandidateJar, [string]$BoundaryPath, [int]$ExpectedClasses)
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $taskSourceZip = [IO.Compression.ZipFile]::OpenRead($SourceJar)
    $taskTemplateZip = [IO.Compression.ZipFile]::OpenRead($TemplatePath)
    $taskCandidateZip = [IO.Compression.ZipFile]::OpenRead($CandidateJar)
    try {
        $taskSource = Get-ZipEntryMap $taskSourceZip
        $taskTemplate = Get-ZipEntryMap $taskTemplateZip
        $taskCandidate = Get-ZipEntryMap $taskCandidateZip
        $taskSourceClasses = @($taskSource.Keys | Where-Object { $_.EndsWith('.class', [StringComparison]::Ordinal) }).Count
        $taskCandidateClasses = @($taskCandidate.Keys | Where-Object { $_.EndsWith('.class', [StringComparison]::Ordinal) }).Count
        if ($taskCandidateClasses -ne $ExpectedClasses) { throw "Expected $ExpectedClasses X3 classes, got $taskCandidateClasses" }
        foreach ($taskName in $taskSource.Keys) {
            if ($taskName.EndsWith('/', [StringComparison]::Ordinal) -or
                $taskName.EndsWith('.class', [StringComparison]::Ordinal) -or
                $taskName -ceq 'META-INF/MANIFEST.MF') { continue }
            if (-not $taskCandidate.ContainsKey($taskName) -or
                -not [HungBySonAutoAttackX3BuildBytes]::Equal((Read-ZipEntryBytes $taskSource[$taskName]),
                    (Read-ZipEntryBytes $taskCandidate[$taskName]))) {
                throw "Source resource changed or disappeared in X3: $taskName"
            }
        }
        $taskWrapperClasses = @($taskTemplate.Keys | Where-Object {
            $_.EndsWith('.class', [StringComparison]::Ordinal) -and
            ($_.StartsWith('lib/', [StringComparison]::Ordinal) -or $_.StartsWith('MrQuyet/', [StringComparison]::Ordinal))
        })
        if ($taskWrapperClasses.Count -ne 19) { throw "Expected 19 launcher wrapper classes, got $($taskWrapperClasses.Count)" }
        foreach ($taskName in $taskWrapperClasses) {
            if (-not $taskCandidate.ContainsKey($taskName) -or
                -not [HungBySonAutoAttackX3BuildBytes]::Equal((Read-ZipEntryBytes $taskTemplate[$taskName]),
                    (Read-ZipEntryBytes $taskCandidate[$taskName]))) {
                throw "Launcher template wrapper changed: $taskName"
            }
        }
        $taskIcon = 'MrQuyet/mid.png'
        if (-not $taskTemplate.ContainsKey($taskIcon) -or -not $taskCandidate.ContainsKey($taskIcon) -or
            -not [HungBySonAutoAttackX3BuildBytes]::Equal((Read-ZipEntryBytes $taskTemplate[$taskIcon]),
                (Read-ZipEntryBytes $taskCandidate[$taskIcon]))) { throw 'Launcher icon differs from the pinned template' }
        if (-not $taskCandidate.ContainsKey('META-INF/MANIFEST.MF')) { throw 'Candidate manifest is missing' }
        @(
            "SOURCE_CLASSES=$taskSourceClasses"
            "X3_CLASSES=$taskCandidateClasses"
            "TEMPLATE_WRAPPER_CLASSES=$($taskWrapperClasses.Count)"
            "SOURCE_NONCLASS_RESOURCES_BYTE_IDENTICAL=true"
            'TEMPLATE_WRAPPERS_BYTE_IDENTICAL=true'
            'TEMPLATE_ICON_BYTE_IDENTICAL=true'
            'MANIFEST_PRESENT=true'
        ) | Set-Content -Encoding UTF8 -LiteralPath $BoundaryPath
        return $taskSourceClasses
    } finally {
        $taskSourceZip.Dispose(); $taskTemplateZip.Dispose(); $taskCandidateZip.Dispose()
    }
}

function Assert-X3ClassVersionAndClcd {
    param([string]$CandidateJar, [hashtable]$SingleProperties, [string]$PackerPropertiesPath,
        [string]$ReportPath, [int]$ExpectedClasses)
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $taskCandidateZip = [IO.Compression.ZipFile]::OpenRead($CandidateJar)
    try {
        $taskEntries = Get-ZipEntryMap $taskCandidateZip
        $taskClassEntries = @($taskEntries.Keys | Where-Object { $_.EndsWith('.class', [StringComparison]::Ordinal) })
        if ($taskClassEntries.Count -ne $ExpectedClasses) { throw 'X3 class count differs from the verified packer report' }
        $taskMaximumMajor = 0
        foreach ($taskName in $taskClassEntries) {
            $taskMajor = [HungBySonAutoAttackX3BuildBytes]::Major((Read-ZipEntryBytes $taskEntries[$taskName]))
            if ($taskMajor -gt 51) { throw "X3 class exceeds the Java 7 ceiling: $taskName ($taskMajor)" }
            if ($taskMajor -gt $taskMaximumMajor) { $taskMaximumMajor = $taskMajor }
        }

        $taskPacker = Read-Properties $PackerPropertiesPath
        $taskSharedOwner = Get-RequiredProperty $taskPacker 'shared.exp.owner'
        $taskSharedEntry = $taskSharedOwner + '.class'
        $taskSingleShared = [string]$SingleProperties['shared.exp.owner']
        if ([string]::IsNullOrWhiteSpace($taskSingleShared)) { $taskSingleShared = 'luna/generated/ExpEstimateSharedRmsStore' }
        if ($taskSharedOwner -cne $taskSingleShared) { throw 'Packer shared EXP owner differs from the pinned single source catalog' }

        $taskScanNames = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        $taskAddedText = Get-RequiredProperty $SingleProperties 'added.classes'
        foreach ($taskSingleName in $taskAddedText.Split(',')) {
            $taskEntry = Convert-ClassNameToEntry $taskSingleName
            $taskOwner = $taskEntry.Substring(0, $taskEntry.Length - 6)
            foreach ($taskNamespace in @('', 'b/', 'c/')) {
                if ($taskOwner -ceq $taskSharedOwner -and $taskNamespace -ne '') { continue }
                [void]$taskScanNames.Add($taskNamespace + $taskEntry)
            }
        }
        foreach ($taskNamespace in @('', 'b/', 'c/')) {
            [void]$taskScanNames.Add($taskNamespace + 'Static.class')
            [void]$taskScanNames.Add($taskNamespace + 'X3SlotSupport.class')
        }
        $taskMaximumScanCount = 0
        foreach ($taskName in $taskScanNames) {
            if (-not $taskEntries.ContainsKey($taskName)) { throw "CLDC check expected support class: $taskName" }
            $taskConstants = [HungBySonAutoAttackX3BuildBytes]::Utf8Constants((Read-ZipEntryBytes $taskEntries[$taskName]))
            foreach ($taskConstant in $taskConstants) {
                foreach ($taskForbidden in $taskForbiddenClcdRefs) {
                    if ($taskConstant.IndexOf($taskForbidden, [StringComparison]::Ordinal) -ge 0) {
                        throw "Unsupported CLDC API '$taskForbidden' in X3 support class $taskName"
                    }
                }
            }
            $taskMaximumScanCount++
        }
        @(
            "MAX_CLASS_MAJOR=$taskMaximumMajor"
            "CLDC_SUPPORT_CLASSES=$taskMaximumScanCount"
            'CLDC_UNSUPPORTED_REFERENCES=false'
            "SHARED_EXP_CLASS=$taskSharedEntry"
            'FULL_CLASS_LINK_REQUIRED=HungBySonX3HostTest'
        ) | Set-Content -Encoding UTF8 -LiteralPath $ReportPath
    } finally { $taskCandidateZip.Dispose() }
}

if (-not ('HungBySonAutoAttackX3BuildBytes' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Text;
public static class HungBySonAutoAttackX3BuildBytes {
    public static bool Equal(byte[] a, byte[] b) {
        if (Object.ReferenceEquals(a, b)) return true;
        if (a == null || b == null || a.Length != b.Length) return false;
        for (int i = 0; i < a.Length; i++) if (a[i] != b[i]) return false;
        return true;
    }
    public static int Major(byte[] data) {
        if (data == null || data.Length < 8 || data[0] != 0xca || data[1] != 0xfe || data[2] != 0xba || data[3] != 0xbe)
            throw new InvalidOperationException("Invalid class file");
        return ((data[6] & 255) << 8) | (data[7] & 255);
    }
    public static string[] Utf8Constants(byte[] data) {
        Major(data);
        int p = 8;
        int count = ((data[p++] & 255) << 8) | (data[p++] & 255);
        var values = new List<string>();
        for (int i = 1; i < count; i++) {
            if (p >= data.Length) throw new InvalidOperationException("Truncated constant pool");
            int tag = data[p++], size;
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
if ($ExpectedX3ClassCount -lt 1) { throw 'ExpectedX3ClassCount must be positive' }
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskBuildRoot = [IO.Path]::GetFullPath((Join-Path $taskWorkspace 'build'))
if ([string]::IsNullOrWhiteSpace($DependencyDir)) {
    $DependencyDir = if ($env:LUNA_DEPS) { $env:LUNA_DEPS } else { Join-Path $taskWorkspace '../../.codex-deps' }
}

Push-Location $taskWorkspace
try {
    $taskSingleInput = (Resolve-Path -LiteralPath $SingleInputJar).Path
    $taskSingleProofPath = (Resolve-Path -LiteralPath $SingleVerifiedJson).Path
    $taskSingleCatalogPath = (Resolve-Path -LiteralPath $SingleCatalog).Path
    $taskSingleTimingPath = (Resolve-Path -LiteralPath $SingleTimingCatalog).Path
    $taskTemplate = (Resolve-Path -LiteralPath $TemplateJar).Path
    $taskX3Release = (Resolve-Path -LiteralPath $X3ReleaseJar).Path
    $taskSingleProof = Get-Content -Encoding UTF8 -Raw -LiteralPath $taskSingleProofPath | ConvertFrom-Json
    if (-not $taskSingleProof.Verified -or -not $taskSingleProof.AllClassesLinked -or
        -not $taskSingleProof.NativeExpAndRmsTested -or -not $taskSingleProof.NativeInfoInputAndConfirmationTested -or
        -not $taskSingleProof.SuppliedSvgIcon -or -not $taskSingleProof.IconMatchesNativeA -or
        -not $taskSingleProof.ModalSurvivesNativeFarmAndEnter -or -not $taskSingleProof.PointerOnlyConfirmation) {
        throw 'Single input requires verified SVG icon, EXP/RMS and native modal input proof'
    }
    $taskProofHash = [string]$taskSingleProof.SHA256
    if ($taskProofHash -notmatch '^[A-Fa-f0-9]{64}$') { throw 'Single verified.json has no pinned candidate SHA-256' }
    if ([string]::IsNullOrWhiteSpace($ExpectedSingleSourceHash)) {
        throw 'ExpectedSingleSourceHash must be set to the published single candidate SHA-256 before X3 staging'
    }
    if ($ExpectedSingleSourceHash -cne $taskProofHash) {
        throw 'ExpectedSingleSourceHash differs from the verified single candidate'
    }
    $taskSingleHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskSingleInput).Hash
    if ($taskSingleHash -cne $taskProofHash) { throw 'SingleInputJar does not match its verified build proof' }
    $taskSingleProperties = Read-Properties $taskSingleCatalogPath
    if ((Get-RequiredProperty $taskSingleProperties 'output.sha256') -cne 'BCF304BB6245CDBB6F48D5888404F8186E4E0895E2DCDAF608C68D53FEADA6E2') {
        throw 'Unexpected inherited Auto Attack symbol catalog'
    }
    $taskSingleProperties['output.sha256'] = $taskSingleHash
    $taskSingleProperties['added.classes'] += ',luna/ui/ExpPercentInterpolator.class,luna/features/ExpRateSupport.class,luna/generated/ExpInfoBridge.class,luna/generated/ExpInfoModalGuard.class,luna/ui/ExpInfoIcon.class'
    if ((Get-RequiredProperty $taskSingleProperties 'output.sha256') -cne $taskSingleHash) {
        throw 'Single Auto Attack catalog output.sha256 does not match the source JAR'
    }
    if ((Get-RequiredProperty $taskSingleProperties 'rms.name') -cne 'hbs_auto_attack_v1' -or
        (Get-RequiredProperty $taskSingleProperties 'rms.record.bytes') -cne '4' -or
        (Get-RequiredProperty $taskSingleProperties 'default.enabled') -cne 'false') {
        throw 'Single source Auto Attack RMS/default contract differs from the X3 stage requirements'
    }
    $taskAutoAttackDelay = [int](Get-RequiredProperty $taskSingleProperties 'delay.default.ms')
    if ($taskAutoAttackDelay -ne 650) { throw 'Single source must retain the agreed shared 650ms default before X3 packaging' }
    if ((Get-RequiredProperty $taskSingleProperties 'delay.minimum.ms') -cne '1' -or
        (Get-RequiredProperty $taskSingleProperties 'delay.maximum.ms') -cne '1000') {
        throw 'Single source delay range must remain 1..1000ms'
    }
    if ((Get-RequiredProperty $taskSingleProperties 'delay.shared.farm') -cne 'true') {
        throw 'Single source catalog does not bind the shared Farm/Auto delay implementation'
    }
    if ((Get-RequiredProperty $taskSingleProperties 'delay.movement.resume.ms') -cne '100' -or
        (Get-RequiredProperty $taskSingleProperties 'cache.disabled.map.id') -cne '48' -or
        (Get-RequiredProperty $taskSingleProperties 'native.inventory.panel.allowed') -cne '0') {
        throw 'Single source must bind movement resume100ms, excluded cache map48 and native inventory panel scope'
    }
    if ((Get-RequiredProperty $taskSingleProperties 'rms.farm.config.logical') -cne 'sys_auto_cfg' -or
        (Get-RequiredProperty $taskSingleProperties 'rms.farm.position.logical') -cne 'sys_farm_loc') {
        throw 'Single source does not identify the preserved Farm RMS records'
    }
    foreach ($taskKey in @('native.farm.enabled.owner', 'native.farm.enabled.field', 'native.farm.enabled.descriptor', 'delay.shared.farm',
        'native.farmAutoMinimum.owner', 'native.farmAutoMinimum.method', 'native.farmAutoMinimum.descriptor',
        'native.requestMinimum.owner', 'native.requestMinimum.method', 'native.requestMinimum.descriptor',
        'native.farmChoose.owner', 'native.farmChoose.method', 'native.farmChoose.descriptor',
        'hook.movement.keyboard.owner', 'hook.movement.keyboard.method', 'hook.movement.localUpdate.owner',
        'hook.movement.localUpdate.method', 'hook.movement.ground.owner', 'hook.movement.ground.method')) {
        [void](Get-RequiredProperty $taskSingleProperties $taskKey)
    }
    if ($ExpectedX3ClassCount -ne 812) { throw 'The verified Hung X3 stage must contain exactly 812 classes' }

    $taskSingleRelease = (Resolve-Path -LiteralPath '..\jar\Hung_By_Son.jar').Path
    $taskSingleReleaseBeforeHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskSingleRelease).Hash
    if ($StageFromVerifiedCandidate) {
        $taskSingleProofCandidate = (Resolve-Path -LiteralPath $taskSingleProof.Candidate).Path
        $taskSingleStageRoot = Split-Path -Parent $taskSingleProofPath
        if (-not $taskSingleStageRoot.StartsWith($taskBuildRoot + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase) -or $taskSingleInput -cne $taskSingleProofCandidate -or
            -not $taskSingleInput.StartsWith($taskSingleStageRoot + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Staged single input must be the exact candidate inside its verified workspace/build stage'
        }
    } elseif ($taskSingleReleaseBeforeHash -cne $taskSingleHash) {
        throw 'X3 packaging input must be the verified current single release'
    }
    $taskActualTemplateHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskTemplate).Hash
    if ($taskActualTemplateHash -cne $ExpectedTemplateHash -or $ExpectedTemplateHash -cne $taskTemplateHash) {
        throw 'Launcher template must match the pinned X3 wrapper template hash'
    }
    if ($ExpectedX3ReleaseHash -notmatch '^[A-Fa-f0-9]{64}$') { throw 'ExpectedX3ReleaseHash must be a SHA-256 value' }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskX3Release).Hash -cne $ExpectedX3ReleaseHash) {
        throw 'Existing X3 release differs from the pinned baseline preimage'
    }
    $taskReleaseJarDir = (Resolve-Path -LiteralPath '..\jar').Path
    $taskReleaseSnapshot = Get-JarDirectorySnapshot $taskReleaseJarDir
    $taskGolden = (Resolve-Path -LiteralPath 'inputs/luna_final-golden-baseline.jar').Path
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskGolden).Hash -cne $taskGoldenHash) {
        throw 'Immutable golden baseline hash changed'
    }
    $taskDeps = (Resolve-Path -LiteralPath $DependencyDir).Path
    $taskAsm = Join-Path $taskDeps 'asm-9.7.jar'
    $taskAsmTree = Join-Path $taskDeps 'asm-tree-9.7.jar'
    $taskMicro = Join-Path $taskDeps 'microemulator.jar'
    foreach ($taskDependency in @($taskAsm, $taskAsmTree, $taskMicro)) {
        if (-not (Test-Path -LiteralPath $taskDependency -PathType Leaf)) { throw "Missing dependency: $taskDependency" }
    }
    $taskAsmCp = $taskAsm + ';' + $taskAsmTree

    $taskRequiredFiles = @(
        'tools/luna/HungBySonX3Packer.java', 'tools/legacy/luna/HsoChildX3Packer.java',
        'tools/legacy/luna/RepackSupportX3.java', 'tools/legacy/luna/x3/X3SlotSupport.java.in',
        'fixtures/x3/HungBySonX3HostTest.java', 'fixtures/x3/HsoChildX3LauncherTest.java',
        'fixtures/x3/HungBySonCrowdedCacheX3HostTest.java',
        'fixtures/x3/HungBySonAutoAttackX3HostTest.java',
        'fixtures/hung-by-son/HungBySonExpBinaryTest.java'
    )
    foreach ($taskFile in $taskRequiredFiles) {
        if (-not (Test-Path -LiteralPath $taskFile -PathType Leaf)) { throw "Required X3 source/fixture is missing: $taskFile" }
    }

    $taskBuild = [IO.Path]::GetFullPath((Join-Path $taskBuildRoot $BuildTag))
    if (-not $taskBuild.StartsWith($taskBuildRoot + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) { throw 'X3 stage escaped workspace/build' }
    if (Test-Path -LiteralPath $taskBuild) { throw 'BuildTag already exists; use a unique X3 stage name' }
    $taskTools = Join-Path $taskBuild 'tools'
    $taskSupport = Join-Path $taskBuild 'support'
    $taskHost = Join-Path $taskBuild 'host'
    $taskSemantic = Join-Path $taskBuild 'semantic'
    $taskSemanticHost = Join-Path $taskBuild 'semantic-host'
    $taskScreens = Join-Path $taskBuild 'screenshots'
    $taskRuntime = Join-Path $taskBuild 'runtime-home'
    New-Item -ItemType Directory -Path $taskBuild, $taskTools, $taskSupport, $taskHost,
        $taskSemantic, $taskSemanticHost, $taskScreens, $taskRuntime | Out-Null

    $taskJavac = (Get-Command javac -ErrorAction Stop).Source
    $taskJava = (Get-Command java -ErrorAction Stop).Source
    Run-Checked $taskJavac @('-encoding', 'UTF-8', '--release', '7', '-cp', $taskAsmCp, '-d', $taskTools,
        'tools/luna/HungBySonX3Packer.java', 'tools/legacy/luna/HsoChildX3Packer.java',
        'tools/legacy/luna/RepackSupportX3.java') (Join-Path $taskBuild 'tools-compile.txt')
    $taskSlotSupportSource = Join-Path $taskSupport 'X3SlotSupport.java'
    Copy-Item -LiteralPath 'tools/legacy/luna/x3/X3SlotSupport.java.in' -Destination $taskSlotSupportSource
    Run-Checked $taskJavac @('-encoding', 'UTF-8', '--release', '7', '-d', $taskSupport, $taskSlotSupportSource) `
        (Join-Path $taskBuild 'slot-support-compile.txt')

    $taskCandidate = Join-Path $taskBuild 'Hung_By_Son_x3.jar'
    $taskPackerReport = Join-Path $taskBuild 'x3-pack-report.txt'
    $taskPackerPropertiesPath = $taskPackerReport + '.properties'
    Run-Checked $taskJava @('-Xverify:all', '-cp', "$taskTools;$taskAsmCp", 'HungBySonX3Packer',
        $taskSingleInput, $taskTemplate, $taskCandidate, (Join-Path $taskSupport 'X3SlotSupport.class'),
        $taskPackerReport, $taskSingleHash) (Join-Path $taskBuild 'x3-pack.txt')
    if (-not (Test-Path -LiteralPath $taskCandidate -PathType Leaf) -or
        -not (Test-Path -LiteralPath $taskPackerPropertiesPath -PathType Leaf)) {
        throw 'X3 packer must create a fresh candidate and report properties'
    }
    $taskCandidateHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $taskCandidate).Hash
    $taskPackerProperties = Read-Properties $taskPackerPropertiesPath
    foreach ($taskExpected in @{
        'source.sha256' = $taskSingleHash
        'template.sha256' = $taskActualTemplateHash
        'output.sha256' = $taskCandidateHash
        'shared.exp.owner' = 'luna/generated/ExpEstimateSharedRmsStore'
        'shared.exp.store' = 'hbs_exp_levels_v2'
        'auto.rms.logical.name' = 'hbs_auto_attack_v1'
        'auto.rms.physical.names' = 'ahbs_auto_attack_v1,bhbs_auto_attack_v1,chbs_auto_attack_v1'
        'cross.slot.references' = 'shared-exp-only'
        'cross.slot.unapproved.references' = 'none'
    }.GetEnumerator()) {
        if ((Get-RequiredProperty $taskPackerProperties $taskExpected.Key) -cne $taskExpected.Value) {
            throw "X3 packer report property '$($taskExpected.Key)' differs from the pinned contract"
        }
    }
    $taskSourceArchive = [IO.Compression.ZipFile]::OpenRead($taskSingleInput)
    try {
        $taskSourceMap = Get-ZipEntryMap $taskSourceArchive
        $taskSourceClassCount = @($taskSourceMap.Keys | Where-Object { $_.EndsWith('.class', [StringComparison]::Ordinal) }).Count
    } finally { $taskSourceArchive.Dispose() }
    if ($taskSourceClassCount -ne 263) { throw 'Published single Auto Attack source must contain the verified 263-class set' }
    if ([int](Get-RequiredProperty $taskPackerProperties 'source.classes') -ne $taskSourceClassCount -or
        [int](Get-RequiredProperty $taskPackerProperties 'output.classes') -ne $ExpectedX3ClassCount) {
        throw 'Packer source/output class counts differ from archive and expected X3 class set'
    }
    $taskClassSetHash = Get-RequiredProperty $taskPackerProperties 'source.classset.sha256'
    if ($taskClassSetHash -notmatch '^[A-Fa-f0-9]{64}$') { throw 'Packer source class-set hash is malformed' }
    $taskArchiveBoundary = Join-Path $taskBuild 'archive-boundary.txt'
    $taskSourceClassesObserved = Assert-X3Archive $taskSingleInput $taskTemplate $taskCandidate $taskArchiveBoundary $ExpectedX3ClassCount
    if ($taskSourceClassesObserved -ne $taskSourceClassCount) { throw 'Source class count changed between checks' }
    $taskClcdReport = Join-Path $taskBuild 'x3-cldc-class-version.txt'
    Assert-X3ClassVersionAndClcd $taskCandidate $taskSingleProperties $taskPackerPropertiesPath $taskClcdReport $ExpectedX3ClassCount

    $taskSingleAutoCatalogCopy = Join-Path $taskBuild 'single-auto-attack.properties'
    $taskAutoText = Set-PropertyLine ([IO.File]::ReadAllText($taskSingleCatalogPath)) 'output.sha256' $taskSingleHash
    [IO.File]::WriteAllText($taskSingleAutoCatalogCopy, $taskAutoText, [Text.UTF8Encoding]::new($false))
    $taskHostTimingText = [IO.File]::ReadAllText($taskSingleTimingPath)
    $taskHostTimingText = Set-PropertyLine $taskHostTimingText 'input.sha256' $taskSingleHash
    $taskHostTimingText = Set-OptionalPropertyLine $taskHostTimingText 'output.sha256' $taskCandidateHash
    $taskHostTimingText = Set-OptionalPropertyLine $taskHostTimingText 'auto.attack.delay.default.ms' ([string]$taskAutoAttackDelay)
    $taskHostTimingText = Set-OptionalPropertyLine $taskHostTimingText 'x3.class.count' ([string]$ExpectedX3ClassCount)
    foreach ($taskKey in @('native.farm.enabled.owner', 'native.farm.enabled.field', 'native.farm.enabled.descriptor', 'delay.shared.farm',
        'native.farmAutoMinimum.owner', 'native.farmAutoMinimum.method', 'native.farmAutoMinimum.descriptor',
        'native.requestMinimum.owner', 'native.requestMinimum.method', 'native.requestMinimum.descriptor',
        'native.farmChoose.owner', 'native.farmChoose.method', 'native.farmChoose.descriptor',
        'request.farmOrigin.field', 'hook.movement.keyboard.owner', 'hook.movement.keyboard.method',
        'hook.movement.keyboard.descriptor', 'hook.movement.localUpdate.owner', 'hook.movement.localUpdate.method',
        'hook.movement.localUpdate.descriptor', 'hook.movement.ground.owner', 'hook.movement.ground.method',
        'hook.movement.ground.descriptor', 'native.movement.held.owner', 'native.movement.held.method',
        'native.movement.held.descriptor', 'native.movement.pending.owner', 'native.movement.pending.method',
        'native.movement.pending.descriptor')) {
        $taskValue = Get-RequiredProperty $taskSingleProperties $taskKey
        $taskHostTimingText = Set-OptionalPropertyLine $taskHostTimingText $taskKey $taskValue
    }
    $taskHostTiming = Join-Path $taskBuild 'x3-host-validation.properties'
    [IO.File]::WriteAllText($taskHostTiming, $taskHostTimingText, [Text.UTF8Encoding]::new($false))

    $taskExpCatalogText = [IO.File]::ReadAllText('symbols/hung-exp-native-notices-x3-20261013.properties')
    $taskExpCatalogText = Set-PropertyLine $taskExpCatalogText 'output.sha256' $taskCandidateHash
    $taskExpCatalogText = Set-OptionalPropertyLine $taskExpCatalogText 'percent.decimals' '3'
    $taskExpCatalogText = Set-OptionalPropertyLine $taskExpCatalogText 'calculating.text' '\u0110ang t\u00EDnh to\u00E1n...'
    $taskExpCatalog = Join-Path $taskBuild 'x3-exp-validation.properties'
    [IO.File]::WriteAllText($taskExpCatalog, $taskExpCatalogText, [Text.UTF8Encoding]::new($false))
    $taskInfoText = [IO.File]::ReadAllText('build/hung-exp-svg-20261019-b/info-symbols.properties')
    $taskInfoText = Set-PropertyLine $taskInfoText 'output.sha256' $taskCandidateHash
    $taskInfoText = Set-OptionalPropertyLine $taskInfoText 'single.sha256' $taskSingleHash
    $taskInfoText = Set-PropertyLine $taskInfoText 'slots' '3'
    foreach ($taskEntry in (Read-Properties $taskExpCatalog).GetEnumerator()) {
        if ($taskEntry.Key -match '^slot\.[012]\.') {
            $taskInfoText = Set-OptionalPropertyLine $taskInfoText $taskEntry.Key $taskEntry.Value
        }
    }
    $taskInfoCatalog = Join-Path $taskBuild 'x3-info-validation.properties'
    [IO.File]::WriteAllText($taskInfoCatalog, $taskInfoText, [Text.UTF8Encoding]::new($false))

    $taskFixtureSources = @(
        'fixtures/x3/HungBySonX3HostTest.java', 'fixtures/x3/HsoChildX3LauncherTest.java',
        'fixtures/x3/HungBySonCrowdedCacheX3HostTest.java', 'fixtures/x3/HungBySonAutoAttackX3HostTest.java',
        'fixtures/hung-by-son/HungBySonExpBinaryTest.java'
    )
    Run-Checked $taskJavac (@('-encoding', 'UTF-8', '-cp', "$taskCandidate;$taskTools;$taskAsmCp;$taskMicro", '-d', $taskHost) + $taskFixtureSources) `
        (Join-Path $taskBuild 'fixtures-compile.txt')
    $taskHostCp = "$taskHost;$taskTools;$taskAsmCp;$taskMicro"

    $taskSemanticSources = @(& rg --files src -g '*.java')
    if ($taskSemanticSources.Count -eq 0) { throw 'No semantic sources found' }
    Run-Checked $taskJavac (@('-encoding', 'UTF-8', '--release', '7', '-cp', $taskMicro, '-d', $taskSemantic) + $taskSemanticSources) `
        (Join-Path $taskBuild 'semantic-compile.txt')
    $taskSemanticFixtures = @(& rg --files fixtures/luna -g '*.java') + @(
        'fixtures/hung-by-son/ExpEstimateHostTest.java', 'fixtures/hung-by-son/ExpPercentInterpolatorHostTest.java',
        'fixtures/hung-by-son/ExpPercentIntegrationHostTest.java', 'fixtures/hung-by-son/ExpRateHostTest.java',
        'fixtures/hung-by-son/ExpInfoHostTest.java')
    if ($taskSemanticFixtures.Count -eq 0) { throw 'No semantic fixtures found' }
    Run-Checked $taskJavac (@('-encoding', 'UTF-8', '--release', '7', '-cp', "$taskSemantic;$taskMicro", '-d', $taskSemanticHost) + $taskSemanticFixtures) `
        (Join-Path $taskBuild 'semantic-fixtures-compile.txt')
    $taskSemanticCp = "$taskSemantic;$taskSemanticHost;$taskMicro"
    foreach ($taskTest in @('AutoAttackHostTest', 'LunaSemanticHostTest', 'LunaControllerHostTest',
        'LunaRouteHostTest', 'AutoCombatHostTest', 'LoginMaterialHostTest')) {
        Run-Checked $taskJava @('-Xverify:all', '-cp', $taskSemanticCp, $taskTest) (Join-Path $taskBuild "$taskTest.txt")
    }
    foreach ($taskTest in @('ExpEstimateHostTest','ExpPercentInterpolatorHostTest','ExpPercentIntegrationHostTest',
        'ExpRateHostTest','ExpInfoHostTest')) {
        Run-Checked $taskJava @('-Xverify:all', '-cp', "$taskCandidate;$taskSemanticHost;$taskMicro", $taskTest) (Join-Path $taskBuild "$taskTest.txt")
    }

    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonX3HostTest',
        $taskCandidate, $taskSingleInput, $taskHostTiming) (Join-Path $taskBuild 'x3-host.txt')
    if (-not (Select-String -LiteralPath (Join-Path $taskBuild 'x3-host.txt') -Pattern "PASS .* $ExpectedX3ClassCount linked classes" -Quiet)) {
        throw 'X3 host did not report a full class-link pass for the expected class count'
    }
    if (-not (Select-String -LiteralPath (Join-Path $taskBuild 'x3-host.txt') -Pattern '^MAP48_CACHE_DISABLED_VERIFIED=true$' -Quiet)) {
        throw 'X3 cache fixture lacks its per-slot map48 exclusion result'
    }
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonCrowdedCacheX3HostTest',
        $taskCandidate) (Join-Path $taskBuild 'crowded-x3-host.txt')
    if (-not (Select-String -LiteralPath (Join-Path $taskBuild 'crowded-x3-host.txt') -Pattern 'PASS \(74 checks' -Quiet)) {
        throw 'Crowded X3 regression did not pass its 74 per-slot checks'
    }

    $taskLauncherScreens = Join-Path $taskScreens 'launcher'
    New-Item -ItemType Directory -Path $taskLauncherScreens | Out-Null
    $taskLauncherArgs = @('-Xverify:all', "-Duser.home=$taskRuntime", '-Djava.awt.headless=true',
        '-cp', "$taskHost;$taskMicro", 'HsoChildX3LauncherTest', $taskCandidate,
        $taskLauncherScreens, 'Hung By Son')
    $taskPriorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $taskJava @taskLauncherArgs 1> (Join-Path $taskBuild 'launcher.out.log') 2> (Join-Path $taskBuild 'launcher.err.log')
        $taskLauncherExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $taskPriorPreference }
    if ($taskLauncherExit -ne 0 -or (Get-Item -LiteralPath (Join-Path $taskBuild 'launcher.err.log')).Length -ne 0) {
        throw 'X3 launcher test failed or stderr was not empty'
    }
    if (Select-String -LiteralPath (Join-Path $taskBuild 'launcher.out.log') -Pattern 'Exception|Error|not found|loi load') {
        throw 'X3 launcher stdout contains a runtime error'
    }
    if (-not (Select-String -LiteralPath (Join-Path $taskBuild 'launcher.out.log') -Pattern '1 -> 2 -> 3 -> 1' -Quiet)) {
        throw 'X3 launcher did not complete the held-* 1 -> 2 -> 3 -> 1 interaction'
    }

    $taskAutoX3ReportDir = Join-Path $taskScreens 'auto-attack-x3'
    New-Item -ItemType Directory -Path $taskAutoX3ReportDir | Out-Null
    $taskAutoX3Log = Join-Path $taskBuild 'auto-attack-x3-host.txt'
    $taskAutoX3Cp = "$taskHost;$taskMicro"
    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskAutoX3Cp, 'HungBySonAutoAttackX3HostTest',
        $taskCandidate, $taskSingleInput, $taskSingleAutoCatalogCopy, $taskPackerReport, $taskMicro, $taskAutoX3ReportDir) $taskAutoX3Log
    $taskAutoX3Lines = @(Get-Content -Encoding UTF8 -LiteralPath $taskAutoX3Log)
    foreach ($taskMarker in @('X3_AUTO_ATTACK_VERIFIED=true', 'X3_AUTO_RMS_TWO_PROCESS_VERIFIED=true',
        'SHARED_FARM_DELAY_VERIFIED=true', 'MANUAL_MOVEMENT_PRIORITY_VERIFIED=true',
        'X3_VIRTUAL_INPUT_VERIFIED=true', 'MOVEMENT_RESUME_100MS_VERIFIED=true',
        'INVENTORY_AUTO_ATTACK_VERIFIED=true',
        'WORLD_CHANGE_MOVEMENT_DELAY_VERIFIED=true')) {
        if ($taskAutoX3Lines -cnotcontains $taskMarker) {
            throw "X3 Auto Attack fixture lacks required completion marker: $taskMarker"
        }
    }
    foreach ($taskWorker in @('write', 'read')) {
        if (-not ($taskAutoX3Lines | Where-Object { $_ -match ('^X3_AUTO_WORKER=' + $taskWorker + ' PASS checks=[0-9]+$') })) {
            throw "X3 Auto Attack fixture lacks the count-bearing $taskWorker worker PASS line"
        }
    }
    if (-not ($taskAutoX3Lines | Where-Object { $_ -match '^HungBySonAutoAttackX3HostTest PASS checks=[0-9]+$' })) {
        throw 'X3 Auto Attack fixture lacks its count-bearing final PASS line'
    }

    Run-Checked $taskJava @('-Xverify:all', '-cp', $taskHostCp, 'HungBySonExpBinaryTest',
        $taskCandidate, $taskExpCatalog, $taskMicro, (Join-Path $taskScreens 'exp-x3')) `
        (Join-Path $taskBuild 'exp-native-x3.txt')

    Run-Checked $taskJavac @('-encoding', 'UTF-8', '-cp', "$taskHost;$taskMicro", '-d', $taskHost,
        'fixtures/x3/HungBySonExpInfoX3Test.java') (Join-Path $taskBuild 'exp-info-fixture-compile.txt')
    Run-Checked $taskJava @('-Xverify:all', '-cp', "$taskHost;$taskMicro", 'HungBySonExpInfoX3Test',
        $taskCandidate, $taskSingleInput, $taskInfoCatalog, $taskMicro,
        (Join-Path $taskScreens 'exp-info-x3')) (Join-Path $taskBuild 'exp-info-x3.txt')
    $taskInfoLines = @(Get-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'exp-info-x3.txt'))
    if ($taskInfoLines | Where-Object { $_ -match 'Exception|Error|MIDletThread throws' }) {
        throw 'X3 EXP info worker reported a runtime error'
    }
    foreach ($taskMarker in @('X3_EXP_INFO_VERIFIED=true','X3_EXP_RATE_ISOLATION_VERIFIED=true','X3_EXP_INFO_LIFECYCLE_VERIFIED=true')) {
        if ($taskInfoLines -cnotcontains $taskMarker) { throw ('Missing X3 EXP info result: ' + $taskMarker) }
    }

    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $taskSingleInput).Hash -cne $taskSingleHash -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskCandidate).Hash -cne $taskCandidateHash -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskTemplate).Hash -cne $taskActualTemplateHash -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskX3Release).Hash -cne $ExpectedX3ReleaseHash -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $taskGolden).Hash -cne $taskGoldenHash) {
        throw 'Pinned single, template, X3 endpoint baseline, or golden changed during staging'
    }
    $taskReleaseSnapshotAfter = Assert-JarDirectorySnapshot $taskReleaseSnapshot $taskReleaseJarDir
    if (Test-Path -LiteralPath (Join-Path $taskBuild 'verified.json')) { throw 'Refusing to overwrite verified.json' }
    [ordered]@{
        Verified = $true
        Published = $false
        LiveServerTested = $false
        X3Candidate = $taskCandidate
        X3CandidateSHA256 = $taskCandidateHash
        X3Classes = $ExpectedX3ClassCount
        SingleSource = $taskSingleInput
        SingleSourceSHA256 = $taskSingleHash
        SingleStagedBeforePublication = [bool]($StageFromVerifiedCandidate -and $taskSingleReleaseBeforeHash -cne $taskSingleHash)
        SingleEndpointAtStagingSHA256 = $taskSingleReleaseBeforeHash
        SingleVerifiedJson = $taskSingleProofPath
        SingleExpInfoVerified = $true
        SingleRmsLifecycleVerified = [bool]$taskSingleProof.NativeExpAndRmsTested
        SingleCatalog = $taskSingleCatalogPath
        SingleTimingCatalog = $taskSingleTimingPath
        Template = $taskTemplate
        TemplateSHA256 = $taskActualTemplateHash
        ExistingX3Endpoint = $taskX3Release
        ExistingX3EndpointSHA256 = $ExpectedX3ReleaseHash
        PackerReport = $taskPackerReport
        PackerProperties = $taskPackerPropertiesPath
        ArchiveBoundary = $taskArchiveBoundary
        ClcdClassVersionReport = $taskClcdReport
        FullClassLink = $true
        SharedExpBackendRootOnly = $true
        ExpInfoAllThreeSlotsVerified = $true
        SuppliedSvgIcon = $true
        PointerOnlyConfirmation = $true
        ExpRateSlotIsolation = $true
        Decimals = 3
        IconSize = 12
        AutoAttackRmsSlotIsolation = $true
        NativeInputEventsVerified = $true
        NativeInputEventsBoundToSourceSHA256 = $taskSingleHash
        SharedFarmDelayVerified = $true
        ManualMovementPriorityVerified = $true
        MovementResume100msVerified = $true
        InventoryAutoAttackVerified = $true
        Map48CacheDisabledVerified = $true
        VirtualInputChannelsVerified = $true
        ExpNativeAndSharedFileRmsVerified = $true
        CrowdedX3Verified = $true
        LauncherTransition = 'held-*; 1 -> 2 -> 3 -> 1; live instances retained'
        X3RegressionTests = @(
            'HungBySonX3HostTest (all class links, root shared EXP, per-slot RMS and Auto Attack defaults)',
            'HungBySonCrowdedCacheX3HostTest (74 checks across 3 namespaces)',
            'HsoChildX3LauncherTest (held-* and live 1 -> 2 -> 3 -> 1)',
            'HungBySonAutoAttackX3HostTest (two-process actual X3 Auto Attack modes, Farm delay, movement cancellation, slot isolation, and virtual input)',
            'HungBySonExpBinaryTest (native EXP, cleanup, shared File RMS, HUD/notice)'
            'HungBySonExpInfoX3Test (per-slot SVG icon, native modal, rate and Static lifecycle)'
        )
        SemanticRegressionTests = @('AutoAttackHostTest', 'LunaSemanticHostTest', 'LunaControllerHostTest',
            'LunaRouteHostTest', 'AutoCombatHostTest', 'LoginMaterialHostTest')
        ReleaseJarSnapshotUnchanged = $true
        AllOtherEndpointsPreserved = $true
        ReleaseJarEndpoints = @($taskReleaseSnapshotAfter.Keys | Sort-Object | ForEach-Object {
            [ordered]@{ Name = $_; SHA256 = $taskReleaseSnapshotAfter[$_] }
        })
        DependencyDir = $taskDeps
    } | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $taskBuild 'verified.json')
    Write-Output "VERIFIED_X3=$taskCandidate; SHA256=$taskCandidateHash; STAGE_ONLY=true"
} finally { Pop-Location }
