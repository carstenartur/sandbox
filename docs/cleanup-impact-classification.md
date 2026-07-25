# Cleanup impact classification

Sandbox classifies cleanup transformations before execution so local syntax changes are not confused with coordinated API or representation migrations.

| Level | Scope and compatibility claim | Ordinary save action |
|---|---|---|
| `LOCAL_SAFE` | One compilation unit; no externally visible signature change | Allowed |
| `PROJECT_CLOSED` | Multiple compilation units; references are proven closed inside the selected source scope | Not allowed |
| `COMPATIBILITY_MANAGED` | Public or external representation changes with explicit adapters and migration policy | Not allowed |
| `MANUAL_REFACTORING` | Interactive decisions or non-Java resource changes are required | Not allowed |

## Diagnostics contract

Coordinated-cleanup preview and CLI diagnostics expose:

- the impact level;
- whether the change is project-wide;
- whether ordinary save-action execution is allowed;
- the number of affected compilation units;
- a compatibility statement;
- the selected and automatically added source scope;
- transformed and rejected candidates with stable reason codes.

Compilation-unit handles remain privacy-preserving opaque identifiers in JSON reports.

## Current mappings

- ordinary local Int-to-Enum transformations: `LOCAL_SAFE`;
- package-scoped Int-to-Enum migration plans: `PROJECT_CLOSED`;
- coordinated JUnit `ExternalResource` migrations: `PROJECT_CLOSED`;
- public Int-to-Enum API migrations: rejected until a `COMPATIBILITY_MANAGED` adapter policy is implemented;
- migrations requiring build files, persistence mappings, serialization schemas or interactive choices: `MANUAL_REFACTORING`.

A cleanup profile or save-action integration must reject every level whose `ordinarySaveActionAllowed()` value is false. Product UI integrations should display the compatibility statement and affected-unit count before apply.
