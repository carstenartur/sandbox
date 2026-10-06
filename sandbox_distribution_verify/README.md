# Distribution Verification

This module is the final reactor module of the `distribution` Maven profile. It verifies the assembled standalone Eclipse IDE product and the p2 update site using Java 25 APIs only.

It checks:

- consistency of Eclipse, Orbit and Bouncy Castle versions across the Maven POM, PDE target, product and Oomph setup;
- equality of the Sandbox feature sets in the product, update site and delivery-module dependencies;
- p2 metadata, referenced artifacts, sizes and available checksums;
- presence of every published Sandbox feature in the materialized product;
- duplicate singleton bundles;
- normal startup of the Eclipse IDE workbench;
- installation of the update site into a fresh Eclipse destination;
- startup of the fresh installation;
- execution of the cleanup application for formatter, charset-modernization and functional-converter probes, including a published 1.3.5-to-candidate aggregate upgrade, and compilation of the transformed Java sources.

Run the complete build on Windows, Linux or macOS with:

```text
mvn -Pdistribution --batch-mode -Dtycho.localArtifacts=ignore clean verify
```

The build requires a JDK 25 and Maven. It does not invoke Bash or Python. A headless Linux machine needs an X display for the SWT workbench launch; CI supplies this with Xvfb outside Maven.

The mathematics gate also runs the complete workbench suite against the packaged
adapter and retains its disposable Java 17 project only after all nine SWT cases
succeed. Build and verify the same artifacts in one sequential invocation:

```text
mvn -Pdistribution,cli-dist,swtbot --batch-mode -Dtycho.localArtifacts=ignore -Dsandbox.math.retainHeadlessProbe=true clean verify
mvn -Pdistribution --batch-mode -pl sandbox_distribution_verify -Dtycho.localArtifacts=ignore exec:java@verify-installed-mathematics
```

The second command consumes the fresh p2 installation and the retained workbench
receipt. It checks exact adapter JAR and embedded SDK hashes, then launches the
installed mathematics application for read-only analysis, explicit apply and an
idempotence analysis. Every child process has `DISPLAY` and `WAYLAND_DISPLAY`
removed. Report options, source snapshots, edit ranges and proof regions must agree;
apply must equal the reviewed preview. The project metadata and configuration must
remain unchanged. Original and generated Java are compiled with `--release 17` and
an empty dependency classpath, then compared over 64 boundary-input pairs in
separate classloaders. The evidence directory records a fresh RUNNING/FAIL/PASS
receipt, commands, reports, logs and source snapshots. A rebuilt adapter requires
a new successful workbench receipt even when its embedded SDK is unchanged.
