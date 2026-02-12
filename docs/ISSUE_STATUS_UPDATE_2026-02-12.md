# Issue Status Update — 2026-02-12

This document contains the updated issue bodies for #676, #453, and #549, reflecting the actual implementation status in the `main` branch as of February 12, 2026.

---

## Issue #676 — „Sicherheitsmaßnahmen umsetzen"

### Status-Übersicht

Die Sicherheitsprüfungen vor Loop-to-Stream-Konvertierungen wurden in mehreren Phasen implementiert. Stand: **12.02.2026**.

### ⚠️ Teilweise umgesetzt / Lücken

| # | Sicherheitsmaßnahme | Status | Implementierungstand |
|---|-------------------|--------|---------------------|
| 2.1 | Iterator.remove() nicht konvertieren | ✅ Umgesetzt | `PreconditionsChecker.checkIteratorRemove()` blockiert alle Konvertierungen |
| 2.2 | Collection-Modifikationen erkennen | ✅ Umgesetzt | `CollectionModificationDetector` mit field-access Support |
| 2.3 | Thread-Safety bei Feldern | ⚠️ Teilweise | `PreconditionsChecker.checkThreadSafety()` nur für einfache Fälle |
| 2.4 | Concurrent Collections | ✅ Detection umgesetzt, Integration ausstehend | `ConcurrentCollectionDetector` erkennt alle Typen, noch nicht in Konvertierungsentscheidungen integriert |
| 2.5 | Loops mit Synchronisation | ✅ Umgesetzt | `PreconditionsChecker.checkSynchronization()` blockiert synchronized-Blocks |

**Details zu 2.4 — Concurrent Collections:**
- ✅ `ConcurrentCollectionDetector` vollständig implementiert in `sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/ConcurrentCollectionDetector.java`
- ✅ Erkennt alle relevanten Typen: CopyOnWriteArrayList, CopyOnWriteArraySet, ConcurrentHashMap, ConcurrentSkipListMap, ConcurrentSkipListSet, ConcurrentLinkedQueue, ConcurrentLinkedDeque, LinkedBlockingQueue, LinkedBlockingDeque, ArrayBlockingQueue, PriorityBlockingQueue, DelayQueue, SynchronousQueue
- ✅ Tests vorhanden: `ConcurrentCollectionDetectorTest.java`
- ✅ `PreconditionsChecker.isConcurrentCollection()` vorhanden
- ⚠️ **Lücke**: Der Code-Kommentar sagt "This flag is currently detected but not yet integrated into conversion decisions" — Detection ✅, aber Integration in `isSafeToRefactor()` noch ausstehend

### 🔍 Weitere fehlende Sicherheitsmaßnahmen

| # | Lücke | Status | Begründung |
|---|-------|--------|-----------|
| 1 | `this.list.remove(x)` nicht erkannt | ✅ Umgesetzt | `CollectionModificationDetector.isModification()` unterstützt `FieldAccess` mit `this.list.remove(x)` Pattern. Test `testFieldAccessModification()` bestätigt dies. |
| 2 | Map-Iterator-Remove nicht erkannt | ❌ Fehlt | `map.entrySet().iterator()` → `it.remove()` wird nicht blockiert |
| 3 | `replaceAll`/`sort`/`removeIf` nicht erkannt | ✅ Umgesetzt | Alle drei sind in `CollectionModificationDetector.MODIFYING_METHODS` enthalten: `"removeIf", "replaceAll", "sort"`. Tests vorhanden (`testListRemoveIf`, `testListReplaceAll`, `testListSort`). |
| 4 | Map-Modifikationen fehlen | ✅ Umgesetzt | `MODIFYING_METHODS` enthält: `"put", "putAll", "putIfAbsent", "compute", "computeIfAbsent", "computeIfPresent", "merge", "replace"`. Tests vorhanden für alle (`testMapPutIfAbsent`, `testMapCompute`, `testMapComputeIfAbsent`, `testMapComputeIfPresent`, `testMapMerge`, `testMapReplace`). |
| 5 | Nested-Loop-Capture (Closure-Problem) | ❌ Fehlt | Siehe Issue #670 für Details |
| 6 | Exception-Handling bei Iterator-While-Loops | ❌ Fehlt | Try-catch im Loop-Body kann Stream-Exception-Semantik ändern |

### Empfehlung: Priorisierte nächste Schritte

**Hohe Priorität:**
1. ~~Concurrent Collection Typ-Erkennung~~ → ✅ `ConcurrentCollectionDetector` implementiert, **Integration in `isSafeToRefactor()` noch ausstehend**
2. ~~Map-Modifikationsmethoden ergänzen~~ → ✅ Alle ergänzt
3. ~~`this.field`-Modifikationserkennung~~ → ✅ FieldAccess-Receiver implementiert
4. **Concurrent Collection Integration**: `PreconditionsChecker.isSafeToRefactor()` sollte das `isConcurrentCollection` Flag aktiv nutzen
5. **Map-Iterator-Remove**: `map.entrySet().iterator()` Pattern erkennen

**Mittlere Priorität:**
- Exception-Handling prüfen (try-catch im Loop-Body)
- Nested-Loop-Capture (siehe Issue #670)

**Niedrige Priorität:**
- Field-Thread-Safety weiter verbessern
- Tests für alle Edge-Cases ergänzen

---

## Issue #453 — „Architektur: Unified Loop Representation (ULR)"

### 📊 Aktueller Stand — 2026-02-12

**Status**: ✅ **Phase 1-10 abgeschlossen** — Alle sieben Transformatoren nutzen jetzt die ULR-Pipeline.

Die Unified Loop Representation (ULR) ist das zentrale Architekturkonzept für alle Loop-Transformationen im `sandbox_functional_converter`. Sie ermöglicht bidirektionale Transformationen zwischen verschiedenen Loop-Typen und Streams durch ein gemeinsames Zwischenformat.

### 🔄 Transformations-Übersicht (2026-02-12)

| Transformation | Handler | ULR-Status | Phase |
|---------------|---------|------------|-------|
| Enhanced-for → Stream | `EnhancedForHandler` | ✅ ULR-basiert | Phase 1 |
| Enhanced-for → Iterator-while | `EnhancedForToIteratorWhile` | ✅ ULR-basiert | Phase 8 |
| Iterator-while → Stream | `IteratorWhileHandler` | ✅ ULR-basiert | Phase 7 |
| Iterator-while → Enhanced-for | `IteratorWhileToEnhancedFor` | ✅ ULR-basiert | Phase 8 |
| Stream → Enhanced-for | `StreamToEnhancedFor` | ✅ ULR-basiert | Phase 9 |
| Stream → Iterator-while | `StreamToIteratorWhile` | ✅ ULR-basiert | Phase 9 |
| Traditional-for → Stream | `TraditionalForHandler` | ✅ ULR-basiert | Phase 7 (PR #669) |

**Alle sieben Transformatoren nutzen die ULR-Pipeline**: `LoopModelBuilder → LoopModel → LoopModelTransformer → ASTStreamRenderer`

### 📋 Offene Aufgaben

#### ULR-Migration
- [x] `EnhancedForHandler` auf ULR-Pipeline migrieren (✅ Phase 1 abgeschlossen)
- [x] `IteratorLoopToFunctional` auf ULR-Pipeline migrieren (✅ `IteratorWhileHandler` nutzt ULR: `LoopModelBuilder → LoopModel → LoopModelTransformer → ASTStreamRenderer`, Methode `buildLoopModel()` erstellt LoopModel)
- [x] Reverse-Transformationen für ULR evaluieren (✅ Alle 4 bidirektionalen Handler nutzen ULR: `StreamToEnhancedFor`, `StreamToIteratorWhile`, `IteratorWhileToEnhancedFor`, `EnhancedForToIteratorWhile`. ARCHITECTURE.md bestätigt: "All seven transformers now use the ULR pipeline.")
- [x] `TraditionalForHandler` (PR #669) reviewen und mergen (✅ `TraditionalForHandler.java` im main-Branch, nutzt ULR mit `EXPLICIT_RANGE` SourceType)

#### Phase 10 — Rendering
- [x] ASTStreamRenderer: Block-Lambda mit Kommentaren implementieren (✅ `renderFilterWithComments()` und `renderMapWithComments()` in `ASTStreamRenderer.java`)
- [x] StringRenderer: Comment-aware Rendering (✅ `StringRenderer.renderBlockLambda()` mit `appendNormalizedCommentLines()`)
- [x] End-to-End-Test: Loop mit Kommentaren → Stream mit Kommentaren im Output (✅ `CommentPreservationIntegrationTest` (8 Tests) und `CommentPreservationTest` (Core, 4 Tests). End-to-End: Kommentar vor `if-continue` → korrekt an `FilterOp` angehängt)

### 🏗️ Architektur-Diagramm

```
┌─────────────────────────────────────────────────────────────────┐
│                        ULR Pipeline                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Loop AST → LoopModelBuilder → LoopModel → LoopModelTransformer │
│                                     ↓                            │
│                              ASTStreamRenderer                   │
│                                     ↓                            │
│                            Stream/Loop AST                       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**ULR Core Components:**
- `LoopModel` — Language-agnostic intermediate representation
- `SourceDescriptor` — Loop source (collection, iterator, range)
- `ElementDescriptor` — Loop variable
- `Operation` — Filter, Map, FlatMap, Peek, etc.
- `Terminal` — ForEach, Collect, Reduce, etc.

### 🎯 Nächste Schritte

1. **Comment Preservation Completion**
   - Extend comment support to `FlatMapOp`, `PeekOp`, etc.
   - `IteratorWhileHandler` / `TraditionalForHandler` comment extraction via `JdtLoopExtractor`

2. **Performance Optimization**
   - Benchmark ULR pipeline overhead
   - Profile rendering performance

3. **Documentation**
   - Complete ARCHITECTURE.md with all ULR examples
   - Add ULR design rationale document

---

## Issue #549 — „TODO: Functional Loop Conversion"

### 🎯 Transformationsmatrix — Stand: 12.02.2026

| Ausgang              | Ziel                | Status | Handler/Implementierung |
|---------------------|---------------------|--------|------------------------|
| Enhanced-for        | Stream              | ✅     | `EnhancedForHandler` |
| Enhanced-for        | Iterator-while      | ✅     | `EnhancedForToIteratorWhile` |
| Iterator-while      | Stream              | ✅     | `IteratorWhileHandler` |
| Iterator-while      | Enhanced-for        | ✅     | `IteratorWhileToEnhancedFor` |
| Stream              | Enhanced-for        | ✅     | `StreamToEnhancedFor` |
| Stream              | Iterator-while      | ✅     | `StreamToIteratorWhile` |
| Klassische For      | Stream              | ✅     | `TraditionalForHandler` → `IntStream.range()` |
| Klassische For      | Enhanced-for        | ⏳     | Geplant |
| Array-Indexing      | Enhanced-for        | ⏳     | Geplant |
| Array-Indexing      | Stream              | ⏳     | Geplant |

**Legende:**
- ✅ Umgesetzt und getestet
- ⏳ Geplant / Erkennung vorhanden
- ❌ Nicht geplant / zu komplex

### 📈 Implementierungsstatus

**Vollständig umgesetzt:**
1. ✅ **Enhanced-for → Stream** — `EnhancedForHandler` mit vollständiger ULR-Pipeline
2. ✅ **Enhanced-for ↔ Iterator-while** — Bidirektionale Transformation
3. ✅ **Iterator-while → Stream** — `IteratorWhileHandler` mit ULR
4. ✅ **Stream → Enhanced-for** — `StreamToEnhancedFor` mit Body-Preservation
5. ✅ **Stream → Iterator-while** — `StreamToIteratorWhile` mit Body-Preservation
6. ✅ **Traditional For → Stream** — `TraditionalForHandler` mit `IntStream.range()` und EXPLICIT_RANGE

**In Planung:**
- **Klassische For → Enhanced-for** — Erfordert Array/List-Unterscheidung
- **Array-Indexing → Enhanced-for** — Muster-Erkennung vorhanden, Transformation ausstehend

### 🔍 Offene Aufgaben — Stand: 12.02.2026

#### Hohe Priorität
- [ ] **Array-Indexing → Enhanced-for** — Array-Index-Pattern erkennen und in Enhanced-for konvertieren
- [ ] **Klassische For → Enhanced-for** — Range-basierte For-Loops in Enhanced-for konvertieren
- [ ] **Sicherheitsprüfungen vervollständigen** — Siehe Issue #676

#### Mittlere Priorität
- [ ] **Performance-Optimierung** — Benchmark für alle Transformationen
- [ ] **Edge-Case-Tests** — Nested loops, break/continue, etc.
- [ ] **Dokumentation** — Transformation-Guide für alle Patterns

#### Niedrige Priorität
- [ ] **IDE-Integration** — Quick-Assist-Menü für alle Transformationen
- [ ] **Batch-Refactoring** — Mehrere Loops in einem Durchgang konvertieren

---

## Zusammenfassung der Änderungen

### Issue #676 — Sicherheitsmaßnahmen
- ✅ Punkt 2.4 aktualisiert: "Detection umgesetzt, Integration ausstehend"
- ✅ Punkt 1 der Lücken: "✅ Umgesetzt" (FieldAccess-Support)
- ✅ Punkt 3 der Lücken: "✅ Umgesetzt" (replaceAll/sort/removeIf)
- ✅ Punkt 4 der Lücken: "✅ Umgesetzt" (Map-Modifikationen)
- ✅ Empfehlungen aktualisiert: Punkte 1-3 als erledigt markiert

### Issue #453 — ULR-Architektur
- ✅ Datum aktualisiert: "2026-02-10" → "2026-02-12"
- ✅ IteratorLoopToFunctional Migration: `[ ]` → `[x]`
- ✅ Reverse-Transformationen: `[ ]` → `[x]`
- ✅ TraditionalForHandler: `[ ]` → `[x]`
- ✅ Phase 10 Rendering-Tasks: Alle `[ ]` → `[x]`
- ✅ Transformations-Übersicht: Iterator-while und alle bidirektionalen Handler als "✅ ULR-basiert"

### Issue #549 — Functional Loop Conversion
- ✅ Transformationsmatrix: "Klassische For → Stream" von "⏳" zu "✅"
- ✅ Datum aktualisiert: "31.01.2026" → "12.02.2026"

### Zusätzliche Änderungen
- Datei `sandbox_functional_converter/TODO.md` aktualisiert (siehe nächster Commit)
