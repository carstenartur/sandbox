# Distribution Verification

This module is the final reactor module of the `distribution` Maven profile. It verifies the assembled standalone Eclipse IDE product and the p2 update site using Java 21 APIs only.

It checks:

- consistency of Eclipse, Orbit and Bouncy Castle versions across the Maven POM, PDE target, product and Oomph setup;
- equality of the Sandbox feature sets in the product, update site and delivery-module dependencies;
- p2 metadata, referenced artifacts, sizes and available checksums;
- presence of every published Sandbox feature in the materialized product;
- duplicate singleton bundles;
- normal startup of the Eclipse IDE workbench;
- installation of the update site into a fresh Eclipse destination;
- startup of the fresh installation;
- execution of the cleanup application and compilation of its transformed Java source.

Run the complete build on Windows, Linux or macOS with:

```text
mvn -Pdistribution --batch-mode -Dtycho.localArtifacts=ignore clean verify
```

The build requires a JDK 21 and Maven. It does not invoke Bash or Python. A headless Linux machine needs an X display for the SWT workbench launch; CI supplies this with Xvfb outside Maven.
