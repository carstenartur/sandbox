# Automatic cleanup suggestions in your GitHub project

Sandbox can analyse Java changes in a pull request and propose the complete
cleanup in a separate commit. **It does not automatically change the original
PR branch.** You review an ordinary GitHub diff and accept all changes together
by merging a cleanup PR into that original branch.

## Which project layout do I have?

| Your repository | How to start |
|---|---|
| Eclipse Java project with `.project`, `.classpath` and compiler preferences | Use the workflow below. Keep your existing Eclipse metadata and Java source level. |
| Several Eclipse projects under one repository, including a non-Java aggregator | Use the same workflow. The nearest enclosing Java `.project` determines each file's project. No root-level replacement project is needed. |
| Eclipse plug-ins / PDE / Tycho, such as JDT UI or JDT Core | Keep the native structure. A `.project` is not proof that the target platform and dependent bundles resolve in the cleanup image; see the dependency notes below. |
| Maven or Gradle project that already has valid Eclipse metadata | The native Eclipse layout is used, even when a `pom.xml` or Gradle build file is also present. |
| Maven or Gradle project without Eclipse metadata | Not a one-file setup for this action yet. Build files are not automatically imported. Missing project coverage fails visibly rather than claiming a successful analysis. |

## Install: one workflow for an existing Eclipse Java repository

Create `.github/workflows/cleanup.yml` in **your repository** using this complete
example. The action requires a Linux runner with Docker and GNU shell utilities.
No Sandbox checkout, copied profile, Maven build of Sandbox, personal
access token or additional secret is needed for same-repository PRs.

The example follows Sandbox's **development channel**. Before regular use,
replace `@main` with a reviewed full Sandbox commit SHA; also pin the `image`
input to a tested container digest. A source pin alone does not pin the runtime.

<!-- consumer-workflow:start -->
```yaml
name: Sandbox cleanup suggestions
on:
  pull_request:
    types: [opened, synchronize, reopened]
    paths: ['**/*.java', '**/.project', '**/.classpath', '**/.settings/**', '.github/workflows/cleanup.yml']
permissions:
  contents: read
concurrency:
  group: sandbox-cleanup-${{ github.event.pull_request.number }}
  cancel-in-progress: true
jobs:
  cleanup:
    if: github.event.pull_request.user.login != 'dependabot[bot]' && github.event.pull_request.head.repo.full_name == github.repository && !startsWith(github.event.pull_request.head.ref, 'cleanup/pr-')
    runs-on: ubuntu-latest
    permissions:
      contents: write
      pull-requests: write
    steps:
      - uses: actions/checkout@v7
        with:
          ref: ${{ github.event.pull_request.head.sha }}
          fetch-depth: 0
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '25'
      # main is the development channel. Pin a reviewed full commit SHA for regular use.
      - uses: carstenartur/sandbox/.github/actions/cleanup-review@main
        with:
          github-token: ${{ secrets.GITHUB_TOKEN }}
          base-sha: ${{ github.event.pull_request.base.sha }}
          head-sha: ${{ github.event.pull_request.head.sha }}
          java-home: ${{ env.JAVA_HOME }}
```
<!-- consumer-workflow:end -->

The remote `uses: carstenartur/sandbox/...` reference is intentional. A local
`uses: ./.github/actions/cleanup-review` would look for the action in **your**
repository. Do not copy Sandbox's own `pr-auto-cleanup.yml`: its extra contract
tests belong to development of Sandbox, not to users of the action.

The job's `contents: write` permission is needed to create a proposal branch;
`pull-requests: write` is needed to post the review. Organisation policies and
branch rules must permit those operations. A forbidden branch write falls back
to the complete patch; other failures remain visible. Automatic PR creation
permission is not needed: **Open cleanup PR** opens a form for you to submit.

The example does not filter to a branch named `main`, so it also works in
repositories using `master` or maintenance branches. Proposal heads beginning
with `cleanup/pr-` are excluded to prevent recursive proposals. Fork PRs are
intentionally excluded, including Dependabot PRs subject to fork-like token
restrictions. Do not enable write tokens for untrusted PR code or switch to
`pull_request_target` to bypass that boundary.

## Eclipse projects: preserve the existing structure

Do **not** run an Eclipse-project generator over an existing Eclipse checkout.
Keep `.project`, `.classpath`, `.settings`, project references, source folders,
exclusions and compiler compliance as maintained by that project. In particular,
a Tycho `pom.xml` is not a reason to replace PDE project metadata with a plain
Maven Java classpath. The action accepts only Java source changes in its result;
changes to project metadata are rejected.

The analysis runtime is Java 25, but the target project's source level stays
unchanged. The standalone example targets Java 11. The candidate-runtime test
compiles and executes it before and after cleanup without changing that level.
A locally configured JRE, absolute library path,
classpath variable, m2e/Buildship container, PDE target platform or reference to
another workspace project must also be resolvable in the headless environment.
This action does **not** provision an arbitrary Oomph workspace or resolve every
upstream target platform. Start with the conservative profile and inspect the
JSON reports and your project's normal build before accepting a proposal.

By default, only Java files changed by the PR are passed to the cleanup. An
import outside the original PR diff is nevertheless kept in the result.
`source-mode: project` broadens inputs to complete affected Eclipse projects;
it is not a coordinated multi-file refactoring or a dependency resolver. The
standard CLI still runs refactorings per compilation unit.

## Choosing the cleanup

Omitting `config-file` selects the **profile shipped with the action**. It enables
only conservative Explicit Encoding and preserves existing encoding behaviour:

```properties
cleanup.explicit_encoding=true
cleanup.explicit_encoding_keep_behavior=true
cleanup.explicit_encoding_insert_utf8=false
cleanup.explicit_encoding_aggregate_to_utf8=false
```

There is no hidden requirement to create `.github/cleanup-profiles/`. The built-in
file is mounted read-only from the installed action. To choose other cleanups,
commit a properties file in your repository and add, for example:

```yaml
          config-file: .github/cleanup-profiles/my-cleanup.properties
```

An explicitly requested missing profile is an error, not a reason to silently
switch cleanups. Choose one narrow cleanup per workflow for an attributable,
reviewable result. The older minimal/standard/aggressive profiles belong to the
separate build-from-source `cleanup-action`; they are not this review action's
defaults. See the [input reference](.github/actions/cleanup-review/README.md).

## Review and accept

1. Open or update a PR containing a Java change. The workflow runs automatically.
2. In the bot review, select **Review cleanup diff**. Imports, distant edits and
   companion files appear as normal diff hunks, not one giant replacement block.
3. Select **Open cleanup PR**, then **Create pull request**. Check that its **base**
   is your original feature branch, not your default branch.
4. Review and merge that cleanup PR. All proposed changes enter the original PR
   branch together. Run/check that original PR's normal build and tests before
   merging it into the default branch. Close the cleanup PR to decline it.

The bot creates a proposal branch and commit, not the helper PR or its merge.
Use the latest proposal when the source PR advances. A comparison against the
recorded SHA remains a view of the old result; it is not a promise of current
applicability. Repository review/merge rules still apply. Delete an obsolete
proposal branch only when no open cleanup PR still needs it.

Workflows restricted to PRs **targeting** the default branch do not run on helper
PRs targeting feature branches. Updating the original PR starts its matching
checks. Branch writes using `GITHUB_TOKEN` do not by themselves start push
workflows. See GitHub's [workflow/token behaviour](https://docs.github.com/en/actions/concepts/security/github_token)
and [PR-form links](https://docs.github.com/en/pull-requests/reference/using-query-parameters-to-create-a-pull-request).

For an actual published result, see the
[compact bot review from PR #1674](https://github.com/carstenartur/sandbox/pull/1674#pullrequestreview-5458550027)
and its [complete three-file diff](https://github.com/carstenartur/sandbox/compare/79f94e411cee911a775bfa4ca7fd91e86c6b43ef...22a322ca1070ccacd0a67bdc556923941c989abc).
These are historical examples, not proposals for the current head.

## Diagnose a run

Read the Actions job summary first. `analysis-status` distinguishes these cases:

| Status | Meaning / next step |
|---|---|
| `changes-proposed` | Cleanup produced source changes; inspect the posted review or patch fallback. |
| `no-cleanup-changes` | The runner completed for the selected projects without source changes. This is not a compilation or whole-repository quality guarantee. |
| `no-java-changes` | No applicable Java input in this PR comparison; no project was analysed. |
| `no-supported-projects` | Java input exists, but none belongs to an Eclipse Java project. Fix project setup; do not interpret this as clean code. |
| `partial-analysis` | Some input Java files have no Eclipse Java project. Default behaviour stops before cleanup. |
| `failed` | Preparation or execution failed; inspect the log. Argument, checkout and profile validation also report this state. |

Missing or partial project coverage is an error by default. A repository that
**intentionally** contains standalone Java tooling outside Eclipse projects can
opt in to `unmatched-files: warn`. That explicitly permits partial coverage; the
summary still says which files were not analysed, and evidence is retained.
Disabling the workflow is preferable to accepting an unexplained zero-coverage
run. Do not mark this development-stage advisory workflow as a required check
until its path filters and source coverage fit your repository.

For missing-profile, dirty-checkout or permission errors, read `cleanup.log` in
the evidence artifact. Run from the exact clean PR head with `fetch-depth: 0`.
The artifact also retains `suggestions.patch`, input/skipped-file manifests,
JSON execution reports and, for a proposal, `review.json` and `review.md`.
An image must be accessible to the runner; do not build all of Sandbox to fix a
missing image permission. There is no guaranteed runtime or cache duration.

## Maven and Gradle without Eclipse metadata

A correct adapter must obtain the build's **resolved model**: source sets,
compiler release, generated sources, module dependencies and classpath. Guessing
`src/main/java` or creating a generic `.project` can silently lose semantics.
Such an automatic adapter is not implemented in the review action yet.

Gradle provides an [Eclipse plugin](https://docs.gradle.org/current/userguide/eclipse_plugin.html)
for exporting Eclipse metadata. Exporting a developer project is not, by itself,
a qualified CI import: cache paths and containers still need to resolve inside
the cleanup runtime. Do not generate files into the clean review checkout and
then commit or hide them just to pass the action's source-change checks.
Existing Eclipse metadata takes precedence; it must never be overwritten by a
future automatic build adapter. The [separate Maven plugin](sandbox-maven-plugin/README.md)
is a different CLI integration, not a drop-in replacement for this PR publisher.

## Standalone example and verification

[The complete consumer example](examples/cleanup-review/eclipse) includes an
ordinary Eclipse Java 11 project and its external-action workflow. Existing
Eclipse repositories copy only the workflow, not the example metadata.

Maintainers run the same Maven/JUnit authority as CI:

```bash
./mvnw --batch-mode -f sandbox_common_test/pom-cleanup-review.xml test
# Qualify an explicitly selected image; it must include the source changes being tested:
./mvnw --batch-mode -f sandbox_common_test/pom-cleanup-review.xml -Pconsumer-e2e \
  -Dcleanup.review.image=YOUR_TESTED_IMAGE verify
```

A historical published image with digest
`sha256:329b12a6daeffc9899d68c459e0c7dbe1710f7f16e80c327af400aceb3fbf264`
incorrectly restricted `Charset.forName` migration to Java 18+. This change
corrects its DSL guard to Java 7, matching the existing Java handler. Java 11
consumers need an image built with that correction; updating only the action
reference cannot fix an older runtime image.

The fast regressions use separate temporary Git repositories with no Sandbox
modules and mock only Docker's process boundary. `consumer-e2e` instead uses the
selected image for single and nested Eclipse-project layouts and generic or
execution-environment JRE containers. CI runs these tests in the existing Linux
distribution job, building the candidate image from its already verified CLI
archive. It does not rebuild Sandbox in a separate consumer pipeline or re-run
the fast Surefire suite. The tests verify that `.project`, `.classpath` and
compiler preferences remain byte-identical; the distribution artifact retains
the execution evidence. This does not claim an independently hosted sample
repository, authenticated GitHub UI screenshots or an automated acceptance merge.
