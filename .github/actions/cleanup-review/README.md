# Sandbox Cleanup Review Suggestions

This composite action runs the headless Sandbox Eclipse cleanup application on Java files changed by a pull request and publishes the **complete cleanup result** in a GitHub review. The review groups the diff by file and links to the full patch and execution reports.

A native **Suggested Change** is offered only when it can contain the complete acceptance unit in one commentable range. Several changed locations in the same file are combined, including the unchanged lines between them. If the complete range is unavailable in the PR diff, the review shows the full diff and patch link instead of independently applicable fragments.

By default, the artifact contains `suggestions.patch`, `review.md`, `review.json`, and the cleanup JSON reports. It is uploaded before review publication, so the complete result remains available if GitHub rejects the review. Oversized reviews explicitly direct the reader to the complete artifact; they do not present a shortened patch as complete. If no artifact is available, an oversized result fails publication with a visible error instead of publishing an incomplete review.

## Accepting a suggestion

Open the file's review comment under **Files changed**. Below the native **Suggested change** block, click **Commit suggestion**, then **Commit changes**. This accepts every cleanup edit in that file together; selecting a batch is unnecessary for a single file suggestion.

GitHub displays the complete replacement range in red and green, including unchanged lines between edits. Lines outside that range remain in the file, even when the displayed fragment ends before a closing brace. The review's additional file diffs are collapsed and marked **view only**: they provide the complete patch for inspection, while the native suggestion in the file comment provides GitHub's commit controls.

Files marked **Complete patch required** have no native acceptance button. To apply the whole result, download the artifact and apply `suggestions.patch` to its recorded head. This is an alternative to accepting individual suggestions: the patch already contains all of their changes. The integration-fixture files intentionally contain the before-form and demonstrate the review output.

## How it works

1. The caller checks out the exact pull-request head with full Git history.
2. The action identifies changed Java files from `base...head`.
3. Each file is assigned to its nearest ancestor containing an Eclipse `.project` file.
4. The configured Sandbox cleanup runs once per affected Eclipse project in an isolated temporary workspace.
5. The cleanup changes remain only in the ephemeral Actions checkout.
6. A Node publisher captures the complete diff and constructs one acceptance unit per run, or per file when the caller explicitly declares file independence.
7. The artifact is uploaded, and the publisher verifies that the PR still has the analyzed head before submitting the review.

Git generates the complete patch. The cleanup application's own `--patch` output remains diagnostic evidence and is not used to place review comments. Publication does not stash, restore, or otherwise modify the cleanup result.

## Example

```yaml
permissions:
  contents: read
  checks: write
  issues: write
  pull-requests: write

steps:
  - uses: actions/checkout@v7
    with:
      ref: ${{ github.event.pull_request.head.sha }}
      fetch-depth: 0

  - uses: ./.github/actions/cleanup-review
    with:
      github-token: ${{ secrets.GITHUB_TOKEN }}
      base-sha: ${{ github.event.pull_request.base.sha }}
      head-sha: ${{ github.event.pull_request.head.sha }}
      config-file: .github/cleanup-profiles/review-explicit-encoding.properties
      image: ghcr.io/carstenartur/sandbox-cleanup:latest
      source-mode: changed
      # Use file only when the configured cleanup has no cross-file dependencies.
      suggestion-scope: file
```

## Inputs

| Input | Default | Meaning |
|---|---|---|
| `github-token` | required | Token used to publish the review. |
| `base-sha` | required | Pull-request base commit. |
| `head-sha` | required | Exact checked-out pull-request head. |
| `config-file` | conservative encoding profile | Repository-relative cleanup properties file. |
| `image` | `ghcr.io/carstenartur/sandbox-cleanup:latest` | Cleanup runtime image. |
| `java-home` | empty | Optional host JDK mounted read-only for analysis. |
| `scope` | `both` | `main`, `test`, or `both`. |
| `source-mode` | `changed` | `changed` passes only PR Java files; `project` passes each complete affected Eclipse project. |
| `suggestion-scope` | `run` | `run` keeps the whole cleanup result together; `file` declares that changes in different files are independent. |
| `tool-name` | `sandbox-cleanup` | Name displayed in the review. |
| `artifact-name` | `sandbox-cleanup-review` | Patch/report artifact name. |
| `upload-artifact` | `true` | Retain the complete patch and reports according to the repository artifact-retention policy. |

## Choosing a cleanup

Use one narrowly scoped properties file per review workflow. This keeps every result attributable to a single cleanup. A diff does not describe semantic dependencies, so the publisher must not infer that separate hunks or files are independent.

The default profile enables only the conservative Explicit Encoding strategy:

```properties
cleanup.explicit_encoding=true
cleanup.explicit_encoding_keep_behavior=true
cleanup.explicit_encoding_insert_utf8=false
cleanup.explicit_encoding_aggregate_to_utf8=false
```

A repository can add another properties file and point `config-file` at it without changing the action.

## Several lines and several files

| Cleanup result | Publication and acceptance |
|---|---|
| One replacement covering several adjacent lines | One multiline suggestion, when the complete original range is commentable. |
| An import and one or more uses farther down the same file | One spanning suggestion containing all changes and the unchanged lines between them, when the complete range is commentable. Otherwise, the complete file diff and patch link. |
| Several independent files with `suggestion-scope: file` | One complete suggestion per eligible file. Ineligible files remain visible in the grouped diff. |
| Several files with the default `suggestion-scope: run` | The complete result is a single patch acceptance unit; no individually committable file fragments are offered. |
| A newly created or deleted Java file, or edits outside the PR diff | Included in the complete patch and review. GitHub inline limitations do not remove them from the result. |

The bundled conservative Explicit Encoding workflow uses `suggestion-scope: file`: its changes are local to each compilation unit. It still groups all edits within that file, so an import cannot be separated from the call that requires it. Other profiles default to `run` until their independence is established.

GitHub can commit a user-selected batch of suggestions together, but it does not require the user to select all members of a dependent transformation. Being in the same review is therefore insufficient to make a fix indivisible. Switching the former reviewdog `diff_context` filter to `nofilter` would expose more diagnostics, but would not make separately generated suggestions a complete acceptance unit.

For a patch result, download the artifact, check out its recorded PR head, and apply the complete `suggestions.patch`:

```bash
git apply --check /path/to/suggestions.patch
git apply /path/to/suggestions.patch
git diff --check
```

Run the appropriate compilation and tests before committing. Re-run the cleanup on an updated head instead of assuming an old patch still describes the current source. The publisher rejects stale-head reviews.

### Execution scope is a separate concern

The default Docker/CLI entry point invokes `JavaCleanup`, which creates a `CleanUpRefactoring` for each compilation unit and performs its complete `Change`. Multiple edits inside that file are supported. `source-mode: project` supplies more input files but still processes them separately; it does **not** create a coordinated multi-file refactoring or roll back the whole run if a later file fails.

Sandbox has a separate `ProjectWideJavaCleanup` application and semantic multi-file planning infrastructure. The default action does not select that application. Supporting a cleanup that changes a declaration and callers in other files requires both a runner that plans and verifies the complete source scope and publication that keeps the result together. `suggestion-scope: run` provides the publication policy; it does not supply the missing execution transaction. See [Coordinated multi-file cleanups](../../../docs/multi-file-cleanups.md).

## Safety boundaries

- The script fails if the checkout does not match `head-sha`, tracked files are already dirty, a path escapes the repository, or a cleanup modifies a non-Java file.
- Every Eclipse project receives a separate temporary workspace, avoiding project-name collisions.
- Files without an ancestor `.project` are reported and skipped rather than processed without bindings.
- GitHub suggestions require a complete commentable range. The publisher never drops a required edit merely to fit a suggestion into the PR diff. Files without a usable API patch are retained in the review and artifact.
- The bundled workflow publishes reviews only for branches in the same repository. External fork support needs a separate unprivileged analysis and privileged, validating publisher workflow; it must not execute fork code with a write-capable token.
- If GitHub rejects inline ranges, publication falls back to the complete review without inline suggestions. Other API errors remain visible. Repeated publication of the same generated review by the standard `github-actions[bot]` token is detected to avoid duplicate bot reviews.
- The container image is recorded by resolved repository digest in the job summary when Docker exposes one. Production consumers may replace `latest` with a release tag or digest.

## Verification

```bash
bash -n .github/actions/cleanup-review/run-cleanup-review.sh
.github/actions/cleanup-review/test/run-cleanup-review-test.sh
./mvnw --batch-mode -f sandbox_common_test/pom-cleanup-review.xml test
```

Maven/JUnit owns the executable contract: `CleanupReviewPublisherTest` runs the publisher scenarios and requires every case to pass, alongside the action's Java evidence and runtime contracts. The standalone test POM runs these same Java sources without building Eclipse plugins; the normal `sandbox_common_test` Maven gate also discovers them. The Node scenario harness exercises grouped replacements, multiple files, off-diff changes, GitHub range constraints, and complete-result fallbacks without making network writes. The shell preflight uses a mocked Docker executable while keeping Git and the action runner real to check the shell adapter.

### Selecting the analysis JVM

The optional `java-home` input names a JDK on the Linux runner. The action
mounts it read-only and supplies both `JAVA_HOME` and `PATH` inside the cleanup
container, including for older launchers that invoke `java` from `PATH`. With
no input, the image's default runtime is retained. The repository review
workflow provisions Java 25 and passes its `JAVA_HOME`; this avoids using an
older published image's JVM to resolve the Java-25 contributor projects.
No `.classpath`, compiler preference or source compatibility level is changed.
