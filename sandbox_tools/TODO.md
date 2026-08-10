# Sandbox Tools Reference – Maintenance Status

> **Navigation**: [Main README](../README.md) | [Module README](README.md) | [Architecture](ARCHITECTURE.md)

## Current state

The iterator-loop-to-enhanced-for implementation is archived in Sandbox after being upstreamed to Eclipse JDT UI.

Completed:

- the active Sandbox `org.eclipse.jdt.ui.cleanUps` contribution is retired;
- the standard cleanup id remains owned by `org.eclipse.jdt.ui`;
- a runtime extension-registry test prevents duplicate registration;
- the original source and transformation tests remain available;
- the feature, cheat sheet, and Help are labelled as reference material;
- existing installations can receive an update that removes the former contribution.

## Maintenance policy

This module is not an independently maintained production cleanup. New behavior and defect fixes belong upstream in Eclipse JDT UI.

Sandbox maintenance is limited to concrete needs:

- repair a build or target-platform regression affecting the reference bundle;
- prevent accidental reintroduction of the duplicate cleanup extension;
- preserve tests or documentation that remain useful for contribution research;
- compare with upstream only when a specific issue, experiment, or contribution requires it.

There is no periodic synchronization commitment.

## Deliberately not planned

- a dedicated Sandbox cleanup tab;
- a second user-facing cleanup option;
- save-action registration for the archived implementation;
- automatic synchronization with every JDT change;
- new production features developed only in the Sandbox copy;
- TriggerPattern DSL replacement for this control-flow and binding-sensitive transformation.

## Verification checklist

When this area changes:

- [ ] `sandbox_tools/plugin.xml` contains no `org.eclipse.jdt.ui.cleanUps` contribution.
- [ ] `ToolsCleanupRegistrationTest` finds exactly one matching cleanup id.
- [ ] The matching contributor is `org.eclipse.jdt.ui`.
- [ ] Historical transformation tests remain green.
- [ ] The feature remains installable and clearly labelled as reference-only.
- [ ] Help and cheat-sheet text do not mention a Sandbox Tools cleanup tab or active Sandbox implementation.
- [ ] The distribution build remains green.

## Where to report problems

- Active cleanup behavior: Eclipse JDT UI issue tracker.
- Sandbox feature packaging, Help, or duplicate-registration regression: Sandbox issue tracker.
