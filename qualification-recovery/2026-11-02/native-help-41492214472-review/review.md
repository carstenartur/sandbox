# Unabhängiger Vergleich des öffentlichen Help-Artefakts 41492214472

**Ergebnis:** ZIP, eingebettetes TAR und sämtliche Manifestbindungen sind intakt. Alle fünf Mathematik-PNGs unterscheiden sich vom unveränderlichen ee20-/36be-Stand in tatsächlich dekodierten RGB-Pixeln. Die Quelltextpanes sind in allen fünf Paaren pixelidentisch. Eine reine Metadaten- oder Kompressionsabweichung ist durch die Daten ausgeschlossen; die technische Ursache der abweichenden RGB-Werte ist mit diesen Artefakten nicht bestimmt.

Prüfung am 2026-11-02 durch `/root/inverse_commit_review`. Keine JVM, neue Capture, Bildbearbeitung, Normalisierung, SHA-Substitution, Quellenänderung oder Remote-Aktion. Ausschließlich sichere Extraktion unveränderter Archivbytes sowie lesende Struktur-/Hash-/Pixelanalyse; neue Analysebelege liegen in diesem Verzeichnis.

## Vergleichsgrundlage und Trennung von der neuen Capture

Root ordnete die heruntergeladene Datei dem öffentlichen Run `41492214472` auf Head `b639…` zu. Die hier geprüften Inhalte stammen ausschließlich aus dieser ZIP-Datei. Das ZIP enthält Bildbelege und Hashlisten; JUnit-Ergebnisse oder weitere öffentliche CI-Zustände wurden im Rahmen dieses Dateivergleichs nicht erneut abgefragt.

Die Vergleichsdateien unter `local-snapshot/` wurden gegen die zehn unveränderlichen Git-Blobs aus **beiden** Commits geprüft:

- `ee20d3766f7d8d051f1f69341a6e5627fbb4f025` — ursprünglicher Commit der geprüften Capture-Assets.
- `36be6f4476ce10e218833a5fe64fd9362358c7c8` — gleicher Assetstand nach Integration der Main-Basis, Tree `9954a7fa9585302036043898524d1e2b8c98a162`.

Jede der zehn eingefrorenen Dateien ist byteidentisch mit den entsprechenden Gitobjekten beider Commits. Die parallel laufende neue Capture auf `6a1a8d58…` wurde nicht als Vergleichsbasis verwendet. Der zunächst protokollierte Arbeitsverzeichnis-HEAD in `local-snapshot-identity.json` beschreibt lediglich den Checkout beim Kopieren; die verglichenen Assetbytes sind ausdrücklich die alten, durch Gitobjekte bestätigten Dateien.

## Container- und Manifestprüfung

| Gegenstand | Geprüftes Ergebnis |
|---|---|
| ZIP | `native-help-41492214472.zip`, 3.975.277 Bytes |
| ZIP-SHA-256 | `228b03a5baa3e0cd58e948c1fd8cd33a797c06894d1a1a9a997df2254da6a1fd` |
| ZIP-Struktur | 37 reguläre Dateien; alle ZIP-CRC-Prüfungen erfolgreich |
| TAR | `generated-help-screenshots-41492214472.tar.gz`, 1.974.635 Bytes |
| TAR-SHA-256 | `230c526132c2ac16b6dc192ae7022a911d9b799efee24629f184d1bf5f672737` |
| Archivbindung | TAR-Hash stimmt mit `archive-sha256.txt` überein; Gzip-CRC und TAR-Header gültig |
| Manifest | 34 Nutzdateien vollständig gehasht, jeweils sowohl in ZIP als auch TAR |
| Containervergleich | Alle 34 korrespondierenden Nutzdateien sind zwischen ZIP und TAR byteidentisch; beide `sha256.txt` ebenfalls byteidentisch |
| Extraktion | Neuer Unterordner; absolute Pfade, Traversierung, doppelte Pfade, Links und Spezialdateien ausgeschlossen |

Manifestdatei `sha256.txt`: SHA-256 `11290fd45511a6bf8aa32017ed9c9aadfce1bef942233aa8d091daeccb749131`.

## Alle zehn Mathematikdateien

Alle fünf PNGs sind unterschiedlich. Alle fünf Provenienz-JSONs unterscheiden sich inhaltlich ausschließlich im Feld `evidence.screenshotSha256`; jeder dort gespeicherte Wert stimmt mit den tatsächlichen Bytes des jeweils zugehörigen PNGs überein. Profile, Before-/After-Texte, SDK-Revision und -Hash, Adapterfingerprint, Adapterbasis sowie sämtliche übrigen Provenienzfelder sind gleich.

| PNG | Öffentliche Bytes | ee20-/36be-Bytes | Abweichende entpackte Scanline-Bytes | Abweichende RGB-Pixel |
|---|---:|---:|---:|---:|
| `01-bouncy-castle-choice.png` | 67.070 | 67.071 | 2.773 | 1.172 |
| `02-bouncy-castle-majority.png` | 62.289 | 62.308 | 2.771 | 1.172 |
| `03-bouncy-castle-long.png` | 72.450 | 72.463 | 2.752 | 1.172 |
| `04-bouncy-castle-swapped-choice.png` | 60.960 | 60.966 | 2.771 | 1.172 |
| `05-bouncy-castle-modular-square.png` | 185.304 | 185.309 | 2.694 | 1.175 |

Alle fünf öffentlichen PNGs sind darüber hinaus **vollständig byteidentisch** mit ihren jeweiligen PNGs aus Main-Commit `0343431c70d55f971ac61b2436ab5965cb5c6865`. Diese Feststellung ist ein Dateivergleich; sie weist für sich allein keinen bestimmten Entstehungsweg der öffentlichen Dateien nach.

## PNG-Chunks, zlib und Pixel

Alle zehn PNGs haben dieselben IHDR-Werte: **1280 × 900, 8 Bit, Farbtyp 6 (RGBA), keine Interlace-Kodierung**. Die Nicht-IDAT-Chunks `IHDR`, `sBIT` und `IEND` sind in jedem Paar byteinhaltlich gleich. Unterschiede liegen ausschließlich in den IDAT-Daten und deren Prüfsummen. Alle PNG-Chunk-CRCs sind gültig.

Die zusammengesetzten IDAT-Daten beginnen in beiden Varianten mit dem zlib-Header `789c`. Sie lassen sich jeweils fehlerfrei zu **4.608.900 Scanline-Bytes** dekomprimieren. Diese entpackten Bytes sind innerhalb jedes Paares verschieden; auch ihre Adler-Prüfsummen unterscheiden sich. Der Befund bleibt nach PNG-Dekodierung bestehen und ist deshalb keine bloße Änderung des Deflate-Streams bei gleichem Bildinhalt.

Die Alpha-Komponente ist in sämtlichen Pixeln beider Varianten **255**. Es gibt keine Alphaabweichung und keine lediglich in vollständig transparenten Pixeln versteckte RGB-Abweichung. Die gemessenen Unterschiede betreffen RGB; maximale absolute Kanalabweichungen betragen R=92, G=96, B=140.

Bei **01–04 sind sowohl sämtliche 1.172 Differenzkoordinaten als auch alle RGB-Deltawerte untereinander identisch**. SHA-256 der gepackten Differenzmaske: `abf726e91b5d9921f563a1d12b95301f7c68ac91d10b47ab1ec7f546a1958950`.

In allen fünf Paaren enthält der gesamte Bildstreifen **y=318 bis einschließlich 766** — beide sichtbaren Quelltextpanes einschließlich ihrer Zwischenfläche — **null unterschiedliche Pixel**. Die Unterschiede liegen außerhalb dieses Bereichs. Beispiele der gemessenen zusammenhängenden Regionen, Koordinaten nullbasiert und Grenzen einschließlich:

| Region | Differenzpixel | Visuelle Zuordnung am unveränderten Original |
|---|---:|---|
| x=1165–1176, y=14–28 | 102 | Symbol in der oberen Toolbar |
| x=1250–1263, y=268–282 | 132 | Symbol in der Compare-Toolbar |
| x=46–61, y=42–56 | 131 | Dateisymbol im Changes-Baum |
| x=15–19, y=787–795 | 31 | Informationssymbol in der Statuszeile |
| x=82–96, y=89–102, bei 01–04 | 163 | Symbol des CleanUp-Eintrags im Baum |
| x=100–114, y=112–125, bei 05 | 163 | Derselbe Eintragstyp an anderer Baumposition |

Diese Zuordnung beruht auf den berechneten Pixelkoordinaten und Sichtprüfung des unveränderten Originalbilds. Sie belegt die Lage der Abweichungen. Sie belegt **keine** bestimmte Iconversion, Themeänderung, GTK-/SWT-/Grafikbibliotheksversion oder Einstellung als Ursache. Eine solche technische Ursache wäre mit zusätzlichen Laufzeitbelegen zu bestimmen; sie wird hier nicht behauptet.

## Erhaltene Analysebelege

- `comparison.json`: vollständiger Vergleich aller zehn Dateien, sämtliche Datei-/Chunk-/IDAT-/Scanline-/RGBA-Hashes und die fünf JSON-Differenzen. SHA-256 `9d814c952221ad11e3930ee45504d8204d21e876517aa08dedefb1f709499b8c`.
- `pixel-analysis.json`: Komponentenrechtecke, Kanalstatistik, Differenzmasken, Farbpaare und Deltavergleich. SHA-256 `c80486e2334818a5d4e4dbac4b363e62a47d6cae529f339b8f8cad90da424d13`.
- `extraction-inventory.json`: sichere Extraktion, alle ZIP-/TAR-Mitglieder und ihre Prüfsummen. SHA-256 `2200fd4c7d22a33f57eb014c80b9386a23f74a9b7d43bb90ffc918a9463f8b9f`.
- `zip/`, `tar/` und `local-snapshot/`: unveränderte extrahierte beziehungsweise gegen immutable Git-Blobs geprüfte Dateibytes.

Der Dateivergleich rechtfertigt weder die Aussage „pixelidentisch“ noch eine nachträgliche Änderung der Bild- oder Provenienzbytes. Die Capture- und Codekorrekturen bleiben eine getrennte, von Root durchgeführte Arbeit.
