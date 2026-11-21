# Upstream cleanup qualification

This Maven/JUnit scenario runs the shipped project-wide cleanup application on
the real Eclipse projects from JDT Core, JDT UI and SWT. Its inventory contains
every registered Sandbox Java cleanup, including contributions which create
problem markers without changing source. It verifies the installed registration
IDs and implementation classes and requires the combined profile to activate
every contribution.

The corpus uses the Eclipse 4.41 release matching Sandbox's target platform:

| Repository | Ref | Exact commit |
|---|---|---|
| `eclipse-jdt/eclipse.jdt.core` | `R4_41` | `6725c16c24d94c83302346dc384bb915a0f2fe1a` |
| `eclipse-jdt/eclipse.jdt.ui` | `R4_41` | `54bd63e6453a7eb5db0bfc1d60f17e9568095619` |
| `eclipse-platform/eclipse.platform.swt` | `R4_41` | `3e3b24b4691c4f18a7d5e22653fe28d4c47385d7` |

The reusable JGit fixture verifies the repository, advertised tag and commit.
Eclipse imports each actual `.project` and honors its `.classpath`, including
source exclusions and linked SWT GTK source folders. Compiler-test resources
containing deliberately invalid Java are not invented source roots. The SWT
job uses Linux GTK; native Cocoa and Windows projects are listed separately in
the evidence and are not claimed as qualified by that job.
For snippets, the job selects the repository's own `.classpath_gtk`, retaining
its platform-specific exclusions and recording that selection. The historical
OpenGL example is explicitly excluded: it is outside the upstream reactor and
requires the unavailable `org.eclipse.opengl` 0.5.0 bundle. That project is not
represented as a successful compiler qualification.

Before importing projects, the corpus installs the actual upstream test-support
bundles into its private runtime copy. Exact installable-unit versions and the
fixed Eclipse 4.41 repository are recorded with the director command and log;
the product alone does not supply the required performance, text and UI test
libraries. Their transitive dependencies are resolved by p2.

## Execute

Use Java 25. The inexpensive source/profile contract is:

```sh
./mvnw -f qa/upstream-cleanups/pom.xml test
```

With an unpacked product, a display-free inventory test additionally compiles
the actual helper and shared compiler assertions, loads its Equinox fragment,
and checks the real cleanup registrations:

```sh
./mvnw -f qa/upstream-cleanups/pom.xml \
  -Dsandbox.cleanup.product=/absolute/path/to/eclipse test
```

For a full corpus run, build the product from the same source commit and run:

```sh
dbus-run-session -- xvfb-run -a ./mvnw -f qa/upstream-cleanups/pom.xml \
  -Dsandbox.cleanup.family=core \
  -Dsandbox.cleanup.product=/absolute/path/to/eclipse \
  -Dsandbox.cleanup.productCommit=FULL_SANDBOX_COMMIT \
  -Dsandbox.cleanup.output=/absolute/path/to/fresh-evidence test
```

Repeat for `ui` and `swt`. The existing Benchmark workflow provides the same
three-family matrix through its `cleanup_upstream` input or the
`cleanup-upstream-qa` pull-request label. It builds one product, verifies its
archive and source identity, and retains evidence even when qualification fails.

## Acceptance and evidence

Every profile starts with the original source and project metadata. Conflicting
encoding, loop-target, and mathematical modes have separate profiles. The real
application performs Apply and records its report and patch. The shared
`CompilerDiagnosticAssertions` checks complete compilation units in the target
and every transitively dependent imported source project. The reverse dependency
closure comes from actual resolved `CPE_PROJECT` entries, including PDE container
resolution; unrelated projects are outside that target's diagnostic scope.
Each CU uses its own project's compiler options, with optional warnings enabled
and the original language level, release setting and error severities retained.
Additional occurrences of an existing warning also fail. An error in the original
target or any scoped caller prevents qualification of that target. Newly added
CUs are checked against an empty baseline. Compiler option changes fail, and
sources, metadata and newly created CUs are restored across the complete scope
before the next profile. Baseline state is retained only for the current target
and its callers. Binary clients and source projects not imported into this
family's workspace are not claimed as source-caller qualification.

Evidence includes the exact source checkout, runtime, profile inputs, resolved
classpath, compiler settings, source inventory, baseline diagnostic counts,
per-profile patches and generated files, marker inventories, failures, and an
incrementally written receipt. `diagnostic-project-edges.tsv` records dependency
to consumer edges; each target's `diagnostic-scope.tsv` records its scoped projects
and CU counts, and `callers/` retains each caller's source inventory, classpath and
compiler settings. Baseline error identities identify their owning CU even when
the error occurs in a caller. The display-free inventory test also exercises an
actual API change across separate Java projects, transitive scope, new caller CUs
and restoration of different project compiler options.
A green registry check or zero changed files
does not establish successful source qualification.

This gate checks Java cleanup execution and compiler preservation. Complete
upstream behavioral test suites, XML resource cleanup, CSS tooling, and separate
interactive refactorings require their own execution evidence. Existing
mathematics-specific upstream tests remain available in `qa/math-optimization`.
