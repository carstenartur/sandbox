# Independent execution evidence for the reporting investigation

This remains a diagnostic tool, not a production replacement for Surefire.
The experimental reporter still needs complete interrupted-run XML support,
parallel/retry validation and downstream console/display-name verification.
Do not conflate preservation of native events with a ready Jenkins reporter.

## Preserve progress without repeated full XML writes

`ExecutionLedger` is a JUnit Platform service listener disabled by default.
With `-Djdt.qa.progress.dir=/absolute/new/path`, it appends and flushes every
start, finish or skip event once. Each file belongs to one process/plan and
contains monotonic sequence numbers, elapsed time, native unique IDs, parent
IDs, sources, display names and failure/skip details. Text fields use UTF-8
Base64 to preserve tabs, newlines and non-ASCII text unambiguously.

Only the actual `testPlanExecutionFinished` callback writes `PLAN_END`. A
shutdown hook never fabricates it. Thus a forcibly terminated JVM retains
completed tests without making an incomplete run appear successful. Flushing
provides process-crash evidence; power-loss durability/fsync is not promised.

`ExecutionLedgerSummary` validates the schema, sequence, state transitions and
completion. It rejects truncated/duplicate evidence and refuses to overwrite
an existing summary. It produces separate `complete` and `successful` fields
and a sorted native-outcome inventory. Exit zero means that the evidence was
parsed, NOT that the test run passed; callers must require both fields to be
true and compare the non-empty inventories and XML results independently.

Install the identical observer before both A/B variants using the installer's
optional `LEDGER` mode. The stock reporter's bytecode is verified and preserved;
existing service providers are retained. `.unmonitored` backups are distinct
from the `.baseline` backups used later for the reporter experiment. A second
installation or unexpected runtime bytecode is rejected. All of this is
restricted to an explicitly marked disposable repository.

## Verification

There are now 18 probe/installer/ledger tests per variant. The new tests use
actual Jupiter callbacks, including failing/error/aborted/disabled tests, and
a child JVM terminated with `Runtime.halt(23)`. Negative cases reject missing,
truncated and duplicated evidence; completed and successful are tested
separately. The existing eight XML inventories must still agree before/after.
No full JDT success is inferred from these small fixtures.

## Upstream work

Before expanding the experimental patch into a general solution, compare
Apache's existing proposal:
https://github.com/apache/maven-surefire/pull/3471

It addresses class-template report ownership and related counting/display
issues. Our pinned 3.5.6 probe remains useful independent evidence; this
repository must not imply that a competing general-purpose fix is ready or
that the upstream proposal has already been validated by these tests.
