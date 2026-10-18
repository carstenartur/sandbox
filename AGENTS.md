# Repository instructions

Read `.github/copilot-instructions.md` for the build, testing, and review policies.

## Pull request integration

- Create ordinary pull requests directly against `main`.
- Do not create native GitHub stacks or pull requests targeting another feature branch unless the user explicitly requests that workflow.
- Keep review fixes, test corrections, and CI repairs on the existing pull request branch.
- If a change needs an unmerged prerequisite, finish and merge that prerequisite first, then base the next change on the updated `main`.
- Before calling a pull request mergeable, verify its actual GitHub base branch, absence of stack membership, required checks, and unresolved review threads.
