# Sandbox cleanups in JDT LS

The existing `sandbox_cleanup_application` bundle contributes two optional JDT LS
commands. There is no second transformation engine, copied cleanup implementation,
new server fork, or automatic save action. The adapter reads the existing
`org.eclipse.jdt.ui.cleanUps` contributions and their normal option initializers,
then delegates to the same `ICleanUp` objects used by Eclipse.

## Scope

Java cleanup contributions from Encoding, Functional Converter, Platform Helper,
JFace/SubMonitor, JUnit, Method Reuse, Int-to-Enum, Mathematics, Container Cleanup,
Use General Type, and TriggerPattern/Common are eligible when installed. This
exposes their **existing supported cases**, not new transformation capabilities.
`sandbox.cleanup.list` is the authoritative inventory of installed contributions.

The already-upstream While-to-For cleanup (`sandbox_tools`) is deliberately not
listed or executable. Search views, CSS tools, and other non-Java UI features are
not Java cleanups and are not converted into language-server features.

The XML cleanup is listed as unavailable with an explicit reason: its current
`XMLPlugin.rewrite()` writes workspace resources while creating the fix. Running
it through a supposedly read-only preview would be unsafe. It needs a separate
resource-edit adapter before it can be enabled; the bridge never calls that path.
Unknown future Sandbox contributions are also unavailable until their preview
contract has been assessed.

## Build and runtime requirements

Use the repository's Java 25/Maven build:

```sh
./mvnw --batch-mode clean verify
# Also publish the local p2 repository when preparing a server installation:
./mvnw --batch-mode -Prepo package
```

The existing application test module contains the bridge's JUnit tests and still
uses `useUIHarness=false`. No additional workflow or test runner is introduced.
The JDT LS 1.62.0 milestone repository is pinned in `sandbox_target/eclipse.target`
so Maven and the Oomph/PDE contributor workspace use the same build dependencies.
Module-local Maven repositories alone do not populate PDE's active target.
The test fragment explicitly requires m2e and Buildship, as the upstream JDT LS
tests do: these are optional for its syntax server but needed when its standard
server starts. The production application's server dependencies remain optional;
ordinary Eclipse/CLI entry points do not require JDT LS. Making bundles available
in the build target is not a request to install them in every Sandbox product.

**This is a small adapter, not an SWT-free repackaging of Sandbox.** The current
Sandbox bundles still declare JDT UI/Workbench dependencies. They must be resolved
along with the cleanup bundles. Copying only `sandbox_cleanup_application.jar`
into a stock JDT LS installation is not sufficient. Do not replace arbitrary
platform JARs in an editor-managed server or bypass OSGi dependency checks.

For development, provision a **separate** JDT LS installation with p2. Its roots
are the upstream `languageServer.product` plus these existing Sandbox bundle IUs:

```text
sandbox_cleanup_application
sandbox_encoding_quickfix
sandbox_functional_converter
sandbox_platform_helper
sandbox_jface_cleanup
sandbox_junit_cleanup
sandbox_method_reuse
sandbox_int_to_enum
sandbox_math_cleanup
sandbox_container_cleanup
sandbox_use_general_type
sandbox_triggerpattern
```

Use the locally built `sandbox_updatesite/target/repository`, the JDT LS p2
repository `https://download.eclipse.org/jdtls/milestones/1.62.0/repository/`, and the
released Eclipse/Orbit repositories declared by `sandbox_target/eclipse.target`.
p2 must resolve their combined dependencies successfully; do not force-install
incompatible versions. Start that installation with the normal JDT LS application
`org.eclipse.jdt.ls.core.id1` and a separate workspace. For an extension-bundle
client, JDT LS also supports absolute bundle paths in
`initializationOptions.bundles`, **after all mandatory runtime dependencies have
been provisioned**.

Provisioning into stock VS Code/Theia/Neovim distributions and a no-display,
end-to-end language-server launch still require qualification. No prebuilt VSIX,
Open VSX publication, or validated editor installation is claimed by this change.

## Commands

Send `workspace/executeCommand` with `command: "sandbox.cleanup.list"` and an
empty argument list. Each result has `id`, `bundle`, `defaults`, `available`, and
`reason`. Defaults come from the original plugin; a listed cleanup is **not**
automatically enabled. Use the same option keys and string values as Eclipse
cleanup profiles. The adapter does not rewrite workspace preferences.

For example, the arguments to `workspace/executeCommand` for an encoding preview
are:

```json
{
  "command": "sandbox.cleanup.preview",
  "arguments": [{
    "cleanupId": "org.eclipse.jdt.ui.cleanup.explicitencoding",
    "options": {
      "cleanup.explicit_encoding": "true",
      "cleanup.explicit_encoding_keep_behavior": "true"
    },
    "documents": [{
      "uri": "file:///workspace/project/src/p/Example.java",
      "version": 7,
      "source": "package p;\nclass Example { /* exact current editor text */ }\n"
    }]
  }]
}
```

`source` must be the complete, exact current editor contents, including line
endings; the illustration above is not a request to insert placeholder source.
Wait for normal `didOpen`/`didChange` synchronization before invoking the command.
The server rejects stale text, duplicate units, read-only sources, and mixed
Java-project selections. Each request chooses one cleanup, avoiding overlapping
edits from independently generated transformations.

The response contains `edit` (an LSP `WorkspaceEdit`), `requiredUris`, and
`messages`. When `requiredUris` is nonempty, **no edits are returned**. Open those
related files, synchronize them, add their versions and exact source to the
request, and repeat. This preserves the existing multi-file scope and planning
contract without silently modifying undisclosed files.

When the scope is complete, one cleanup instance receives all units in
`checkPreConditions`. Its existing fixes are converted with JDT LS's
`TextEditConverter`; `checkPostConditions` releases plans even on failure or
cancellation. The adapter never invokes `Change.perform()`. All returned document
edits carry the supplied editor versions. A client must support versioned
`documentChanges`, preview the **whole** edit, recheck all document versions/text,
and apply it only after explicit acceptance. Do not strip versions, apply only a
subset of a coordinated edit, or turn this command into an implicit save action.

Compiler options, bindings, semantic guards, exception handling, and cleanup
limitations remain those of the original implementation. A Java-source preview
does not promise Maven/Gradle/PDE dependency migration or non-text resource changes.

## References

- JDT LS extension bundles: https://github.com/eclipse-jdtls/eclipse.jdt.ls/wiki/Contribute-an-extension-bundle
- Delegate contract: `org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler`
- Existing cleanup lifecycle: [multi-file cleanups](../docs/multi-file-cleanups.md)
