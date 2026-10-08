# Sandbox Cleanup Review

This composite action runs the headless Sandbox Eclipse cleanup application on Java files changed by a pull request. It captures the **complete cleanup result** and publishes it as one commit on a separate `cleanup/pr-…` branch. The review contains a short summary, a normal GitHub comparison, and a link to open a cleanup pull request.

## Accepting the cleanup

1. Click **Review cleanup diff** in the bot review to inspect the complete commit in GitHub's ordinary diff view. The comparison uses the analyzed head SHA and the cleanup commit SHA, so it shows exactly the recorded result.
2. Click **Open cleanup PR**. GitHub opens a prefilled pull-request form with the original PR's head branch as the target and the cleanup branch as the source.
3. Create that cleanup PR, review it, and merge it. The merge adds all cleanup changes to the original PR branch. The original PR then includes them and its matching CI runs again.

The bot creates the cleanup branch and commit; you create and merge the cleanup PR. Imports, distant uses, changes across several files, and new or deleted Java files stay together in that commit. No inline replacement blocks or individually applicable fragments are published.

A workflow filtered to `pull_request.branches: [main]` does not run on a cleanup PR targeting a feature branch. The original PR's normal CI runs when that branch is updated by your merge, subject to its configured triggers and filters. Pushes made with `GITHUB_TOKEN` do not start `push` workflows. Human creation through the link also avoids the repository setting and additional CI approval needed for PRs created automatically with `GITHUB_TOKEN`. See GitHub's [PR URL parameters](https://docs.github.com/en/pull-requests/reference/using-query-parameters-to-create-a-pull-request), [workflow events](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows), and [token behavior](https://docs.github.com/en/actions/concepts/security/github_token).

## How it works

1. The caller checks out the exact pull-request head with full Git history.
2. The action identifies changed Java files from `base...head` and assigns them to their nearest ancestor Eclipse `.project`.
3. The configured cleanup runs once per affected project in an isolated temporary workspace.
4. The publisher captures the complete Git patch and result before the evidence artifact is uploaded.
5. The publisher checks that the PR still has the analyzed head, creates one commit whose parent is that head, and publishes a review linking to it. It never pushes to the original PR branch.

The default artifact retains `suggestions.patch`, `review.md`, `review.json`, cleanup JSON reports, and the console log. It is uploaded before publication, including diagnostic evidence when cleanup fails. Git generates the authoritative complete patch; the application's own patch output is diagnostic evidence. Publication preserves the cleanup checkout and index.

## Example

The bundled workflow limits publication to PRs from the same repository and skips heads beginning with `cleanup/pr-` to prevent repeated cleanup proposals for cleanup branches. Only its publishing job needs write permissions:

```yaml
permissions:
  contents: write
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
```

`contents: write` permits the cleanup commit and branch; `pull-requests: write` permits the review. Automatic PR creation is not required. The ordinary `GITHUB_TOKEN` is sufficient for same-repository publication when repository rules permit creating the branch.

## Inputs

| Input | Default | Meaning |
|---|---|---|
| `github-token` | required | Token used to create the cleanup branch and publish the review. |
| `base-sha` | required | Pull-request base commit. |
| `head-sha` | required | Exact checked-out pull-request head. |
| `config-file` | conservative encoding profile | Repository-relative cleanup properties file. |
| `image` | `ghcr.io/carstenartur/sandbox-cleanup:latest` | Cleanup runtime image. |
| `java-home` | empty | Optional host JDK mounted read-only for analysis. |
| `scope` | `both` | `main`, `test`, or `both`. |
| `source-mode` | `changed` | `changed` passes only PR Java files; `project` passes each complete affected Eclipse project. |
| `tool-name` | `sandbox-cleanup` | Name displayed in the review. |
| `artifact-name` | `sandbox-cleanup-review` | Patch/report artifact name. |
| `upload-artifact` | `true` | Retain the complete patch and reports according to repository retention policy. |

## Choosing a cleanup

Use one narrowly scoped properties file per workflow to keep each result attributable to a single cleanup. The default enables only conservative Explicit Encoding:

```properties
cleanup.explicit_encoding=true
cleanup.explicit_encoding_keep_behavior=true
cleanup.explicit_encoding_insert_utf8=false
cleanup.explicit_encoding_aggregate_to_utf8=false
```

A repository can select another properties file through `config-file`. Every result is still published as one complete commit; the publisher does not infer semantic independence from separate hunks or files.

The default Docker/CLI entry point invokes `JavaCleanup`, which creates a `CleanUpRefactoring` per compilation unit and performs its complete `Change`. `source-mode: project` widens the input but still processes files separately; it does not plan a coordinated multi-file refactoring or roll back earlier files if a later file fails. A failed cleanup publishes no proposal.

Sandbox also has `ProjectWideJavaCleanup` and semantic multi-file planning infrastructure. A cleanup that changes a declaration and its callers requires a runner that plans and verifies that complete source scope. Keeping its published result in one commit does not supply that execution capability. See [Coordinated multi-file cleanups](../../../docs/multi-file-cleanups.md).

## Complete patch and boundaries

The artifact is also a local acceptance route. Check out the recorded PR head, then apply its complete patch:

```bash
git apply --check /path/to/suggestions.patch
git apply /path/to/suggestions.patch
git diff --check
```

Run the appropriate compilation and tests before committing. Re-run the cleanup on an updated head; publication rejects a stale analyzed head. The integration fixtures deliberately retain their before-form to demonstrate the output.

- Cleanup fails if the checkout differs from `head-sha`, tracked files are dirty, a path escapes the repository, or cleanup modifies a non-Java file.
- Files without an ancestor `.project` are reported and skipped; each imported Eclipse project gets a separate temporary workspace.
- The complete commit and patch include changes outside the original PR diff. Commentable line ranges do not constrain publication.
- The bundled publisher supports same-repository PRs. Fork publication requires a separate unprivileged analysis and privileged validating publisher; fork code must not run with a write-capable token.
- If GitHub denies branch creation, the review provides the complete patch. An oversized fallback requires the uploaded artifact; otherwise publication fails visibly. Other API failures remain visible.
- Existing matching cleanup branches and standard `github-actions[bot]` reviews are reused on retries. No automatic PR creation, merge, or force-push to a contributor branch occurs.
- The summary records the resolved container digest when Docker exposes one. Consumers can replace `latest` with a release tag or digest.

## Verification

```bash
bash -n .github/actions/cleanup-review/run-cleanup-review.sh
.github/actions/cleanup-review/test/run-cleanup-review-test.sh
./mvnw --batch-mode -f sandbox_common_test/pom-cleanup-review.xml test
```

Maven/JUnit owns the executable contract: `CleanupReviewPublisherTest` explicitly runs both `test/cleanup-review.test.cjs` and `test/cleanup-proposal.test.cjs` through Node, requiring nonempty suites with every case passing and no skipped cases. They exercise capture, complete commit publication, retry handling, and stale-head rejection without network writes. The Java evidence and runtime contracts run alongside them. The standalone POM uses those same Java sources without building Eclipse plugins; the normal `sandbox_common_test` Maven gate discovers them too. The shell preflight keeps Git and the action runner real and supplies a mocked Docker executable.

### Selecting the analysis JVM

The optional `java-home` input names a JDK on the Linux runner. It is mounted read-only, and both `JAVA_HOME` and `PATH` are supplied inside the container. With no input, the image runtime is retained. The bundled workflow uses Java 25 so older image runtimes do not have to resolve Java-25 contributor projects. Project classpaths, compiler preferences, and source compatibility remain unchanged.
