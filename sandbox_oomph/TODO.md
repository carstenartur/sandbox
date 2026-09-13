# Oomph maintenance

Baseline: Eclipse 2026-09 / Platform 4.41, Java 21 and Tycho 5.0.4.

## Required for setup changes

- Preserve the official catalog URL, project/stream identity and existing task IDs.
- Run the Maven setup contract tests and the native provisioning/workspace acceptance test.
- Test STARTUP import and MANUAL setup after a restart, including missing-project recovery.
- Keep project tool repositories, the target, product and build baseline aligned.
- Update [README](README.md) and [Architecture](ARCHITECTURE.md) when behavior changes.

## Further coverage

- Exercise the native acceptance test on Windows and macOS in addition to Linux x86_64.
- Add a second clean-host scenario starting from the Eclipse Java package.
- Test an installer-driven upgrade across a future coordinated Eclipse baseline change.
- Keep upstream JDT migration QA separate and pinned; its corpus execution remains in the existing QA workflow.
