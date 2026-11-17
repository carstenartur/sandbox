# GitHub Actions for Automated Code Cleanup

This directory contains GitHub Actions workflows and a custom action for automated Eclipse JDT code cleanup using the sandbox cleanup application.

## Quick Start

### For Pull Requests (Automatic suggestions)

For installation in **your own repository**, use the complete
[GitHub Actions integration guide](../../GITHUB_ACTIONS.md). Native Eclipse Java
projects keep their `.project`, `.classpath` and compiler preferences; neither
a Sandbox checkout nor a copied cleanup profile is required.

Here, `pr-auto-cleanup.yml` runs the shipped conservative encoding profile and
publishes a complete result commit on a separate cleanup branch. The review
links to the ordinary GitHub diff and a cleanup-PR form targeting the original
PR branch. It does **not** automatically push changes to that original branch.

The local workflow also runs Sandbox's own Maven/JUnit contract and consumer
qualification tests. External users copy the guide's workflow, not this
maintainer workflow or the Sandbox test modules.

### For Manual Cleanup

Use the `manual-cleanup.yml` workflow when you want to:
- Clean a specific branch
- Choose a different cleanup profile (minimal/standard/aggressive)
- Clean only a specific directory
- Preview changes without committing

**To run**:
1. Go to **Actions** tab in GitHub
2. Select **Manual Cleanup** workflow
3. Click **Run workflow**
4. Choose your options

## Available Workflows

### Release Workflow (`deploy-release.yml`)

The release workflow builds the stable distribution, publishes its Eclipse update
site and GitHub Release, and deploys the reusable Maven modules from the same
release commit to Maven Central. Maintainers can start it manually or by pushing
a release request. See the [release procedure](#detailed-release-process).

### 1. Fix NLS Comments (`fix-nls.yml`)

**Triggers**: Automatically on PR opened/synchronized (on PRs that modify `.java` files)
- Only runs for PRs created by `copilot[bot]`
- Or when PR is labeled with `auto-fix-nls`

**What it does**:
- Scans plugin source directories (not test modules)
- Adds missing `//$NON-NLS-n$` comments to string literals
- Commits and pushes changes if needed
- Preserves existing NLS comments (no duplicates)

**Directories processed**:
- ✅ All `sandbox_*/` and `sandbox-*/` src directories (plugin modules)
- ❌ Excludes `*_test/` directories (test modules)
- ❌ Excludes `sandbox_test_commons/`, `sandbox_web/`, etc.

**Limitations**:
- Only processes single-line statements ending with `;`, `)`, or `}`
- Multi-line string concatenations are not fully supported
- Uses simple quote counting (may not handle all edge cases with escape sequences)

**Use cases**:
- GitHub Copilot creates code without NLS comments
- Automatic cleanup of Eclipse internationalization warnings
- Ensures string literals are properly marked for translation

**Manual trigger**: Add the `auto-fix-nls` label to any PR to manually trigger this workflow.

### 2. Cleanup Review (`pr-auto-cleanup.yml`)

**Triggers**: relevant PR changes targeting `main`; manual dispatch runs the
contract/qualification job, not PR publication without a PR context.

The read-only contract job runs the fast Maven/JUnit contracts. The existing
Linux distribution job qualifies the native consumer against its freshly built
candidate image, reusing the verified CLI archive. The publishing job uses the exact PR head and the
[cleanup-review action](../actions/cleanup-review/README.md) with scoped
`contents: write` and `pull-requests: write`. Fork heads and cleanup proposal
heads do not publish further proposals.

The default is the shipped conservative encoding profile. Set `config-file`
only for an intentional repository-owned override. `scope` and `source-mode`
control inputs, not Maven/Gradle import or coordinated cross-file refactoring.
Sandbox explicitly uses `unmatched-files: warn` because it contains standalone
Java CI scripts; external consumers default to an error on incomplete project
coverage. Always inspect `analysis-status` and skipped-file counts.

### 3. Manual Cleanup (`manual-cleanup.yml`)

**Triggers**: Manual dispatch from GitHub Actions UI

**What it does**:
- Allows you to specify branch, profile, and directory
- Shows git diff of changes
- Optionally commits and pushes changes
- Provides summary in workflow output

**Options**:
- **branch**: Which branch to clean (default: current)
- **cleanup_profile**: minimal/standard/aggressive
- **source_dir**: Directory to clean (default: `.`)
- **commit_changes**: Whether to push changes (default: true)
- **verbose**: Enable verbose logging (default: true)

## Cleanup actions: choose the right entry point

| Need | Entry point |
|---|---|
| Review automatic cleanup proposals in an existing Eclipse repository | [Consumer guide](../../GITHUB_ACTIONS.md) and [cleanup-review](../actions/cleanup-review/README.md). Uses a prebuilt image and a shipped conservative default; accepts changes through a separate cleanup PR. |
| Try a complete external example | [Standalone Eclipse project](../../examples/cleanup-review/eclipse), including its own external-action workflow. Existing projects copy only that workflow. |
| Run the older Sandbox-local build-and-apply route manually | `manual-cleanup.yml` and [cleanup-action](../actions/cleanup-action/README.md). This is a different action, not the PR-review setup. |

The `minimal`, `standard` and `aggressive` profiles under
`.github/cleanup-profiles` belong to the configurable/manual route. They are
not the default of the current PR-review workflow. Do not add an independent
`git add .; git commit; git push` step after the review action: that would bypass
the explicit acceptance workflow.

### Configuration and troubleshooting

The authoritative [consumer guide](../../GITHUB_ACTIONS.md) covers custom
profiles, permissions, source selection, Eclipse/PDE dependencies and status
outputs. A source SHA and a container digest pin different components; neither
implies that a consumer's target platform has been resolved.

**Can external repositories use it?** Yes, native Eclipse Java repositories can
reference `carstenartur/sandbox/.github/actions/cleanup-review@<reviewed-ref>`.
Use the complete workflow in the guide. No source build of Sandbox is required.

**What about forks?** The bundled publisher is limited to same-repository PRs.
Do not switch to `pull_request_target` and execute untrusted head code with
write permissions. A separate unprivileged analysis/validating publisher is
needed for a broader contribution flow.

**What does a green no-change result mean?** Read `analysis-status`.
`no-java-changes` means nothing was analysed; `no-supported-projects` means
Java inputs were not assigned to Eclipse Java projects. Neither is a clean-code
claim. Missing coverage errors by default; `unmatched-files: warn` is an
explicit opt-in to incomplete coverage.

**How do I accept or reject?** Review the complete diff, open the cleanup PR,
and merge it into the original PR branch to accept. To reject, do not merge;
there is no automatic update to undo. Run your project's checks on the result.

**Why can a run take time?** The review route pulls a prebuilt image, then
starts Eclipse for each affected project. Download, startup and dependency
resolution costs vary. Old timings for building Sandbox on the runner do not
describe this route.

**How do I disable it?** Disable the workflow in GitHub Actions or remove the
consumer workflow file. No implicit `[skip cleanup]` title convention exists.

## CI & Testing Workflows

### Distribution Smoke Test (`distribution-smoke.yml`)

Pull requests, pushes to `main`, scheduled runs and normal manual dispatches
verify the complete distribution on Ubuntu 24.04 and Windows 2025 with Java 25.
Each runner executes the Maven distribution build and installed-mathematics
verification. Linux also verifies native cleanup consumers in the candidate
Docker image.

Tycho continues to assemble Linux, Windows and macOS product archives. Native
macOS execution is outside this CI matrix and is not a release prerequisite.
The [distribution compatibility guide](../../docs/distribution-compatibility.md)
describes the resulting coverage and the commands for local native verification.
The mathematics plug-in resolves its [pinned Regelsuche SDK](../../sandbox_math_cleanup/lib/README.md) from Maven Central during the build.

The canonical product verification and the quality-evidence publication are deliberately separate:

- `maven.yml` provides the normal Maven/Tycho build signal for relevant pushes and pull requests. It does not publish the authoritative `/tests/` or `/coverage/` pages.
- `test-report.yml` inventories test sources and guards against accidentally losing registered tests. It is not the public test-report publisher.
- `coverage.yml` runs the complete quality-evidence path and is the sole publisher of the measured test and coverage pages described below.

### Verified test and coverage metrics (`coverage.yml`)

**Triggers**:
- relevant pull requests targeting `main`;
- relevant pushes to `main`;
- manual workflow dispatch;
- daily schedule, skipped when no commit was made in the preceding 24 hours.

**Verification command**:

```bash
xvfb-run --auto-servernum mvn \
  --no-transfer-progress \
  -e -V -T 1C --batch-mode \
  -Dtycho.localArtifacts=ignore \
  -Pjacoco,reports,product,repo,benchmark,cli-dist,maven-plugin \
  clean verify
```

The workflow uses established report consumers rather than repository-specific XML parsers:

- `mikepenz/action-junit-report@v6` reads all Surefire and Failsafe `TEST-*.xml` files and supplies the exact total, passed, failed, and skipped testcase counts;
- `cicirello/jacoco-badge-generator@v2` reads the aggregate `sandbox_coverage/target/site/jacoco-aggregate/jacoco.csv` and generates the instruction-coverage Shields endpoint;
- Maven's `reports` profile produces the module HTML reports copied below `/tests/`.

Pull-request and non-`main` branch runs execute and validate the complete generation path but never mutate GitHub Pages. A successful push to `main`, manual run on `main`, or applicable scheduled run publishes all of the following in one update:

- `badges/tests.json`;
- `badges/coverage.json`;
- `quality-summary.json`, including the source commit and measured values;
- `tests/index.html` and every discovered Maven module report;
- the aggregate JaCoCo HTML report below `/coverage/`.

The evidence artifact name includes both `github.run_number` and `github.run_attempt`, so reruns cannot collide with an immutable artifact from an earlier attempt. Failed builds retain whatever JUnit and JaCoCo evidence is available but cannot replace the last successful public values.

### Published reports

- Test totals and module reports: <https://carstenartur.github.io/sandbox/tests/>
- Aggregate JaCoCo report: <https://carstenartur.github.io/sandbox/coverage/>
- Commit-bound machine-readable summary: <https://carstenartur.github.io/sandbox/quality-summary.json>

For the detailed evidence contract and local reproduction instructions, see [`docs/quality-metrics.md`](../../docs/quality-metrics.md).

## Related Documentation

- [Published test and coverage metrics](../../docs/quality-metrics.md)
- [Sandbox Cleanup Application](../../sandbox_cleanup_application/README.md)
- [PR cleanup integration](../../GITHUB_ACTIONS.md)
- [Cleanup review action reference](../actions/cleanup-review/README.md)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)

## License

Eclipse Public License 2.0

SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0

---

## Detailed Release Process

### Start a release

Choose **Actions → Release Workflow → Run workflow**. Enter the numeric part of
the current development version in `release_version`. For example, a project
at `1.3.10-SNAPSHOT` can release `1.3.10`. The workflow advances the next
development version by one patch by default. Choose a minor or major increment
in `next_version_increment`, or supply an exact `next_development_version`.
For an existing release request branch, the same workflow reads the version
from the branch's `.github/release-request` marker.

Configure the Sandbox repository's Actions secrets before publishing.

| Secret | Value |
| --- | --- |
| `CENTRAL_USERNAME` | Central Portal user token username with publishing access to `io.github.carstenartur` |
| `CENTRAL_PASSWORD` | Corresponding user token password |
| `MAVEN_GPG_KEY` | ASCII-armored signing private key whose public key is available to Central |
| `MAVEN_GPG_PASSPHRASE` | Passphrase when the signing key is protected |

Select `dry_run` to build the stable sources, Maven artifacts and Eclipse
distribution and inspect the release evidence. Signing and publication run
only for a real release. `skip_tests` is available for diagnostic dry runs.

### Release artifacts

| Destination | Contents |
| --- | --- |
| [Maven Central](https://repo.maven.apache.org/maven2/io/github/carstenartur/) | `sandbox-ast-api`, `sandbox-ast-api-jdt`, `sandbox-functional-converter-core`, `sandbox_common_core` and `sandbox-maven-plugin` under `io.github.carstenartur`, at the Sandbox release version |
| [Eclipse update site](https://carstenartur.github.io/sandbox/releases/) | Installable plug-ins and features, with a versioned p2 repository at `releases/X.Y.Z/` |
| [GitHub Releases](https://github.com/carstenartur/sandbox/releases) | Stable source archives, cleanup CLI distributions, release notes and verification reports |

The release job verifies the public p2 repository, tag archives and draft
GitHub Release before publishing the release. The Central job then checks out
that verified commit, signs the Maven artifacts and waits for the Portal to
report publication. A separate pull request advances `main` to the next
development version. The workflow also opens the Marketplace and Zenodo
follow-up issue.

### Retry a failed Central deployment

If the GitHub Release exists and the Central job failed, inspect the job log
and the deployment in the [Central Portal](https://central.sonatype.com/publishing/deployments).
If the Portal is still publishing, allow that deployment to finish. After a
failed deployment or a credentials correction, choose **Re-run failed jobs** in the
original release run. The Central job reuses the verified release commit.
