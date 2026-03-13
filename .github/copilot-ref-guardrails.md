# Refactoring Guardrails — JDT Porting Constraints

> **Read this when**: You are about to refactor, restructure, or "clean up" code.

⚠️ **CRITICAL**: This repository is designed for **easy porting to Eclipse JDT**. The following rules are **hard constraints** that MUST NOT be violated.

## 1. Do NOT introduce new base classes that don't exist in Eclipse JDT

Each cleanup class (e.g., `UseExplicitEncodingCleanUpCore`, `JFaceCleanUpCore`) directly extends Eclipse JDT's `AbstractCleanUp`. **Do NOT create intermediate base classes** like `AbstractSandboxCleanUpCore` — this would break the 1:1 porting correspondence with Eclipse JDT's internal structure.

## 2. Do NOT "de-duplicate" boilerplate in `*CleanUpCore` classes

The apparent code duplication across cleanup core classes (e.g., similar `createFix()`, `getStepDescriptions()`, `getRequirements()` patterns) is **intentional**. Each instance carries plugin-specific variation. The pattern matches how Eclipse JDT's internal cleanup classes work. **De-duplication would break the porting process.**

## 3. Do NOT create shared interfaces for cleanup fix enums

Each `*FixCore` enum (e.g., `JfaceCleanUpFixCore`, `UseExplicitEncodingFixCore`) has plugin-specific method signatures. **Do NOT try to unify them** behind a common interface.

## 4. Do NOT restructure package layout

The `org.sandbox.jdt.internal.*` package structure maps directly to `org.eclipse.jdt.internal.*`. **Do NOT reorganize packages** — the current layout enables mechanical find/replace when porting to JDT.

## 5. Always read ARCHITECTURE.md before proposing changes to a plugin

Each plugin directory contains an `ARCHITECTURE.md` that documents design decisions and constraints. **Read this file first.**

## 6. The `MY` prefix in `MYCleanUpConstants` is intentional

**Do NOT rename this class.** The `MY` prefix avoids naming conflicts with Eclipse JDT's `CleanUpConstants`. When porting, the prefix is simply removed.

## 7. What IS safe to refactor

- Bug fixes within existing classes
- New cleanup plugins following the established pattern
- New helper/utility methods within `sandbox_common`
- Extracting logic into helper classes within the same plugin
- Test improvements
- Documentation improvements
- Build/CI improvements

## Why These Guardrails Matter

Without these rules, AI assistants naturally propose DRY refactoring — which is normally good practice but harmful here because it breaks the JDT portability invariant. The project deliberately sacrifices code elegance to maintain a structure that can be mechanically ported to Eclipse JDT.

## Package Correspondence

| Sandbox | Eclipse JDT |
|---------|-------------|
| `org.sandbox.jdt.internal.corext.fix` | `org.eclipse.jdt.internal.corext.fix` |
| `org.sandbox.jdt.internal.ui` | `org.eclipse.jdt.internal.ui` |
| `MYCleanUpConstants` | `CleanUpConstants` |

To port: replace `sandbox` with `eclipse` in package paths.
