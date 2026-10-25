# Analysis generation ownership

`AnalysisGenerationCache` is an application-owned lifecycle primitive for the
released Java file index. `AnalysisGenerations` shares its capacity across SOURCE,
PATH and API data in the normal application context. `HistoricalAnalysis` keeps
repository leases and generation pins through complete REST DTO mapping.

Each immutable snapshot value identifies one complete, closeable generation for
an already-canonical repository name. Equal snapshots reuse a generation until a
repository or global refresh epoch changes. An unsuccessful replacement is
reported to the caller; the prior healthy generation retains only its old key.
A candidate invalidated while building is closed and retried, with a maximum of
three attempts. Repeated invalidation returns an explicit retry error.

A scoped reader owns a slot pin through result mapping and resource cleanup.
Per-repository interruptible locks prevent duplicate builds and serialize use of
the same generation. A fair semaphore limits concurrent builds across repositories;
it is released before reading a reused generation. Snapshots are read again after
waiting for a build permit. Builds, readers, value comparisons and resource close
callbacks execute outside the short map monitor. Epoch comparison and candidate
installation occur atomically with invalidation.

The entry limit bounds idle retention, not memory in bytes. Active slots can
exceed the limit temporarily and are trimmed when released. The full corpus/index
still occupies memory proportional to history, and a replacement can overlap its
previous generation. Eviction cannot close a pinned generation. Shutdown rejects
new requests, waits for readers and in-progress eviction cleanup, then attempts to
close every retained generation. Cleanup errors remain visible without masking a
primary reader/build failure. Interruptions preserve the interrupt flag and an
unpublished candidate is closed. Callbacks must not retain generations, reenter
this cache or close it; recursive use is rejected before waiting.

The runtime integration preserves these conditions:

- Pass raw request names to the repository service, which normalizes once. Use
  that same canonical identity for cache, SQL and index keys. The cache's
  `invalidateCanonical` method deliberately performs no normalization.
- Hold an existing-only repository lease throughout the scoped read. Refresh the
  borrowed Git handle before every snapshot read, including retries and reads
  after waiting; another handle may have published while this request waited.
- Include all current refs, detached HEAD and retained Search roots in the history
  snapshot. Treat missing authoritative Git objects as errors.
- Invalidate after accepted pushes, including deletion-only pushes and failed
  projection indexing; invalidate at both the start and end of a rebuild, after
  successful repository deletion, and during administrative refresh.
- Use a global refresh epoch for local dependency/configuration changes that do
  not alter Git history, including replacement of a JAR at the same path and the
  arrival of a previously missing artifact. Admin refresh or restart must make
  those changes visible; no automatic filesystem freshness is implied.
- Close the generation owner before closing the repository service it uses.

`AnalysisGenerationCacheTest` exercises deterministic blocked-reader/build cases,
single-flight behavior, canonical identity separation, invalidated candidate
cleanup, bounded retries, eviction, global admission, interruption, reentrancy,
shutdown waiting and exception-preserving disposal.
