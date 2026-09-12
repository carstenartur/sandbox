# Sandbox Eclipse Help SWTBot tests

This standalone Eclipse test plug-in owns cleanup-independent Workbench scenarios used to verify and generate the installed Sandbox Help screenshots.

It is deliberately **not** a fragment of a product plug-in. The module may depend on the runtime bundles whose real UI it exercises, but no product bundle depends on this test bundle.

## Ownership boundary

This module contains:

- Eclipse Help structure and screenshot-evidence tests;
- cleanup-profile and real Cleanup-preview SWTBot drivers;
- pinned-workspace screenshot scenarios and provenance checks;
- patched-JDT atomic-preview scenarios.

`sandbox_usage_view_test` contains only tests of `sandbox_usage_view` itself.

## Reproduce the normal Help screenshots

From the repository root on a graphical workstation:

```bash
./mvnw \
  -Dtycho.localArtifacts=ignore \
  -f sandbox_help_build/pom.xml \
  -Phelp-screenshots \
  clean verify
```

On headless Linux, run the same Maven command under Xvfb. The dedicated Help build aggregator supplies all runtime, Help and feature bundles required by the Workbench scenarios.

The screenshot tests do not clone repositories. Pinned upstream files are provisioned before Eclipse starts and passed into the test through explicit properties or environment variables.

## Shared Java scenario lifecycle

`CleanupScreenshotScenario` declares an immutable source selection and the preview
contract: `FILE_COMBINED_DIFF` for stock LTK or `ATOMIC_CANDIDATE` for coordinated
migrations. Invalid or missing source selections fail before the UI scenario runs.
These declarations do not by themselves prove any UI behavior or upstream identity.

`CleanupWorkbenchDriver` and the Java-only `CleanupScenarioRunner` now share the
source-inventory and Undo lifecycle for these existing scenario entry points:

| Scenario | Contract | State when the existing callback returns |
|---|---|---|
| JFace file selection | `FILE_COMBINED_DIFF` | The multi-file operation was undone; one aggregate Undo must still restore `SingleFileCleanup.java`. |
| Method Reuse | `FILE_COMBINED_DIFF` | The callback already applied and undid the transformation. |
| Int-to-Enum | `ATOMIC_CANDIDATE` | The callback already checked deselection, coordinated Apply and Undo. |

The driver snapshots **every Java file in the disposable project** using its raw
bytes before and after the callback and, when needed, after aggregate Undo. At
those checkpoints it rejects unexpected changes, added or deleted Java files,
missing Undo history and incomplete restoration. It never repairs source contents behind a
failing assertion. Undo history is cleared before execution and after the callback
and restoration checks, including their failure paths.

The existing concrete scenarios still own profile configuration, navigation,
selection, semantic diff assertions, intermediate Apply/Undo, clipping checks and
screenshot capture. Their UI assertions and image baselines are not replaced or
weakened by the lifecycle extraction. JUnit's `CleanupScreenshotScenarioTest`,
`CleanupSourceSnapshotTest` and `CleanupScenarioRunnerTest` cover the new contracts
in the normal Maven/Tycho test run; they need no SWTBot interaction or network access.

### Remaining upstream boundary (#1497)

This is the first lifecycle extraction, not the completed cleanup-independent
navigation driver. Profile/preview navigation still needs to be extracted from
the concrete scenarios. The descriptor must then be extended with exact retained
Oomph-workspace identity, the corresponding headless plan and validated screenshot
provenance before adding the first pinned upstream Java scenario. No screenshot
in this change is newly claimed to originate from an Oomph-provisioned JDT checkout.
