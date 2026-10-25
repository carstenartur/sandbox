# Complete the released JGit library migration

## Intent and authority

The maintainer requested both adoption of released `jgit-storage-hibernate`
0.11.4 and completion of the architectural migration described in issue #1303.
The initial reference implementation is the immutable upstream tag `v0.11.4`, commit
`454ff1558e618571a9ffb1d2edb867cbdeffffb9`. The starting Sandbox commit is
`7cdfab40bddbbe5ae0d906a050d2968580e18747`.

The selected consumer release is now 0.11.5. The maintainer authorized its
publication with the public API prerequisites merged in upstream PR #396.

## Existing behavior

At the starting commit, both Maven consumers select Core 0.11.3. The application already
owns repository handles through `SandboxRepositoryService` and has a guarded,
tested external Core persistence context. Normal startup still constructs
`CopiedServerPersistenceContext`. Search, analytics and administrative resources
use copied entities and `GitDatabaseQueryService`; the Core-only consumer
contract intentionally rejects Search and Java Analysis.

## Approach

Continue the existing adapter migration in independently verifiable changes.
The alternatives are a version-only update, which leaves the requested runtime
migration unfinished, and replacing the complete server with the upstream
server, which would unnecessarily change existing REST URLs and deployment
behavior. The implementation choice is to retain the Sandbox server and its
DTOs while replacing generic implementation behind its application boundaries.

1. Select stable 0.11.5 in both consumers and the migration ledger. Preserve
   historical evidence with its actual version and commit.
2. Supply and release missing generic APIs upstream before their consumer
   cutover: message-only search, author-name filtering, documentation/string
   extraction, and read-only physical storage statistics. Upstream PR #396
   supplies these additions for stable 0.11.5, as issue #1303 permits.
3. Migrate generic history ingestion and queries to released Search. Keep
   application response DTOs independent of persistence entities.
4. Replace Java extraction and semantic queries with public Java Analysis
   facades. Use repository snapshots and expose binding quality. Never resolve
   dependencies from the network implicitly during source analysis.
5. Retain embedding/rank-fusion and genuinely Sandbox-specific reports in an
   optional application extension. Missing native capabilities must be explicit.
6. Provision supported schemas with released migration locations, Core before
   Search, then Hibernate validation. Prefer PostgreSQL/Lucene for deployment.
7. Make the external persistence context the normal server path once every
   resource it wires is compatible; preserve Smart HTTP and lifecycle behavior.
8. Remove replaced copied packages and obsolete packaging/deployment references
   after replacement tests pass. Update both the Sandbox consumer contract and
   the upstream expected-module descriptor when the consumed module set changes.

## Constraints

- Use pinned non-SNAPSHOT artifacts; no source copies, submodules or floating
  source dependencies from upstream.
- Java 25; Maven/JUnit remain the executable verification authority.
- Ordinary PRs target current `main`; no stacked PRs. Changes stay within the
  repository's review-size policy. Dependent slices follow the merged prerequisite.
- Preserve existing REST URLs and compatible response fields. Document deliberate
  changes such as first-parent changed paths; do not silently remove capabilities.
- Generic storage, indexing and Java analysis belong upstream. Public facade
  results may be mapped to Sandbox DTOs; internal storage classes must not leak.
- Do not perform an unattended production database migration. Implementation and
  tests use disposable databases. Real deployments require backup/preflight and
  explicit maintenance migration, with restore-based rollback.
- Existing Git objects, refs and reflogs are authoritative. Derived projections
  can be rebuilt; search failure must not invalidate a successful Git publication.
- Keep heavy server/native dependencies out of Eclipse unless a real runtime
  consumer requires them.

## Verification and completion

The builder review identified actual 0.11.4 API gaps. Filtering an already
paginated full-text result cannot preserve message-only search. Counting reachable
Git objects cannot be labelled a physical storage inventory. Java in-memory
analysis is a supported alternative to absent persistence migrations, but a
latest-snapshot-only query cannot replace historical endpoints. Preserve those
distinctions in tests and endpoint mapping rather than weakening the contracts.

Reuse the current lifecycle, H2, HTTP, dependency-boundary, OSGi and shaded-server
checks. Add behavior tests only for changed contracts: real push/restart/clone,
compound search and first-parent paths, repository isolation, rebuild equivalence,
Java moves/usages with incomplete classpaths, and migration integrity.

The version change is independently releasable. It does not qualify the runtime
cutover. Every later slice updates the machine-readable capability ledger only
for behavior actually verified. Full completion additionally requires no copied
generic `org.eclipse.jgit.storage.hibernate` sources and a green main build.

## Implementation decisions left to the builder

Choose small application interfaces and DTOs based on the existing endpoint
shapes. Reuse the current lifecycle owner. Bound pagination and snapshot work,
close every cursor/handle, and reuse native Maven test fixtures. If a public
upstream API lacks a required generic capability, implement/release that support
upstream before consuming it; do not emulate an unavailable feature with a
Sandbox-only storage fork.
