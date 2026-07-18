# Java build runtime

Sandbox targets Java 21 and its Maven/Tycho build is supported with a **JDK 21 runtime**.

This is more restrictive than merely compiling Java sources with `--release 21`. Tycho, Eclipse JDT, and the target-platform tooling also run inside the selected JDK. A newer JDK can therefore introduce runtime incompatibilities even though it is capable of producing Java 21 bytecode.

The repository includes `.java-version` for version managers and every build-oriented Make target performs a preflight check against the Java runtime reported by Maven.

## Verify the active runtime

```shell
mvn --version
```

The output must contain a line beginning with:

```text
Java version: 21
```

Checking `java --version` alone is not sufficient when `JAVA_HOME`, `PATH`, a shell alias, or a Maven launcher selects a different runtime.

## Fedora

Install the development package and select it for the current shell:

```shell
sudo dnf install java-21-openjdk-devel
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
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
release version 21 not supported
```

and Tycho compiler failures involving the JDK runtime filesystem, such as:

```text
Cannot invoke "java.nio.file.FileSystem.getPath(...)" because "this.fs" is null
```

Use JDK 21 before investigating these as source or target-platform defects.
