# Unabhängige aktuelle JMH-Nachweisprüfung: 9c2d1216

**VERIFIED.** Die frischen Rohdaten, Auswertungen, Quellen- und Laufzeitbindungen sind konsistent. Es besteht kein neuer konkreter Nachweisfehler. Die Ergebnisse belegen keinen positiven Geschwindigkeitsgewinn: **G2_32 ist INCONCLUSIVE; G2_256 und PAIRING sind HIGHER_TIME; alle acht SWU-Paare sind INCONCLUSIVE.**

## Quelle, Lauf und tatsächlicher Abschluss

- Checkout: `/tmp/sandbox-bls-on-native`.
- HEAD: `9c2d121686dd2bfcd774d820312ca603d8fc4b4f`.
- Tree: `d9736c8cd1e397508784e7d79fd1cb08ac978312`.
- Evidence-Verzeichnis: `/workspace/scratch/0eb32b15fbb4/recovery/current-measurement-9c2d1216`.
- Ausgeführtes Zeitfenster: **2026-11-02, 18:31:13–18:36:20 UTC**. Beide tatsächlichen Prozessbelege `maven-exit-code.txt` und `exit-code.txt` enthalten `0`; `source-current.txt` enthält `true`. Vorher-/Nachher-HEAD und Tree stimmen exakt überein; beide getrackten Statusdateien sind leer.
- Der Log enthält **BUILD SUCCESS**, Maven-Laufzeit **05:06 Minuten**. `2026-11-03T00:06:20+05:30` im Maven-Log entspricht exakt **2026-11-02T18:36:20Z**.
- Alle vier Ergebnisdateien sind neuer als der tatsächliche Startmarker. Die frisch vorbereitete BLS-Kohorte wird im selben Lauf vor der Messung erzeugt: Scan **18:31:16.070–18:31:22.670 UTC**, fünf Klassen, neun akzeptierte Regionen, keine Parse-Fehler.
- Die unveränderte Quelle wurde am Beginn und Ende dieser unabhängigen Prüfung nochmals bestätigt. Der erfolgreiche frische Full-Retry ist zusätzlich durch seine echten Exit-0-/Identitätsbelege gebunden; dessen Testzahlen werden hier nicht erneut interpretiert.

## Exakte drei BLS-Messpaare

Einheit: **µs/op**. Die Klammern enthalten die jeweiligen **99,9%-Konfidenzintervalle**. `G2_32` und `G2_256` bezeichnen Nachrichtenlängen von **32 bzw. 256 Byte** beim vollständigen Hash-to-G2-Aufruf, einschließlich Komprimierung. Pairing umfasst den vollständigen Pairing-Aufruf und die Serialisierung; die vorbereiteten Eingabepunkte entstehen im Setup.

| Fall | Original: Mittelwert [99,9%-KI] | Generiert: Mittelwert [99,9%-KI] | Generiert / Original | Entscheidung |
|---|---|---|---:|---|
| G2_32 | 35418.53823744047 [33144.33925787614, 37692.737217004804] | 45464.50721991564 [34443.39349676285, 56485.62094306843] | 1.2836359003617972 | **INCONCLUSIVE** |
| G2_256 | 34720.80172827381 [33097.37607629043, 36344.22738025719] | 40299.8186775641 [37812.4469505204, 42787.1904046078] | 1.1606822616871544 | **HIGHER_TIME** |
| PAIRING | 23714.590923662712 [21629.277237335722, 25799.904609989702] | 52592.22409444444 [50272.22565363587, 54912.222535253015] | 2.2177158469120912 | **HIGHER_TIME** |

Bei **G2_32** überlappen die Intervalle um ungefähr **3.249,344 µs**. Der nominell um 28,36 % höhere Mittelwert erlaubt nach dem verwendeten Intervallkriterium keine gerichtete Schlussfolgerung. Bei **G2_256** liegt das gesamte generierte Intervall über dem Originalintervall, mit einer Lücke von ungefähr **1.468,220 µs**. Bei **PAIRING** beträgt diese Lücke ungefähr **24.472,321 µs**. Diese beiden Vergleiche liefern lokale Hinweise auf höheren Zeitbedarf.

## Acht vollständige SWU-Vergleiche

Die Werte dieser Tabelle sind zur Lesbarkeit auf drei Nachkommastellen gerundet; die maschinenlesbare Zusammenfassung enthält alle ursprünglichen Zahlen und Konfidenzintervalle. Jede Zeile vergleicht vollständiges Hash-to-Curve einschließlich Ausgabeencoding.

| Kurvenprofil / Nachrichtenbytes | Original µs/op | Generiert µs/op | Quotient | Entscheidung |
|---|---:|---:|---:|---|
| P256_XMD_SHA_256/32 | 94.682 | 94.183 | 0.994734525 | INCONCLUSIVE |
| P256_XMD_SHA_256/256 | 98.338 | 92.226 | 0.937853310 | INCONCLUSIVE |
| P384_XMD_SHA_384/32 | 225.988 | 208.079 | 0.920756071 | INCONCLUSIVE |
| P384_XMD_SHA_384/256 | 206.206 | 236.814 | 1.148437401 | INCONCLUSIVE |
| P521_XMD_SHA_512/32 | 356.854 | 388.998 | 1.090078216 | INCONCLUSIVE |
| P521_XMD_SHA_512/256 | 357.308 | 385.208 | 1.078083175 | INCONCLUSIVE |
| BLS12_381_G1_XMD_SHA_256_SSWU_RO/32 | 823.391 | 851.567 | 1.034218846 | INCONCLUSIVE |
| BLS12_381_G1_XMD_SHA_256_SSWU_RO/256 | 828.955 | 815.428 | 0.983681740 | INCONCLUSIVE |

Alle acht Intervallpaare überlappen. Auch Quotienten unter 1 sind hier kein belegter Geschwindigkeitsgewinn.

## Unabhängige Statistikrekonstruktion und Einstellungen

Geprüft wurden **22 Rohdatenzeilen**: sechs BLS- und 16 SWU-Zeilen. Jede Zeile enthält zwei Forks mit fünf Messiterationen, somit **zehn primäre Iterationsbeobachtungen je Variante und insgesamt 220**. Der vollständige Log bestätigt unabhängig **44 abgeschlossene Forks** und **220 primäre Messiterationszeilen**. Diese Zahlen sind Iterationsbeobachtungen, nicht 220 einzelne Programmaufrufe.

Gemeinsame Einstellungen in jeder Zeile: **JMH 1.37**, Average Time (`avgt`), ein Thread, zwei Forks, **3 × 500 ms Warmup**, **5 × 500 ms Messung**, Batchgröße jeweils 1, Einheit `us/op`. Die tatsächlich aufgezeichneten Parameter und die Paarvollständigkeit wurden geprüft.

Der unabhängige Node-Prüfer verwendet keine JMH-/Commons-Math-Statistikimplementierung. Aus allen zehn Werten werden Mittelwert, unverzerrte Stichprobenvarianz mit Nenner 9 und Standardfehler berechnet. Der kritische Student-t-Wert für neun Freiheitsgrade wurde durch Bisektion einer geschlossenen CDF-Formel bei Wahrscheinlichkeit 0,9995 bestimmt: **4.780912585931025**. Das Intervall ist Mittelwert ± t × Standardfehler.

Die CDF wird mit θ = arctan(t/3) als `1/2 + (35θ + 28sin(2θ) + 7sin(4θ) + (4/3)sin(6θ) + (1/8)sin(8θ)) / (35π)` ausgewertet. Damit entsteht ein unabhängiger Rechenweg zur verwendeten 99,9%-Student-t-Konvention.

- Alle Mittelwerte stimmen exakt mit den Rohdaten-Zusammenfassungen überein; maximale Abweichung: **0 µs**.
- Maximale absolute Abweichung eines Fehlerhalbintervalls: **4.2382453102618456e-10 µs**.
- Maximale absolute Abweichung einer KI-Grenze: **4.220055416226387e-10 µs**.
- Auch die berichteten Perzentile wurden aus sortierten Rohwerten rekonstruiert. Die B/op-Rohmetriken wurden rechnerisch geprüft und ihre unveränderte Übernahme in die Reports bestätigt.
- Sämtliche Quotienten und alle elf Entscheidungen wurden erneut aus den rekonstruierten Zahlen abgeleitet und stimmen mit den tatsächlichen Reportdateien überein. Getrennte Intervalle bestimmen die lokale Richtung; überlappende Intervalle ergeben INCONCLUSIVE.

## Quellen-, Manifest-, SDK- und Runtimebindung

**BLS:** Alle sechs Raw-Zeilen tragen denselben tatsächlichen Fixture-Manifest-SHA256 **`daeb9d5449ca3e6433a153f3734d37999087e2ccb1528c3db4e8c0fdee784931`**, dasselbe Fixture-Verzeichnis und dieselben beiden gepinnten Archive. Das Manifest bindet Kohorte, Input-Receipt, Scanreport und Profil. Alle **17 Kohortendateien** sowie **sieben konsultierten Quellenabhängigkeiten** wurden gehasht. Originalquellen und Abhängigkeiten stimmen zusätzlich bytegenau mit den Einträgen des gepinnten Quellenarchivs überein. Die Klassenwerte, akzeptierten Regionen und Dateihashes stimmen zwischen Scanreport, Kohorte, Manifest und Performance-Report überein.

| BLS-Klasse | Akzeptierte Regionen | Original-SHA256 | Gewöhnliche Apply-Ausgabe: SHA256 |
|---|---:|---|---|
| BLS12_381G2HashToCurve.java | 2 | `f38ce5cd9f54df04afb9a335d709b79f85bdde53fce3cee05ac308c466f7e13f` | `c5c77430d497a03479c5f9e73b5a2a74ed1ea67cc7da2e443f7606dc29fc2dd3` |
| BLS12_381Pairing.java | 1 | `796d1a157cb2893054caf901bea7c6471ddbad47a86c3fc640c2978bc5888da0` | `dacb0faf94d09a86f12ed0640e38e18059e38fac14f75a43cb6de68502aafef1` |
| Fp12Element.java | 0 | `c4a0bba167311890878798e94c3566dc8d603b24ec5d58a88c462629b8cd0354` | `c4a0bba167311890878798e94c3566dc8d603b24ec5d58a88c462629b8cd0354` |
| Fp2Element.java | 1 | `b59fd659daf0de62923b1f3be85dfe0329aa45fb43add8a28b6befa8e4517a85` | `8d8b95aec88d70886204ca048aa2f7132585d5f1d0673e7cba986ec8e0c11ebc` |
| Fp6Element.java | 5 | `cfcb0d5c98b901b6033039869fef98b464ae12a214cd5c0c5445347057fb6b1d` | `760c5e22c1c26205742a1104128ae6be346f482e09c01ae5d7b733fe2e5d1c36` |

Fp12 ist ausdrücklich unverändert und trotzdem vollständig Bestandteil beider kompilierten Varianten. Alle **zehn BLS-Quelldateien** besitzen dieselben Hashes wie im bereits geprüften historischen `7fcd719`-Kohortenstand. Die aktuellen Messwerte und Entscheidungen wurden trotzdem ausschließlich aus dem neuen Lauf ermittelt; insbesondere wird die frühere HIGHER_TIME-Einstufung für G2_32 nicht übernommen.

**SWU:** Die Raw-Zeilen binden das tatsächliche `bc-mathematical`-Scanverzeichnis und das gepinnte Binärarchiv. Setup und Teardown des Benchmarks prüfen dessen Manifest, Scanreport sowie Original-/Apply-Dateihashes. Der unabhängig geprüfte Original-SHA256 ist `6cd8fbd459615e6936bd8eeea7413ea6a85dc91e6dd558de7d6c118c4d523119`, der Apply-SHA256 `3c3fe40670a3144489b067f1bc9c330ef3d2829e6144c087f68302f8446d4b45`; die gemessene Datei ist `ANALYZED`, hat keine Parse-Fehler und **drei akzeptierte Regionen**.

**Nachweisgrenze des größeren SWU-Corpus:** Sein übergeordneter Scanreport lautet tatsächlich **COMPLETED_WITH_PARSE_ERRORS** und nennt **zehn Parse-Fehler-Dateien**. Die gemessene SWU-Datei gehört nicht dazu. Die BLS-Kohorte besitzt dagegen fünf analysierte Klassen ohne Parse-Fehler. Diese Messprüfung behauptet deshalb keine fehlerfreie Analyse oder semantische Qualifikation der gesamten BC-Bibliothek.

Beide Profile sind bytegleich mit den getrackten QA-Profilen. BLS verwendet den ausdrücklich freigegebenen Runtime-Modus `AUDITED_OPENJDK_25_0_5_8` mit Arbeitsbudget 20.000.000; das SWU-Profil verwendet `UNSPECIFIED` mit Arbeitsbudget 1.000.000. In beiden bleiben `tailRecursion=false`, 20.000 Zustände und fünf Sekunden unverändert. Die vollständigen 14 Optionswerte je Profil stehen in der JSON-Zusammenfassung.

- BC-Binärarchiv 1.85.2: SHA256 `986b0fb92ec10e0c66b43e036ce0077e6150cfaecd1db9fb92b56672e157afe5`.
- BC-Quellenarchiv 1.85.2: SHA256 `b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f`.
- SDK70: Source-Revision `70c13fb580062b1fd653f35b81b2fb7ed1903c28`, JAR-SHA256 `ba0293dedb3fde9ca2f3042dcef8ae4d6bb2d576adedff8ba7a2b4d186f1cbe1`, Qualifikationsversion `0.5.2-issue1657-70c13fb58006`. Tatsächliche JAR, Qualifikationsmetadaten und geladener SDK-Ursprung stimmen überein.
- **475 tatsächliche Runtime-Dateien** wurden gegen die aufgezeichneten SHA256-Werte geprüft, einschließlich QA-/Adapterklassen und Abhängigkeiten. Die fünf geladenen Klassenursprünge sind vorhanden und entsprechen der vorgesehenen SDK-, JDT- und QA-Klassenherkunft. BLS- und SWU-Input-Receipts enthalten dieselben Runtime-Bindungen.
- Alle Raw-Zeilen nennen `/tmp/jdt-pr5614-tools/jdk-25.0.5+8/bin/java`, JDK **25.0.5**, VM **OpenJDK 64-Bit Server VM**, Build **25.0.5+8-LTS**. Die tatsächliche JDK-`release`-Datei bestätigt diese Version. JDK-Release und Launcher wurden zusätzlich gehasht.
- Die JVM-Argumente sind jeweils innerhalb der BLS- und SWU-Paare identisch. Beide Gruppen haben `-Xmx512m`; BLS fügt `-Xms128m` und erneut `-Xmx512m` hinzu. Die vollständigen tatsächlich aufgezeichneten Argumentlisten werden unverändert in der Zusammenfassung bewahrt. Es wird keine gruppenübergreifende Identität dieser Argumentlisten behauptet.

## Vier frische Ergebnisdateien

Alle Pfade liegen unter `/tmp/sandbox-bls-on-native/qa/math-optimization/target/`.

| Datei | Bytezahl | SHA256 |
|---|---:|---|
| bc-bls-jmh.json | 46660 | `394aac651f89a40ef6d09469bb1e3a75b096da3cecfa456f90a85fe97184e7e4` |
| bc-bls-performance.json | 13537 | `52b888d8d7788d67be15b530de8942355ef68e763955c8c79ff6d6ed5aa8c618` |
| bc-hash-to-curve.json | 122167 | `ee587b4068fbf5d47fa0207e4522e404e834c7e3f9d65ed5ed3d63ce49fb4b8c` |
| bc-performance-review.json | 27773 | `9854f88a817318039bcd2a1e777905523355635eac60a2d963f5fa11f994b9d0` |

Weitere vollständig nachgerechnete Bindungen:

| Datei | SHA256 |
|---|---|
| bc-bls/cohort.json | `e7c22e4a30405e73b217ba2278029abd48c8e9a8434522d6cf78d36921a85ba3` |
| bc-bls/inputs.json | `106c03f725427a80be025cfe0e07c2b79777e704563da8caada9691508a750af` |
| bc-bls/review.json | `0f0faf013ef786e17db15aa5fee8a310460ab9669ad09a15ddf72b3ed3997502` |
| bc-bls/profile.properties | `e1bd041d34a79707e8e6820b50a2fcdcf7619937ff0ce2b2cf6ad12a43427552` |
| bc-mathematical/benchmark.properties | `f64186a58aed080165ec3ba11ef6c13fc2e790ba4af845b097b91ef153ab655f` |
| bc-mathematical/review.json | `4a556c3dfda1503cec277126172a5e661261f37358cfdbb6f16e355d69d323ee` |
| measurement/run.log | `b3da255b8fd2e710aed25e9878ef24d615e6445158efcfd6890824f486971d6b` |
| measurement/command.sh | `3cf7290e965ed52517803a445cf3315e083252cf4caa36c795169bde6a91af95` |

## Interpretation und Prüfungsgrenzen

Die Messungen umfassen die vollständigen Original- bzw. generierten Aufrufe einschließlich vorhandener Guards, Fallback-Code und Ausgabeencoding. Die in Setup und Teardown ausgeführten Kompilierungs-, Hash-, Quellen- und Gleichheitsprüfungen sind durch den gelesenen Benchmarkcode und den erfolgreichen Lauf gebunden; der unabhängige Reviewer hat sie nicht erneut ausgeführt. Für BLS prüft das Setup 16 feste Eingaben pro Variante und die tatsächlich geladenen erforderlichen Kohortenklassen; SWU prüft 64 feste Nachrichten pro Konfiguration und das Laden der kompilierten SWU-Variante.

**GC-Profilerwerte isolieren keine Kandidatenallokationen.** Sie enthalten JVM-/Harness-Aktivität und insbesondere die abschließende Quellen-/Runtimevalidierung und Bereinigung außerhalb der primären Latenzmessung. Aus diesen Werten folgt hier weder eine isolierte Allokationsverbesserung noch eine isolierte Allokationsregression.

Es folgt kein allgemeines Leistungsversprechen, keine Constant-Time-Eigenschaft und keine vollständige semantische Gleichheit für sämtliche Eingaben. Optimiererkosten, Budgets und Implementierung wurden unverändert gelassen. Die neue lokale Bewertung ersetzt keine historischen Rohdaten und übernimmt deren Schlussfolgerungen nicht ungeprüft.

## Gesicherte unabhängige Ausgaben

- `current-performance-review-9c2d1216.json`: kompakte maschinenlesbare Zusammenfassung mit allen 22 rekonstruierten Zeilen, elf Paarvergleichen, Hashes, Laufzeit- und Quellenbindungen.
- `current-performance-review-9c2d1216.cjs`: unabhängiger Rechen- und Hashprüfer mit geschlossenem Student-t-Rechenweg; abschließende Ausführung **Exit 0 / VERIFIED**.
- `current-performance-review-9c2d1216-SHA256SUMS`: Abschlussmanifest für Review, Zusammenfassung und Prüfer.

Keine JVM, Python-, Maven-, Benchmark- oder neuen Produktionstestläufe wurden für diese unabhängige Prüfung gestartet. Es wurden ausschließlich bestehende Dateien gelesen, Hashes/Statistiken mit Node berechnet und diese Review-Artefakte außerhalb des Repositorys geschrieben.
