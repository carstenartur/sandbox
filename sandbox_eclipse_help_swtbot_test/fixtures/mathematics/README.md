# Real mathematics Help inputs

These snapshots contain upstream arithmetic unchanged. `after.java.txt` is the
actual output of the current production `MathematicalAnalysis` pipeline, then
required byte-for-byte by the native registered-cleanup test. These files are
not manually written optimization suggestions.

| Fixture | Input provenance | Packaging |
| --- | --- | --- |
| 01 | Bouncy Castle `DHParametersHelper.hasAnySmallFactorsSafe`, commit `c314b9cdffa3958a0eff5344f8fdcdb0181ed830`, source lines 107–137 | Complete original method in its original package/class with its real imports; unrelated members omitted. Bindings use the target's `bcprov` bundle. |
| 02 | JDT Core `NumericTest.test001`, `R4_40` / `ef3d6f2115df89d7964bc13aa363ab8d6bd21256`, embedded Java program, lines 31–44 | Exact decoded upstream Java string-literal program, including its checks and comment. |

`example.properties` records the exact source path/revision, selected numeric
kinds and SHA-256 of `before.java.txt`. Source links and the distinction between
production code and upstream regression fixtures are also in installed Help.
The JDT sample retains its EPL-2.0 terms (see this test bundle's LICENSE.txt).
The Bouncy Castle excerpt is MIT-licensed; its copyright and complete permission
notice ship in `sandbox_math_cleanup_help/html/bouncy-castle-license.html`.

All examples use Java 17, INT, PRESERVE_JAVA, READABILITY, a work budget of
1,000,000 and 20,000 states. They illustrate statically known arithmetic, not
measured speedups or arbitrary-variable algebra. Preserve the actual emitter's
output exactly when updating snapshots. The former JDT UI ExtractTemp inputs
`A_test114_in.java` and `A_test54_in.java` were removed because they only compute
locals that are never read; the live-output cleanup correctly leaves them unchanged.

The focused local entry point is `MathematicsHelpScreenshotsSWTBotTest` under the
existing `help-screenshots` profile. The standard Help merge gate calls the same
capture helper and compares committed images without changing image tolerances.
