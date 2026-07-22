# Build provenance

## Purpose

Every published snapshot and every validated commit on `main` must be traceable to an exact Git commit and exact GitHub Actions runs. Pull-request checks alone are not sufficient because a squash merge creates a new commit SHA on `main`.

Sandbox therefore publishes two complementary manifests:

1. **Commit provenance** records the exact post-merge Java CI, Core Module Build, CodeQL, Codacy Security Scan and Test Report runs for one `main` commit.
2. **Snapshot provenance** records the exact commit checked out by the product/update-site build, the Java CI run that authorized publication, and SHA-256 digests of the generated Tycho product tree and p2 repository.

## Published locations

- Latest validated commit: `https://carstenartur.github.io/sandbox/provenance/latest.json`
- Commit-specific manifest: `https://carstenartur.github.io/sandbox/provenance/<commit-sha>/build-manifest.json`
- Provenance dashboard: `https://carstenartur.github.io/sandbox/provenance/`
- Latest snapshot manifest: `https://carstenartur.github.io/sandbox/snapshots/latest/build-manifest.json`

The workflow artifacts use immutable names containing the full commit SHA and are retained for 90 days.

## Commit workflow

`.github/workflows/post-merge-provenance.yml` is triggered only after a successful `Java CI with Maven` run. Publication proceeds only when the triggering run represents a push to `main`.

The workflow checks out `github.event.workflow_run.head_sha`, not the moving default branch. It then waits until the following push workflows for that exact SHA have completed:

- Java CI with Maven;
- Core Module Build;
- CodeQL;
- Codacy Security Scan;
- Test Report.

A failed, missing or indefinitely incomplete required workflow prevents provenance publication. `Core Module Build` consequently runs for every `main` push, while retaining path filtering for pull requests.

## Snapshot workflow

`.github/workflows/deploy-snapshot.yml` also checks out the exact successful Java CI `head_sha`. The p2 repository is built only from that checkout. Its manifest contains:

- validated commit SHA and commit URL;
- source Java CI run ID and URL;
- project, Java, Maven, Tycho, SpotBugs and Eclipse target versions;
- SHA-256 hashes of target-definition files;
- static test totals and disabled-test count;
- complete product-tree and update-site tree digests;
- individual p2 metadata hashes;
- optional patched JDT UI commit/version when supplied by the product build.

This removes the race in which a later `main` commit could otherwise be checked out after the triggering Java CI run had finished.

## Manifest schema

The JSON document currently uses `schema_version: 1`. Top-level fields are:

- `repository`, `commit_sha`, `commit_url`, `ref` and `event_name`;
- `provenance_workflow`, including the source Java CI run;
- `toolchain`, including root POM properties, runtime versions, p2 repository URLs and target-definition hashes;
- `tests.inventory` from the repository test scanner and `tests.executed` when JUnit XML is available;
- `artifacts.products`, `artifacts.product_tree` and `artifacts.update_site`;
- `related_workflows` for the exact post-merge validation runs;
- `patched_jdt_ui` for the optional pinned fork provenance.

Tree digests are deterministic: files are sorted by repository-relative path, and the digest covers each path plus the SHA-256 of its bytes. This distinguishes equal files at different p2/product locations and is independent of filesystem traversal order.

## Local verification

Run the scripts without publishing:

```bash
python3 .github/scripts/test_build_provenance.py
python3 .github/scripts/generate_test_report.py
python3 .github/scripts/generate_build_manifest.py \
  --output build-manifest.json \
  --html-output build-provenance.html
python3 -m json.tool build-manifest.json
```

`collect_commit_workflows.py` additionally requires `GITHUB_TOKEN`, `GITHUB_REPOSITORY` and an exact commit SHA because it queries the GitHub Actions API.

## Security and operational boundaries

- Repository POM, target definitions and CI-generated XML are trusted inputs. The scripts do not process arbitrary uploaded XML.
- The collector accepts only workflow runs whose `head_sha` exactly matches the requested commit and whose event is `push`.
- A later rerun supersedes an earlier attempt of the same workflow for the same SHA.
- Pull-request validation never publishes to GitHub Pages.
- The provenance workflows do not search for or comment on unrelated pull requests.
- A manifest records `not_built_in_this_run` when no product or update-site output exists instead of implying that an artifact was validated.

Related issue: #1218.
