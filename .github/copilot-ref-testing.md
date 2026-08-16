# Testing Reference

> **Read this when**: You are writing tests, fixing test failures, or debugging CI.

## Running Tests

Most tests are Eclipse plugin tests requiring Xvfb:

```bash
# Set Java 21 first!
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# Run all tests
xvfb-run --auto-servernum mvn -Pjacoco verify

# Run specific test class
xvfb-run --auto-servernum mvn test -Dtest=Java22CleanUpTest -pl sandbox_functional_converter_test

# Run specific test method
xvfb-run --auto-servernum mvn test -Dtest=Java22CleanUpTest#testSimpleForEachConversion -pl sandbox_functional_converter_test
```

## Exception: Tests That Don't Need Xvfb

| Module | Why |
|--------|-----|
| `sandbox_common_test` | Pure JUnit 5 + standalone ASTParser |
| `sandbox-functional-converter-core` | Standard Maven JAR, no Eclipse |
| `sandbox_mining_core` | Standard Maven JAR, no Eclipse |

```bash
mvn test -pl sandbox_common_test          # No xvfb needed
mvn test -pl sandbox-functional-converter-core
mvn test -pl sandbox_mining_core
```

**Prefer these faster paths** when working on code in these modules.

## Repository Policy Tests

Maven/JUnit is the executable authority for repository-policy checks. Run the focused gate locally with:

```bash
mvn -pl sandbox_common_test \
  -Dtest='RepositoryPolicy*Test,PinnedGitRepositoryTest' test
```

A normal root `mvn verify` also executes these tests. The gate:

- compares the current change slice with the configured Git base through JGit;
- reports changed text lines per file and rejects a slice above 1,500 lines;
- rejects every newly added tracked `.py` file;
- rejects newly added Python or `actions/setup-python` workflow invocations, including additions to legacy-allowlisted workflows;
- requires the existing Python file/workflow allowlists to shrink when legacy automation is removed;
- verifies upstream test repositories through a pinned JGit fixture without command-line Git or Python.

For a local branch, set an exact base when it cannot be inferred from `origin/main`:

```bash
mvn -pl sandbox_common_test \
  -Drepository.policy.base=refs/remotes/origin/main test
```

A justified exception must be documented in the PR body before implementation continues:

```markdown
## Repository policy exception

Explain why the change cannot be split, which generated/binary content is involved,
which alternatives were considered, and how reviewers can validate the slice.
```

The explanation must contain at least 80 non-whitespace characters. Editing the allowlist cannot authorize new Python automation; additions are checked against the JGit base independently.

## SWTBot UI Tests

```bash
xvfb-run --auto-servernum mvn verify -Pswtbot -pl sandbox_usage_view_test
```

Excluded from default build. Tests verify actual UI interactions.

## Test Conventions

- JUnit 5 (`org.junit.jupiter.api.*`)
- Test classes end with `Test` suffix
- `@Test`, `@BeforeEach`, `@AfterEach` annotations
- `assertThrows()` instead of `@Test(expected=...)`
- Test modules end with `_test`
- Use `@EnumSource` or `@ValueSource` for parameterized tests
- `@Disabled` for future features (with explanation)

## CI Log Analysis — CRITICAL

**YOU CANNOT RELIABLY RUN ECLIPSE PLUGIN TESTS LOCALLY** in the agent environment. When tests fail:

1. **ALWAYS check CI logs** — never guess expected output
2. Look for `expected:` vs `but was:` sections
3. Copy the exact output from CI

```bash
# Download and search CI log
curl -s "<log_url>" > /tmp/ci_log.txt
grep -B 5 -A 50 "expected:" /tmp/ci_log.txt
```

## Common Test Failure Causes

| Symptom | Cause | Fix |
|---------|-------|-----|
| `UnsupportedClassVersionError` | Wrong Java version | Set Java 21 |
| Display/Xvfb errors | Missing display server | Use `xvfb-run --auto-servernum` |
| Import order mismatch | Eclipse ImportRewrite behavior | Check CI log for actual order |
| Whitespace/tab differences | Eclipse formatter quirks | Check CI log, normalize in test |
| `\r\n` vs `\n` | CRLF line endings | Normalize with `.replace("\r\n", "\n")` |
| NLS comment mismatch | Stale `//$NON-NLS-n$` | Use `replaceAndRemoveNLS()` |

## Test Pattern Enums (Encoding Tests)

All test pattern enums in `sandbox_encoding_quickfix_test` MUST have:
- `String given` and `String expected` fields
- `boolean skipCompileCheck` field
- Two constructors (2-arg defaulting `skipCompileCheck=true`, 3-arg explicit)

## Test Expected Output Rules

1. **Never guess** — always check CI log
2. Expected output must exactly match cleanup output (imports, whitespace, NLS)
3. Don't modify tests back and forth — fix root cause ONCE
4. Given input must compile when using `FullCompileCheck`
