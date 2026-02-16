# Sandbox Cleanup Maven Plugin

Maven plugin for running Sandbox Eclipse JDT cleanup transformations on any
Maven project.

## Quick Start

Add the plugin to your `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.sandbox</groupId>
            <artifactId>sandbox-maven-plugin</artifactId>
            <version>1.2.6-SNAPSHOT</version>
            <configuration>
                <configFile>${project.basedir}/cleanup.properties</configFile>
                <toolVersion>1.2.6-SNAPSHOT</toolVersion>
            </configuration>
        </plugin>
    </plugins>
</build>
```

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
| `toolVersion`        | `sandbox.toolVersion`   | `1.2.6-SNAPSHOT`                       | Version of the CLI tool to use                           |
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
    <groupId>org.sandbox</groupId>
    <artifactId>sandbox-maven-plugin</artifactId>
    <version>1.2.6-SNAPSHOT</version>
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
