# Documentation Inventory and Enhancement Tracking

This document provides an inventory of all documentation in the repository and tracks potential enhancements.

**Last Updated:** 2026-06-27

---

## Documentation Completeness Summary

### Eclipse Plugin Modules

| Plugin | README | ARCHITECTURE | TODO |
|--------|--------|--------------|------|
| sandbox_cleanup_application | ✅ | ✅ | ✅ |
| sandbox_common | ✅ | ✅ | ✅ |
| sandbox_common_core | ✅ | ✅ | ❌ |
| sandbox_css_cleanup | ✅ | ✅ | ✅ |
| sandbox_encoding_quickfix | ✅ | ✅ | ✅ |
| sandbox_extra_search | ✅ | ✅ | ✅ |
| sandbox_functional_converter | ✅ | ✅ | ✅ |
| sandbox_int_to_enum | ✅ | ✅ | ✅ |
| sandbox_jface_cleanup | ✅ | ✅ | ✅ |
| sandbox_junit_cleanup | ✅ | ✅ | ✅ |
| sandbox_method_reuse | ✅ | ✅ | ✅ |
| sandbox_oomph | ✅ | ✅ | ✅ |
| sandbox_platform_helper | ✅ | ✅ | ✅ |
| sandbox_test_commons | ✅ | ✅ | ✅ |
| sandbox_tools | ✅ | ✅ | ✅ |
| sandbox_triggerpattern | ✅ | ✅ | ✅ |
| sandbox_usage_view | ✅ | ✅ | ✅ |
| sandbox_use_general_type | ✅ | ✅ | ✅ |
| sandbox_xml_cleanup | ✅ | ✅ | ✅ |

### Plain Maven Modules

| Module | README | ARCHITECTURE | TODO |
|--------|--------|--------------|------|
| sandbox-ast-api | ✅ | ❌ | ✅ |
| sandbox-ast-api-jdt | ✅ | ❌ | ❌ |
| sandbox-benchmarks | ✅ | ❌ | ❌ |
| sandbox-functional-converter-core | ✅ | ❌ | ❌ |
| sandbox-jgit-server-webapp | ✅ | ❌ | ❌ |
| sandbox-jgit-storage-hibernate | ✅ | ❌ | ❌ |
| sandbox-maven-plugin | ✅ | ❌ | ❌ |
| sandbox_mining_cli | ✅ | ❌ | ❌ |
| sandbox_mining_core | ✅ | ❌ | ✅ |

### Infrastructure Modules

| Module | README | ARCHITECTURE | TODO |
|--------|--------|--------------|------|
| sandbox_coverage | ✅ | ✅ | ✅ |
| sandbox_product | ✅ | ✅ | ✅ |
| sandbox_target | ✅ | ✅ | ✅ |
| sandbox_web | ✅ | ✅ | ✅ |

### Distribution / Packaging Modules

| Module | README | Notes |
|--------|--------|-------|
| sandbox_cleanup_cli_dist | ✅ | Distribution packaging for cleanup CLI |
| sandbox_cleanup_docker | ✅ | Docker packaging for cleanup CLI |

### Test Modules

Test modules (`sandbox_*_test`) intentionally do not carry their own `README.md` — they are
companion modules to the corresponding plugin. The following have dedicated test documentation:

| Module | TESTING.md | TODO_TESTING.md |
|--------|------------|-----------------|
| sandbox_common_test | ✅ | N/A |
| sandbox_junit_cleanup_test | ✅ | ✅ |

### Feature Modules

All feature modules have complete internationalization (English + German):

| Feature Module | feature.properties | feature_de.properties |
|----------------|-------------------|----------------------|
| sandbox_cleanup_application_feature | ✅ | ✅ |
| sandbox_css_cleanup_feature | ✅ | ✅ |
| sandbox_encoding_quickfix_feature | ✅ | ✅ |
| sandbox_extra_search_feature | ✅ | ✅ |
| sandbox_functional_converter_feature | ✅ | ✅ |
| sandbox_int_to_enum_feature | ✅ | ✅ |
| sandbox_jface_cleanup_feature | ✅ | ✅ |
| sandbox_junit_cleanup_feature | ✅ | ✅ |
| sandbox_method_reuse_feature | ✅ | ✅ |
| sandbox_platform_helper_feature | ✅ | ✅ |
| sandbox_tools_feature | ✅ | ✅ |
| sandbox_triggerpattern_feature | ✅ | ✅ |
| sandbox_usage_view_feature | ✅ | ✅ |
| sandbox_use_general_type_feature | ✅ | ✅ |
| sandbox_xml_cleanup_feature | ✅ | ✅ |

### Root-Level Documentation

| File | Status | Purpose |
|------|--------|---------|
| README.md | ✅ | Main project documentation with comprehensive index |
| CONTRIBUTING.md | ✅ | Contribution guidelines, build instructions, release process |
| CODE_OF_CONDUCT.md | ✅ | Community guidelines |
| SECURITY.md | ✅ | Security policy and vulnerability reporting |
| LICENSE.txt | ✅ | Eclipse Public License 2.0 |
| BUILD_ACCELERATION.md | ✅ | Maven profile and parallel build documentation |
| GITHUB_ACTIONS.md | ✅ | GitHub Actions workflow documentation |

### Specialized Documentation

| File | Location | Purpose |
|------|----------|---------|
| TRIGGERPATTERN.md | sandbox_common/ | Pattern matching engine documentation |
| TESTING.md | sandbox_common_test/ | HelperVisitor API test suite guide |
| TESTING.md | sandbox_junit_cleanup_test/ | JUnit migration test organization |
| TODO_TESTING.md | sandbox_junit_cleanup_test/ | Implementation tracking for JUnit migration |
| COMPARISON-PROCESS.md | docs/ | Mining pipeline comparison workflow |
| cleanup-cli.md | docs/ | Cleanup CLI usage guide |
| docker.md | docs/ | Docker deployment guide |
| maven-plugin.md | docs/ | Maven plugin usage guide |

---

## Known Gaps

The following modules have a README but are missing ARCHITECTURE.md or TODO.md. These are primarily
plain Maven modules or bridges where a full ARCHITECTURE document is less critical, but could be
added as the modules mature:

| Module | Missing |
|--------|---------|
| sandbox_common_core | TODO.md |
| sandbox-ast-api | ARCHITECTURE.md |
| sandbox-ast-api-jdt | ARCHITECTURE.md, TODO.md |
| sandbox-benchmarks | ARCHITECTURE.md, TODO.md |
| sandbox-functional-converter-core | ARCHITECTURE.md, TODO.md |
| sandbox-jgit-server-webapp | ARCHITECTURE.md, TODO.md |
| sandbox-jgit-storage-hibernate | ARCHITECTURE.md, TODO.md |
| sandbox-maven-plugin | ARCHITECTURE.md, TODO.md |
| sandbox_mining_cli | ARCHITECTURE.md, TODO.md |
| sandbox_mining_core | ARCHITECTURE.md |

---

## Maintenance Guidelines

### When Adding New Plugins

1. **Required Documentation:**
   - Create `README.md` with overview, features, and usage
   - Create `ARCHITECTURE.md` with design and implementation details
   - Create `TODO.md` with pending tasks and enhancements
   - Add navigation headers linking to main README and siblings

2. **Required Feature Module Files:**
   - Create `feature.properties` with English description
   - Create `feature_de.properties` with German translation
   - Update `build.properties` to include the properties files
   - Ensure `feature.xml` references the properties file with `%description`, `%copyright`, etc.

3. **Update Main README:**
   - Add plugin to the Projects section
   - Include plugin in the Table of Contents

4. **Update this inventory** to reflect the new module.

### When Updating Documentation

1. **Update the "Last Updated" date** at the top of this file
2. **Verify links** after restructuring or moving files
3. **Update both EN and DE** feature.properties when changing feature descriptions
4. **Keep TODO.md current** by moving completed items to ARCHITECTURE.md or removing them

---

## Recent Changes

### 2026-06-27: Documentation Audit

**Added:**
- Created `sandbox_mining_core/README.md`
- Created `sandbox_mining_cli/README.md`
- Created `sandbox-maven-plugin/README.md`
- Created `sandbox-ast-api-jdt/README.md`
- Created `sandbox-jgit-storage-hibernate/README.md`
- Created `sandbox-jgit-server-webapp/README.md`
- Fixed outdated Tycho version reference in `pom.xml` (5.0.1 → 5.0.2)
- Updated this inventory to include all modules added since 2026-01-11

**Modules added to inventory (not previously tracked):**
- sandbox_css_cleanup (with feature and test counterparts)
- sandbox_int_to_enum (with feature and test counterparts)
- sandbox_use_general_type (with feature and test counterparts)
- sandbox_common_core
- sandbox_test_commons
- sandbox-ast-api, sandbox-ast-api-jdt
- sandbox-benchmarks
- sandbox-functional-converter-core
- sandbox-jgit-server-webapp, sandbox-jgit-storage-hibernate
- sandbox-maven-plugin
- sandbox_mining_cli, sandbox_mining_core
- sandbox_cleanup_cli_dist, sandbox_cleanup_docker

### 2026-01-11: Initial Documentation Audit and Completion

**Added:**
- Created `sandbox_method_reuse_feature/feature.properties`
- Created `sandbox_method_reuse_feature/feature_de.properties`
- Updated `sandbox_method_reuse_feature/build.properties`
- Created this inventory document (DOCUMENTATION_INVENTORY.md)
