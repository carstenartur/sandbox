# Release-aligned LTK patch for atomic Cleanup screenshots

This optional QA path installs both the coordinated JDT UI host and the LTK
file-count fix proposed in eclipse-platform/eclipse.platform.ui#4382. It does
not alter the ordinary Sandbox product or claim upstream acceptance.

## Source and runtime identity

`.github/patched-ltk.properties` identifies one advertised fork ref, its exact
commit, the official R4_41 parent, and all three reviewed source blobs.
`PinnedLtkRuntimeIT` uses the existing JGit `PinnedGitRepository` fixture. It
rejects a moved ref, changed parent, altered patch files or unrelated changes.
SWTBot does not fetch any source repository.

The IT runs Maven/Tycho for the LTK core and its complete test bundle, preserving
logs and XML before the disposable checkout closes. All 13 new regression
methods must occur without failures, errors or skips. The bundle must retain
the Eclipse 4.41 runtime manifest contract and be newer than the exact stock
`3.16.0.v20260702-0744` artifact resolved by the existing isolated target build.

A separate p2 feature patch targets `org.eclipse.platform` exactly at
`4.41.0.v20260828-1142`: LTK is not owned by the JDT feature. Maven's existing
Tycho publisher generates the repository. Java verifies the exact three IUs,
patch scope/lifecycle/replacement, bundle bytes and the two artifact identities,
and provides SHA-256 metadata. Only then is the disposable target extended.

`provenance.json` records source, runtime and test evidence. The SWTBot host
requires `SANDBOX_LTK_PATCH_EVIDENCE` to name the generated `runtime.properties`;
it verifies the actually wired bundle version, loaded `CompositeChange` class
bytes and the two-file counting regression before the existing atomic scenarios.

## Local execution

After the existing patched-JDT build, isolated compatibility comparison and p2
publication, invoke the same Maven gate used by the atomic screenshot workflow:

```sh
mvn -B -ntp -Dsandbox.tycho.linux-only=true \
  -pl sandbox_target,sandbox_common_core,sandbox_common,sandbox_common_test -am \
  -Dtest=LtkRuntimePatchTest,LtkRuntimeMetadataTest,PinnedLtkRuntimeIT \
  -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false \
  -Dsandbox.ltk.root="$PWD" \
  -Dsandbox.ltk.output="$PWD/target/ltk-runtime-patch" \
  -Dsandbox.ltk.resolvedJars="$PWD/target/patched-jdt-ui-compatibility/resolved-jars.txt" \
  -Dsandbox.ltk.maven="$(command -v mvn)" package
export SANDBOX_LTK_PATCH_EVIDENCE="$PWD/target/ltk-runtime-patch/runtime.properties"
```

The `package` phase is required: Tycho must consume the packaged OSGi JAR of
`sandbox_common_core`, not its plain `target/classes` reactor output. Tests run
before packaging; this does not skip the selected JUnit gates.

Use a disposable checkout: this command intentionally modifies its target file.
The output directory must not already exist. The IT is explicitly selected;
normal `mvn verify` runs only the fast packaging/metadata unit tests.

## Acceptance boundary

The loaded-runtime assertion is not a live preview-label assertion. Readable
safety/source text, affected-file counts in the visible wizard, regenerated and
visually accepted PNGs, and the retained Oomph/JDT real-source scenario remain
separate acceptance steps under #1497. Existing image comparison remains strict;
no changed image becomes a baseline merely because it was generated successfully.
