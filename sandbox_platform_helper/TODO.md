# Platform Helper Plugin — TODO

> **Navigation**: [Main README](../README.md) | [Plugin README](README.md) | [Architecture](ARCHITECTURE.md)

## Current state

The cleanup now follows a semantics-preserving contract:

- supported five-argument `Status` constructors lose only a code whose
  compile-time value is `IStatus.OK`;
- the original severity, String/Class plug-in identity, message and throwable
  remain in the shorter constructor;
- `MultiStatus` changes only a compile-time zero to the named `IStatus.OK`
  constant;
- nonzero, unresolved and runtime-computed application codes remain unchanged;
- bundled TriggerPattern rules no longer replace explicit-identity constructors
  with caller-inferred factory methods.

Issue [#1498](https://github.com/carstenartur/sandbox/issues/1498) tracks this
semantic correction. Issue [#1497](https://github.com/carstenartur/sandbox/issues/1497)
tracks pinned real-corpus screenshot and provenance reuse.

## Completed in the semantic-preservation slice

- [x] Compare severity and status code by resolved compile-time integer value.
- [x] Recognize `IStatus.OK`, literal zero and named compile-time zero constants.
- [x] Preserve explicit String and Class identity overloads.
- [x] Preserve null and non-null throwable expressions.
- [x] Reject nonzero and nonconstant `Status` codes.
- [x] Reject nonzero and nonconstant `MultiStatus` codes.
- [x] Remove identity-changing Status rules from the general logging hint library.
- [x] Make the dedicated Platform Status hint retain `$pluginId`.
- [x] Replace tests that expected plug-in identity or application code loss.
- [x] Correct README, architecture, Help, feature metadata and cheat sheet.

## Required before closing #1498

- [ ] Make the full Maven/Tycho build green on the safety branch.
- [ ] Verify generated cleanup-tab screenshots and update them only from the
  real SWTBot workbench run.
- [ ] Add exact-pin inventory assertions for the selected JDT UI 4.40 corpus.
- [ ] Prove that `ProposalSorterHandle.java` is changed only by removing the
  redundant OK code while retaining `JavaPlugin.getPluginId()`.
- [ ] Prove that `JarBuilder.java` remains unchanged because it uses
  `IJavaStatusConstants.INTERNAL_ERROR`.
- [ ] Prove that numeric zero in `JavaSearchResult.java` is recognized by value.
- [ ] Verify Apply and byte-exact Undo through the real Cleanup preview.

## Follow-up design work

### Machine-readable rejection reasons

The native detector currently rejects unsafe shapes by returning no operation.
For pinned-corpus provenance and user diagnostics, expose stable classifications
such as:

- `NON_OK_STATUS_CODE`;
- `UNRESOLVED_STATUS_CODE`;
- `UNSUPPORTED_SEVERITY`;
- `UNRESOLVED_STATUS_TYPE`;
- `ALREADY_SIMPLIFIED`;
- `FACTORY_IDENTITY_NOT_PROVEN`.

This should be shared by headless inventory and Workbench scenarios rather than
reimplemented in screenshot tests.

### Optional factory-method proof

A future rewrite to `Status.info/warning/error` is acceptable only when a robust
analysis proves that the original identity equals the bundle identity inferred
from the calling class. Possible proof inputs include:

- exact `Class<?>` identity matching the enclosing top-level type;
- PDE bundle metadata and source ownership;
- a known generated plug-in-id constant whose value equals the containing
  bundle symbolic name.

Until such proof exists, retaining the explicit identity is the correct default.
Do not add a preference that merely asks the user to accept semantic uncertainty.

### MultiStatus scope

Normalizing zero to `IStatus.OK` is a readability-only change. Re-evaluate
whether it provides enough value to remain part of the cleanup after real-corpus
inventory. Never infer that an arbitrary code is irrelevant because child
statuses determine aggregate severity; `IStatus#getCode()` remains observable.

### Additional Platform APIs

Potential separate cleanups, each requiring its own semantic contract:

- `CoreException` creation and propagation;
- `OperationCanceledException` patterns;
- progress reporting and `SubMonitor` usage;
- `IAdaptable` and adapter-manager patterns;
- logging API modernization that preserves status identity and codes.

## Testing strategy

Keep Maven/Tycho and Java/JUnit as the test authority. Required layers are:

1. focused constructor transformation and rejection tests;
2. bundled hint behavior/safety tests;
3. real target-platform fixture compilation;
4. actual Eclipse LTK preview, Apply and Undo;
5. pinned upstream inventory and post-change project tests;
6. reproducible Help screenshots with provenance.

A passing screenshot test is not evidence of semantic correctness unless the
candidate detector and expected source facts are verified first.

## TriggerPattern DSL status

The active `platform-status.sandbox-hint` covers only explicit five-argument
`IStatus.OK` source shapes and rewrites them to the corresponding four-argument
constructor while retaining `$pluginId`.

The richer native cleanup remains authoritative for constant-value analysis,
including literal and named zero expressions. Three- and four-argument Status
constructors are intentionally not rewritten to factories.

The general `platform-logging.sandbox-hint` now modernizes only logging lookup;
it no longer contains Status constructor-to-factory rules.

## Eclipse contribution readiness

Before proposing this cleanup upstream:

- [ ] complete #1498 and exact-pin corpus validation;
- [ ] expose stable applicability/rejection diagnostics;
- [ ] demonstrate no semantic loss on representative Eclipse bundles;
- [ ] document the actual standard-LTK file-selection contract;
- [ ] obtain community feedback on whether zero-code constructor simplification
  is valuable enough for inclusion.

## References

- [Eclipse Platform Status API](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/runtime/Status.html)
- [Eclipse Platform MultiStatus API](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/runtime/MultiStatus.html)
- [Semantic preservation issue](https://github.com/carstenartur/sandbox/issues/1498)
- [Pinned screenshot scenarios](https://github.com/carstenartur/sandbox/issues/1497)
