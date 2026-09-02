# Sandbox Eclipse Help SWTBot tests

This standalone Eclipse test plug-in owns cleanup-independent Workbench scenarios used to verify and generate the installed Sandbox Help screenshots.

It is deliberately **not** a fragment of a product plug-in. The module may depend on the runtime bundles whose real UI it exercises, but no product bundle depends on this test bundle.

## Ownership boundary

This module contains:

- Eclipse Help structure and screenshot-evidence tests;
- cleanup-profile and real Cleanup-preview SWTBot drivers;
- pinned-workspace screenshot scenarios and provenance checks;
- the PDE XML Problems-view and Quick-Fix scenario;
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
