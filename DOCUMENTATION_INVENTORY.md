# Documentation Inventory

**Last updated:** 2026-07-21  
**Correctness audit:** [DOCUMENTATION_AUDIT.md](DOCUMENTATION_AUDIT.md)

This inventory records where current user, contributor, architecture, roadmap, testing, and QA information lives. A missing architecture or TODO file is not treated as a missing README; the categories are tracked separately.

## Root documentation

| File | Purpose |
|---|---|
| `README.md` | Project status, installation, build quick start, module overview, and capability boundaries |
| `CONTRIBUTING.md` | Build profiles, release process, contributor workflow, and Eclipse target maintenance |
| `DOCUMENTATION_AUDIT.md` | Code-backed correctness and completeness assessment |
| `DOCUMENTATION_INVENTORY.md` | This index |
| `BUILD_ACCELERATION.md` | Maven/Tycho build profiles and performance guidance |
| `GITHUB_ACTIONS.md` | CI and GitHub Actions behavior |
| `CODE_OF_CONDUCT.md` | Community standards |
| `SECURITY.md` | Vulnerability reporting |
| `LICENSE.txt` | Eclipse Public License 2.0 |

## QA and cross-cutting architecture

| File | Purpose |
|---|---|
| `docs/qa/README.md` | QA report index |
| `docs/qa/open-pr-review-2026-07-21.md` | Final merge/closure decision for every open Sandbox and JDT-fork PR |
| `docs/qa/project-qa-2026-07-21.md` | Build, tests, CI, product, release, cleanup, CLI, and documentation assessment |
| `docs/qa/multi-file-cleanup-post-merge-qa.md` | Focused multi-file cleanup assessment |
| `docs/multi-file-cleanups.md` | Planned multi-file cleanup architecture and lifecycle |
| `docs/multi-file-cleanup-cheatsheet.md` | Implementation checklist and mechanism selection |
| `docs/COMPARISON-PROCESS.md` | Mining comparison workflow |
| `docs/cleanup-cli.md` | Cleanup CLI usage |
| `docs/docker.md` | Docker packaging and execution |
| `docs/maven-plugin.md` | Maven plug-in usage |

## Eclipse plug-in modules

| Module | README | Architecture | Roadmap/TODO |
|---|:---:|:---:|:---:|
| `sandbox_cleanup_application` | ✅ | ✅ | ✅ |
| `sandbox_common` | ✅ | ✅ | ✅ |
| `sandbox_common_core` | ✅ | ✅ | — |
| `sandbox_css_cleanup` | ✅ | ✅ | ✅ |
| `sandbox_encoding_quickfix` | ✅ | ✅ | ✅ |
| `sandbox_extra_search` | ✅ | ✅ | ✅ |
| `sandbox_functional_converter` | ✅ | ✅ | ✅ |
| `sandbox_int_to_enum` | ✅ | ✅ | ✅ |
| `sandbox_jface_cleanup` | ✅ | ✅ | ✅ |
| `sandbox_junit_cleanup` | ✅ | ✅ | ✅ |
| `sandbox_method_reuse` | ✅ | ✅ | ✅ |
| `sandbox_oomph` | ✅ | ✅ | ✅ |
| `sandbox_platform_helper` | ✅ | ✅ | ✅ |
| `sandbox_test_commons` | ✅ | ✅ | ✅ |
| `sandbox_tools` | ✅ | ✅ | ✅ |
| `sandbox_triggerpattern` | ✅ | ✅ | ✅ |
| `sandbox_usage_view` | ✅ | ✅ | ✅ |
| `sandbox_use_general_type` | ✅ | ✅ | ✅ |
| `sandbox_xml_cleanup` | ✅ | ✅ | ✅ |

## Plain Maven, bridge, service, and tooling modules

| Module | README | Architecture | Roadmap/TODO |
|---|:---:|:---:|:---:|
| `sandbox-ast-api` | ✅ | — | ✅ |
| `sandbox-ast-api-jdt` | ✅ | — | — |
| `sandbox-benchmarks` | ✅ | — | — |
| `sandbox-functional-converter-core` | ✅ | — | — |
| `sandbox-jgit-server-webapp` | ✅ | — | — |
| `sandbox-jgit-storage-hibernate` | ✅ | — | — |
| `sandbox-maven-plugin` | ✅ | — | — |
| `sandbox_mining_cli` | ✅ | — | — |
| `sandbox_mining_core` | ✅ | — | ✅ |

## Infrastructure and packaging modules

| Module | README | Architecture | Roadmap/TODO |
|---|:---:|:---:|:---:|
| `sandbox_coverage` | ✅ | ✅ | ✅ |
| `sandbox_product` | ✅ | ✅ | ✅ |
| `sandbox_target` | ✅ | ✅ | ✅ |
| `sandbox_web` | ✅ | ✅ | ✅ |
| `sandbox_cleanup_cli_dist` | ✅ | — | — |
| `sandbox_cleanup_docker` | ✅ | — | — |

## Test documentation

Most test modules are companions to their implementation module and do not need a second user README. Dedicated guides exist where the test structure is itself nontrivial:

| Module | Documentation |
|---|---|
| `sandbox_common_test` | `TESTING.md` |
| `sandbox_junit_cleanup_test` | `TESTING.md`, `TODO_TESTING.md` |
| `sandbox_xml_cleanup_test` | XML architecture/roadmap plus focused transformation test classes |

The CI-generated test inventory remains the source of truth for active versus disabled test counts. Capability wording must not claim completion for disabled “not yet implemented” cases.

## Feature modules

Every published Sandbox feature module contains localized `feature.properties` and `feature_de.properties` metadata. Feature descriptions must be updated together with the implementation module when user-visible scope changes.

Current feature families include cleanup application, CSS, encoding, extra search, functional converter, Int-to-Enum, JFace, JUnit, method reuse, platform helper, tools, TriggerPattern, usage view, general-type, and XML cleanup.

## Remaining documentation depth gaps

The following modules have user-facing README coverage but no dedicated architecture document and/or roadmap:

- `sandbox_common_core` — no standalone TODO;
- `sandbox-ast-api` — no architecture document;
- `sandbox-ast-api-jdt` — no architecture or roadmap;
- `sandbox-benchmarks` — no architecture or roadmap;
- `sandbox-functional-converter-core` — no architecture or roadmap;
- `sandbox-jgit-server-webapp` — no architecture or roadmap;
- `sandbox-jgit-storage-hibernate` — no architecture or roadmap;
- `sandbox-maven-plugin` — no architecture or roadmap;
- `sandbox_mining_cli` — no architecture or roadmap;
- `sandbox_mining_core` — no dedicated architecture document.

These are depth improvements, not missing basic module documentation. New documents should be added only when they provide maintained design or operational information rather than repeating the README.

## Maintenance rules

When a feature or build baseline changes:

1. update the module README and architecture/roadmap where applicable;
2. update root capability/version wording;
3. update English and German feature metadata;
4. link unsupported behavior to a test, issue, or explicit limitation;
5. update this inventory when files are added, removed, or renamed;
6. update the audit when a former documented gap is fixed;
7. ensure release claims match automated installation and smoke-test evidence.

A generated capability inventory and CI consistency check are tracked in issue #1219.
