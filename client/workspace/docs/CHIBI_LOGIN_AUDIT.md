# Hso.jar / chibi_final.jar: login and character-entry audit

Audit date: 2026-10-28. Read-only analysis; neither release JAR was patched.

This audit records the pre-fix inputs. A subsequent compatibility fix was
published to `chibi_final.jar` and `luna_final (1).jar`; the originals were
preserved as backups. See the 2026-10-28 entry in [RELEASES.md](RELEASES.md)
for current hashes, changes and passed checks. Live-server login remains
unverified.

After the user reported that the first fix still stalled, a second audit found
another width mismatch in **opcode 3 self-character initialization**: its bag
capacity was read as a byte rather than Hso's short, shifting the optional-pet
and timestamp tail before the readiness write. The native bytecode-slice fixture
reproduces that failure. The current releases include the short read and Hso's
minimum-one, ceiling page calculation. The comparison below describes the
original audit and is not an exhaustive inventory of the current fixes.

## Exact inputs

| File | SHA-256 |
|---|---|
| `client/jar/Hso.jar` | `C69F9C6E9FF3AB998D21C9D78919859E5364C4D28B628723DC74DA48EFDC68C6` |
| `client/jar/chibi_final.jar` | `A5C8FC9C5659518E2E62AB702381FD4E781B3C55ABE82F87EA4FF63A0D142DFB` |

The current Hso reference differs from both Hso hashes recorded in RELEASES.md
and the older Chibi Up patcher. Conclusions here use the current input bytes.
MIDlet release metadata must not be confused with the version string serialized
in the login packet.

## Findings

| Boundary | Current Hso | Chibi Final |
|---|---|---|
| Login opcode 1, serialized version | `9.9.9` | `9.9.9` |
| Username / password UTFs | Raw caller values | Raw caller values |
| Sixth login UTF | `arg6 + "|" + signature(username, connectionKey)` | `arg6 + "|HSOR_SECRET_2026"` |
| Session-key readiness in login serializer | If connected with a null key, bounded wait of up to ten 15ms sleeps | No corresponding key lookup or wait |
| Final login UTF | `LOCK`, after device-info UTF | `LOCK`, after device-info UTF |
| Character-selection opcode 13 | `writeByte(0); writeInt(charId)` | `writeByte(0); writeInt(charId)` |
| Full inventory lists, opcode 16, subtypes 3/4/5/7 | Count uses `readShort()` | Count uses `readByte()` |
| Storage-capacity field at the start of opcode 65 | `readShort()` | `readByte()` widened to a short field |

Hso login serializer is `IIlI.II(String,String,String,String,String,String,int,byte)`.
Its authentication helper is `IIIl.II(String,byte[])`. The selector sender is
`IIlI.II(byte,int)`; its byte argument is not serialized in place of the literal
zero. Chibi's selector method takes one int, but emits the same five payload
bytes for the same character ID. A Java descriptor difference here is not a
wire-format difference.

## Receive-side bytecode evidence

Current Hso network dispatch is `Il.run()`. Opcode 16 calls
`Illllll.IIlI(lII)`, and opcode 65 calls `Illllll.IIIIII(lII)`.
Both delegate to `Illllll.II(lII,IIIlIlI,byte)`, with context 0 and 9
respectively. In that shared method, `javap -p -c` confirms:

- Bytecode offset 14: `DataInputStream.readShort()` for context-9 capacity.
- Bytecode offset 104: `DataInputStream.readShort()` for subtype-4 full-list count.

In Chibi's corresponding private static three-argument parser:

- Bytecode offset 14: `DataInputStream.readByte()`, followed by `i2s`.
- Bytecode offset 106: `DataInputStream.readByte()` for subtype-4 full-list count.

Focused generated evidence under `build/chibi-receiver-audit-20261028/`:

- `hso/Illllll.java`: shared parser at line 4438; context-9 capacity at 4443;
  subtype-4 count at 4458; other full-list counts at 4501, 4537 and 4588.
- `chibi-receiver-readable.txt`: shared parser at line 5665; capacity at 5671;
  subtype-4 count at 5685; subsequent full-list branches also use byte counts.
  This is a generated display with abbreviated obfuscated identifiers, not
  editable feature source.
- `transport/Il.java`: current Hso frame decoding and opcode dispatch.

For example, a two-byte capacity of 300 is `01 2C`. A one-byte reader consumes
only `01` and interprets `2C` as the next field, shifting the following header.
A two-byte list count of 5 is `00 05`; the old byte reader sees count 0.
These are illustrations of the confirmed layout difference, not captured
server payloads. Packet framing bounds the damage to a packet; this does not
by itself prove desynchronization of the entire TCP stream.

## Interpretation and limits

The reported selection screen means a character list has already been received.
The outgoing selection packet itself has no demonstrated layout discrepancy.
There are, however, both an authentication-contract difference and receive-side
inventory/storage layout differences. Repointing Luna's endpoint does not make
these boundaries compatible with the current Hso reference.

If the live server sends the layout consumed by the working Hso client, Chibi
will misread these inventory/storage fields. This is a concrete compatibility
candidate for the post-selection failure. The two JARs alone do not establish
which packet first fails, whether the server separately rejects the old
authentication suffix, or which readiness condition leaves the spinner active.
No live login, account action, packet capture, or emulator gameplay reproduction
was performed.

The next diagnostic is a trace of opcode/length/handler exception and world-entry
state around selection, without logging credentials. Any compatibility patch
should cover both authentication and the verified receive layouts, using a new
staged artifact and binary fixtures before replacing a release. Changing the
version string or opcode-13 sender alone would not address these findings.
