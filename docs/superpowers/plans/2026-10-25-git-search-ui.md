# Git search UI repair implementation plan

> **For agentic workers:** Use superpowers:executing-plans. Track verification in the task workspace.

**Goal:** Make the existing Git search, history and indexing controls usable and document their real server-backed behavior.

**Architecture:** Retain the released 0.11.8 backend. Eclipse is an HTTP client. Explicit user actions import one chosen repository through normal JGit push and rebuild one named server index through its authenticated maintenance endpoint.

**Tech Stack:** Java 25, Eclipse SWT/JFace, JGit, Maven/Tycho, JUnit.

**Spec:** The user's approved findings in this conversation: empty repository requests, no-op index handlers, misleading architecture and missing user documentation.

## Global constraints

- No additional jgit-storage-hibernate release or copied backend implementation.
- Ordinary pull request directly against main; no stacked prerequisite.
- Maven/JUnit tests, including real HTTP and Git operations. No new workflow/test framework.
- No automatic upload of all EGit repositories. Import shows source and destination and never force-pushes or deletes remote refs.
- Errors must remain distinguishable from successful searches with zero hits.
- Background requests must not freeze the workbench or update disposed controls.

## Approaches

Fixing only the empty name would retain ineffective import commands. Embedding Hibernate in Eclipse would contradict the completed server migration. Repair the existing REST integration and use the existing Smart HTTP ingestion and authenticated rebuild contracts; this is the implementation choice for the authorized repairs.

## Review focus

- Missing repository, unreachable backend, HTTP errors and malformed responses must produce visible errors.
- History uses an exact repository-relative path, not a Java FQN.
- A rejected Git update must be reported; imports must preserve other server refs.
- Cancelled operations and disposed views must not report success or update widgets.
- Documentation must describe model prerequisites, server configuration and actual screenshots truthfully.

## Task 1: REST client and views

- [ ] Add a focused Eclipse test fragment; reproduce real HTTP contract failures and view input failures.
- [ ] Validate repository input, propagate transport/HTTP/parse errors, parse actual response fields.
- [ ] Add repository input, correctly mapped table columns and background requests to search/history/analytics.
- [ ] Run the focused Maven/Tycho tests.

## Task 2: Repository import and rebuild

- [ ] Prove local Git import changes the destination and rejected updates fail using real repositories.
- [ ] Replace the empty indexer path with an explicit source/destination import; no automatic workspace uploads.
- [ ] Wire rebuild to authenticated POST /api/admin/rebuild for the selected repository; do not persist the token.
- [ ] Exercise HTTP authorization/error responses and cancellation, and rerun module tests.

## Task 3: Documentation and qualification

- [ ] Add installed Help and an example workflow, update root/module READMEs and remove obsolete embedded-HSQLDB claims.
- [ ] Capture real populated views from deterministic HTTP fixtures with assertions; include semantic-search prerequisites.
- [ ] Run relevant Maven verification and repository policy checks, inspect screenshots, then get one independent final review.
- [ ] Create/update an ordinary PR against main and inspect actual CI results.
