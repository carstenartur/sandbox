# BLS-Veröffentlichung: lokaler Kandidat und vollständige Blob-Zuordnung

**PASS.** Die elf unveränderten QA-Dateien wurden in einem neu angelegten, separaten Worktree auf die aktuelle Native-Basis übertragen. Der aktive Qualifikationscheckout blieb unverändert.

## Exakte Identitäten

| Stand | Commit | Tree |
|---|---|---|
| Lokaler BLS-Kandidat | `a226360ade62e962bacd053c07166158e9a0f3fa` | `652696bc384c8a6c809a70499d13fe8e51d8cc8f` |
| Lokale Native-Basis und alleiniger Parent | `80e16c7da0f094969d207815757d234b5a3bab11` | `c124a6e750303e0ba6e0a1c3322df5ceaf87fe6c` |
| Bytegleiche öffentliche Native-Basis | `36b8e60ce32b20d6d0e0d70ac2b58aafe59377d8` | `c124a6e750303e0ba6e0a1c3322df5ceaf87fe6c` |
| Aktive Qualifikationsquelle | `9c2d121686dd2bfcd774d820312ca603d8fc4b4f` | `d9736c8cd1e397508784e7d79fd1cb08ac978312` |

Worktree: `/tmp/sandbox-bls-publication-ready`. Lokaler Branch: `codex/bls-publication-ready-20261102`. Kandidat ohne getrackte oder ungetrackte Änderungen; sein Parent ist exakt die angegebene lokale Native-Basis.

## Übertragung und Scope

Gegen Native: **11 Dateien, +884/−3, 887 geänderte Textzeilen**. Die geänderten Pfade entsprechen exakt dem Payload; alle elf Dateien sind UTF-8-Text mit Git-Modus `100644`. Kein anderer Pfad wurde hinzugefügt, entfernt oder verändert.

Eingang: `publication-on-native-payload.json`, 127989 Byte, SHA256 `f72998cccb52c23c10713403f43ab3375f9c32842c54f8803a5499d382336d33`. Die darin noch dokumentierte historische Basis wurde als historische Herkunft behandelt; der neue Kandidat wurde ausdrücklich auf `80e16c7` aufgebaut.

Für jede Datei wurden Inhalt, Bytezahl, SHA256, Git-Blob und Modus gegen das Payload geprüft. Jeder Blob stimmt zusätzlich mit der aktiven Quelle `9c2d121` und der ursprünglichen Quelle `7fcd71944ffdc2b231efdfd4706437b74cbd622b` überein.

Alle folgenden Pfade liegen unter `qa/math-optimization/`:

| Relativer Pfad | Identischer Payload-/Quellen-/Kandidaten-Blob |
|---|---|
| `README.md` | `43e16295bfacad0c16eda0b1512624a88822fc30` |
| `pom.xml` | `bb30b2e62b71b4470ac236dc08a898b5c703fbb0` |
| `profiles/bc-bls.properties` | `e5aeeadd38d95c49118d72b84a2a70380c8bea8b` |
| `src/main/java/org/sandbox/math/qa/BlsBenchmark.java` | `d8206c1ab76f109dc21aa627f73a8ae3ba8c96e6` |
| `src/main/java/org/sandbox/math/qa/BlsBenchmarkReport.java` | `3def302e2c06d5e68eacc93be54ecef73792cba1` |
| `src/main/java/org/sandbox/math/qa/BlsBenchmarkRunner.java` | `ae3d0bd21b62aa6872570fb2889097b9af0b510c` |
| `src/main/java/org/sandbox/math/qa/BlsSourceCohort.java` | `6011ad03322629684e38857a6133aaef1753831a` |
| `src/main/java/org/sandbox/math/qa/FiniteSeriesBenchmarkReport.java` | `b3301d4172f449ebb4543927087cd6b9bbf6c345` |
| `src/main/java/org/sandbox/math/qa/ProjectMathScan.java` | `a03234055becd3beb01c18b415b284de26f18a6c` |
| `src/test/java/org/sandbox/math/qa/BlsSourceCohortTest.java` | `9b4d25e22ffb8f94969753a85cf033d3f79b3e95` |
| `src/test/java/org/sandbox/math/qa/BlsUpstreamSourceIdentityTest.java` | `31386bfe68dc8d749c6097c27ddf088b1dda6e5f` |

## Vollständiger Vergleich mit der aktiven Qualifikationsquelle

Alle **3556 getrackten Einträge** wurden mittels vollständiger rekursiver Git-Bäume abgeglichen: Pfadmengen, Dateimodi und Objekttypen sind gleich. **3551 Blobs sind identisch; genau 5 Blobs unterscheiden sich.** Damit sind insbesondere sämtliche Produktion, QA, SDK-Dateien, Profile, Tests, PNGs und übrigen Dateien bytegleich.

Die einzige Abweichungsmenge sind die fünf `sandbox_math_cleanup_help/images/0*-bouncy-castle-*.provenance.json`. Der vollständige JSON-Vergleich beschränkt sich je Datei auf drei Werte:

- `evidence.adapterBaseRevision`: `0343431c70d55f971ac61b2436ab5965cb5c6865` → `ae693e5006e104824112880be7db92b3403bd93d`.
- `evidence.adapterClassesSha256`: `7d1937943aab91a2dd3ebc65a9dcd651a200165d5ce3796d36ba94d5be69be33` → `08518a0b85228e81124c5523d3db97ac664c384d65c5898332622ed9b0ecdce4`.
- `evidence.adapterSourceState`: Beschreibung der neuen Basis einschließlich Detached-Comment-Freshness-Prüfung.

Diese fünf Dateien stammen unverändert aus der bereits unabhängig geprüften öffentlichen Capture 41497649570 und werden von der Native-Basis übernommen. Alle übrigen JSON-Werte, einschließlich Profile, PNG-Hashes und vollständiger Vorher-/Nachherquellen, bleiben gleich.

## Gesicherte Nachweise

- `publication-ready-preflight.json`: Eingangshash, elf vollständige Dateizuordnungen und Zustand der aktiven Quelle vor der Übertragung.
- `publication-ready-mapping.json`: Kandidaten-/Basis-/Quellenidentitäten, Scope, alle elf Dateimetadaten sowie beide Blobs und alle JSON-Differenzen der fünf Provenance-Dateien.
- `publication-ready-all-blobs.tsv.gz`: vollständige Zuordnung aller 3556 Pfade mit Modus, Typ, Quellen-Blob, Kandidaten-Blob und Gleichheitsflag; **128968 Byte**, SHA256 `01d77cb15f1e43cef08055288d83ad7e41a37d3a4edd764bb42639dadd843400`.
- Dekomprimierte TSV: 618662 Byte, SHA256 `fe86f0316b1cf815967f311401bdd1ed1e0b6e093559b97e4074c058cfa8663f`. Gzip-Roundtrip, Header und alle 3556 Datenzeilen wurden geprüft.

## Grenzen und Übergabe

Der aktive Checkout `/tmp/sandbox-bls-on-native` hat vor und nach der Vorbereitung unverändert HEAD `9c2d121686dd2bfcd774d820312ca603d8fc4b4f`, Tree `d9736c8cd1e397508784e7d79fd1cb08ac978312` und einen sauberen getrackten Quellstand.

Dieser Receipt belegt die lokale Vorbereitung und vollständige Inhaltszuordnung. Es wurden keine JVM, Maven-Tests, JMH-Messungen oder Remote-Operationen ausgeführt. Die laufende Qualifikation verbleibt bei Root auf der aktiven Quelle. Vor dem Native-Merge wurde kein öffentlicher abhängiger PR erstellt; die spätere Veröffentlichung muss auf dem dann aktuellen Main erfolgen.
