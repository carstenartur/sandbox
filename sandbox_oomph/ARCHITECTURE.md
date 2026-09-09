# Oomph setup architecture

The contributor baseline is Eclipse 2026-06 / Platform 4.40, Java 21 and Tycho 5.0.4.
This directory contains Oomph models and a standalone Maven/JUnit acceptance test, not an Eclipse cleanup plug-in.

## Public integration contract

The official `com.github.projects.setup` catalog references the root Project in
`https://raw.githubusercontent.com/carstenartur/sandbox/main/sandbox_oomph/sandboxproject.setup`.
Keep that address, project name `sandbox`, stream `main`, Git task ID `git.clone.sandbox` and target task ID `sandbox.target` stable.
Existing workspaces also refer to these objects. The custom Product and combined Configuration are optional entry points.

The Project owns development-tool requirements because an official catalog user may choose a standard Eclipse product.
The optional Product supplies an SDK and personal installation preferences. Both routes use the same Project.
The combined Configuration references a ProductVersion through its Installation and a Stream through its Workspace.
The separately pinned upstream JDT QA models retain their own product, repositories and stream.

## Workspace task ordering

1. GitCloneTask locates or clones Sandbox.
2. MavenImportTask imports Maven projects, including modules without committed Eclipse metadata.
3. ProjectsImportTask discovers remaining Eclipse projects.
4. TargetPlatformTask activates `sandbox_target/eclipse.target` by its existing target name.
5. ProjectsBuildTask builds the workspace.

Explicit predecessor references enforce the import/target/build order. Maven import must precede Eclipse import:
Oomph's Maven task skips STARTUP import when any project from a source locator is already in the workspace.
MANUAL setup runs Maven discovery again and restores missing projects.
An explicit second Maven source locator imports this standalone verification module because m2e follows root-POM modules.
Maven discovery excludes the root artifact `central`; Eclipse import keeps its existing project name `sandbox`, avoiding
two differently named projects at the same repository location.
The two import tasks exclude `.git`, `.github`, root `target` content and the test installation under `sandbox_oomph/target`.

The repository target remains the source of workspace dependencies. Installing development tools into the host IDE is a
separate p2 operation; it must not replace the target with the running platform. Baseline changes must update the target,
root build, products, project tool repository, capability inventory and active documentation together.

## Existing workspaces

The existing `oomph.redirection.sandbox` property redirects the remote project model to its local Git copy.
Contributors update that copy before running manual setup. Neither branch switching nor user-content deletion is added.
Working sets are predicate based. Setup does not replace Package Explorer metadata, and semantic save actions remain explicit.
Formatter import is documented using the repository's existing formatter file.

## Acceptance tests

`sandbox_oomph/pom.xml` is independent of the Tycho reactor so a setup check does not first require a built Sandbox IDE.
JUnit owns the assertions and child-process lifecycle. The integration test downloads a fixed Eclipse SDK 4.40,
installs Oomph and the actual project's p2 requirements, and compiles a small test-only Eclipse application.
The application runs the real SetupTaskPerformer in a workbench, including JGit clone, m2e/PDE imports, target activation,
working sets and build tasks. It validates the optional configurations with Oomph's registered EMF packages and checks
the development launch with PDE's bundle resolver.
It uses Oomph's standard scope locations and honors requested IDE restarts before asserting workspace completion.
Three consecutive manual passes also cover generated manifests with a warmed PDE
model; each pass removes and restores the verification project and preserves user content.
Before checking build markers, it joins PDE's classpath-update job family and the
workspace builds those updates schedule until both have settled. It repeats that
barrier after saving the workspace and reading its target, since those operations
can enqueue another build. Failed checks
include generated-bundle manifest/model state and pending-job diagnostics.
The disposable batch installation supplies the wizard's license-confirmation callback; the public setup retains normal interactive license confirmation.
During task execution it also temporarily uses p2's existing director batch UI service for signed content from
`download.eclipse.org` and `archive.eclipse.org`. The one unsigned legacy dependency, `jakarta.xml.bind`
`2.3.3.v20201118-1818`, is accepted only when its classifier, ID, version and SHA-512 match the published
[Eclipse WTP R3.41.0 artifact metadata](https://download.eclipse.org/webtools/downloads/drops/R3.41.0/R-3.41.0-20260225091541/repository/artifacts.xml.xz).
Unexpected or modified unsigned artifacts fail the test. This prevents unattended signer/origin dialogs in the test workbench;
the original service is restored afterward and no persistent trust-all preference is written. A timed-out Eclipse
process records a JVM thread dump before termination so a future stall retains its blocking call stack.

A second Eclipse process reopens the same workspace and verifies MANUAL setup, recovery of a removed project,
and preservation of a separately created user project. No replacement Oomph parser, mock importer or Python test framework is used.
The test redirects the official catalog's Sandbox URL to the candidate model and selects the candidate Git ref only in the test.
Production models retain the public URL and main stream. CI includes this check in the existing Maven verification gate.

The acceptance test currently runs on Linux x86_64. Cross-platform installer interaction and the separate upstream JDT QA
scenario remain additional release checks; the workspace setup itself uses platform-independent Oomph tasks and paths.
