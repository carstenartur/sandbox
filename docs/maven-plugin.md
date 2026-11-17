# Sandbox Cleanup Maven Plugin

Run Eclipse JDT cleanups on Maven Java sources with the Sandbox CLI. The CLI requires Java 25. On a Linux CI runner, use GTK and a display such as Xvfb.

## Quick Start

Set `sandbox.version` to the Sandbox release published on Maven Central and add the plugin to your `pom.xml`. Maven downloads the matching CLI distribution from GitHub Releases on first use.

```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.github.carstenartur</groupId>
            <artifactId>sandbox-maven-plugin</artifactId>
            <version>${sandbox.version}</version>
            <configuration>
                <configFile>${project.basedir}/cleanup.properties</configFile>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Project sources and workspace

In version 1.3.10, the CLI needs an Eclipse workspace that already contains the project. Close Eclipse and set `SANDBOX_WORKSPACE` to that workspace before invoking Maven. With an empty workspace, the CLI reports `Skipping file outside workspace`.

The development version creates a temporary Eclipse workspace by default. For plain Maven and m2e projects it reads the module’s source roots, compiler settings and resolved dependencies for the selected scope. It imports a native Eclipse Java project directly when the CLI can use its classpath. Set `SANDBOX_WORKSPACE` to use a workspace you have already configured.

If main and test sources use different Java language levels, run `-Dsandbox.scope=main` and `-Dsandbox.scope=test` separately. `sandbox:check` and `sandbox:diff` preview edits in memory. `sandbox:apply` writes changes to the original source files.

## Goals

### `sandbox:check`

Check for cleanup changes without modifying files. Fails the build if changes
are detected (configurable with `failOnChanges`).

```bash
mvn sandbox:check -Dsandbox.configFile=cleanup.properties
```

**Default phase:** `verify`

### `sandbox:apply`

Apply cleanup transformations to source files.

```bash
mvn sandbox:apply -Dsandbox.configFile=cleanup.properties
```

### `sandbox:diff`

Show unified diff output of what changes would be made.

```bash
mvn sandbox:diff -Dsandbox.configFile=cleanup.properties
```

## Configuration

| Parameter            | Property                | Default                                | Description                                              |
|----------------------|-------------------------|----------------------------------------|----------------------------------------------------------|
| `configFile`         | `sandbox.configFile`    | —                                      | Cleanup configuration file (required)                    |
| `toolVersion`        | `sandbox.toolVersion`   | Plugin version                         | Version of the CLI tool to use                           |
| `toolSource`         | `sandbox.toolSource`    | Auto-download from GitHub              | URL or local path to tool distribution                   |
| `scope`              | `sandbox.scope`         | `both`                                 | Scope filter: `main`, `test`, `both`                     |
| `sourceDir`          | `sandbox.sourceDir`     | `${project.basedir}`                   | Source directory to process                               |
| `patchFile`          | `sandbox.patchFile`     | —                                      | Output file for unified diff patch                       |
| `reportFile`         | `sandbox.reportFile`    | —                                      | Output file for JSON report                              |
| `failOnChanges`      | `sandbox.failOnChanges` | `true`                                 | Fail build on changes (check/diff goals)                 |
| `cacheDir`           | `sandbox.cacheDir`      | `${user.home}/.sandbox-cleanup/cache`  | Directory to cache downloaded tool                       |
| `verbose`            | `sandbox.verbose`       | `false`                                | Enable verbose output                                    |

## Examples

### CI: Check + Report

```xml
<plugin>
    <groupId>io.github.carstenartur</groupId>
    <artifactId>sandbox-maven-plugin</artifactId>
    <version>${sandbox.version}</version>
    <executions>
        <execution>
            <id>cleanup-check</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <configFile>${project.basedir}/.github/cleanup-profiles/standard.properties</configFile>
                <reportFile>${project.build.directory}/cleanup-report.json</reportFile>
                <failOnChanges>true</failOnChanges>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Apply with Diff

```bash
mvn sandbox:diff -Dsandbox.configFile=cleanup.properties
mvn sandbox:apply -Dsandbox.configFile=cleanup.properties
```

### Using Local Tool Distribution

```bash
mvn sandbox:check \
    -Dsandbox.configFile=cleanup.properties \
    -Dsandbox.toolSource=/path/to/sandbox-cleanup-cli
```

## Exit Code Mapping

| CLI Exit Code | Maven Behavior (check/diff)    | Maven Behavior (apply)       |
|---------------|-------------------------------|------------------------------|
| 0             | Build succeeds                | Build succeeds               |
| 2             | `MojoFailureException` if `failOnChanges=true` | Build succeeds (info logged) |
| Other         | `MojoExecutionException`      | `MojoExecutionException`     |
