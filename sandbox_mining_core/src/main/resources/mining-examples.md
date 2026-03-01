## Mining Examples

These examples show how commits should be evaluated and what good
DSL rules look like. Use these as reference when analyzing new commits.

### Example 1: GREEN — Direct DSL Implementation

**Commit message:** "Replace Collections.unmodifiableList with List.copyOf"

**Expected evaluation:**
- relevant: true
- trafficLight: GREEN
- reusability: 8
- codeImprovement: 6
- category: "Collections Modernization"

**DSL rule:**
```
trigger: method_invocation("Collections", "unmodifiableList")
  guard: source_version >= 10
pattern:
  Collections.unmodifiableList($arg) => List.copyOf($arg)
import:
  + java.util.List
  - java.util.Collections
```

### Example 2: YELLOW — Needs Minor DSL Extension

**Commit message:** "Use try-with-resources for InputStream handling"

**Expected evaluation:**
- relevant: true
- trafficLight: YELLOW
- reusability: 7
- category: "Resource Management"
- languageChangeNeeded: "Support for wrapping existing variable declarations in try-with-resources blocks"

### Example 3: RED — Not Yet Implementable

**Commit message:** "Migrate from Observer pattern to EventBus"

**Expected evaluation:**
- relevant: true
- trafficLight: RED
- reusability: 5
- category: "Architecture Migration"
- languageChangeNeeded: "Requires cross-file analysis and architectural pattern detection"

### Example 4: NOT_APPLICABLE — Irrelevant Commit

**Commit message:** "Update version numbers in pom.xml"

**Expected evaluation:**
- relevant: false
- irrelevantReason: "Version bump only, no code transformation pattern"
- trafficLight: NOT_APPLICABLE

### Common Mistakes to Avoid

1. **Do NOT use XML tags** in dslRule:
   - Wrong: `<trigger>method_invocation(...)</trigger>`
   - Right: `trigger: method_invocation(...)`

2. **Do NOT use isType()**:
   - Wrong: `guard: isType($var, "String")`
   - Right: `guard: instanceof($var, "String")`

3. **Do NOT mark version bumps, CI changes, or documentation as relevant**

4. **Do NOT propose rules that only apply to a single specific codebase**
