# Sandbox development with Oomph

Sandbox is included in the official Eclipse Installer under **Github Projects → Sandbox Project → Main**.
The catalog points directly to [`sandboxproject.setup`](sandboxproject.setup) in this repository.
No custom catalog or manual setup-file import is required for that route.

The supported contributor baseline is **Eclipse 2026-06 / Platform 4.40, Java 21, and Tycho 5.0.4**.

## Set up a workspace

1. Start the [Eclipse Installer](https://www.eclipse.org/downloads/packages/installer) in Advanced Mode.
2. Select Eclipse Platform SDK 4.40 (2026-06), or the matching Eclipse IDE for Java Developers package, and a JDK 21.
3. On the Projects page select **Github Projects → Sandbox Project → Main**.
4. Choose installation, workspace and Git clone locations. Complete installation and let workspace setup finish.
5. Check the setup log and Problems view before developing. The active target should be **target platform for sandbox**.

The project installs JDT, PDE, EGit, m2e including its PDE integration, ASTView, JEView and PDE Spies.
Maven imports run before the remaining Eclipse project imports; the repository's target definition is then activated
and the workspace is built. The CI fixture under `.github`, root build output and Oomph's test installation are excluded from import.
The existing Sandbox working set is retained; additional dynamic sets organize Core, Tests, Help and Distribution.

An existing compatible Eclipse installation can use **File → Import → Oomph → Projects into Workspace** and select
the same catalog entry. Oomph may request an IDE restart after adding development tools.

## Develop and verify

- Open `sandbox_product/sandbox.product.launch` and run or debug it as an Eclipse Application. It selects all features
  from the versioned product, including the constant-to-enum, container, CSS and general-type cleanups.
- Run plain Java tests in their Maven modules as JUnit tests. Run plug-in test classes using **JUnit Plug-in Test**.
- The workspace compiler is useful feedback; Maven remains the release/build authority:

```sh
./mvnw clean verify
./mvnw -Pdistribution clean verify
```

Linux UI tests need a display, for example `xvfb-run --auto-servernum ./mvnw clean verify`.
The [repository formatter](../eclipse-formatter.xml) can be imported under **Java → Code Style → Formatter**.
Setup applies UTF-8 and the declared compiler/editor preferences; it does not install a hidden save-action or cleanup profile.

## Update an existing workspace

Fetch/pull the desired repository changes, then use **Help → Perform Setup Tasks** and include the import and target tasks.
The setup's existing redirection deliberately reads the project model from the local Git clone after initial installation.
Consequently, changing `main` on GitHub does not silently replace a contributor's local setup or source checkout.
Manual setup discovers missing Maven projects again. It preserves unrelated projects and does not replace
Package Explorer `dialog_settings.xml` or delete the runtime workspace.

## Entry points

| File | Purpose |
| --- | --- |
| [sandboxproject.setup](sandboxproject.setup) | Public project entry referenced by the official Github Projects catalog |
| [sandbox.setup](sandbox.setup) | Optional Sandbox SDK product, with its existing configurable heap and provisioning release |
| [sandbox-installer.setup](sandbox-installer.setup) | Optional combined configuration referencing that product and the same main project stream |
| [jdt-migration-qa.configuration.setup](jdt-migration-qa.configuration.setup) | Separate pinned upstream JDT migration QA environment |

The optional combined configuration can be opened in the Installer's Advanced Mode. It uses proper Installation/ProductVersion
and Workspace/Stream references; it is not involved in the normal official-catalog entry path.
Keep the public project URL, `sandbox` project name, `main` stream and existing task IDs stable.

## Setup verification

Fast local contract checks:

```sh
./mvnw -f sandbox_oomph/pom.xml test
```

Real provisioning and workspace acceptance test on Linux x86_64, with JDK 21 and an X display:

```sh
xvfb-run --auto-servernum ./mvnw -f sandbox_oomph/pom.xml \
  -Doomph.integration=true -Dsandbox.oomph.ref=main verify
```

Set `sandbox.oomph.ref` to the candidate branch for a setup change. Forks can also set `sandbox.oomph.repository`.
The Maven/JUnit test provisions a clean SDK, resolves the development tools with p2, validates the models with EMF/Oomph,
resolves Sandbox through the live official catalog, and executes real Oomph workspace tasks. It restarts Eclipse and repeats
manual setup after removing a project from the workspace without deleting its files. Target resolution, project imports,
Java 21, build errors, PDE launch resolution and preservation of user-owned content are checked. Reports and logs are under `target/`.
The existing Maven CI workflow includes this gate for setup, target, product and project-metadata changes.

See [Architecture](ARCHITECTURE.md), [Maintenance](TODO.md), and the
[official catalog](https://github.com/eclipse-oomph/oomph/blob/master/setups/com.github.projects.setup).
