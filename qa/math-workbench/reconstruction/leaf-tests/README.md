# Reconstruction leaf tests

Fresh Java 25 Maven/JUnit execution of seven strict-option, three copied-edit and four job scheduling tests. Each implementation followed an observed failing test run; the final run reports 14 tests, no failures, errors or skips. The copied-edit tests first exposed the missing subclass-copy contract, then the copyable stub produced the intended two assertion failures.

The temporary harness and raw logs/XML are retained here for review. Its absolute paths identify the actual run; they are not repository build entry points. The surviving local SDK snapshot supplies enum/API classes only. This evidence does not qualify the reconstructed SDK distribution, Eclipse plugin packaging, preferences, quick assist, CLI, native SWT, or product installation. Those gates remain separate.
