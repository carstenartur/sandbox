# Unabhängiges Review — Native AST-/Kommentar-Freshness

**APPROVED für Commit `298def9d2ae0603857102c70819294b4cf156176`.**

| Identität | Geprüfter Wert |
|---|---|
| Tree | `5b01c8734662a4948440244ccab62dd0fdca0506` |
| Parent | `36be6f4476ce10e218833a5fe64fd9362358c7c8` |
| Umfang | Zwei Dateien, +58/−2; neun zusätzliche Produktionszeilen |
| Arbeitsverzeichnis bei Abschluss | Getrackter Stand sauber |

Unabhängige Prüfung am 2026-11-02 durch `/root/inverse_commit_review`, rein lesend anhand von Quellcode, Commitobjekten und vorhandenen RED-/GREEN-Logs und XML-Berichten. Der Reviewer hat keine JVM gestartet, keine Tests erneut ausgeführt und keine Quellen oder Remote-Objekte geändert.

## Befund und endgültige Korrektur

Die ursprüngliche Freshness-Prüfung besuchte die normale AST-Struktur. Losgelöste Line-/Block-Kommentare blieben unberücksichtigt. Bei einer gleichlangen Ersetzung von Leerraum durch einen Kommentar konnte deshalb ein alter AST zum aktuellen Quelltext zugelassen werden. Der Tail-Inspector prüfte die alte Kommentarliste; der anschließende Ersatz des ganzen Methodenrumpfs konnte den neuen Kommentar entfernen.

Die gemeinsame Freshness-Prüfung erfasst jetzt zusätzlich Kommentarart, Startposition und Länge sämtlicher Kommentare in ihrer bestehenden Reihenfolge. Jeder Kommentareintrag wird über den vorhandenen Work-Callback belastet; die zusätzliche Positionssignatur geht auch in den belasteten Vergleich ein. Der gebundene Original-AST und die bisherigen Abbruch-/Budgetpfade bleiben erhalten.

Beide Javadoc-Parsermodi werden weiterhin unterstützt, ohne zusätzliche Traversierung der Dokumentationstags. Dies wurde zusätzlich anhand der tatsächlich verwendeten JDT-3.47.0-Quellen überprüft: `DocCommentParser.parse` erzeugt auch bei deaktivierter Tag-Auswertung einen Javadoc-Knoten mit Quellbereich. Der vorhandene ENABLED/DISABLED-Retry bleibt damit mit dem Vergleich der Kommentarart und -bereiche vereinbar.

Der Reviewer meldete eine konkrete Regression des ersten Fixentwurfs: `CompilationUnit.getCommentList()` darf laut JDT-Vertrag null sein, wenn Kommentarinformation fehlt. Das neue unbedingte for-each hätte dann eine NullPointerException statt der bisherigen kontrollierten Stale-Ablehnung erzeugt. Der Implementierer reproduzierte dies mit einem tatsächlich ausgeführten RED-Test. Im endgültigen Commit liefert `matchesAstSource` bei fehlender Kommentarliste unmittelbar false. Die öffentliche Native-Überladung lehnt den AST kontrolliert mit `STALE_RECURSION_SOURCE` ab; fehlende Information wird nicht als leere Kommentarliste interpretiert.

## Nachvollzogene Ausführungsbelege

Die erhaltenen XMLs unter `/workspace/scratch/0eb32b15fbb4/recovery/native-freshness-review/` belegen:

- `red-xml`: zwei erwartete Assertion-Failures für gleichlang eingefügte Block-/Zeilenkommentare; keine Errors oder Skips. Die zugelassenen Original-/Generated-Fassungen wurden im Reproducer vor der abschließenden Assertion tatsächlich kompiliert und ausgeführt.
- `extended-red-xml`: vier erwartete Assertion-Failures, zusätzlich geänderte Kommentarart und verschobener Kommentarbereich bei unveränderter Gesamtlänge; keine Errors oder Skips.
- `missing-metadata-red-xml`: eine erwartete Assertion-Failure durch die tatsächliche NullPointerException im Zwischenstand; keine Errors oder Skips.
- `reviewed-green.log` und `reviewed-green-xml`: abschließender sauberer Neuaufbau mit `clean`, erneuter Kompilierung der Produktions- und Testquellen, Exit 0 und BUILD SUCCESS in 17,774 Sekunden.

| Finale Suite | Tests | Failures / Errors / Skips |
|---|---:|---|
| JavaTailRecursionInspectorTest | 15 | 0 / 0 / 0 |
| JavaTailRecursionReplayTest | 9 | 0 / 0 / 0 |
| JavaTailRecursionRewriteTest | 21 | 0 / 0 / 0 |
| JavaFiniteSumBudgetTest | 6 | 0 / 0 / 0 |
| JavaValueResidueTest | 11 | 0 / 0 / 0 |
| MathCleanUpOptionsTest | 9 | 0 / 0 / 0 |
| DocumentedSourceSnapshotTest | 2 | 0 / 0 / 0 |
| MathematicalInterruptionTest | 3 | 0 / 0 / 0 |
| **Gesamt** | **76** | **0 / 0 / 0** |

Die GREEN-Berichte enthalten beide Javadoc-Parsermodi, unveränderte Header-Kommentare als positive Kontrolle, die neuen negativen Freshness-Fälle sowie die bestehenden Budget-, Abbruch- und Snapshot-Fälle.

## Exakte Quellenbindung

Die Gitobjekte des freigegebenen Commits wurden gegen den Arbeitsstand und `reviewed-green-source-sha256.txt` verglichen. Beide committed Dateien sind byteidentisch mit den qualifizierten Quellen:

| Datei | SHA-256 |
|---|---|
| `sandbox_math_cleanup/src/org/sandbox/jdt/internal/corext/fix/math/MathematicalAnalysis.java` | `d928b99e80e50c7f240c2e42813ab79e4130d68fbcf5e0ff30c993a077eaa697` |
| `sandbox_math_cleanup_test/src/org/sandbox/jdt/internal/corext/fix/math/JavaTailRecursionRewriteTest.java` | `9b4602ba915198ec67f1ea1e3db7cc7695b4a1bcdd2f33c641548c66ba6fd205` |

**Keine weiteren konkreten Findings.** Die Freigabe betrifft den exakt genannten Commit und die geprüften Ausführungsbelege; sie ist keine Aussage über einen bereits erfolgten Merge, Release oder spätere Änderungen.
