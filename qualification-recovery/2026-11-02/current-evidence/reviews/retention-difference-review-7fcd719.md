# Independent retention difference review — 7fcd719

## APPROVED — Retention-Gate für `7fcd71944ffdc2b231efdfd4706437b74cbd622b`

Review vom 2026-11-02 durch den unabhängigen Reviewer `inverse_commit_review`.

**Kein verbleibender konkreter Befund.** Die erhaltenen Regionen, die Textänderungen, die einzelne Proof-Änderung und die vier neuen Regionen sind nachvollzogen. Prüfer und Profile wurden nicht verändert; während dieser Prüfung wurde keine JVM gestartet.

Geprüfte Source-Identität:

- HEAD: `7fcd71944ffdc2b231efdfd4706437b74cbd622b`
- Tree: `2fa20615db38841fbb3bd390b0c8d3794ef28f8a`
- Checkout: `/tmp/sandbox-recovered-integration`
- Aktuelle Retention-Evidenz: `/workspace/scratch/0eb32b15fbb4/recovery/current-retention-7fcd719`
- Baseline: `/workspace/scratch/0eb32b15fbb4/recovery/baseline-1785`
- Aktuelle Scans: `/tmp/sandbox-recovered-integration/qa/math-optimization/target/bc-strict` und `bc-mathematical`

## 1. Ausführung und Retention unabhängig bestätigt

Die drei frischen XML-Suites enthalten tatsächlich **16 Testcases, 0 Failures, 0 Errors und 0 Skips**: 13 Auditor-Tests, 1 Baseline-Integritätstest und 2 aktuelle Profilprüfungen. Exit-Code **0**, Maven **BUILD SUCCESS**, **10,243 s**, abgeschlossen **2026-11-02 06:26:00 UTC**. Der aufgezeichnete Start war **06:25:49 UTC**.

Beleg: `recovery/current-retention-7fcd719/run.log`, `exit-code.txt`, `started-utc.txt`, `finished-utc.txt` und die drei XML-Dateien unter `surefire-reports/`.

Die Regionsindizes wurden zusätzlich direkt aus den ursprünglichen und aktuellen Evidence-Dateien rekonstruiert. Die gespeicherten Review-Kopien samt SHA-256 wurden gegen ihre tatsächlichen Eingaben verglichen:

| Profil | Baseline | Aktuell | Erhalten | Verloren | Neue Regionen | Kostenänderungen bei erhaltenen Regionen |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Strict | 146 | 146 | **146** | **0** | 0 | **0** |
| Mathematical | 149 | 153 | **149** | **0** | 4 | **0** |

Alle erhaltenen Originaltexte stimmen überein. Die archivierten Review-Kopien und ihre aufgezeichneten Hashes stimmen mit Baseline beziehungsweise aktuellem Scan überein. Der Auditor prüft außerdem die vollständige Auswahl von **3.136 Einträgen**, **3.126 geparste Dateien**, dieselben **10 Parserfehler**, Archive, Optionen, Laufzeiteingaben und Source-Abhängigkeiten.

Belege: `reports/bc-strict/retention.json`, `reports/bc-mathematical/retention.json`, die jeweiligen `summary.json`, `baseline-review.json` und `current-review.json`.

## 2. Die 31 Textänderungen sind vollständig erklärt

**Sie sind nicht sämtlich reine Umbenennungen.**

| Profil | Nur konsistente `_math`-Umbenennungen | Wiederverwendung bereits deklarierter Ausgaben | Gesamt |
| --- | ---: | ---: | ---: |
| Strict | 21 | 10 | 31 |
| Mathematical | 22 | 9 | 31 |

Die weiteren Änderungen ersetzen zusätzliche temporäre Variablen durch bereits berechnete lokale Ausgaben. Beispielsweise wird in `HawkEngine` aus:

```java
final int _math8 = 2 * v;
int idx1 = _math8;
int idx2 = _math8 + 1;
```

die gleichwertige Folge:

```java
int idx1 = 2 * v;
int idx2 = idx1 + 1;
```

Die betroffenen Bindungsänderungen liegen in `HawkEngine`, `HawkKeyPairGenerator`, `HawkSigner`, `Hnf`, beiden `XMSS*PrivateKeyParameters` und `Polynomial`. Für **sämtliche 10 beziehungsweise 9 Fälle** wurden die lokalen Bindungen unabhängig in die Ausgabenausdrücke eingesetzt: **Alle typisierten Ausdrucksbäume stimmen vorher/nachher überein**, einschließlich Operatoren und Klammerung.

Das entspricht der generischen Zulassung in `sandbox_math_cleanup/src/org/sandbox/jdt/internal/corext/fix/math/JavaComputationEmitter.java`: Nur eindeutige lokale Deklarationen dürfen spätere Ausgaben versorgen; Eingaben mit gleichem Namen, Returns und Array-Stores sind ausgeschlossen. Die Berechnung der ersten Verwendung und die Prüfung vorgezogener temporärer Berechnungen verhindern Vorwärtsabhängigkeiten. Die verbleibenden Nummernverschiebungen folgen aus den eingesparten temporären Variablen.

### Vollständige Klassifikation der Bindungsänderungen

Alle Pfade beginnen mit `org/bouncycastle/`. Bei jedem Eintrag war der Vergleich der expandierten typisierten Outputs erfolgreich.

| Source-Region | Strict | Mathematical | Geprüfte Outputs |
| --- | --- | --- | --- |
| `pqc/crypto/hawk/HawkEngine.java:50036:196` | Bindungswiederverwendung | Bindungswiederverwendung | `gdStart`, `fsStart`, `gsStart`, `t1Start`, `t2Start` |
| `pqc/crypto/hawk/HawkEngine.java:51036:55` | Bindungswiederverwendung | Keine Replacement-Änderung | `idx1`, `idx2` |
| `pqc/crypto/hawk/HawkEngine.java:82149:83` | Bindungswiederverwendung | Bindungswiederverwendung | `Fd`, `Gd`, `fgt` |
| `pqc/crypto/hawk/HawkEngine.java:83332:112` | Bindungswiederverwendung | Bindungswiederverwendung | `Ft`, `Gt`, `ft`, `gt` |
| `pqc/crypto/hawk/HawkKeyPairGenerator.java:18153:142` | Bindungswiederverwendung | Bindungswiederverwendung | `tq00`, `tq01`, `tq11` |
| `pqc/crypto/hawk/HawkSigner.java:67225:275` | Bindungswiederverwendung | Bindungswiederverwendung | `t1Offset`, `h0Offset`, `h1Offset`, `xxOffset` |
| `pqc/crypto/sqisign/Hnf.java:3283:37` | Bindungswiederverwendung | Bindungswiederverwendung | `j`, `k` |
| `pqc/crypto/xmss/XMSSMTPrivateKeyParameters.java:8695:228` | Bindungswiederverwendung | Bindungswiederverwendung | `secretKeySize`, `secretKeyPRFSize`, `publicSeedSize`, `totalSize` |
| `pqc/crypto/xmss/XMSSPrivateKeyParameters.java:11431:228` | Bindungswiederverwendung | Bindungswiederverwendung | `secretKeySize`, `secretKeyPRFSize`, `publicSeedSize`, `totalSize` |
| `pqc/math/ntru/Polynomial.java:10340:643` | Bindungswiederverwendung | Bindungswiederverwendung | `m`, `hi`, `saOff`, `sbOff`, `z0Off`, `z2Off`, `z1Off`, `next` |

Die übrigen 21 beziehungsweise 22 Änderungen stimmen nach ausschließlich konsistenter, bijektiver Umbenennung der `_math`-Bezeichner überein. Die zusätzliche reine Umbenennung im Mathematical-Profil betrifft `HawkEngine.java:128239:839`.

Die unabhängige statische Hilfsprüfung wurde nur im Speicher ausgeführt: Java-Kommentare wurden lexikalisch getrennt; bei den reinen Umbenennungen wurden alle unveränderten Textsegmente sowie konsistente, bijektive Code-Bezeichner geprüft. Die übrigen geradlinigen `int`-Deklarationen wurden mit Operatorpräzedenz und Klammerung gelesen, frühere Bindungen strukturell eingesetzt und die benannten, typisierten Outputs verglichen. Dies war keine Java-Ausführung und ersetzt nicht die aufgezeichneten ausgeführten Regressionstests.

## 3. `assumptionsHash`: ausschließlich der absolute Receipt-Pfad

Für `org/bouncycastle/crypto/bls/BLS12_381Serialization.java:4656:109` ist **nur** diese Proof-Leaf verschieden:

```text
6a5255e8e126c47eeed4743a705a154ed88ab557c8911aca41bff6e82222ebd8
→
de0d8ff2e25863d53d3480ecf130bd6c64b9bf7b15301f646320ebe05db37796
```

Die **fünf fachlichen Annahmen sind identisch**:

- `BIG_INTEGER_BIT_LENGTH_BOUND(input0, 384)` für `x`
- `BIG_INTEGER_BIT_LENGTH_BOUND(input1, 4096)` für `P`
- `BIG_INTEGER_VALUE_SEMANTICS(input0)` für `x`
- `BIG_INTEGER_VALUE_SEMANTICS(input1)` für `P`
- `POSITIVE(input1)` für `P`

Der gepinnte SDK70-Code berechnet den Hash aus sortierten, längenpräfixierten Feldern **einschließlich `provenance`**. Damit ist die Reihenfolge der Annahmen unschädlich; die absoluten Receipt-Pfade beeinflussen den Hash.

Gepinnte Quelle: [EvidenceHashes.java bei SDK70](https://github.com/carstenartur/regelsuche/blob/70c13fb580062b1fd653f35b81b2fb7ed1903c28/regelsuche-optimization-sdk/src/main/java/de/regelsuche/sdk/optimization/EvidenceHashes.java), Commit `70c13fb580062b1fd653f35b81b2fb7ed1903c28`, Tree `c2501a7c9bd9c57cc31e910eee3c112ee8b4e13a`.

Beide Hashes wurden exakt nachgerechnet. **Allein** der Austausch dieses Präfixes:

```text
/home/runner/work/sandbox/sandbox/qa/math-optimization/target/bc-mathematical/source-context/
```

durch:

```text
/tmp/sandbox-recovered-integration/qa/math-optimization/target/bc-mathematical/source-context/
```

erzeugt exakt den neuen Hash. Beide referenzierten Quelldateien behalten ihre vollständigen Hashes:

| Abhängigkeit | SHA-256 |
| --- | --- |
| `Fp2Element.java` | `b59fd659daf0de62923b1f3be85dfe0329aa45fb43add8a28b6befa8e4517a85` |
| `BLS12_381G1.java` | `40622d99ac46febebb82af4cd9286bdf843f10a6fffaf11291e161102aedc9c1` |

Auch die tatsächliche Zulassung ist weiterhin begründet: `x` entsteht aus `new BigInteger(1, xBytes)` mit 48 Bytes; das zuvor abgeschlossene `x.compareTo(P)` belegt die erforderliche Nichtnull-Verwendung von `P`. Dessen statische Alias-Kette führt über `Fp2Element.P` zum positiven Literal `BLS12_381G1.Q`. Der generische quellgebundene Nachweis steht in `sandbox_math_cleanup/src/org/sandbox/jdt/internal/corext/fix/math/JavaSourceContext.java`.

**Keine fachliche Annahme wurde abgeschwächt; Ersatztext, übriger Proof und Kosten bleiben identisch.**

### Erhaltenes Ergebnis der Hash-Nachrechnung

```json
{
  "oldComputed": "6a5255e8e126c47eeed4743a705a154ed88ab557c8911aca41bff6e82222ebd8",
  "oldRecorded": "6a5255e8e126c47eeed4743a705a154ed88ab557c8911aca41bff6e82222ebd8",
  "newComputed": "de0d8ff2e25863d53d3480ecf130bd6c64b9bf7b15301f646320ebe05db37796",
  "newRecorded": "de0d8ff2e25863d53d3480ecf130bd6c64b9bf7b15301f646320ebe05db37796",
  "pathOnlyRewriteHash": "de0d8ff2e25863d53d3480ecf130bd6c64b9bf7b15301f646320ebe05db37796",
  "pathOnlyCanonicalEqualsNew": true,
  "semanticAssumptionSetEqual": true,
  "assumptionCount": [5, 5]
}
```

Die nachgelesene kanonische Vorschrift lautet für jede Annahme: die vier Felder `kind`, `subject`, `parameter`, `provenance` jeweils als UTF-8-Byteanzahl, Doppelpunkt und Wert konkatenieren; die vollständigen Annahme-Strings sortieren, konkatenieren und mit SHA-256 hashen. Für diese vorhandenen ASCII-Annahmestrings wurde die Vorschrift unabhängig in Python nachvollzogen, ohne JVM oder Dateimutationen.

## 4. Die vier Zusätze beruhen auf generischer Zulassung

| Neue Region | Nachvollzogene Transformation |
| --- | --- |
| `org/bouncycastle/crypto/bls/BLS12_381G2HashToCurve.java:6070:309` | Entfernt unobservierte Vorab-Reduktionen vor der quellbewiesenen, erneut normalisierenden Factory. Produzenten und Moduli werden in ursprünglicher Reihenfolge erfasst; Guards prüfen exakte BigInteger-Klassen, Grenzen und Modulus. Nicht zugelassene Werte führen die ursprüngliche Reduktion aus. |
| `org/bouncycastle/crypto/bls/Fp6Element.java:4092:19` | Spezialisierung von `t1.mul(NON_RESIDUE)` |
| `org/bouncycastle/crypto/bls/Fp6Element.java:4147:21` | Spezialisierung von `a2b2.mul(NON_RESIDUE)` |
| `org/bouncycastle/crypto/bls/Fp6Element.java:4745:19` | Spezialisierung von `s3.mul(NON_RESIDUE)` |

Die drei Fp6-Fälle leiten die Komponenten `(a−b, a+b)` aus dem quellbewiesenen konstanten Wert `(1,1)` ab. Sie verwenden eine frische normalisierende Factory, prüfen Receiver/Komponenten/Modulus/Konstante und behalten den Originalaufruf als Fallback. Beide Adapter überprüfen den erzeugten Code erneut und verlangen einen positiven geschätzten Kosteneffekt; keine BC-Klassen- oder Methodennamen dienen als Zulassungsliste.

Geprüfte generische Adapter:

- `sandbox_math_cleanup/src/org/sandbox/jdt/internal/corext/fix/math/JavaValueResidueOptimizer.java`
- `sandbox_math_cleanup/src/org/sandbox/jdt/internal/corext/fix/math/JavaValueCallOptimizer.java`

Die neue G2-Region trägt den numerischen Nachweis `SAME_MODULUS_POLYNOMIAL_NORMAL_FORM` und aufgezeichnete Source-/Candidate-Scores `145778 → 134178`. Die drei Fp6-Regionen tragen `INTEGER_POLYNOMIAL_NORMAL_FORM`, `TYPED_OPERATION_CONGRUENCE`, `TYPED_STRUCTURAL_IDENTITY` und jeweils `165387 → 146135`. Diese Werte sind geschätzte Kosten, keine gemessenen Laufzeiten.

## 5. Übrige Evidence-Differenzen und Grenzen der Freigabe

Die übrigen Evidence-Differenzen sind ebenfalls erklärt: neue leere Schemafelder `fixedPower`, `sourceNumericBranch` und `valueInverse`, Reihenfolge der Annahmen, Receipt-Pfade und aufgezeichnetes verbleibendes Generierungsbudget. Nach Berücksichtigung genau dieser Unterschiede bleibt keine unerklärte Änderung. Diese Einordnung wurde ausschließlich als unabhängige Analyse vorgenommen; der Auditor oder seine Vergleiche wurden dafür nicht geändert.

Die aktuelle Profilprüfung bindet die konfigurierte Semantik und die Budgets an die Baseline. Sie normalisiert lediglich die bereits definierten Defaultwerte für fehlende `tailRecursion`-/`valueRuntime`-Schlüssel und die Reihenfolge der Numeric-Kinds; kein neuer Profileintrag wurde im Review geändert.

**HEAD und Tree waren bei Abschluss weiterhin exakt `7fcd71944ffdc2b231efdfd4706437b74cbd622b` / `2fa20615db38841fbb3bd390b0c8d3794ef28f8a`; der getrackte Stand war sauber.** Diese Freigabe gilt dem abgeschlossenen Retention-Gate und enthält keine Aussage über noch ausstehende JMH-Messergebnisse.

## Erhaltung der Hilfsergebnisse

Bei der lesenden Prüfung wurden keine separaten Hilfsskripte oder Ergebnisdateien angelegt. Die Werkzeuge führten die statischen Auswertungen ausschließlich im Speicher aus. Die relevanten Klassifikations-, Identitäts- und Hash-Ergebnisse sind oben in diesem Receipt erhalten. Für das Speichern dieses Receipts wurden die großen JSON-Dateien nicht erneut analysiert und keine Tests oder Messungen gestartet.
