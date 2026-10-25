# Sandbox JGit storage integration (`sandbox-jgit-storage-hibernate`)

[Server](../sandbox-jgit-server-webapp/README.md) · [Consumer contract](../docs/JGIT_STORAGE_HIBERNATE_CONTRACT.md) · [ADR 0002](../docs/adr/0002-adopt-released-jgit-storage-hibernate.md)

This minimal OSGi bridge exports `org.sandbox.jgit.storage.integration` and uses the
public `jgit-storage-hibernate-core` API. It contains no copied storage, entities,
parser, query service or model code. Core is its only production dependency.

Both consumer POMs select published **0.11.8** through
`jgit-storage-hibernate.version`. The executable server additionally consumes released
Search and Java Analysis; those modules, Architecture, benchmarks and DJL remain
outside this bridge. Candidate verification substitutes the same-run library version
in both POMs and checks actual dependency resolution.

Released modules own Git packs/refs/reflogs/locks, schema streams, commit projection
queries, Java parsing, semantic comparison and generic file indexing. The server
owns REST DTOs, history-generation policy, application formats/reports, pools/cache,
optional embedding inputs and rank fusion. Normal server startup uses those released
modules. Deployed legacy databases require the explicit documented adoption procedure;
removal of copied classes does not itself migrate database contents.

`JGitStorageLibraryBoundary` exposes validated repository identities and the Core
entity-registration contract. `OsgiManifestContractTest` verifies the generated Bnd
manifest, including absence of old exports. The consumer contract checks the built
bridge, dependency trees and executable packaging. See the server documentation for
startup, migration and optional semantic-search behavior.
