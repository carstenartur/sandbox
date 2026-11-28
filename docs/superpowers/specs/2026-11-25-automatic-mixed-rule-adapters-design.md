# Automatic mixed resource adapters

## Intent

Complete the Java cleanup qualification requested by Carsten without relaxing compiler diagnostics, profile coverage, fatal rejection of unproved transformations, or source restoration. The existing #1931 corpus gate is separate from this production capability.

## Current evidence

Main fb8e1a82ffa80774d8c6b91db4abaecdaf5e1892 has the supplier/group support runtime and p2 delivery, but JUnitMultiFilePlanner aborts a shared ExternalResource hierarchy used as both Rule and ClassRule before strict per-unit handling can run. Strict already preserves complete unsupported units on Vintage while migrating independent supported units. The R4_41 reference was 1453/1457; the integrated #1931 head 8265c6572f96ac5b743a4d2d2921b6b89c24d62a is being requalified independently.

## Approaches

1. Convert a shared fixture to both Jupiter callback scopes. Rejected: registration scope is per use and callbacks can execute at the wrong scope or more than once.
2. Preserve every mixed component. Existing safe best-effort fallback, but does not add the missing automatic migration.
3. Preserve the fixture and register proven uses through the existing lifecycle adapters. Chosen implementation decision: it retains object identity, helper methods, virtual hooks and Vintage consumers while providing real automatic migrations.

## Required behavior

- Keep the original fixture hierarchy, superclass, fields, initializers and accessible virtual before/after contracts. Do not rename shared hooks or introduce reflection.
- Emit separate per-use/per-scope Jupiter registrations backed by org.sandbox.junit.jupiter.ExternalResourceExtension only for semantically proven owners. A named class/interface match alone is insufficient.
- Preserve constructor-assigned and mutable fixture selection semantics using deferred suppliers. Where peers exist, preserve JUnit 4 effective order and simultaneous receiver selection with the existing groups, or retain the whole unsupported consumer.
- Account for inherited annotated declarations, TestName timing, static/instance scope, shadowing, alias/order effects, custom runners and custom apply overrides. Unproved shapes retain whole Vintage contracts with structured reasons; they are not silently partially migrated.
- A mixed component with no supported strategy remains fatal before edits. A successful mixed plan must emit real adapter/source changes for supported owners, retain unsupported owners atomically, and record both outcomes.
- Preserve every inherited rule/lifecycle declaration needed by a retained Vintage consumer. Mark preserved declarations as processed so the ordinary local cleanup cannot rewrite them.
- Revalidate captured source/bindings before rewrite, preserve idempotency and exact native Undo.
- Require a real compatible support provider. For interactive cleanup, missing support remains actionable rejection.
- The headless PDE path may provision the target-project support import transactionally only after validating the real available provider. No profile-controlled assertion can stand in for that proof. Roll back on CHECK, planning failure, write failure and no introduced support usage.
- Update existing documentation to state supported and retained boundaries without claiming native JUnit 6 execution from the R4_41/JDT5 corpus.

## Proof

Use real Apply output, complete compiler diagnostics before/after/Undo, and actual Jupiter/Vintage execution. Cover at least one migrated instance-rule owner, one migrated class-rule owner, and a retained Vintage owner using the same fixture. Compare test identities/outcomes and lifecycle traces, including failed setup, constructor assignment and selection stability. Test missing/incompatible dependencies and metadata restoration. Retain negative fatal tests for unsupported mixed shapes.

For the actual R4_41 corpus, report observed source changes and retained markers, then require all 25 registered cleanups, 31 profiles and three source families to pass the unchanged acceptance gate. Counts belong to the exact tested tree. Do not count retained tests as migrated.

## Constraints

Java 25 / Tycho 5.0.4 / Eclipse 4.41. The support runtime remains Java 21 compatible. Use StandardCharsets and complete compiler diagnostics. No new Python automation in the repository. Existing JDT-oriented package structure and cleanup lifecycle contracts remain. Keep the production change separately reviewable from #1931, with a documented exception if the native proof and atomic dependency transaction exceed the 1500-line review target.
