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
