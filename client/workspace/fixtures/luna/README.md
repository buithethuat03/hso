# Luna fixtures

Fixtures are host-readable, deterministic inputs. They must not require a
JAR, a decompiler, an emulator, or a deleted historical artifact. Add a
fixture beside the owning controller when a timing, path, menu, or settle
invariant is discovered.

`LunaSemanticHostTest.java` is the minimum smoke test. Runtime traces use the
following fields, one event per line:

```text
timeMs feature event owner mapId x y detail
```

Keep traces small and semantic. A trace is evidence for a test, not a second
implementation of controller behavior.

`LunaControllerHostTest.java` also models the horse first-open lifecycle:
login screen, settling world screen, stable game screen, delayed mount retry,
logout, relogin, and enable-after-disabled. It intentionally does not require
the binary JAR or a live RMS store.
