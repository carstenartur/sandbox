# Published release baseline implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement this single bounded fix.

**Goal:** Generate 1.3.7 notes from the previously published stable release even when its version commit is outside main's ancestry.

**Architecture:** Keep the existing workflow step and Git range. Obtain the baseline from GitHub's published, non-prerelease releases instead of `git describe`. Execute the real workflow shell block from Maven/JUnit against temporary Git histories; substitute only the remote GitHub CLI response.

**Tech Stack:** Java 25, Maven/JUnit 5, Git, GitHub CLI, Bash.

**Spec:** The release-preparation request and the reproduced repository state: latest published release `v1.3.6`, while `git describe origin/main` returns `v1.3.2` because the stable version commit is not a main ancestor.

## Global constraints

- Ordinary PR against main, independent of mathematics qualification #1741.
- No new Python automation and no release publication or tag creation.
- Do not suppress API or missing-tag failures.
- Preserve the existing empty-history and first-release messages.

## Review focus

- A stable release on a side branch must exclude already released changes.
- Drafts and prereleases must not become the baseline.
- No published release must retain the first-release message.
- GitHub/API failure must stop the step, not masquerade as a first release.
- A missing tag must fail; an empty valid range must retain its explicit message.

## Task: Correct and verify the release-notes baseline

**Files:** `.github/workflows/deploy-release.yml`; new `sandbox_common_test/src/org/sandbox/jdt/triggerpattern/test/policy/ReleaseNotesContractTest.java`.

**Interface:** Existing `STARTING_SHA` environment variable and generated `release-notes.md`; GitHub CLI supplies the previous published tag.

- [ ] Add JUnit scenarios that execute the extracted workflow block against a real side-branch release history, with a local `gh` function supplying the remote response.
- [ ] Run `./mvnw -pl sandbox_common_test -Dtest=ReleaseNotesContractTest test`; confirm the old workflow includes previously released changes and ignores API failures.
- [ ] Replace ancestor-tag discovery with `gh release list --exclude-drafts --exclude-pre-releases --limit 1 --json tagName --jq '.[0].tagName // empty'`; require successful commands with `set -euo pipefail`.
- [ ] Run the new scenarios, existing release/version contracts and repository-policy tests under Maven; inspect the diff and obtain an independent review.
- [ ] Commit, open an ordinary PR after #1741 integration, pass its actual required checks and integrate. Record the result in the release handoff.
