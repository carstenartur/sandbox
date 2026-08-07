# Eclipse Help bundles and screenshots

Sandbox keeps installed end-user documentation separate from runtime code. Each independently installable cleanup or quick-fix family follows this structure:

```text
<family>                 runtime plug-in
<family>_help            Eclipse Help plug-in
<family>_test            tests
<family>_feature         installs runtime and Help plug-ins together
```

The runtime plug-in must not require its Help plug-in. The feature is the installation boundary that brings both bundles into Eclipse.

## Reproducing the screenshots locally

The screenshot generator is a normal Tycho/SWTBot test. It does not call GitHub, download an Actions artifact, or depend on a GitHub-specific environment variable. It writes the generated PNG files directly into the checked-out `sandbox*_help/images` directories.

Prerequisites:

- Java 21 or later;
- Maven as used for the normal Sandbox build;
- an available graphical display;
- on headless Linux, Xvfb and the same GTK runtime libraries required by the normal UI test build.

From the repository root on a graphical workstation:

```bash
mvn \
  -f sandbox_help_build/pom.xml \
  -Phelp-screenshots \
  clean verify
```

From the repository root on headless Linux:

```bash
xvfb-run \
  --auto-servernum \
  --server-args="-screen 0 1600x1200x24" \
  mvn \
  -f sandbox_help_build/pom.xml \
  -Phelp-screenshots \
  clean verify
```

`sandbox_help_build/pom.xml` is a repository-owned Maven aggregator. It contains the complete Help reactor: target platform, shared runtime bundles such as `sandbox_common`, every documented runtime/Help/feature family, and the SWTBot host and tests. This is necessary because Maven's `-am` follows Maven dependencies, while Tycho also resolves OSGi `Require-Bundle` relationships. A fresh checkout must not rely on an already installed `sandbox_common` or target artifact in the developer's local Maven repository.

The aggregator is not a second parent POM and does not alter the normal module ownership. Participating projects continue to inherit the central repository POM; the Help build POM only defines the complete, reproducible reactor for this task.

The Maven profile supplies the checkout root to the test through the standard Maven property `maven.multiModuleProjectDirectory`. The test rejects any output directory that is not recognizably the Sandbox checkout root, so it cannot silently place documentation assets in an unrelated directory.

## Determinism

The profile fixes the Eclipse locale to English and fixes the Java language, country, timezone, and file encoding. Before each capture, SWTBot sizes the active dialog to a fixed client area and captures that dialog rather than the entire desktop.

The CSS preference page normally discovers Node.js, npx, Prettier, and Stylelint asynchronously. The screenshot profile explicitly suppresses that host-dependent status probe while capturing documentation, so the generated image does not vary with locally installed command-line tools. Normal Eclipse launches retain the live availability check.

Screenshots generated on different operating systems can still differ in native window decorations, font rendering, theme, or widget metrics. The canonical committed images are therefore reproduced and compared on the documented Linux/Xvfb reference environment. Developers on Windows, macOS, or a graphical Linux desktop can still generate and inspect the same screens locally without GitHub Actions.

## Updating screenshots

1. Change the runtime UI or Help page.
2. Run the local command above.
3. Review the changed PNG files in the corresponding `*_help/images` directories.
4. Confirm that the Help page still references the expected file name.
5. Commit the runtime, Help, test, and generated-image changes together.

The `Eclipse Help screenshots` workflow runs the same Maven command under the reference Xvfb display and fails when the regenerated PNG files differ from the committed files. It is a reproducibility check, not the source of the generated documentation.

## Help-content validation

Every Help bundle must contain:

- `META-INF/MANIFEST.MF`;
- `plugin.xml` with `org.eclipse.help.toc` and, where applicable, `org.eclipse.help.contexts` contributions;
- `toc.xml`;
- local HTML pages and stylesheets;
- referenced images;
- a `build.properties` entry for every shipped documentation resource.

The normal Maven/Tycho reactor validates bundle metadata and feature resolution. Repository-level structural validation additionally checks local TOC links and image references so missing documentation resources fail before publication.
