# Unabhängiges Review der nativen Help-Capture — 8a0a951

**APPROVED.** Die fünf nativen Mathematikaufnahmen und ihre Provenienzen sind konsistent mit dem tatsächlich ausgeführten SWTBot-Lauf, den vollständigen Profilen, den Fixtures und dem kompilierten Adapter. Kein konkreter Befund.

Prüfung am 2026-11-02 durch `/root/inverse_commit_review`, ausschließlich lesend. Keine JVM, neue Capture, Quellenänderung oder Remote-Aktion. Einzige eigene Schreiboperation ist dieser Review-Receipt außerhalb des Repositorys.

## Geprüfter Stand

- Repository: `/tmp/sandbox-native-on-main`.
- HEAD: `8a0a95144809a9f07a30aad74b4856f22190b85d`.
- Git-Tree des HEAD: `12bb77006ab52acee0c7bf08660104e08455677e`.
- Adapterbasis: `0343431c70d55f971ac61b2436ab5965cb5c6865`; dieser Commit ist nach unabhängiger Git-Prüfung Vorfahr des HEAD. Alle fünf Fixture-Einstellungen und Provenienzen nennen exakt diese Basis sowie die zusätzlichen nativen Tail-/Default-off-/SDK70-Änderungen.
- Der geprüfte Capture-Stand besteht aus diesem HEAD **plus den zehn unten gehashten, noch nicht committeten Assets** unter `sandbox_math_cleanup_help/images/`. Zu Beginn und Ende der Prüfung sind exakt fünf PNGs und fünf zugehörige Provenienzdateien getrackt geändert; keine anderen getrackten Dateien. Die neuen Capture-Bytes sind somit nicht bereits Bestandteil des genannten HEAD-Trees.
- Belege: `/workspace/scratch/0eb32b15fbb4/recovery/native-help-8a0a951/`.

## Tatsächliche Ausführung

Lauf von `2026-11-02T06:42:05Z` bis `2026-11-02T06:44:23Z`, Exit-Code `0`, `BUILD SUCCESS`, Maven-Gesamtdauer `02:15 min`. Beide Identitätsbelege nennen denselben HEAD und Tree.

| Fokussierter SWTBot-Test | Ergebnis | Dauer laut XML |
|---|---|---:|
| `captureRealMathematicsExamples` | Erfolgreich, kein Fehler/Skip | 13.498 s |
| `profileRecordsEveryRegisteredMathematicsOption` | Erfolgreich, kein Fehler/Skip | 0.002 s |

Suite: **2 Tests, 0 Failures, 0 Errors, 0 Skips**, 13.523 s. Das gesicherte XML ist neuer als der Startmarker und byteidentisch mit dem ursprünglichen Bericht unter `sandbox_eclipse_help_swtbot_test/target/surefire-reports/`. Alle zehn Assets sind ebenfalls neuer als der Startmarker. XML-Properties belegen Screenshot-Modus `true`, Ausgabe-/Repositorypfad `/tmp/sandbox-native-on-main`, Java `25.0.5`, VM `25.0.5+8-LTS`, Java-Home `/tmp/jdt-pr5614-tools/jdk-25.0.5+8`.

## Vollständige effektive Profile

Alle fünf Provenienzen enthalten exakt dieselben **14 registrierten Schlüssel**. Die Menge stimmt mit `MathCleanUpOptions.KEYS` und der erfolgreichen Prüfung gegen die tatsächliche Eclipse-CleanUp-Registry überein. Der Capture-Treiber persistiert das jeweilige Projektprofil, liest es über `CleanUpPreferenceUtil.loadOptions` zurück und vergleicht die gesamte effektive Mathematikmap vor dem Öffnen der nativen Vorschau.

| Schlüssel | Wert |
|---|---|
| `cleanup.mathematics` | `true` |
| `cleanup.mathematics.checkedOptIn` | `false` |
| `cleanup.mathematics.exclusions` | Leerer String |
| `cleanup.mathematics.explanations` | `NONTRIVIAL` |
| `cleanup.mathematics.goal` | `LOWER_ESTIMATED_RUNTIME` |
| `cleanup.mathematics.mathematicalOptIn` | `false` in 01–04; `true` in 05 |
| `cleanup.mathematics.maxStates` | `20000` |
| `cleanup.mathematics.numericKinds` | `INT` in 01/02/04; `LONG` in 03; `BIG_INTEGER` in 05 |
| `cleanup.mathematics.safetyProfile` | `PRESERVE_JAVA` |
| `cleanup.mathematics.tailRecursion` | **`false` in allen fünf Profilen** |
| `cleanup.mathematics.timeoutSeconds` | `5` |
| `cleanup.mathematics.underflowChecks` | `false` |
| `cleanup.mathematics.valueRuntime` | `UNSPECIFIED` |
| `cleanup.mathematics.workBudget` | `1000000` |

Damit ist die 14-Key-Capture durch die ausgeführte Profil- und Registry-Prüfung belegt. Die Tail-Option ist in den tatsächlich verwendeten Profilen ausgeschaltet. Die Projekte verwenden Quell-/Zielniveau Java 17; dies ist unabhängig von der ausführenden Java-25-VM.

## Fixture-, Vorschau-, Apply- und Undo-Bindung

Für jede Aufnahme wurden unabhängig sämtliche 20 beziehungsweise 21 Werte der `example.properties` gegen die Provenienz verglichen. Die dort vollständig gespeicherten Before-/After-Texte stimmen **bytegenau** mit `before.java.txt` und `after.java.txt` überein; Before-/After-Hashes und beide dokumentierten Fragmente wurden geprüft.

Zusätzlich stimmen alle fünf Before-Fixtures exakt mit den angegebenen Zeilen des tatsächlichen Quellenarchivs `/tmp/jdt-pr5614-m2/p2/osgi/bundle/bcprov.source/1.85.2/bcprov.source-1.85.2.jar` überein. Dessen unabhängig ermittelter SHA-256 ist `9b15d2ccb83c76f9f393cb8cb97d0cb971d04dee667cc3f9d7f4ac28f582cb91`, identisch mit allen fünf Provenienzen.

| Aufnahme | BC-Quelldatei | Quellzeilen |
|---|---|---|
| `01-bouncy-castle-choice` | `org/bouncycastle/crypto/digests/SHA1Digest.java` | 1–369 |
| `02-bouncy-castle-majority` | `org/bouncycastle/crypto/digests/MD4Digest.java` | 1–287 |
| `03-bouncy-castle-long` | `org/bouncycastle/crypto/digests/LongDigest.java` | 1–426 |
| `04-bouncy-castle-swapped-choice` | `org/bouncycastle/crypto/digests/MD5Digest.java` | 1–363 |
| `05-bouncy-castle-modular-square` | `org/bouncycastle/crypto/hash2curve/impl/SimplifiedShallueVanDeWoestijneMapToCurve.java` | 1–85 |

Der gelesene Capture-Code verwendet den registrierten Mathematics-CleanUp im echten `CleanUpRefactoringWizard`. Er verlangt genau die Fixturedatei im nativen Changes-Baum und exakte Gleichheit der berechneten Vorschau mit der After-Fixture. Beide sichtbaren, nicht editierbaren Quellpanes müssen den vollständigen Before-/After-Text enthalten; die dokumentierten Fragmente werden auf Sichtbarkeit geprüft. Die Aufnahme stammt aus `GC.copyArea` des stabilen SWT-Dialogs. Anschließend prüft der Test den angewandten Inhalt einschließlich Dateibytes und AST-/Binding-Diagnostik ohne Fehler sowie das bytegenaue Undo zur Originaldatei. Erst nach diesen erfolgreichen Prüfungen werden PNG und Provenienz geschrieben.

## Tatsächlicher Adapter und SDK70

Der Klassenfingerprint wurde unabhängig nach dem gelesenen `BundleClassFingerprint.java` nachgerechnet: lexikografisch sortierte `.class`-Eintragsnamen, für UTF-8-Name und Klassenbytes jeweils vorangestellte Vierbyte-Länge in Big-Endian, anschließend SHA-256 über die gesamte Folge.

- **350 Klassen** aus `sandbox_math_cleanup/target/classes` stimmen als Name-/Byte-Map exakt mit den Klassen des gebauten `sandbox_math_cleanup/target/sandbox_math_cleanup-1.3.8-SNAPSHOT.jar` überein.
- Beide unabhängigen Berechnungen ergeben `7d1937943aab91a2dd3ebc65a9dcd651a200165d5ce3796d36ba94d5be69be33`, identisch mit `adapterClassesSha256` in allen fünf Provenienzen.
- Das vollständige Bundle-JAR hat SHA-256 `56d2406676225dfde39f538ea157034c8f14faf452780c008184b950e3e69e19`. Genau diesen Wert protokolliert der ausgeführte Capture fünfmal als Hash des tatsächlich installierten Bundles, einmal pro Aufnahme.
- Der Klassenfingerprint deckt die Adapterklassen ab. Das eingebettete SDK wird separat gebunden: Sowohl das SDK im gebauten Bundle als auch `sandbox_math_cleanup/lib/regelsuche-optimization-sdk.jar` haben SHA-256 `ba0293dedb3fde9ca2f3042dcef8ae4d6bb2d576adedff8ba7a2b4d186f1cbe1`. Der Bundle-ClassPath enthält dieses eingebettete JAR; der ausgeführte Capture liest und prüft dessen Bytes direkt aus dem installierten Bundle.
- Die erhaltene `sandbox_math_cleanup/lib/qualification.json` bindet exakt diesen SDK-Hash an Revision `70c13fb580062b1fd653f35b81b2fb7ed1903c28` und Version `0.5.2-issue1657-70c13fb58006`. Alle fünf Provenienzen nennen dieselbe Revision und denselben Hash.

## Bildprüfung und exakte Asset-Hashes

Alle PNGs sind gültig als PNG gekennzeichnet und haben laut IHDR **1280 × 900 Pixel**. Jeder tatsächlich berechnete PNG-Hash stimmt mit `screenshotSha256` seiner Provenienz überein. Repräsentativ wurden **01 (INT), 03 (LONG) und 05 (BIG_INTEGER)** visuell geöffnet: native Changes-Bäume und Before-/After-Editoren sichtbar, dokumentierte Arithmetikfragmente lesbar, kein Fehlerdialog oder Ladezustand. In 05 sind der mathematische Vertrag und der Original-Fallback sichtbar; der Kommentar bezeichnet die Operationskostenschätzung ausdrücklich als keine Zeitmessung. Kein visueller Befund.

Die folgenden Dateipaare liegen unter `/tmp/sandbox-native-on-main/sandbox_math_cleanup_help/images/`:

| Basisname | PNG-SHA-256 | Provenienz-SHA-256 |
|---|---|---|
| `01-bouncy-castle-choice` | `96e03facc1360d3bb91077d11517db6fed3dd7564f6c5122b181a7c034ccb4a1` | `b3cffa190cb2dc58d0f2d6bd165bb58b8ad510233dad476f5c8ee0376fc42316` |
| `02-bouncy-castle-majority` | `be9eb3a7b99169ee0b463627be2154f2446a084e7c19345f52d3643bb9f4100d` | `aa26f9fc99c983ee02638b8f6dc699709d37b15e360031d1ebdbd24e3327a1e4` |
| `03-bouncy-castle-long` | `9a9020c3fc5b49655acab5c03ac15fb96d1d262e56c6acc9f6882e54c6a1efe1` | `930c04e41a3abb9fc8b9d4f3131324be2689641ba92379b6adb1060f79c77ec6` |
| `04-bouncy-castle-swapped-choice` | `322953785aa9708570d5abecb104e6ba75e819709ac0a9269f8428fb653e6469` | `70591d4da211ad16fe48a3be4eba1fea25c3315fe7b7ab1084695cef553d84c7` |
| `05-bouncy-castle-modular-square` | `38fa509a5be6aba0b84b67e9b31d2e355d36675ebc05aeccc4f384a87e61d985` | `96dd854a25639ccc128d795bb6112c450347aaf2c6c0b5dccfe104d09237c146` |

Weitere Prüfsummen im Belegverzeichnis: `run.log` → `7eb3945c1658f3ad0160400ea19e5b1f680b01709ee08b0e53c8bed8a98826b9`; `surefire-reports/TEST-org.sandbox.jdt.ui.helper.views.MathematicsHelpScreenshotsSWTBotTest.xml` → `81c84512ce6b4f76a260df8e0a78dff00ed2d36c4bde96f64195a16b0baf8812`.

Die Freigabe gilt für die geprüfte Capture und genau die genannten Assetbytes. Sie behauptet keinen bereits erfolgten Commit, Merge oder Release und enthält keinen Performancevorteil aus diesen Help-Beispielen.
