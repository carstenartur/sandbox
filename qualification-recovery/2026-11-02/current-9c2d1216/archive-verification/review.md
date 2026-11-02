# Unabhängige Archivprüfung: aktueller Nachweis 9c2d1216

**Ergebnis: PASS.** Das exakt benannte fertige Archiv wurde vollständig gelesen, sicher in einen neuen Ordner extrahiert und gegen beide internen Verzeichnisse sowie die ursprünglichen Eingaben geprüft.

## Identität

| Gegenstand | Verifizierter Wert |
|---|---|
| Archiv | `complete-current-evidence-9c2d1216.tar.gz` |
| Bytes | 52199315 |
| SHA256 | `253eb11d40ef97685b5469de49e353c889b784e179f95274b26686fac4765def` |
| Git-Blob | `3f89a76c33a79cfbf97dcb9e53e3ba827f5a8a6f` |
| MANIFEST.json SHA256 | `cedda43c1733bc0b009467ca996bc54ceff133e97d99bf0e078a157a28a5137a` |
| SHA256SUMS SHA256 | `85ec13ffe475df27d8aaaf26711cf4d44284f201d1da70233d33b1383f2761c7` |
| Qualifizierter HEAD | `9c2d121686dd2bfcd774d820312ca603d8fc4b4f` |
| Qualifizierter Tree | `d9736c8cd1e397508784e7d79fd1cb08ac978312` |

## Vollständige Prüfung

- GNU gzip-Integritätstest: tatsächlicher Exit 0; zusätzlich vollständig erfolgreiches Node-zlib-Dekomprimieren mit CRC/ISIZE-Prüfung.
- Alle 14046 TAR-Header-Prüfsummen stimmen, einschließlich 1576 GNU-Langnamen-Header. Alle tatsächlichen Mitglieder sind reguläre Dateien oder Verzeichnisse; keine Links, Spezialdateien, doppelten Pfade, absoluten Pfade oder Traversierung. Zwei Abschlussblöcke und sämtliche Padding-/Endbytes sind null.
- Genau 12470 Mitglieder: 11169 reguläre Dateien und 1301 Verzeichnisse. Das entspricht sämtlichen Manifest-Einträgen, den zwei erzeugten Metadatendateien und den impliziten Elternverzeichnissen einschließlich Archivwurzel. Die separate NUL-Liste stimmt exakt damit überein.
- **11167/11167 Eingabedateien** (443621935 Bytes) stimmen mit ihrem Manifest-SHA256 überein. Alle 11168 SHA256SUMS-Einträge sind vorhanden und korrekt. SHA256SUMS selbst stimmt mit dem außen festgelegten Hash überein.
- Nach der Extraktion wurden alle 11169 Dateien erneut vom Dateisystem gehasht. Die ursprünglichen Stage-Dateien und sämtliche 11167 ursprünglichen Eingabedateien wurden ebenfalls vollständig gehasht. Die manifestierten Dateien und ursprünglichen Eingabeverzeichnisse stimmen in Inventar, Größen, Typen und Modi; der vollständige aktuelle target-Baum und die aufgenommenen Run-Verzeichnisse enthalten keine unerklärten Auslassungen.
- Finale Archiv-Identität (Inode, Größe, Änderungszeiten und SHA256) sowie die Quellen-Identität wurden vor und nach der Prüfung bestätigt. Kein bestehendes Archiv, Stage oder Checkout wurde verändert.

## Separater Stage-Befund

Im ursprünglichen Copy-Stage liegt zusätzlich außerhalb des Manifest-Inventars `runs/retention/reports/bc-mathematical/.retention.json.dOF5kS` (62652416 Bytes, SHA256 `e449d13dcecba46dede7ee35fd37891306fbe85d6d0ddd121ef96f379f495090`). Dieser Eintrag ist nicht im finalen TAR enthalten. Der gleichnamige Eintrag fehlt im entsprechenden ursprünglichen Quellverzeichnis; der Hash stimmt nicht mit der manifestierten vollständigen retention.json überein. Sämtliche ursprünglichen Eingaben und die manifestierte retention.json stimmen dagegen vollständig mit dem Archiv überein. Der zusätzliche Stage-Eintrag blieb während dieser Prüfung unverändert. Seine Entstehungsursache ist aus den vorliegenden Daten nicht belegt und wird nicht behauptet. Dieser Befund wurde Root unmittelbar gemeldet.

## Quellen- und Abschlussbelege

| Aufgenommener Lauf | UTC-Start | UTC-Ende | Wrapper / Maven | Quellenbindung |
|---|---|---|---|---|
| full-retry | 2026-11-02T18:19:46Z | 2026-11-02T18:29:49Z | 0 / 0 | HEAD/Tree exakt; vorher/nachher sauber; source-current=true |
| retention | 2026-11-02T18:30:18Z | 2026-11-02T18:30:30Z | 0 / 0 | HEAD/Tree exakt; vorher/nachher sauber; source-current=true |
| measurement | 2026-11-02T18:31:13Z | 2026-11-02T18:36:20Z | 0 / 0 | HEAD/Tree exakt; vorher/nachher sauber; source-current=true |

Der ursprüngliche Full-Versuch bleibt ausdrücklich **INCOMPLETE_COMPLETION_CHAIN**: Prozess-Exit unbekannt (null), automatischer Abschlussbeleg fehlt; sein Log-Hash und seine Startidentität stimmen. Es wurde kein Exit 0 ergänzt. Der dortige preserved-target-Baum ist explizit ausgelassen; der vollständige qualifizierte aktuelle target-Baum des Wiederholungslaufs ist enthalten.

Die archivierte vollständige Blob-Zuordnung wurde gegen die tatsächlich vorhandenen Git-Trees erneut geprüft: **3556 Einträge, 3551 gleiche Blobs, exakt fünf unterschiedliche Help-Provenance-JSONs**. Kandidat `a226360ade62e962bacd053c07166158e9a0f3fa` / Tree `652696bc384c8a6c809a70499d13fe8e51d8cc8f` enthält gegenüber Native `80e16c7da0f094969d207815757d234b5a3bab11` exakt die elf angegebenen QA-Dateien. Ihre Bytes/Hashes und sämtliche Zuordnungszeilen stimmen. Die vier aktuellen JMH-Ausgaben und die drei bereits abgeschlossenen unabhängigen Messprüfungsdateien haben unverändert ihre zuvor geprüften Hashes; hier erfolgte keine neue statistische oder fachliche Bewertung.

Beide Help-ZIPs sind als **externe, nicht eingebettete** Dateien ausgewiesen. Ihre Größen und SHA256 wurden gelesen und bestätigt (Runs 41497649570 und 41500326710); sie sind kein Bestandteil dieses TAR-Payloads.

## Grenzen und Ausgabe

Die separate gleichnamige `.partial`-Datei gehört nicht zum fertigen Archiv und wurde weder verändert noch als erfolgreicher Abschluss gewertet. Eine begrenzte Prozess-Metadatenprüfung vor der Archivprüfung fand keinen passenden laufenden Archivierer; ihre Entstehungsursache wird hier nicht behauptet.

Keine JVM, kein Maven, keine neue Messung, kein ausgeführter archivierter Code und keine Remote-Schreiboperation. Prüfzeit: 2026-11-02T19:08:02.570Z bis 2026-11-02T19:08:15.199Z. Vollständige maschinenlesbare Zusammenfassung: `verification.json`; nachvollziehbarer Prüfcode: `verify.cjs`; neue Extraktion: `extracted/`.
