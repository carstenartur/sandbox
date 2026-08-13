# Reviewable Maven/JUnit Delivery Policy

This policy is a hard delivery constraint, not a preference.

## Review size

- Target at most about **1,500 changed source lines** per review pull request.
- Stop and split at about **2,000 changed source lines** unless an exception was explicitly documented before further implementation.
- A large integration branch may retain combined work as a reference, but its pull request must stay draft and must not be merged as one review unit.
- Commit and merge every coherent, independently testable slice before accumulating the next slice.
- Generated or binary files do not justify hiding an oversized source-code change; exceptions must be explicit in the pull-request description.

## Test authority

- **Maven and JUnit are the executable test authority.** A clean `mvn verify` must execute the authoritative checks.
- GitHub Actions may provision dependencies and invoke Maven. It must not implement a parallel assertion or test framework.
- Do not add Python automation, Python test runners, `setup-python` dependencies, or script-owned semantic assertions without explicit owner approval.
- Shell is limited to thin environment or process adapters. Product contracts and result assertions belong in Java/JUnit.
- Upstream Git repositories used by tests are cloned, checked out, pinned and cleaned up through a reusable JGit-backed JUnit 5 extension; use a JUnit 4 rule only in a module that genuinely still requires JUnit 4.

## Failing tests

A temporarily failing integration test may be quarantined only when all of these conditions hold:

1. the exact test methods are named;
2. the test source remains executable and is not deleted;
3. an open issue records the expected behavior and remaining failure;
4. normal Maven/JUnit gates remain active;
5. no global failure-ignore or module-wide skip switch is introduced;
6. the pull request does not claim the quarantined behavior as passing.

## CI and branch discipline

- Do not create trigger-only commits. Re-run the existing workflow or failed job.
- Once a pull request is ready for review, change it only for a concrete review finding or an exact-head gate failure.
- Do not keep adding features to a red integration branch. Split the smallest coherent slice, make it green, merge it, and continue from the new `main`.
- A workflow success is evidence, not the product goal. Prefer a smaller design and direct JUnit contract over another validator, report format, timeout or wrapper.

## Required review checklist

Before opening or updating a pull request, verify:

- the changed-line budget is still reviewable;
- no new automation language or test authority was introduced;
- every new behavior has Maven/JUnit coverage;
- any external repository fixture uses JGit from JUnit;
- all quarantined tests are explicitly documented;
- the branch contains no unrelated follow-up work.

Tracked by issue #1473.
