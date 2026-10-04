# Reconstructed Eclipse adapter leaf contracts

The final raw Maven/JUnit run reports 32 tests without failures, errors or skips. This includes the previous 14 leaf tests; counts must not be added. New red runs expose strict argument/report requirements, the queued-cancellation lifecycle, and private-working-copy/binary-class invalidation. Compilation-only failures during dependency setup are not counted as expected behavioral failures.

These tests use only the provisional embedded SDK archive and coherent Eclipse Maven dependencies. No Regelsuche development output directories are on the final test classpath. The SDK is explicitly provisional and numerical semantics are qualified separately. Native preferences, normal refactoring preview/apply/undo, registered application execution and installed-product identity still require their actual Eclipse gates.
