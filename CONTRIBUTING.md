# Contributing to Sandbox

> **Navigation**: [Main README](README.md)

Contributions are welcome! This is an experimental sandbox project for testing Eclipse JDT cleanup implementations.

## How to Contribute

1. **Fork the repository** on GitHub
2. **Create a feature branch** from `main` (the default branch):
   ```bash
   git checkout -b feature/my-new-cleanup
   ```
3. **Make your changes** following the existing code structure and conventions
4. **Test your changes** thoroughly:
   ```bash
   mvn -Pjacoco verify
   ```
5. **Commit your changes** with clear commit messages:
   ```bash
   git commit -m "feat: add new cleanup for XYZ pattern"
   ```
6. **Push to your fork** and **create a Pull Request** targeting the `main` branch

## Contribution Guidelines

- Follow existing code patterns and cleanup structures
- Add comprehensive test cases for new cleanups
- Update documentation (README, architecture.md, todo.md) as needed
- Ensure SpotBugs, CodeQL, and all tests pass
- Keep changes focused and minimal

## Reporting Issues

Found a bug or have a feature request? Please [open an issue](https://github.com/carstenartur/sandbox/issues) on GitHub with:
- Clear description of the problem or suggestion
- Steps to reproduce (for bugs)
- Expected vs. actual behavior
- Eclipse and Java version information

**Note**: This project primarily serves as an experimental playground. Features that prove stable and useful may be contributed upstream to Eclipse JDT.

---

## Release Process

> **For Maintainers**: This section describes how to create and publish new releases.

The Sandbox project uses an **automated release workflow**:

1. Navigate to **Actions** → **Release Workflow** → **Run workflow**
2. Enter the release version (e.g., `1.2.2`)
3. Enter the next SNAPSHOT version (e.g., `1.2.3-SNAPSHOT`)
4. Click **Run workflow**

The workflow automatically:
- Updates all version files using `tycho-versions-plugin` (except `sandbox-functional-converter-core`)
- Builds and verifies the release
- Creates git tag and maintenance branch
- Deploys to GitHub Pages
- Generates release notes from closed issues
- Creates GitHub release
- Bumps to next SNAPSHOT version

The new release will be available at `https://carstenartur.github.io/sandbox/releases/X.Y.Z/` within a few minutes.

📖 **Detailed Release Documentation**: [GitHub Workflows README](.github/workflows/README.md#detailed-release-process)

---

## Building from Source

> **For Contributors/Developers**: This section describes how to build the project locally.

### Prerequisites

**IMPORTANT**: This project (main branch, targeting Eclipse 2025-12) requires **Java 21** or later.

The project uses Tycho 5.0.2 which requires Java 21. Building with Java 17 or earlier will fail with:
```
UnsupportedClassVersionError: ... has been compiled by a more recent version of the Java Runtime (class file version 65.0)
```

Verify your Java version:
```bash
java -version  # Should show Java 21 or later
```

### Building

#### Build Profiles

The project supports Maven profiles to optimize build speed:

| Profile | Modules Built | Use Case |
|---------|---------------|----------|
| `dev` (default) | All bundles, features, tests | Fast local development |
| `product` | + Eclipse Product (`sandbox_product`) | Building distributable product |
| `repo` | + P2 Update Site (`sandbox_updatesite`) | Building update site |
| `jacoco` | + Coverage reports | CI/Coverage builds |
| `reports` | + HTML test reports | CI/Test report builds |

#### Build Commands

| Command | Description |
|---------|-------------|
| `mvn -T 1C verify` | Quick dev build (fastest) |
| `mvn -Pproduct -T 1C verify` | Build with Eclipse product |
| `mvn -Prepo -T 1C verify` | Build with P2 update site |
| `mvn -Pproduct,repo -T 1C verify` | Full release build |
| `mvn -Pjacoco,product,repo -T 1C verify` | Full CI build with coverage |
| `mvn -T 1C -DskipTests verify` | Skip tests for local iteration |

#### Using Make (Convenience)

A Makefile is provided for easier build commands:

```bash
make dev         # Fast development build with tests
make dev-notests # Fast development build without tests
make product     # Build with product (requires xvfb for tests)
make repo        # Build with repository (requires xvfb for tests)
make release     # Full release build with coverage (requires xvfb for tests)
make test        # Run tests with coverage (requires xvfb)
make clean       # Clean all build artifacts
make help        # Show all available targets
```

**For advanced build optimization**: See [BUILD_ACCELERATION.md](BUILD_ACCELERATION.md) for detailed information on build profiles and performance optimization strategies.

### Troubleshooting

#### Build fails with `UnsupportedClassVersionError` or `TypeNotPresentException`

This error occurs when building with Java 17 or earlier:

```
TypeNotPresentException: Type P2ArtifactRepositoryLayout not present
...class file version 65.0, this version only recognizes class file versions up to 61.0
```

**Solution**: Upgrade to Java 21 or later. Verify with `java -version`.

#### Build fails with `Unable to provision` errors

This usually indicates a Java version mismatch. Check that:
1. `JAVA_HOME` is set to Java 21+
2. `java -version` shows Java 21+
3. Maven is using the correct Java version: `mvn -version`

### Using the Eclipse Product Locally

After building the project, you can run the Eclipse product with the bundled cleanup plugins:

```bash
# Navigate to the product directory
cd sandbox_product/target/products/org.sandbox.product/

# Launch Eclipse
./eclipse
```

### Using Cleanup Plugins via Command Line

You can apply cleanup transformations using the Eclipse cleanup application:

```bash
eclipse -nosplash -consolelog -debug \
  -application org.eclipse.jdt.core.JavaCodeFormatter \
  -verbose -config MyCleanupSettings.ini MyClassToCleanup.java
```

> **Note**: Replace `MyCleanupSettings.ini` with your cleanup configuration file and `MyClassToCleanup.java` with the Java file you want to process.

---

## Eclipse Version Configuration

> **For Maintainers/Contributors**: This section contains technical details about Eclipse version migration.

The Eclipse version (SimRel release) used by this project is **not centrally configured**. When updating to a new Eclipse release, you must update the version reference in **multiple files** throughout the repository.

### Files to Update

When migrating to a new Eclipse version, update the following files:

1. **`pom.xml`** (root)
   - Repository URLs in the `<repositories>` section
   - Example: `https://download.eclipse.org/releases/2025-12/`
   - Also update Orbit repository URL: `https://download.eclipse.org/tools/orbit/simrel/orbit-aggregation/2025-12/`

2. **`sandbox_target/eclipse.target`**
   - Primary Eclipse release repository URL in first `<location>` block
   - Example: `<repository location="https://download.eclipse.org/releases/2025-12/"/>`
   - Also update Orbit repository URL

3. **`sandbox_product/category.xml`**
   - Repository reference location
   - Example: `<repository-reference location="https://download.eclipse.org/releases/2025-12/" .../>`

4. **`sandbox_product/sandbox.product`**
   - Repository locations in `<repositories>` section
   - Example: `<repository location="https://download.eclipse.org/releases/2025-12/" .../>`

5. **`sandbox_oomph/sandbox.setup`**
   - P2 repository URL in the version-specific `<setupTask>` block
   - Example: `<repository url="https://download.eclipse.org/releases/2025-12"/>`

### Version Consistency Guidelines

- **Use HTTPS**: All Eclipse download URLs should use `https://` (not `http://`)
- **Use explicit versions**: Prefer explicit version URLs (e.g., `2025-12`) over `latest` for reproducible builds
- **Keep versions aligned**: All files should reference the same Eclipse SimRel version
- **Git URLs**: Use HTTPS for git clone URLs (e.g., `https://github.com/...`, not `git://`)
- **Main branch**: All Oomph setup files should reference the `main` branch, not `master`

### Current Configuration

- **Eclipse Version**: 2025-12
- **Java Version**: 21
- **Tycho Version**: 5.0.2
- **Default Branch**: `main`
