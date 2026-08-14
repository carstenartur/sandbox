#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

REPOSITORY = "carstenartur/sandbox"
BASELINE_INTEGRATION = "23ac5f395c9d9a80dba35d9cb906d86a3d465a66"
APP_PATH = Path("sandbox_cleanup_application/src/org/sandbox/jdt/core/cleanupapp/ProjectWideCodeCleanupApplication.java")
UTILITY_PATH = Path("sandbox_common_core/src/main/java/org/sandbox/jdt/cleanup/multifile/api/LineDelimiterPreserver.java")
TEST_PATH = Path("sandbox_common_core/src/test/java/org/sandbox/jdt/cleanup/multifile/api/LineDelimiterPreserverTest.java")
POM_PATH = Path("sandbox_common_core/pom.xml")


def run(args: list[str], cwd: Path | None = None, *, check: bool = True, capture: bool = False) -> subprocess.CompletedProcess[str]:
    print("+", " ".join(args), flush=True)
    result = subprocess.run(args, cwd=cwd, text=True,
                            stdout=subprocess.PIPE if capture else None,
                            stderr=subprocess.STDOUT if capture else None)
    if capture and result.stdout:
        print(result.stdout, end="")
    if check and result.returncode != 0:
        raise SystemExit(result.returncode)
    return result


def output(name: str, value: str) -> None:
    target = os.environ.get("GITHUB_OUTPUT")
    if target:
        with open(target, "a", encoding="utf-8") as stream:
            stream.write(f"{name}={value}\n")
    print(f"{name}={value}")


def gh_json(path: str) -> dict:
    result = run(["gh", "api", path], capture=True)
    return json.loads(result.stdout)


def configure(repository: Path) -> None:
    run(["git", "config", "user.name", "Carsten Hammer"], cwd=repository)
    run(["git", "config", "user.email", "carsten.hammer@t-online.de"], cwd=repository)
    run(["git", "fetch", "--prune", "origin", "+refs/heads/*:refs/remotes/origin/*"], cwd=repository)


def prepare_1472(repository: Path, artifact: int) -> None:
    configure(repository)
    pull = gh_json(f"repos/{REPOSITORY}/pulls/1472")
    if pull.get("merged"):
        output("open", "false")
        output("head", pull["head"]["sha"])
        return

    run(["git", "checkout", "feature/junit4-preset-model"], cwd=repository)
    run(["git", "reset", "--hard", "origin/feature/junit4-preset-model"], cwd=repository)
    workflow = run(["git", "show", "origin/main:.github/workflows/eclipse-help-screenshots.yml"],
                   cwd=repository, capture=True).stdout
    if any(token in workflow for token in ("Commit regenerated JUnit screenshot", "git push", "contents: write")):
        raise SystemExit("The main Help workflow is not a read-only verifier")
    workflow_path = repository / ".github/workflows/eclipse-help-screenshots.yml"
    workflow_path.write_text(workflow, encoding="utf-8")

    archive = repository.parent / "junit-help.zip"
    with archive.open("wb") as stream:
        result = subprocess.run(["gh", "api", f"repos/{REPOSITORY}/actions/artifacts/{artifact}/zip"], stdout=stream)
        if result.returncode != 0:
            raise SystemExit(result.returncode)
    extract = repository.parent / "junit-help-artifact"
    shutil.rmtree(extract, ignore_errors=True)
    extract.mkdir(parents=True)
    with zipfile.ZipFile(archive) as source:
        source.extractall(extract)
    candidates = list(extract.rglob("junit-migration-cleanup.png"))
    if len(candidates) != 1:
        raise SystemExit(f"Expected one generated JUnit screenshot, found {candidates}")
    image = candidates[0].read_bytes()
    if not image.startswith(b"\x89PNG\r\n\x1a\n"):
        raise SystemExit("Generated JUnit Help image is not a PNG")
    destination = repository / "sandbox_junit_cleanup_help/images/junit-migration-cleanup.png"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(image)

    run(["git", "add", ".github/workflows/eclipse-help-screenshots.yml",
         "sandbox_junit_cleanup_help/images/junit-migration-cleanup.png"], cwd=repository)
    if run(["git", "diff", "--cached", "--quiet"], cwd=repository, check=False).returncode != 0:
        run(["git", "commit", "-m", "Commit reproducible JUnit Help screenshot"], cwd=repository)
        run(["git", "push", "origin", "HEAD:feature/junit4-preset-model"], cwd=repository)
    head = run(["git", "rev-parse", "HEAD"], cwd=repository, capture=True).stdout.strip()
    output("open", "true")
    output("head", head)


def commit_regenerated_1472(repository: Path) -> None:
    configure(repository)
    changes = run(["git", "status", "--porcelain=v1", "--untracked-files=all", "--",
                   ":(glob)sandbox*_help/images/*.png"], cwd=repository, capture=True).stdout.splitlines()
    if not changes:
        output("head", run(["git", "rev-parse", "HEAD"], cwd=repository, capture=True).stdout.strip())
        return
    allowed = "sandbox_junit_cleanup_help/images/junit-migration-cleanup.png"
    unexpected = [entry for entry in changes if entry[3:] != allowed]
    if unexpected:
        raise SystemExit(f"Unexpected Help screenshot changes: {unexpected}")
    run(["git", "add", allowed], cwd=repository)
    run(["git", "commit", "-m", "Refresh reproducible JUnit Help screenshot"], cwd=repository)
    run(["git", "push", "origin", "HEAD:feature/junit4-preset-model"], cwd=repository)
    output("head", run(["git", "rev-parse", "HEAD"], cwd=repository, capture=True).stdout.strip())


def recover(repository: Path, path: Path, required: str) -> str:
    destination = repository / path
    if destination.is_file():
        content = destination.read_text(encoding="utf-8", errors="replace")
        if required in content:
            return content
    return run(["git", "show", f"{BASELINE_INTEGRATION}:{path.as_posix()}"],
               cwd=repository, capture=True).stdout


def transform_application(content: str) -> str:
    content = content.replace("import java.io.ByteArrayOutputStream;\n", "")
    diagnostics_import = "import org.sandbox.jdt.cleanup.multifile.api.IMultiFileCleanUpDiagnosticsProvider;"
    utility_import = "import org.sandbox.jdt.cleanup.multifile.api.LineDelimiterPreserver;"
    if utility_import not in content:
        content = content.replace(diagnostics_import, diagnostics_import + "\n" + utility_import)

    replacement = '''
\tprivate static void preserveOriginalLineDelimiters(List<SourceSnapshot> sources) throws IOException, CoreException {
\t\tfor (SourceSnapshot source : sources) {
\t\t\tbyte[] current= Files.readAllBytes(source.path());
\t\t\tbyte[] normalized= LineDelimiterPreserver.preserve(source.before(), current,
\t\t\t\t\tsource.file().getCharset(true));
\t\t\tif (!Arrays.equals(current, normalized)) {
\t\t\t\tFiles.write(source.path(), normalized);
\t\t\t}
\t\t}
\t}

\tprivate static void createParent'''
    raw_pattern = re.compile(
        r"\n\tprivate enum OriginalLineDelimiter\b.*?\n\tprivate static void createParent",
        re.DOTALL,
    )
    utility_pattern = re.compile(
        r"\n\tprivate static void preserveOriginalLineDelimiters\(List<SourceSnapshot> sources\).*?\n\tprivate static void createParent",
        re.DOTALL,
    )
    if raw_pattern.search(content):
        content = raw_pattern.sub("\n" + replacement, content, count=1)
    elif utility_pattern.search(content):
        content = utility_pattern.sub("\n" + replacement, content, count=1)
    else:
        marker = "\n\tprivate static void createParent"
        if marker not in content:
            raise SystemExit("Cannot locate line-delimiter helper insertion point")
        content = content.replace(marker, "\n" + replacement, 1)
    if "LineDelimiterPreserver.preserve" not in content or "ByteArrayOutputStream" in content:
        raise SystemExit("Charset-aware application transformation was incomplete")
    return content


def ensure_junit_dependency(repository: Path, content: str) -> str:
    if "junit-jupiter" in content:
        return content
    donor = None
    for pom in sorted(repository.rglob("pom.xml")):
        if pom == repository / POM_PATH:
            continue
        text = pom.read_text(encoding="utf-8", errors="ignore")
        for dependency in re.findall(r"(?s)<dependency>.*?</dependency>", text):
            if "<artifactId>junit-jupiter</artifactId>" in dependency:
                donor = dependency
                break
        if donor:
            break
    if donor is None:
        for pom in sorted(repository.rglob("pom.xml")):
            text = pom.read_text(encoding="utf-8", errors="ignore")
            for dependency in re.findall(r"(?s)<dependency>.*?</dependency>", text):
                if "<artifactId>junit-jupiter-api</artifactId>" in dependency:
                    donor = dependency
                    break
            if donor:
                break
    if donor is None:
        raise SystemExit("No managed JUnit Jupiter test dependency was found")
    donor = "\n".join("\t" + line if line.strip() else line for line in donor.strip().splitlines())
    if "</dependencies>" in content:
        return content.replace("</dependencies>", donor + "\n\t</dependencies>", 1)
    return content.replace("</project>", "\t<dependencies>\n" + donor + "\n\t</dependencies>\n</project>", 1)


def reconcile_1474(repository: Path, automation: Path) -> None:
    configure(repository)
    run(["git", "checkout", "integration/junit-migration-hardening"], cwd=repository)
    run(["git", "reset", "--hard", "origin/integration/junit-migration-hardening"], cwd=repository)
    merge = run(["git", "merge", "--no-edit", "origin/main"], cwd=repository, check=False)
    if merge.returncode != 0:
        conflicts = run(["git", "diff", "--name-only", "--diff-filter=U"], cwd=repository, capture=True).stdout.splitlines()
        preserve_integration = {
            "sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/JUnit4MigrationPresets.java",
            "sandbox_junit_cleanup/src/org/sandbox/jdt/internal/ui/preferences/cleanup/CleanUpMessages.properties",
            "sandbox_junit_cleanup/src/org/sandbox/jdt/internal/ui/preferences/cleanup/SandboxCodeTabPage.java",
            "sandbox_junit_cleanup_test/src/org/sandbox/jdt/internal/corext/fix/JUnit4MigrationPresetsTest.java",
            "sandbox_junit_cleanup_help/images/junit-migration-cleanup.png",
        }
        for path in conflicts:
            side = "--ours" if path in preserve_integration else "--theirs"
            run(["git", "checkout", side, "--", path], cwd=repository)
            run(["git", "add", "--", path], cwd=repository)
        remaining = run(["git", "diff", "--name-only", "--diff-filter=U"], cwd=repository, capture=True).stdout.strip()
        if remaining:
            raise SystemExit(f"Unresolved integration conflicts: {remaining}")
        run(["git", "commit", "--no-edit"], cwd=repository)

    utility = automation / ".github/automation/LineDelimiterPreserver.java"
    test = automation / ".github/automation/LineDelimiterPreserverTest.java"
    for source, relative in ((utility, UTILITY_PATH), (test, TEST_PATH)):
        destination = repository / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)

    app = transform_application(recover(repository, APP_PATH, "public final class ProjectWideCodeCleanupApplication"))
    (repository / APP_PATH).write_text(app, encoding="utf-8")
    pom = recover(repository, POM_PATH, "<project")
    (repository / POM_PATH).write_text(ensure_junit_dependency(repository, pom), encoding="utf-8")

    for temporary in (
        ".github/scripts/apply_junit_integration_fixes.py",
        ".github/workflows/integration-jdt-core-full-qa.yml",
        ".github/workflows/integration-final-corpus-qa.yml",
    ):
        target = repository / temporary
        if target.exists():
            target.unlink()
    run(["git", "add", "-A"], cwd=repository)
    if run(["git", "diff", "--cached", "--quiet"], cwd=repository, check=False).returncode != 0:
        run(["git", "commit", "-m", "Stabilize charset-safe project-wide cleanup integration"], cwd=repository)
    output("head", run(["git", "rev-parse", "HEAD"], cwd=repository, capture=True).stdout.strip())


def push_1474(repository: Path) -> None:
    run(["git", "push", "origin", "HEAD:integration/junit-migration-hardening"], cwd=repository)
    output("head", run(["git", "rev-parse", "HEAD"], cwd=repository, capture=True).stdout.strip())


def main() -> None:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    prepare = sub.add_parser("prepare-1472")
    prepare.add_argument("repository", type=Path)
    prepare.add_argument("--artifact", type=int, required=True)
    regenerate = sub.add_parser("commit-regenerated-1472")
    regenerate.add_argument("repository", type=Path)
    reconcile = sub.add_parser("reconcile-1474")
    reconcile.add_argument("repository", type=Path)
    reconcile.add_argument("automation", type=Path)
    push = sub.add_parser("push-1474")
    push.add_argument("repository", type=Path)
    args = parser.parse_args()
    if args.command == "prepare-1472":
        prepare_1472(args.repository.resolve(), args.artifact)
    elif args.command == "commit-regenerated-1472":
        commit_regenerated_1472(args.repository.resolve())
    elif args.command == "reconcile-1474":
        reconcile_1474(args.repository.resolve(), args.automation.resolve())
    else:
        push_1474(args.repository.resolve())


if __name__ == "__main__":
    main()
