# Java 25 reconstruction qualification

These are new results from the reconstructed Issue #1657 checkout. No result from
the lost, unpublished implementation is used as qualification of this source.
The restored migration is commit `0268465a686ab8f076dc2805e44b0c04dcf17a9b`.
The configuration and test-source hashes in [source-identities.json](source-identities.json)
match `638a2a39fdcc5593693eef1dd7bfcdcab20bd251` byte for byte. Tests ran while
other mathematics source was being reconstructed on the shared branch; this
receipt does not claim a complete reactor build of that checkpoint.

Readable log copies have trailing whitespace and surplus final blank lines
removed. [raw-process-output.tar.gz](receipts/raw-process-output.tar.gz) retains
the unchanged process output. JUnit XML and model snapshots are unchanged.

| Check | New result | Receipt |
| --- | --- | --- |
| Runtime consistency regression against the original Java 21 configuration | 7 expected failures before migration | [red log](receipts/runtime-policy-red.log) |
| Four runtime/baseline/compiler/CI policy classes on Temurin 25.0.2+10 | 26 passed, no failures or skips | [log](receipts/runtime-policy-temurin25.log) |
| The same freshly compiled policy classes on the pinned JustJ runtime | 26 passed, no failures or skips | [JUnit XML](receipts/TEST-runtime-policy-justj25.xml) |
| Actual Maven/JaCoCo/Tycho argument composition | 60 passed, no failures or skips | [JUnit XML](receipts/TEST-vm-model.xml) |
| Actual Java 21 rejection by the POSIX CLI, Make and Maven Enforcer; Java 25 acceptance by Make | 4 passed, no failures or skips | [JUnit XML](receipts/TEST-runtime-entrypoints.xml) |
| Oomph stale-receipt regressions before the freshness fix | 2 expected failures | [red log](receipts/oomph-receipts-red.log) |
| Standalone Oomph Maven `verify` after the fix | 7 passed; the one opt-in native integration test skipped | [JUnit XML](receipts/TEST-oomph-receipts.xml), [Maven log](receipts/oomph-receipts-green.log) |

## JustJ identity

The immutable p2 repository is
`https://download.eclipse.org/justj/jres/25/updates/release/25.0.4.v20260826-0822`.
The Linux x86-64 fragment was downloaded directly from its `plugins/` directory:
`org.eclipse.justj.openjdk.hotspot.jre.full.linux.x86_64_25.0.4.v20260826-0822.jar`.
Its extracted `jre/bin/java` actually ran the 26 tests and reports
`25.0.4.1+1-LTS`; see [the runtime properties](receipts/justj25-runtime.log).
The fragment SHA-256 is
`bbbd27989fd446e9718ec7da5f24cd8523b2e25a15f4705a9eeba49f2b9387a8`;
`content.jar` SHA-256 is
`f1a52a2d70f5fb98d8de1c5c0b11d21bf4cf1e4d8651f28f9a42bd3e88ac7cc6`.

This qualifies the actual downloaded runtime against these policies. It does not
replace materializing and starting the assembled product with that runtime.
The compilation target continues to use the standard JavaSE-25 execution
environment; JustJ remains the product runtime supplied by the director.

## VM-argument qualification

The [Java/JUnit harness](harness/TychoVmArgumentsQualificationTest.java.txt) uses
Maven 3.9.16's `ModelBuilder` on byte-for-byte copies of the root, mathematics-test
and help-test POMs. The 60 cases cover three OS activation models, five module/profile
combinations, JaCoCo enabled/disabled, and paths with/without spaces.

For coverage cases it executes the actual JaCoCo 0.8.15 `AgentMojo` against the
effective Maven project. It then evaluates the argument expression obtained from
the actual Tycho 5.0.4 test-mojo descriptor using Maven's
`PluginParameterExpressionEvaluator`. It parses that result with the Plexus
parser called by the pinned Tycho implementation; the extracted
[bytecode](receipts/tycho-splitArgLine-bytecode.txt) records that call.

Assertions compare the complete non-agent token list and the exact coverage-agent
token. They check the macOS flag, screenshot and repository paths, headless-probe
settings, timeout, locale, time zone and encoding. The archive
[vm-model-cases.tar.gz](receipts/vm-model-cases.tar.gz) retains every effective POM,
before/after argument string, parsed token list and input POM snapshot.

These are OS **model** checks executed on Linux. The Windows cases also use POSIX
fixture paths; neither Windows drive/backslash paths nor a native Windows/macOS
launch have been qualified here.

## Reproduction

The harness files are archived as `.java.txt` so they are clearly qualification
artifacts, not tests silently omitted from a production test module. Copy them
into a new scratch directory under their `.java` names before compiling.
Set `proof_root` to the repository, `proof_work` to an empty output directory,
`proof_java25` to Temurin 25.0.2+10, and `proof_maven` to Maven 3.9.16's directory.
Dependencies are the pinned jars already present in the local Maven cache:

```sh
proof_cp="$proof_work/classes:$proof_work/junit-platform-console-standalone-1.13.4.jar:$proof_maven/lib/*:$HOME/.m2/repository/org/jacoco/jacoco-maven-plugin/0.8.15/jacoco-maven-plugin-0.8.15.jar:$HOME/.m2/repository/org/jacoco/org.jacoco.core/0.8.15/org.jacoco.core-0.8.15.jar"
"$proof_java25/bin/javac" -J-Xmx256m --release 25 -cp "$proof_cp" -d "$proof_work/classes" "$proof_work/TychoVmArgumentsQualificationTest.java" "$proof_work/RuntimeEntryPointsQualificationTest.java"
"$proof_java25/bin/java" -Xmx256m -Dqualification.source="$proof_root" -Dqualification.output="$proof_work/models" -cp "$proof_cp" org.junit.platform.console.ConsoleLauncher execute --select-class TychoVmArgumentsQualificationTest --reports-dir "$proof_work/model-reports" --details summary
"$proof_java25/bin/java" -Xmx256m -Dqualification.source="$proof_root" -Dqualification.output="$proof_work/entrypoints" -Dqualification.java21="$proof_java21" -Dqualification.java25="$proof_java25" -Dqualification.maven="$proof_maven/bin/mvn" -cp "$proof_cp" org.junit.platform.console.ConsoleLauncher execute --select-class RuntimeEntryPointsQualificationTest --reports-dir "$proof_work/entrypoint-reports" --details summary
```

The entrypoint harness used real Temurin `21.0.12.1+1` and `25.0.2+10` installations.
Its four exact invocations and exit statuses are in the `receipts/*.command.txt`
files. The POSIX CLI is invoked through `sh`; this check does not establish
archive executable permissions or qualify the Windows `.bat` launcher.

The policy classes are `JavaRuntimeConsistencyTest`,
`RepositoryBaselineConsistencyTest`, `JdtCompilerVersionContractTest` and
`CiBuildScopeContractTest`, under
`sandbox_common_test/src/org/sandbox/jdt/triggerpattern/test/policy/`.
Compile these with `javac --release 25` using JUnit Console 1.13.4 and Gson 2.13.2,
then invoke the downloaded JustJ Java executable from the repository directory:

```sh
"$proof_justj/jre/bin/java" -Xmx256m -jar "$proof_work/junit-platform-console-standalone-1.13.4.jar" execute --class-path "$proof_work/policy-classes:$HOME/.m2/repository/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar" --scan-class-path --reports-dir "$proof_work/policy-reports" --details summary
mvn -B -ntp -f sandbox_oomph/pom.xml verify
```

The Oomph red invocation selected only
`OomphSetupTest#rejectsEarlierPassOrRestartWhenNativeLaunchWritesNoReceipt+readsOnlyNewReceiptAndRetainsPreviousEvidence`.
The green invocation above ran all ordinary tests. The fresh-runtime guard and
SDK publisher/digest pins remain active. Previous phase receipts are archived
before each native launch, and a launch must produce a new receipt to succeed.

## Follow-up: contributor JRE selection

The later preparation review found that the JavaSE-25 project task still used
`${jre.location-21}`. The probe had supplied that variable with its Java 25 VM,
so the earlier assertions did not catch a contributor selecting a Java 21 JDK.
The strengthened contract failed on that exact mismatch before the fix; see
[the red receipt](receipts/jre-selection/TEST-red.xml).

Oomph's [JRETask model](https://help.eclipse.org/latest/topic/org.eclipse.oomph.setup.doc/javadoc/org/eclipse/oomph/setup/jdt/JRETask.html)
declares version and location as separate required attributes. The project now
selects `${jre.location-25}`. The native probe supplies that variable in addition
to its existing Java 21 variable. The four policy classes
then passed again on JustJ: [26 tests, zero failures/skips](receipts/jre-selection/TEST-justj-green.xml).
The standalone Oomph Maven gate again reports seven passes and one opt-in native
skip: [receipt](receipts/jre-selection/TEST-oomph.xml).
[Source hashes](receipts/jre-selection/source-sha256.txt) identify this follow-up;
the earlier checkpoint hashes and receipts are retained unchanged. This does
not yet qualify the native workspace setup.

## Outstanding native gates

A fresh Oomph installation at an exact final reconstructed source commit, its
workspace SDK-classpath probe, the assembled product/distribution, and native
Windows/macOS tests remain separate required gates. None is claimed by these
unit/configuration results. The prior failed/OOM Oomph outcomes are not reused as
new launch receipts, and the old pre-reconstruction successes are not included
in this receipt set.
