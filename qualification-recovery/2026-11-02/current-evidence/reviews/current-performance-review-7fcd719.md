# Unabhängige JMH-Nachweisprüfung — 7fcd719

**Ergebnis: APPROVED für Integrität, Quellenbindung und Auswertung des abgeschlossenen Messlaufs.** Die drei BLS-Vergleiche zeigen jeweils höheren Zeitbedarf der generierten Fassung; alle acht SWU-Vergleiche bleiben wegen überlappender Intervalle unentschieden. Kein neuer Befund gegen die vorgelegte Nachweiskette.

Prüfung am 2026-11-02 durch den unabhängigen Reviewer `/root/inverse_commit_review`: ausschließlich vorhandene Dateien und Quelltexte gelesen, Hashes und Statistik mit Python nachgerechnet. Keine JVM gestartet, keine Messung wiederholt, keine Repositorydatei geändert.

## 1. Exakter Stand und abgeschlossener Lauf

- Repository: `/tmp/sandbox-recovered-integration`.
- Commit: `7fcd71944ffdc2b231efdfd4706437b74cbd622b`.
- Git-Tree: `2fa20615db38841fbb3bd390b0c8d3794ef28f8a`.
- Nachweise: `/tmp/sandbox-recovered-integration/.superpowers/measurement-7fcd719-forks/`.
- Start `2026-11-02T06:33:16Z`, Ende `2026-11-02T06:38:12Z`; Exit-Code `0`, Maven `BUILD SUCCESS`, Maven-Gesamtdauer `04:54 min`.
- `identity-before.txt` und `identity-after.txt` enthalten identisch den genannten Commit und Tree. Beide `tracked-*.txt` sind leer; eine abschließende unabhängige Git-Abfrage bestätigt denselben Stand ohne Änderungen an getrackten Dateien.
- Die vier Rohdaten-/Auswertungsdateien wurden innerhalb dieses Laufs geschrieben: BLS um 06:34:57/58 UTC, SWU um 06:38:12 UTC. Die zuvor vorbereitete BLS-Kohorte wurde unverändert verwendet; der erfolgreiche Messlauf hat sie nicht neu erzeugt.

Der erste Versuch unter `.superpowers/measurement-7fcd719/` endete beim JMH-`BinaryLinkServer` mit `SocketException: Operation not permitted`, bevor Messungen entstanden. Das dort gesicherte Rohdatenfile ist 0 Byte groß; `attempt-status.json` weist ausdrücklich `INFRASTRUCTURE_FAILURE_BEFORE_MEASUREMENT` und `measurements: 0` aus. **Dieser Versuch ist kein RED und kein Performancebefund.**

## 2. Messkonfiguration und unabhängige Nachrechnung

| Merkmal | Geprüfter Wert in allen 22 Rohreihen |
|---|---|
| JMH / Modus / Einheit | `1.37` / `avgt` / `us/op` |
| Threads / Forks | `1` / `2` |
| Warmup je Fork | `3 × 500 ms`, Batchgröße `1` |
| Messung je Fork | `5 × 500 ms`, Batchgröße `1` |
| Primärsamples je Variante | Zwei Arrays mit jeweils fünf endlichen, positiven Werten |
| Java-Binary | `/tmp/jdt-pr5614-tools/jdk-25.0.5+8/bin/java` |
| JDK / VM | `25.0.5` / `OpenJDK 64-Bit Server VM` / `25.0.5+8-LTS` |

Alle Einstellungen, JVM-Identitäten und JVM-Argumente stimmen innerhalb jeder Serie und jedes Original-/Generated-Paares überein. Die beiden Serien haben unterschiedliche Heap-Argumentlisten: BLS enthält effektiv `-Xms128m -Xmx512m` (mit wiederholtem `-Xmx512m`), SWU `-Xmx512m`. Die vollständigen Argumentlisten bleiben in den Rohdaten erhalten.

Unabhängig geprüft wurden **6 BLS-Reihen + 16 SWU-Reihen = 22 Reihen mit 220 Primärsamples**, genau drei beziehungsweise acht vollständige, eindeutige Paare. Aus den zehn Iterationsmittelwerten jeder Variante wurden Mittelwert, unverzerrte Stichprobenstandardabweichung und das zweiseitige 99,9-%-Intervall neu berechnet: `Mittelwert ± t(0,9995; 9) × s / √10`, mit unabhängig ermitteltem `t = 4,780912585931217`. Alle gespeicherten Mittelwerte, Fehler und Intervallgrenzen stimmen überein; größte relative numerische Abweichung `1,91 × 10⁻¹⁵`.

Alle Primärmetriken und übernommenen GC-Metriken der beiden Auswertungen entsprechen exakt ihren Rohdaten; alle Quotienten und Entscheidungen wurden unabhängig nachgerechnet. Die Entscheidungsregel lautet: vollständig höheres Generated-Intervall → `HIGHER_TIME`, vollständig niedrigeres → `LOWER_TIME`, sonst `INCONCLUSIVE`.

## 3. Exakte BLS-Ergebnisse

Einheit jeweils **µs/op**. Intervalle sind die 99,9-%-Intervalle der jeweiligen Variante. Quotient = Generated-Mittelwert / Original-Mittelwert.

| Fall | Original-Mittelwert | Original-Intervall | Generated-Mittelwert | Generated-Intervall | Quotient | Entscheidung |
|---|---:|---|---:|---|---:|---|
| `G2_32` | 35261.45004523809 | [33412.21978217366, 37110.680308302515] | 44017.33674434732 | [40305.92802073033, 47728.745467964305] | 1.2483132907998964 | `HIGHER_TIME` |
| `G2_256` | 37637.31427076923 | [32513.837349253237, 42760.79119228522] | 45916.62974469698 | [43059.39006761247, 48773.869421781485] | 1.2199762558604725 | `HIGHER_TIME` |
| `PAIRING` | 22918.29181995106 | [21230.691054831917, 24605.892585070203] | 55600.64595666666 | [53393.20056130528, 57808.09135202804] | 2.4260379610082734 | `HIGHER_TIME` |

Damit beträgt der zusätzliche mittlere Zeitbedarf in diesem Lauf rund **24,83 %**, **22,00 %** und **142,60 %**. Bei jedem dieser drei Paare liegt die untere Generated-Grenze oberhalb der oberen Original-Grenze.

## 4. SWU-Vergleiche

Für alle acht Fälle wurden die vollständigen Mittelwerte und Intervallgrenzen gegen ihre zehn Rohsamples geprüft. Die Intervalle überlappen jeweils; Mittelwertquotienten unter eins begründen hier keinen gesicherten Geschwindigkeitsvorteil.

| Kurvenprofil / Nachrichtenbytes | Generated / Original | Entscheidung |
|---|---:|---|
| `P256_XMD_SHA_256/32` | 0.9744010700058744 | `INCONCLUSIVE` |
| `P256_XMD_SHA_256/256` | 1.0245161732441246 | `INCONCLUSIVE` |
| `P384_XMD_SHA_384/32` | 1.0242789916163497 | `INCONCLUSIVE` |
| `P384_XMD_SHA_384/256` | 0.9018669833356704 | `INCONCLUSIVE` |
| `P521_XMD_SHA_512/32` | 0.9636627764609303 | `INCONCLUSIVE` |
| `P521_XMD_SHA_512/256` | 1.1358632488256264 | `INCONCLUSIVE` |
| `BLS12_381_G1_XMD_SHA_256_SSWU_RO/32` | 1.062807782069868 | `INCONCLUSIVE` |
| `BLS12_381_G1_XMD_SHA_256_SSWU_RO/256` | 1.046244625353198 | `INCONCLUSIVE` |

## 5. Quellen-, Manifest-, SDK- und JVM-Bindung

Die in den Rohdaten angegebenen BC-Archive wurden tatsächlich gehasht: `bcprov-jdk18on-1.85.2.jar` → `986b0fb92ec10e0c66b43e036ce0077e6150cfaecd1db9fb92b56672e157afe5`; das zugehörige Quellenarchiv → `b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f`. Beide stimmen mit den Analyseberichten und Manifesten überein. Archivverzeichnis: `/tmp/jdt-pr5614-m2/org/bouncycastle/bcprov-jdk18on/1.85.2/`.

Das tatsächlich vorhandene und in den `runtimeInputs` als geladene `ComputationOptimizer`-Herkunft gebundene SDK-JAR unter `sandbox_math_cleanup/lib/regelsuche-optimization-sdk.jar` hat SHA-256 `ba0293dedb3fde9ca2f3042dcef8ae4d6bb2d576adedff8ba7a2b4d186f1cbe1`. Die erhaltene `/workspace/scratch/0eb32b15fbb4/recovery/sdk70/qualification.json` bindet genau dieses Standalone-JAR an SDK-Revision `70c13fb580062b1fd653f35b81b2fb7ed1903c28` und Version `0.5.2-issue1657-70c13fb58006`. Das SDK dient der Erzeugung der gemessenen Quellen; diese Aussage bedeutet keine SDK-Optimierung innerhalb jedes zeitgemessenen BLS-/SWU-Aufrufs.

BLS-Manifest, `cohort.json`, `review.json`, `inputs.json`, Profil und die fünf Original-/Generated-Quelldateien stimmen wechselseitig überein. Alle zehn Quellhashes stimmen zusätzlich mit der zuvor qualifizierten Kohorte unter `target/surefire-reports/practical-corpus/bls-source-cohort/cohort.json` überein. Die neun akzeptierten Regionen verteilen sich unverändert wie folgt:

| Klasse unter `org/bouncycastle/crypto/bls/` | Original-SHA-256 | Generated-SHA-256 | Regionen |
|---|---|---|---:|
| `BLS12_381G2HashToCurve.java` | `f38ce5cd9f54df04afb9a335d709b79f85bdde53fce3cee05ac308c466f7e13f` | `c5c77430d497a03479c5f9e73b5a2a74ed1ea67cc7da2e443f7606dc29fc2dd3` | 2 |
| `BLS12_381Pairing.java` | `796d1a157cb2893054caf901bea7c6471ddbad47a86c3fc640c2978bc5888da0` | `dacb0faf94d09a86f12ed0640e38e18059e38fac14f75a43cb6de68502aafef1` | 1 |
| `Fp12Element.java` | `c4a0bba167311890878798e94c3566dc8d603b24ec5d58a88c462629b8cd0354` | `c4a0bba167311890878798e94c3566dc8d603b24ec5d58a88c462629b8cd0354` | 0 |
| `Fp2Element.java` | `b59fd659daf0de62923b1f3be85dfe0329aa45fb43add8a28b6befa8e4517a85` | `8d8b95aec88d70886204ca048aa2f7132585d5f1d0673e7cba986ec8e0c11ebc` | 1 |
| `Fp6Element.java` | `cfcb0d5c98b901b6033039869fef98b464ae12a214cd5c0c5445347057fb6b1d` | `760c5e22c1c26205742a1104128ae6be346f482e09c01ae5d7b733fe2e5d1c36` | 5 |

Das unveränderte BLS-Profil hat SHA-256 `e1bd041d34a79707e8e6820b50a2fcdcf7619937ff0ce2b2cf6ad12a43427552`, Work-Budget 20.000.000, maximal 20.000 Zustände, Timeout 5 s und `AUDITED_OPENJDK_25_0_5_8`. Das SWU-Profil `bc-mathematical.properties` hat SHA-256 `de1a5f6eaa47bc22040a13ab3aa975206a74b24ad1c0e635000a5ef30b8c978a`; es ist das separat gebundene mathematische Profil, nicht das BLS-Profil.

Für `org/bouncycastle/crypto/hash2curve/impl/SimplifiedShallueVanDeWoestijneMapToCurve.java` stimmen die tatsächlichen Quellen mit SWU-Manifest und Analysebericht überein: Original `6cd8fbd459615e6936bd8eeea7413ea6a85dc91e6dd558de7d6c118c4d523119`, Generated `3c3fe40670a3144489b067f1bc9c330ef3d2829e6144c087f68302f8446d4b45`, drei akzeptierte Regionen.

Die Quellprüfung von `BlsBenchmark`, `BlsSourceCohort`, `BlsBenchmarkReport`, `FiniteSeriesBenchmarkReport`, `BouncyCastleHashToCurveBenchmark` und `FullCallBenchmarkReport` bestätigt die jeweilige Bindung: Setup und Teardown prüfen die Quellen/Manifeste; die Varianten werden separat kompiliert und geladen, tatsächliche Ergebnisse verglichen. Der BLS-Auswerter prüft zusätzlich Manifestparameter und JVM-Identität gegen seinen eigenen Prozess. Der SWU-Auswerter enthält diese zusätzliche BLS-Metadatenstruktur nicht; dort tragen Rohdatenparameter, Benchmark-Setup/Teardown und das gebundene Analysemanifest die Quellenkette. Der Reviewer hat deshalb Einstellungen und Statistik auch für SWU selbst nachgerechnet.

## 6. Prüfsummen des konkreten Nachweises

Die folgenden Pfade sind relativ zu `/tmp/sandbox-recovered-integration/qa/math-optimization/target/`; alle SHA-256-Werte wurden aus den vorhandenen Bytes ermittelt.

| Datei | SHA-256 |
|---|---|
| `bc-bls-jmh.json` | `4515cfb5e34b7e3d6edd026cfc3578e7ac97bc87a3582b47e5b5c9cf8833e15d` |
| `bc-bls-performance.json` | `b2bb87d1cb5a347d06b681013d594fec34601a96e9dcef10a009e6109ae6d9e5` |
| `bc-hash-to-curve.json` | `26954b16d5a9145aef743f735ee399a7245f7766f70aea35f8267182497322d5` |
| `bc-performance-review.json` | `c31acad66e7c41091446382e6e7c7456d1a03366af9efafbf1956036d90ca5f1` |
| `bc-bls/benchmark.properties` | `1b5f2671d712a01496c6509dbeeff6560b1e3ff88c8b3e882400dcc51d5feb14` |
| `bc-bls/cohort.json` | `ee529d287d7668f96ada16892ed080adb73c68386e14157226ee611ea1a8a021` |
| `bc-bls/review.json` | `925cd1e407a34589b080df13de6355e362b222fac0cea2ccb426da72b14ed16c` |
| `bc-bls/inputs.json` | `325074ecc0537dcf84dfea81892fc56f031f44a2484a60ac10ee1c85cbb8df4c` |
| `bc-mathematical/benchmark.properties` | `ed58b8194c819fac10a01d19ada2e96ba26b6acd5955997c3d4509b1641b3a14` |
| `bc-mathematical/review.json` | `63ef3a39491e83e0e1f367af26a0c0ba5a869d9ec35569cf134fb40efd39e05c` |

Zusätzliche Laufbelege unter `.superpowers/measurement-7fcd719-forks/`: `run.log` → `43c842681d0487922a1015dc1e29eaa7602ca7264f4b7ebd34f59ab723177d25`; beide `identity-*.txt` → `18d89e4e0f29586b656d04a7b74dd96e0deda13c762486e2b6917c9d5913de5c`; beide leeren `tracked-*.txt` → `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`; `exit-code.txt` → `9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa`.

## 7. Geltungsgrenzen

Gemessen werden vollständige Benchmark-Aufrufe mit den erzeugten Guards und Fallbacks. G2 umfasst Hash-to-Curve und Komprimierung; Pairing verwendet im Setup vorbereitete Eingabepunkte und enthält Pairing sowie Ergebniskodierung. Quellenprüfung, Kompilierung, Vorbereitung und Setup-Gleichheitsprüfung liegen außerhalb der primären Zeitmessung. Die zehn Samples je Variante sind Iterationsmittelwerte aus zwei Forks, keine zehn unabhängigen JVM-Läufe.

Die Ergebnisse sind lokale Messbefunde dieses Builds, dieser Quellen und dieser JVM-Konfiguration. Sie begründen weder einen positiven Gesamtaufruf-Speedup noch neue Kostenmodellgewichte oder eine Constant-Time-Aussage. Die GC-Profilerdaten bleiben zur Nachprüfbarkeit erhalten, enthalten jedoch JVM-/Harness-Arbeit und abschließende Quellen-/Runtime-Prüfung sowie Aufräumen außerhalb der primären Zeitmessung. **Eine isolierte Aussage über Allokationen der Optimierung oder eine kausale Erklärung der Zeitunterschiede wird daraus nicht abgeleitet.**
