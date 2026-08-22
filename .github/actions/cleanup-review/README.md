# Sandbox Cleanup Review Suggestions

This composite action runs the headless Sandbox Eclipse cleanup application on Java files changed by a pull request and publishes the resulting working-tree diff as GitHub **Suggested Changes**.

A contributor can apply one suggestion with **Commit suggestion**, batch several suggestions, or reject a proposal by leaving it unapplied and resolving the review conversation. The complete Git patch and the cleanup JSON reports are retained as a workflow artifact when changes are found, even when GitHub temporarily rejects review publication.

## How it works

1. The caller checks out the exact pull-request head with full Git history.
2. The action identifies changed Java files from `base...head`.
3. Each file is assigned to its nearest ancestor containing an Eclipse `.project` file.
4. The configured Sandbox cleanup runs once per affected Eclipse project in an isolated temporary workspace.
5. The cleanup changes remain only in the ephemeral Actions checkout.
6. `reviewdog/action-suggester` converts the real Git working-tree diff into applicable GitHub review suggestions.

The action deliberately lets Git generate the patch. The cleanup application's own `--patch` output remains diagnostic evidence and is not used to place review comments.

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
```

## Inputs

| Input | Default | Meaning |
|---|---|---|
| `github-token` | required | Token used to publish the review. |
| `base-sha` | required | Pull-request base commit. |
| `head-sha` | required | Exact checked-out pull-request head. |
| `config-file` | conservative encoding profile | Repository-relative cleanup properties file. |
| `image` | `ghcr.io/carstenartur/sandbox-cleanup:latest` | Cleanup runtime image. |
| `scope` | `both` | `main`, `test`, or `both`. |
| `source-mode` | `changed` | `changed` passes only PR Java files; `project` passes each complete affected Eclipse project. |
| `tool-name` | `sandbox-cleanup` | Name displayed in the review. |
| `artifact-name` | `sandbox-cleanup-review` | Patch/report artifact name. |
| `upload-artifact` | `true` | Retain the complete patch and reports according to the repository artifact-retention policy. |

## Choosing a cleanup

Use one narrowly scoped properties file per review workflow. This keeps every suggestion attributable to a single cleanup and avoids combining unrelated rewrites in one review hunk.

The default profile enables only the conservative Explicit Encoding strategy:

```properties
cleanup.explicit_encoding=true
cleanup.explicit_encoding_keep_behavior=true
cleanup.explicit_encoding_insert_utf8=false
cleanup.explicit_encoding_aggregate_to_utf8=false
```

A repository can add another properties file and point `config-file` at it without changing the action.

## Safety boundaries

- The script fails if the checkout does not match `head-sha`, tracked files are already dirty, a path escapes the repository, or a cleanup modifies a non-Java file.
- Every Eclipse project receives a separate temporary workspace, avoiding project-name collisions.
- Files without an ancestor `.project` are reported and skipped rather than processed without bindings.
- GitHub can attach Suggested Changes only where its pull-request diff exposes a valid comment range. The artifact contains the complete patch when some cleanup changes cannot be placed inline.
- The bundled workflow publishes reviews only for branches in the same repository. External fork support needs a separate unprivileged analysis and privileged, validating publisher workflow; it must not execute fork code with a write-capable token.
- Transient review-publisher failures receive one controlled retry. The action restores the working-tree diff first because the pinned suggester leaves it stashed when its GitHub API call fails.
- The container image is recorded by resolved repository digest in the job summary when Docker exposes one. Production consumers may replace `latest` with a release tag or digest.

## Verification

```bash
bash -n .github/actions/cleanup-review/run-cleanup-review.sh
.github/actions/cleanup-review/test/run-cleanup-review-test.sh
```

The contract test uses a mocked Docker executable and verifies project discovery, paths containing spaces, exact changed-file selection, patch generation, report generation, skipped files, and the no-Java no-op path.
