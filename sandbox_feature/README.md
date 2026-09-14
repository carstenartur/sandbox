# Sandbox: one installation feature

The stable installable-unit identifier is **`sandbox_feature.feature.group`**.
The feature includes every normal Sandbox Eclipse component feature. It does not
replace component IDs, turn on cleanup options, or require patched JDT/LTK hosts.

This implementation must pass the aggregate distribution gate before the next
release attempt. Its presence on a development branch does not mean the IU has
already been published. Eclipse Marketplace updates remain deferred under #1613.

## Installation and Marketplace maintenance

After a verified release containing this feature, select **Sandbox - Complete
Cleanups and Tools** in Eclipse's Install New Software dialog. For Marketplace,
configure the single feature IU above and the existing release repository:

```text
https://carstenartur.github.io/sandbox/releases/
```

The listing no longer needs one installable-unit entry per component. New
components are added to `feature.xml` and the normal delivery metadata in this
repository, not to the Marketplace listing. The metadata regression test detects
an omitted component. Advanced users can still install the original features
individually from the same repository.

The dependency direction is **aggregate includes components**. Components do not
require their parent; a Maven parent or p2 category would not provide this
installation behavior.

## Included features

All 16 existing top-level Eclipse feature IDs are retained:

| Feature ID | Capability |
| --- | --- |
| `sandbox_cleanup_application_feature` | Headless cleanup application and Help |
| `sandbox_container_cleanup_feature` | Container cleanup and Help |
| `sandbox_css_cleanup_feature` | CSS cleanup and Help |
| `sandbox_encoding_quickfix_feature` | Explicit encoding cleanup and Help |
| `sandbox_extra_search_feature` | Additional search tools |
| `sandbox_functional_converter_feature` | Functional loop conversion and Help |
| `sandbox_int_to_enum_feature` | Int-to-Enum conversion and Help |
| `sandbox_jface_cleanup_feature` | JFace modernization and Help |
| `sandbox_junit_cleanup_feature` | JUnit migration and Help |
| `sandbox_method_reuse_feature` | Method reuse and Help |
| `sandbox_platform_helper_feature` | Platform helpers and Help |
| `sandbox_tools_feature` | Iterator conversion and Help |
| `sandbox_triggerpattern_feature` | Code patterns, hint DSL and Help |
| `sandbox_usage_view_feature` | Usage View |
| `sandbox_use_general_type_feature` | Type generalization and Help |
| `sandbox_xml_cleanup_feature` | PDE/XML cleanup and Help |

Component features retain ownership of their plug-ins, Help bundles, licenses
and platform constraints. Shared runtime dependencies such as `sandbox_common`
remain transitive. Test fragments, standalone server tooling and optional
patched-host distributions are not aggregate members. The nested feature.xml in
an XML-cleanup test fixture is not an end-user feature.

## Existing individual installations

A previous individual-feature installation has several explicit p2 roots.
Installing the aggregate should not be confused with automatically removing those
old roots. The automated migration check replaces the previous Sandbox component
roots with the aggregate in **one p2 director transaction**, using `-uninstallIU`
for the old root/version pairs and `-installIU` for the new aggregate/version.
Never split that operation into uninstall and install runs. Other installed
software and workspace data are not removed by the test.

The regression uses the published **1.3.4** component repository and the currently
supported stock Eclipse baseline. Older Eclipse hosts still need to meet the new
release's documented Eclipse/Java requirements; the aggregate does not claim
compatibility with every historical host.

## Verification

Use the existing build, with Java 21 and a desktop display (Xvfb on Linux):

```sh
xvfb-run --auto-servernum mvn -Pdistribution clean verify
```

No additional workflow or separate test framework is required. The existing
`DistributionVerifier` still executes its complete checks. A second Maven-bound
Java verification then performs:

1. Stock SDK provisioning, followed by installation of only the aggregate as a
   Sandbox root; exact component versions are read from the built p2 repository.
2. Update to a private higher-version aggregate fixture. The fixture changes only
   aggregate metadata and retains the actual built component requirements. It
   tests the p2 update path, not compatibility with hypothetical future components.
3. Published 1.3.4 individual roots followed by the one-transaction aggregate
   migration, with no missing, duplicated or stale component feature versions.

Each resulting installation is checked through its actual current p2 profile,
not cached feature folders. The stock JDT/LTK host hashes must stay unchanged.
A temporary Equinox probe resolves the expected bundles, instantiates registered
cleanups and checks Help TOC content against the source inventory. The probe is
not published and its configuration insertion is reverted byte-for-byte. A real
packaged cleanup is executed and its output is compiled in all three scenarios.

Evidence is retained under `target/distribution-verification/aggregate/`, including
profiles, commands, logs, runtime JSON and `verification.properties`. Acceptance
requires **both** the original distribution evidence and this aggregate report to
pass, as well as a successful Maven exit. An original `verification.json` alone
is not sufficient if the subsequent aggregate gate fails.

Source membership/version/delivery checks are in `SandboxAggregateFeatureTest`.
`AggregateInstallationEvidenceTest` covers profile parsing and negative cases
including missing/stale components, duplicates and extra roots.

## Eclipse references

- [Included features](https://help.eclipse.org/latest/topic/org.eclipse.pde.doc.user/guide/tools/editors/feature_editor/included_features.htm)
- [Feature metadata](https://help.eclipse.org/latest/topic/org.eclipse.pde.doc.user/tasks/pde_p2_featuremetadata.htm)
- [p2 director](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/p2_director.html)
- [p2 publisher](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/p2_publisher.html)
