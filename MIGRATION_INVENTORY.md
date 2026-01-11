# Documentation Migration Inventory

**Purpose**: Track the movement of detailed plugin-specific information from the top-level README.md to individual plugin README and ARCHITECTURE files.

**Date Started**: 2026-01-11  
**Status**: In Progress

---

## Migration Strategy

### Principles
1. **No Information Loss**: All content must be preserved, just relocated
2. **Better Organization**: Plugin-specific details belong in plugin directories
3. **Clear Navigation**: Main README provides overview + links to detailed docs
4. **Traceability**: This inventory tracks every content movement

### Content Classification

**Keep in Main README:**
- Project overview and quick start
- Build instructions
- High-level plugin summaries (1-2 paragraphs max)
- Links to plugin-specific documentation
- Installation and contributing guidelines

**Move to Plugin README:**
- Detailed usage examples
- Configuration options
- Strategy explanations
- Quick start guides specific to the plugin
- Benefits and use cases

**Move to Plugin ARCHITECTURE:**
- Implementation details
- Design patterns
- Internal component descriptions
- Java version compatibility matrices (detailed)
- Technical limitations

---

## Plugin-by-Plugin Migration Tracking

### 1. sandbox_encoding_quickfix

**Main README Sections** (lines ~327-616, ~290 lines):

| Section | Lines | Destination | Status | Notes |
|---------|-------|-------------|--------|-------|
| Encoding Cleanup – Replace Platform Encoding | 328-332 | Keep (summary) | ✅ Done | Replaced with 25-line concise overview |
| Based on Test Coverage | 335-344 | Moved to README.md | ✅ Done | Added to plugin README test coverage section |
| Cleanup Strategies | 347-354 | Moved to README.md | ✅ Done | Expanded with detailed examples in plugin README |
| Java Version Awareness | 357-365 | Moved to README.md | ✅ Done | Added to plugin README with full details |
| Supported Classes and APIs | 368-386 | Moved to ARCHITECTURE.md | ✅ Done | Added comprehensive table to ARCHITECTURE |
| Examples (all 5) | 389-459 | Moved to README.md | ✅ Done | All 5 examples added to plugin README |
| Aggregation Mode Example | 462-473 | Moved to README.md | ✅ Done | Added to plugin README |
| Additional Fixes | 476-484 | Moved to README.md | ✅ Done | Added to plugin README |
| Cleanup Mode × Java Version Matrix | 487-495 | Moved to ARCHITECTURE.md | ✅ Done | Added to ARCHITECTURE technical details |
| Usage | 498-505 | Moved to README.md | ✅ Done | Added to plugin README |
| Strategy Variants (detailed) | 506-591 | Moved to README.md | ✅ Done | All 3 strategies with examples in plugin README |
| Charset Literal Replacement Table | 592-604 | Moved to README.md + ARCHITECTURE | ✅ Done | Table in README, technical details in ARCHITECTURE |
| Limitations | 607-612 | Moved to README.md | ✅ Done | Added to plugin README |
| Closing note | 615-616 | Moved to README.md | ✅ Done | Added references section to plugin README |

**Migration Complete**: ✅ All 290 lines migrated, replaced with 25-line summary in main README

---

### 2. sandbox_platform_helper

**Main README Sections** (lines ~628-751, ~124 lines):

| Section | Lines | Destination | Status | Notes |
|---------|-------|-------------|--------|-------|
| Platform Status Cleanup – Simplification | 629-636 | Keep (summary) | ✅ Done | Replaced with 18-line concise overview |
| Motivation | 643-650 | Already in README | ✅ Done | Plugin README already comprehensive |
| Before/After Comparison | 652-664 | Already in README | ✅ Done | Plugin README has detailed examples |
| Examples (3) | 667-712 | Already in README | ✅ Done | Plugin README has all examples |
| Cleanup Strategy Selection | 714-721 | Already in ARCHITECTURE | ✅ Done | Technical details in plugin docs |
| Requirements | 723-729 | Already in README | ✅ Done | Plugin README covers requirements |
| Usage | 731-738 | Already in README | ✅ Done | Plugin README has usage instructions |
| Limitations | 740-745 | Already in README | ✅ Done | Plugin README lists limitations |
| Closing note | 749-751 | Already in README | ✅ Done | Plugin README has references |

**Migration Complete**: ✅ All 124 lines migrated, replaced with 18-line summary in main README

---

### 3. sandbox_jface_cleanup

**Main README Sections** (lines ~758-924, ~167 lines):

| Section | Lines | Destination | Status | Notes |
|---------|-------|-------------|--------|-------|
| JFace Cleanup – SubProgressMonitor Migration | 759-761 | Keep (summary) | ✅ Done | Replaced with 21-line concise overview |
| Purpose | 764-774 | Already in README | ✅ Done | Plugin README already comprehensive |
| Migration Pattern | 776-846 | Already in README | ✅ Done | Plugin README has detailed transformation examples |
| Unique Variable Name Handling | 848-869 | Already in ARCHITECTURE | ✅ Done | Implementation detail in plugin docs |
| Idempotence | 871-895 | Already in README | ✅ Done | Plugin README highlights this feature |
| Official Eclipse Documentation | 897-904 | Already in README | ✅ Done | Plugin README has all references |
| Requirements | 906-910 | Already in README | ✅ Done | Plugin README covers requirements |
| Cleanup Name & Activation | 912-923 | Already in README | ✅ Done | Plugin README has configuration details |
| Limitations | (referenced at 924) | Already in README | ✅ Done | Plugin README lists limitations |
| Test Coverage | (lines after 924) | Already in ARCHITECTURE | ✅ Done | Implementation verification in plugin docs |

**Migration Complete**: ✅ All 167 lines migrated, replaced with 21-line summary in main README

---

### 4. sandbox_functional_converter

**Main README Sections** (lines ~927-1284, ~358 lines):

| Section | Lines | Destination | Status | Notes |
|---------|-------|-------------|--------|-------|
| Functional Converter Cleanup | 929-931 | Keep (summary) | ⏳ Pending | Keep brief overview |
| Source and Test Basis | 936-947 | Move to ARCHITECTURE.md | ⏳ Pending | Implementation details |
| Supported Transformations | 950-973 | Move to README.md | ⏳ Pending | User needs to know what's supported |
| Examples (all) | 976-1203 | Move to README.md | ⏳ Pending | User-facing examples |
| Not Yet Supported | 1206-1219 | Move to README.md | ⏳ Pending | Users should know limitations |
| Ignored Cases | 1222-1230 | Move to README.md | ⏳ Pending | Users should know what won't transform |
| Java Version Compatibility | 1233-1245 | Move to README.md | ⏳ Pending | User-facing compatibility info |
| Cleanup Name & Activation | 1248-1258 | Move to README.md | ⏳ Pending | User-facing configuration |
| Limitations | 1261-1268 | Move to README.md | ⏳ Pending | Users should know limitations |
| Summary | 1271-1278 | Move to README.md | ⏳ Pending | Good summary for plugin README |
| Further Reading references | 1281-1284 | Keep | ⏳ Pending | Link to plugin README instead |

**New Main README Content**:
- Brief overview: "Converts imperative loops to Java 8 Streams"
- Link to plugin README and ARCHITECTURE

---

### 5. sandbox_junit_cleanup

**Main README Sections** (lines ~1286-2124, ~839 lines - LARGEST section):

| Section | Lines | Destination | Status | Notes |
|---------|-------|-------------|--------|-------|
| JUnit Cleanup – Feature Overview | 1288-1290 | Keep (summary) | ⏳ Pending | Keep brief overview |
| Migration Summary | 1293-1309 | Move to README.md | ⏳ Pending | User-facing overview |
| JUnit 3 Classes and Methods | 1312-1459 | Move to README.md | ⏳ Pending | User-facing migration guide |
| JUnit 4 Annotations and Classes | 1462-1800 | Move to README.md | ⏳ Pending | User-facing migration guide |
| JUnit Assertion Migration | 1803-1972 | Move to README.md | ⏳ Pending | User-facing transformation guide |
| JUnit Assumption Migration | 1975-2036 | Move to README.md | ⏳ Pending | User-facing transformation guide |
| Notes | 2039-2048 | Move to ARCHITECTURE.md | ⏳ Pending | Implementation notes |
| Limitations | 2051-2074 | Move to README.md | ⏳ Pending | Users should know limitations |
| Usage | 2077-2111 | Move to README.md | ⏳ Pending | User-facing usage instructions |
| Closing note & test coverage | 2114-2120 | Move to ARCHITECTURE.md | ⏳ Pending | Implementation details |
| Marketplace badge | 2122-2124 | Keep | ⏳ Pending | Can stay in main README |

**New Main README Content**:
- Brief: "Migrates JUnit 3/4 tests to JUnit 5"
- Link to plugin README for comprehensive migration guide

---

### 6. sandbox_method_reuse

**Main README Sections** (lines ~2127-2204, ~78 lines):

| Section | Lines | Destination | Status | Notes |
|---------|-------|-------------|--------|-------|
| Method Reusability Finder | 2128-2130 | Keep (summary) | ⏳ Pending | Already brief |
| Purpose | 2133-2136 | Already in README | ✅ Done | Plugin README exists |
| Key Features | 2139-2143 | Already in README | ✅ Done | Plugin README exists |
| Inline Code Detection | 2146-2150 | Already in README | ✅ Done | Plugin README exists |
| Safety Analysis | 2153-2156 | Already in README | ✅ Done | Plugin README exists |
| Components | 2159-2168 | Already in README | ✅ Done | Plugin README exists |
| Configuration | 2171-2174 | Already in README | ✅ Done | Plugin README exists |
| Usage | 2177-2180 | Already in README | ✅ Done | Plugin README exists |
| Implementation Status | 2183-2188 | Already in README | ✅ Done | Plugin README exists |
| Closing reference | 2190-2192 | Already in README | ✅ Done | Plugin README exists |

**Note**: sandbox_method_reuse already has good plugin README - main README section can be shortened to brief overview + link

---

### 7. sandbox_xml_cleanup

**Main README Sections** (lines ~2206-2347, ~142 lines):

| Section | Lines | Destination | Status | Notes |
|---------|-------|-------------|--------|-------|
| XML Cleanup – PDE File Optimization | 2207-2209 | Keep (summary) | ⏳ Pending | Keep brief overview |
| Purpose | 2212-2217 | Already in README | ✅ Done | Plugin README exists |
| Supported XML Types | 2220-2243 | Already in README | ✅ Done | Plugin README exists |
| Transformation Process | 2246-2268 | Already in README | ✅ Done | Plugin README exists |
| Configuration | 2271-2290 | Already in README | ✅ Done | Plugin README exists |
| Security Features | 2293-2298 | Already in README | ✅ Done | Plugin README exists |
| Tab Conversion Rule | 2301-2327 | Already in README | ✅ Done | Plugin README exists |
| Usage | 2330-2334 | Already in README | ✅ Done | Plugin README exists |
| Limitations | 2337-2343 | Already in README | ✅ Done | Plugin README exists |
| Test Coverage | 2346-2352 | Already in README | ✅ Done | Plugin README exists |

**Note**: sandbox_xml_cleanup already has comprehensive plugin README - main README section can be shortened

---

### 8. Other Plugins (Brief Sections)

These plugins already have brief sections in main README:

| Plugin | Lines | Status | Notes |
|--------|-------|--------|-------|
| sandbox_cleanup_application | ~316-325 | ✅ Good | Already brief, just needs link |
| sandbox_extra_search | ~619-620 | ✅ Good | Already brief |
| sandbox_usage_view | ~623-625 | ✅ Good | Already brief |
| sandbox_tools | ~753-755 | ✅ Good | Already brief |

---

## Summary Statistics

**Total lines identified for migration**: ~1,998 lines (from main README)

**Completed Migrations (Phase 2 - 3 of 5)**:
- ✅ sandbox_encoding_quickfix: 290 lines → 25 lines (91% reduction)
- ✅ sandbox_platform_helper: 124 lines → 18 lines (85% reduction)
- ✅ sandbox_jface_cleanup: 167 lines → 21 lines (87% reduction)

**Total Progress**: 581 lines migrated (29% of total), average 88% reduction

**Remaining Migrations**:
- ⏳ sandbox_functional_converter: ~358 lines
- ⏳ sandbox_junit_cleanup: ~839 lines (largest section)

**Plugins with existing good README**: 2 (method_reuse, xml_cleanup) - just need main README simplification
**Plugins already brief**: 4 (cleanup_application, extra_search, usage_view, tools)

---

## Migration Checklist

### Phase 1: Preparation
- [x] Create this migration inventory
- [x] Review existing plugin README files
- [x] Identify content overlaps and gaps

### Phase 2: Content Migration (5 major plugins)
- [x] sandbox_encoding_quickfix (~290 lines → 25 lines, 91% reduction)
- [x] sandbox_platform_helper (~124 lines → 18 lines, 85% reduction)  
- [x] sandbox_jface_cleanup (~167 lines → 21 lines, 87% reduction)
- [ ] sandbox_functional_converter (~358 lines remaining)
- [ ] sandbox_junit_cleanup (~839 lines remaining - LARGEST section)

**Status**: 3 of 5 major migrations completed (581 lines removed, 88% average reduction)

### Phase 3: Main README Simplification
- [ ] Replace detailed sections with brief summaries
- [ ] Add clear "See detailed documentation" links
- [ ] Update table of contents
- [ ] Verify all cross-references

### Phase 4: Verification
- [ ] Check all internal links work
- [ ] Verify no information was lost
- [ ] Confirm navigation is clear
- [ ] Run link checker (if available)
- [ ] Review with `git diff` to ensure changes are correct

---

## Notes and Decisions

### Completed Migrations - Lessons Learned

1. **Effective Reduction Strategy**:
   - Main README: 1-2 paragraph overview + key features list + one example + links
   - Achieved 85-91% reduction while maintaining clarity
   - Users can quickly scan and navigate to details

2. **Content Distribution Worked Well**:
   - **Plugin README** gets: Examples, usage, strategies, limitations, user-facing info
   - **Plugin ARCHITECTURE** gets: Technical matrices, implementation details, test coverage
   - **Main README** keeps: Brief overview, quick example, navigation links

3. **Navigation is Critical**:
   - Every section now has clear "Full Documentation" links with specific targets
   - Format: 📖 **Full Documentation**: [Plugin README](path) | [Architecture](path) | [TODO](path)

### Content Organization Decisions

1. **User-facing content → Plugin README**:
   - Examples
   - Usage instructions
   - Configuration options
   - Benefits and limitations
   - Quick start guides

2. **Technical content → Plugin ARCHITECTURE**:
   - Implementation details
   - Design patterns
   - Internal components
   - Test coverage details
   - Technical compatibility matrices

3. **Main README → Brief overview only**:
   - 1-3 sentence description
   - Link to plugin README
   - Link to plugin ARCHITECTURE
   - Maybe one simple example if particularly illustrative

### Link Strategy

Each plugin section in main README will follow this pattern:

```markdown
### N. `sandbox_plugin_name`

Brief 1-3 sentence description of what the plugin does and why it's useful.

**Key Features:**
- Feature 1
- Feature 2
- Feature 3

**Quick Example:**
```java
// Before
old code

// After
new code
```

📖 **Full Documentation**: [Plugin README](sandbox_plugin_name/README.md) | [Architecture](sandbox_plugin_name/ARCHITECTURE.md)
```

---

**Last Updated**: 2026-01-11  
**Next Review**: After Phase 2 completion
