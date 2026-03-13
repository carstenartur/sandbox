# Build & CI Reference

> **Read this when**: You need to build, run CI, fix build failures, or understand Maven profiles.

## Java 21 — REQUIRED

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
java -version  # Must show "21"
```

**Why**: Tycho 5.0.1 and Eclipse 2025-12 require Java 21 (class file version 65.0).
If you see `UnsupportedClassVersionError`, you're using Java 17 instead of 21.

## Maven Profiles

| Profile | Description | Command |
|---------|-------------|---------|
| `dev` (default) | Fast — no Product/Updatesite | `mvn verify` |
| `product` | With Eclipse Product | `mvn -Pproduct verify` |
| `repo` | With P2 Update Site | `mvn -Prepo verify` |
| `jacoco` | With Code Coverage | `mvn -Pjacoco verify` |
| `web` | With WAR file | `mvn -Dinclude=web verify` |
| `swtbot` | With SWTBot UI tests | `mvn -Pswtbot verify` |

**Parallel builds**: `mvn -T 1C verify` (1 thread per CPU core)

**Full build**: `mvn -Pproduct,repo,jacoco -T 1C verify`

## Makefile Shortcuts

```bash
make dev          # Fast build with tests
make dev-notests  # Fast build without tests
make product      # Build with Eclipse Product
make release      # Full release build
make test         # Tests with coverage
make clean        # Clean artifacts
```

## Code Quality Tools

- **SpotBugs**: Runs during compile, fails build on issues. Config: `../spotbugs-exclude.xml`
- **JaCoCo**: Coverage reports in `<module>/target/site/jacoco/` and aggregated in `sandbox_coverage/`
- **CodeQL**: Security scanning in CI
- **Codacy**: Automated code review

## Build Outputs

- Product: `sandbox_product/target/products/`
- WAR: `sandbox_web/target/`
- Coverage: `sandbox_coverage/target/site/jacoco-aggregate/`

## Target Platform

Defined in `sandbox_target/eclipse.target`:
- Eclipse 2025-12
- Eclipse Orbit, JustJ, EGit dependencies
- Tycho resolves from P2 repositories, NOT Maven Central

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| `UnsupportedClassVersionError: class file version 65.0` | Java 17 active | Set `JAVA_HOME` to temurin-21 |
| Tycho resolution errors | Target platform issue | Check `sandbox_target/eclipse.target` |
| SpotBugs failures | Code quality issue | Check `spotbugs-exclude.xml` |
| Unused import errors | Tycho treats as errors | Remove all unused imports |
| Missing NLS comments | Eclipse plugin requirement | Add `//$NON-NLS-1$` to string literals |