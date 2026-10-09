# Sandbox Cleanup Review — action reference

For setup in your own repository, supported Eclipse/PDE layouts, the complete
workflow and acceptance walkthrough, start with the
[GitHub Actions integration guide](../../../GITHUB_ACTIONS.md).
The [standalone Eclipse consumer](../../../examples/cleanup-review/eclipse)
contains no Sandbox build modules.

This action runs the headless cleanup image and publishes one complete result
commit on a separate `cleanup/pr-…` branch. Its short review links to an ordinary
GitHub diff and a form to open a cleanup PR **into the original PR branch**.
It never automatically pushes to that branch, opens the helper PR or merges it.

**Runner:** Linux with Docker and GNU shell utilities, as in the Ubuntu example.

## Inputs

| Input | Default | Meaning |
|---|---|---|
| `github-token` | required | Token for proposal branch and review publication. |
| `base-sha` | required | Pull-request base commit. |
| `head-sha` | required | Exact, clean, checked-out PR head; use full Git history. |
| `config-file` | empty | Empty uses the shipped conservative encoding profile; otherwise a repository-relative properties file. |
| `image` | `ghcr.io/carstenartur/sandbox-cleanup:latest` | Runtime image; pin a tested digest for reproducibility. |
| `java-home` | empty | Optional host JDK mounted read-only. The example supplies Java 25 without changing target-project source levels. |
| `scope` | `both` | `main`, `test`, or `both`. |
| `source-mode` | `changed` | `changed` passes PR Java files; `project` broadens inputs to each affected Eclipse project. Neither adds build-system import or coordinated multi-file execution. |
| `unmatched-files` | `error` | `error` stops if a changed Java file has no Eclipse Java project; `warn` explicitly permits incomplete coverage. |
| `tool-name` | `sandbox-cleanup` | Name shown in the review. |
| `artifact-name` | `sandbox-cleanup-review` | Use a distinct name for each action invocation in a workflow. |
| `upload-artifact` | `true` | Retain evidence for changes, incomplete coverage and cleanup failure. |

`contents: write` and `pull-requests: write` are needed only by the publishing
job. Use `pull_request` with the same-repository and `cleanup/pr-` guards shown
in the integration guide. Never run fork code with a write-capable token.

## Outputs

`has-changes`, `changed-file-count`, `project-count`, `skipped-file-count`,
`patch-file`, `artifact-url`, and `analysis-status` are available. The status
values are `changes-proposed`, `no-cleanup-changes`, `no-java-changes`,
`no-supported-projects`, `partial-analysis`, and `failed`. A zero-change result
is not evidence of complete project resolution or successful compilation.

## Capture and publication guarantees

The nearest enclosing Java `.project` selects the input project. Native Eclipse
metadata is reused without generating or changing project descriptors. A
non-Java Eclipse project is an authoritative boundary, not an invitation to
associate its files with a Java parent. Missing metadata is reported before
Docker starts. Existing compiler preferences and classpaths are not rewritten.

The runner invokes the configured cleanup once per affected project with an
isolated temporary Eclipse workspace. Its default CLI performs a complete
`Change` per compilation unit; project-wide semantic planning is a separate
execution capability. See [coordinated multi-file cleanups](../../../docs/multi-file-cleanups.md).

Git generates the authoritative complete patch, including off-diff imports and
new/deleted Java files. Capture compares the complete patch result with the
complete committable working tree, preserving the real index. Publication
verifies blob/tree identity, checks the exact current PR head, and reuses only
matching proposal branches and standard Actions-bot reviews on retries. It
never force-pushes to contributor branches.

Evidence is uploaded before publication. GitHub denying branch creation gives
a complete-patch fallback; oversized fallbacks require the artifact. Other API
failures remain visible. Explicit custom profiles must exist inside the caller's
repository. The built-in profile is loaded from the installed action and mounted
read-only, not copied into the caller's checkout.

## Maintainer verification

```bash
bash -n .github/actions/cleanup-review/run-cleanup-review.sh
.github/actions/cleanup-review/test/run-cleanup-review-test.sh
./mvnw --batch-mode -f sandbox_common_test/pom-cleanup-review.xml test
./mvnw --batch-mode -f sandbox_common_test/pom-cleanup-review.xml -Pconsumer-e2e -Dcleanup.review.image=YOUR_TESTED_IMAGE verify
```

Maven/JUnit explicitly executes the publisher, proposal and consumer Node suites,
requiring nonempty suites with no failing or skipped cases. The Linux-only
consumer runner suite is qualified on Linux; the other suites remain cross-platform.
The separate
`consumer-e2e` profile runs real container qualification on the standalone
consumer. The Linux distribution job selects its freshly built candidate image.
These maintainer tests are not required when using the action in your own project.
