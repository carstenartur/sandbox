# Häufig gestellte Fragen / Frequently Asked Questions

## Deutsch / German

### Unterstützt das sandbox functional converter plugin das Erhalten von Kommentaren?

**Ja!** Das sandbox functional converter plugin unterstützt vollständig das Erhalten von Kommentaren während der Transformation von Schleifen zu funktionalen Streams.

#### Was wird unterstützt?

- ✅ **Enhanced-For-Schleifen → Streams**: Vollständige Unterstützung
- ✅ **Bidirektionale Transformationen**: Alle 4 Richtungen bewahren Kommentare
- ✅ **Alle Kommentartypen**: Line-Comments (`//`), Block-Comments (`/* */`), Javadoc (`/** */`)
- ✅ **Automatisch aktiviert**: Keine Konfiguration erforderlich

#### Beispiel:

**Vorher:**
```java
for (String item : items) {
    // Überspringe leere Elemente
    if (item.isEmpty()) continue;
    System.out.println(item);
}
```

**Nachher:**
```java
items.stream()
    .filter(item -> {
        // Überspringe leere Elemente
        return !(item.isEmpty());
    })
    .forEachOrdered(item -> {
        System.out.println(item);
    });
```

#### Weitere Informationen:

- Detaillierte Anleitung: [ARCHITECTURE.md](ARCHITECTURE.md)
- Beispiele: [README.md](README.md)
- Architektur: [ARCHITECTURE.md](ARCHITECTURE.md)
- Tests: `CommentPreservationIntegrationTest.java`

---

## English

### Does the sandbox functional converter plugin support preserving comments?

**Yes!** The sandbox functional converter plugin fully supports preserving comments during transformation of loops to functional streams.

#### What is supported?

- ✅ **Enhanced-For Loops → Streams**: Full support
- ✅ **Bidirectional Transformations**: All 4 directions preserve comments
- ✅ **All Comment Types**: Line comments (`//`), block comments (`/* */`), Javadoc (`/** */`)
- ✅ **Enabled by Default**: No configuration required

#### Example:

**Before:**
```java
for (String item : items) {
    // Skip empty items
    if (item.isEmpty()) continue;
    System.out.println(item);
}
```

**After:**
```java
items.stream()
    .filter(item -> {
        // Skip empty items
        return !(item.isEmpty());
    })
    .forEachOrdered(item -> {
        System.out.println(item);
    });
```

#### More Information:

- Detailed guide: [ARCHITECTURE.md](ARCHITECTURE.md)
- Examples: [README.md](README.md)
- Architecture: [ARCHITECTURE.md](ARCHITECTURE.md)
- Tests: `CommentPreservationIntegrationTest.java`

---

## Weitere häufige Fragen / Other Common Questions

### Q: Werden Kommentare in allen Schleifentypen erhalten?

**A (DE)**: Derzeit vollständig unterstützt für Enhanced-For-Schleifen und bidirektionale Transformationen. Iterator-While und traditionelle For-Schleifen bewahren Body-Kommentare, aber nicht einzelne Operations-Kommentare während der Stream-Konvertierung (in Entwicklung).

**A (EN)**: Currently fully supported for enhanced-for loops and bidirectional transformations. Iterator-while and traditional for-loops preserve body comments but not individual operation comments during stream conversion (in development).

---

### Q: Was ist mit inline Kommentaren? / What about inline comments?

**A (DE)**: Inline-Kommentare (auch "trailing comments" genannt) werden jetzt vollständig unterstützt! Das sind Kommentare, die auf derselben Zeile nach dem Code stehen:

```java
// Vorher:
for (String item : items) {
    System.out.println(item); // Print the item
}

// Nachher:
items.stream()
    .forEachOrdered(item -> {
        System.out.println(item); // Print the item
    });
```

Das Plugin erfasst drei Arten von Kommentaren:
- **Leading**: Kommentare vor dem Statement
- **Trailing/Inline**: Kommentare nach dem Statement auf derselben Zeile ✨ **NEU!**
- **Embedded**: Kommentare innerhalb des Statements

**A (EN)**: Inline comments (also called "trailing comments") are now fully supported! These are comments that appear on the same line after the code:

```java
// Before:
for (String item : items) {
    System.out.println(item); // Print the item
}

// After:
items.stream()
    .forEachOrdered(item -> {
        System.out.println(item); // Print the item
    });
```

The plugin captures three types of comments:
- **Leading**: Comments before the statement
- **Trailing/Inline**: Comments after the statement on the same line ✨ **NEW!**
- **Embedded**: Comments inside the statement

---

### Q: Muss ich etwas konfigurieren?

**A (DE)**: Nein! Die Kommentarerhaltung ist standardmäßig aktiviert. Aktivieren Sie einfach die "Use functional call"-Cleanup in Eclipse.

**A (EN)**: No! Comment preservation is enabled by default. Just enable the "Use functional call" cleanup in Eclipse.

---

### Q: Welche Kommentartypen werden unterstützt?

**A (DE)**: Alle Standard-Java-Kommentare:
- Line-Comments: `// Kommentar`
- Block-Comments: `/* Kommentar */`
- Javadoc: `/** Kommentar */`

**A (EN)**: All standard Java comments:
- Line comments: `// comment`
- Block comments: `/* comment */`
- Javadoc: `/** comment */`

---

### Q: Werden Kommentare genau an der gleichen Position eingefügt?

**A (DE)**: Kommentare werden in Block-Lambdas eingefügt. Die Position innerhalb der Lambda ist möglicherweise leicht anders, aber die semantische Beziehung zum Code bleibt erhalten.

**A (EN)**: Comments are inserted into block lambdas. The position within the lambda may be slightly different, but the semantic relationship to the code is preserved.

---

### Q: Was passiert, wenn mehrere Kommentare an einem Statement sind?

**A (DE)**: Alle Kommentare, die mit einem Statement verbunden sind (Zeilen davor und auf der gleichen Zeile), werden extrahiert und im transformierten Code beibehalten.

**A (EN)**: All comments associated with a statement (lines before and on the same line) are extracted and preserved in the transformed code.

---

### Q: Gibt es Tests für diese Funktion?

**A (DE)**: Ja! Es gibt umfassende Tests:
- `CommentPreservationTest`: 4 Unit-Tests
- `StringRendererTest`: 8 Tests für Rendering
- `CommentPreservationIntegrationTest`: 9 Integrationstests
- `LoopBidirectionalTransformationTest`: 5 bidirektionale Tests

**A (EN)**: Yes! There are comprehensive tests:
- `CommentPreservationTest`: 4 unit tests
- `StringRendererTest`: 8 rendering tests
- `CommentPreservationIntegrationTest`: 9 integration tests
- `LoopBidirectionalTransformationTest`: 5 bidirectional tests

---

### Q: Wann wurde diese Funktion implementiert?

**A (DE)**: Die Kommentarerhaltung wurde im Februar 2026 als Teil von Phase 10 implementiert und ist produktionsreif.

**A (EN)**: Comment preservation was implemented in February 2026 as part of Phase 10 and is production-ready.

---

### Q: Wird diese Funktion in Eclipse JDT integriert?

**A (DE)**: Das ist geplant. Die gesamte Sandbox-Codebasis ist so konzipiert, dass sie einfach in Eclipse JDT integriert werden kann (Package-Namen: `org.sandbox` → `org.eclipse`).

**A (EN)**: That is planned. The entire sandbox codebase is designed to be easily integrated into Eclipse JDT (package names: `org.sandbox` → `org.eclipse`).

---

### Q: Wo finde ich weitere Beispiele?

**A (DE)**: Siehe:
- [README.md](README.md) - 8 vollständige Beispiele
- [ARCHITECTURE.md](ARCHITECTURE.md) - Detaillierte technische Dokumentation
- Test-Dateien im `sandbox_functional_converter_test` Modul

**A (EN)**: See:
- [README.md](README.md) - 8 complete examples
- [ARCHITECTURE.md](ARCHITECTURE.md) - Detailed technical documentation
- Test files in the `sandbox_functional_converter_test` module

---

### Q: Kann ich die Funktion deaktivieren?

**A (DE)**: Die Funktion ist immer aktiv, wenn die Cleanup-Transformation durchgeführt wird. Es gibt keinen separaten Schalter, da die Kommentarerhaltung ein gewünschtes Standardverhalten ist.

**A (EN)**: The feature is always active when the cleanup transformation is performed. There is no separate switch because comment preservation is a desired default behavior.

---

### Q: Was sind die Einschränkungen?

**A (DE)**: 
- Iterator-While und traditionelle For-Schleifen: Derzeit nur Body-Kommentare (Operations-Kommentare in Entwicklung)
- Kommentare in komplexen Ausdrücken: Begrenzte Unterstützung
- Mehrere Statements pro Zeile: Kann in Randfällen nicht korrekt zugeordnet werden

**A (EN)**:
- Iterator-while and traditional for-loops: Currently only body comments (operation comments in development)
- Comments in complex expressions: Limited support
- Multiple statements per line: May not be correctly associated in edge cases

---

## Kontakt / Contact

Für weitere Fragen oder Probleme, öffnen Sie bitte ein Issue im GitHub-Repository:
https://github.com/carstenartur/sandbox

For more questions or issues, please open an issue in the GitHub repository:
https://github.com/carstenartur/sandbox
