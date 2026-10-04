# Java build runtime

Sandbox targets Java 25 and its Maven/Tycho build is supported with a **JDK 25 runtime**.

This is more restrictive than merely compiling Java sources with `--release 25`. Tycho, Eclipse JDT, and the target-platform tooling also run inside the selected JDK. A newer JDK can therefore introduce runtime incompatibilities even though it is capable of producing Java 25 bytecode.

The repository includes `.java-version` for version managers and every build-oriented Make target performs a preflight check against the Java runtime reported by Maven.

The Java 25 migration applies to development, CI, Sandbox bundles, product and
CLI execution. Update `JAVA_HOME` and the IDE's configured JRE before importing
the current checkout. Eclipse 2026-09 / Platform 4.41 and Tycho 5.0.4 remain the
named baseline; this project decision does not imply that those upstream
releases universally require Java 25. Java 21 is rejected for Sandbox builds,
and later JDKs require separate qualification.

The materialized product uses the pinned JustJ 25 repository
`https://download.eclipse.org/justj/jres/25/updates/release/25.0.4.v20260826-0822`.
Tycho resolves the ordinary `JavaSE-25` compilation environment and adds the
JustJ runtime only while materializing the product. It must not replace the
compilation environment with a custom empty execution-environment profile.

Cleanup inputs retain their actual source/compliance and library level. The
Java 21 integration fixture and frozen upstream JDT QA configuration remain
intentional, separately pinned targets; emitted code must still compile against
the selected input project's target platform.

## Verify the active runtime

```shell
mvn --version
```

The output must contain a line beginning with:

```text
Java version: 25
```

Checking `java --version` alone is not sufficient when `JAVA_HOME`, `PATH`, a shell alias, or a Maven launcher selects a different runtime.

## Fedora

Install the development package and select it for the current shell:

```shell
sudo dnf install java-25-openjdk-devel
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
mvn --version
make dev
```

When the JDK installation path differs, locate it with:

```shell
readlink -f "$(command -v javac)"
```

Set `JAVA_HOME` to the directory above `bin/javac`.

## Version managers

Tools such as jenv, asdf, and compatible environment managers can use the repository's `.java-version` file. Confirm the result with `mvn --version` before starting the build.

## Typical symptoms of the wrong runtime

Failures reported with unsupported runtimes have included:

```text
release version 25 not supported
```

and Tycho compiler failures involving the JDK runtime filesystem, such as:

```text
Cannot invoke "java.nio.file.FileSystem.getPath(...)" because "this.fs" is null
```

Use JDK 25 before investigating these as source or target-platform defects.
