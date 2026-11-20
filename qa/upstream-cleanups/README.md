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
`CompilerDiagnosticAssertions` checks complete compilation units, including
dependent callers in the selected project, with optional compiler warnings
enabled and the original language level retained. Additional occurrences of an
existing warning also fail. A project with compiler errors before cleanup is
reported as an unresolved baseline and cannot provide a passing qualification.

Evidence includes the exact source checkout, runtime, profile inputs, resolved
classpath, compiler settings, source inventory, baseline diagnostic counts,
per-profile patches and generated files, marker inventories, failures, and an
incrementally written receipt. A green registry check or zero changed files
does not establish successful source qualification.

This gate checks Java cleanup execution and compiler preservation. Complete
upstream behavioral test suites, XML resource cleanup, CSS tooling, and separate
interactive refactorings require their own execution evidence. Existing
mathematics-specific upstream tests remain available in `qa/math-optimization`.
