# Documentation qualification

- Add layout and preview/apply/undo screenshots only after the reconstructed
  workbench tests run against the final installed bundles.
- Link fresh final-pin corpus reports and generated-Java JMH raw samples when
  those runs finish. The earlier raw measurements were lost; recovered aggregate
  reports under `qa/math-optimization/history/pre-reset/` stay historical.
- Check the documented CLI, checked-mode consent and nine option keys against
  the reconstructed application and the installed-product verifier.
- Keep numeric-kind, target-Java and safety-profile limits aligned with the
  actual proof/emission tests. Do not generalize local timing or equality to
  upstream performance, every profile or constant-time cryptographic behavior.
