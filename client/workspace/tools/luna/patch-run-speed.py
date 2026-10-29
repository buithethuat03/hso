"""Raise the inherited Luna movement cadence from 5/8 to 8/8 ticks.

Run from client/workspace. The input JAR is never overwritten by this tool.
The bytecode edit keeps the instruction length and stack shape unchanged.
"""

import argparse
import hashlib
import struct
import zipfile
from pathlib import Path


OLD_GATE = bytes.fromhex("10 6d b2")  # bipush 109; getstatic hsoMoveTick
GATE_TAIL = bytes.fromhex("10 07 7e 7a 04 7e 99")
NEW_GATE = bytes.fromhex("10 ff b2")  # bipush -1: every tick passes


def sha256(data):
    return hashlib.sha256(data).hexdigest().upper()


def constant_pool(class_bytes):
    assert class_bytes[:4] == b"\xca\xfe\xba\xbe", "invalid class header"
    count = struct.unpack_from(">H", class_bytes, 8)[0]
    pool = [None] * count
    offset = 10
    index = 1
    while index < count:
        tag = class_bytes[offset]
        offset += 1
        if tag == 1:  # Utf8
            length = struct.unpack_from(">H", class_bytes, offset)[0]
            offset += 2
            pool[index] = (tag, class_bytes[offset:offset + length].decode("utf-8", "replace"))
            offset += length
        elif tag in (3, 4):
            offset += 4
        elif tag in (5, 6):
            offset += 8
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = (tag, struct.unpack_from(">H", class_bytes, offset)[0])
            offset += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[index] = (tag, *struct.unpack_from(">HH", class_bytes, offset))
            offset += 4
        elif tag == 15:
            offset += 3
        else:
            raise ValueError(f"unknown constant pool tag {tag}")
        index += 1
    return pool


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input_jar", type=Path)
    parser.add_argument("output_jar", type=Path)
    parser.add_argument("--symbols", type=Path, default=Path("symbols/autotrade-symbols.properties"))
    args = parser.parse_args()
    if not args.input_jar.is_file():
        parser.error("input JAR does not exist")
    if args.output_jar.exists():
        parser.error("output JAR already exists")
    if args.input_jar.resolve() == args.output_jar.resolve():
        parser.error("input and output must differ")

    symbols = {}
    for line in args.symbols.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            symbols[key] = value
    entry_name = symbols["class.ENTITY"] + ".class"
    output_entries = {}
    with zipfile.ZipFile(args.input_jar) as source:
        assert source.testzip() is None, "input JAR has a corrupt entry"
        infos = source.infolist()
        assert entry_name in source.namelist(), "entity class missing"
        original = source.read(entry_name)
        assert b"hsoMoveTick" in original, "movement tick field missing"
        pattern_positions = []
        for offset in range(len(original) - 13):
            if original[offset:offset + 3] != OLD_GATE:
                continue
            if original[offset + 5:offset + 12] == GATE_TAIL:
                pattern_positions.append(offset)
        assert len(pattern_positions) == 1, "expected exactly one 5/8 movement gate"
        offset = pattern_positions[0]
        field_index = struct.unpack_from(">H", original, offset + 3)[0]
        pool = constant_pool(original)
        field_ref = pool[field_index]
        assert field_ref[0] == 9, "gate does not read a field"
        owner = pool[pool[field_ref[1]][1]][1]
        name_and_type = pool[field_ref[2]]
        field_name = pool[name_and_type[1]][1]
        descriptor = pool[name_and_type[2]][1]
        assert (owner, field_name, descriptor) == (symbols["class.ENTITY"], "hsoMoveTick", "I"), \
            "gate is not tied to the entity movement tick"
        modified = original[:offset] + NEW_GATE + original[offset + 3:]
        assert len(modified) == len(original)
        assert modified.count(NEW_GATE + original[offset + 3:offset + 12]) == 1
        output_entries[entry_name] = modified

        args.output_jar.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(args.output_jar, "w") as target:
            target.comment = source.comment
            for info in infos:
                target.writestr(info, output_entries.get(info.filename, source.read(info.filename)))

    with zipfile.ZipFile(args.input_jar) as before, zipfile.ZipFile(args.output_jar) as after:
        assert after.testzip() is None, "output JAR has a corrupt entry"
        assert before.namelist() == after.namelist(), "entry list changed"
        changed = [name for name in before.namelist() if before.read(name) != after.read(name)]
        assert changed == [entry_name], f"unexpected changed entries: {changed}"
        assert after.read(entry_name) == modified
    print(f"movement gate: 5/8 ticks (x1.25) -> 8/8 ticks (x2)")
    print(f"changed class: {entry_name}")
    print(f"entries: {len(infos)}; changed entries: {len(changed)}")
    print(f"input SHA-256: {sha256(args.input_jar.read_bytes())}")
    print(f"output SHA-256: {sha256(args.output_jar.read_bytes())}")


if __name__ == "__main__":
    main()
