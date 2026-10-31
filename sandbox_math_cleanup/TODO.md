# Qualification still required

- Run the full Java 25 Tycho reactor and sequential product/p2 installation gates.
- Run a real workbench interaction test for options, warning, preview, apply,
  byte-exact undo, cancellation and stale-source rejection; produce screenshots
  only from that execution.
- Preserve the recovered [Bouncy Castle/JMH report](../qa/math-optimization/history/pre-reset/repository-at-8ee0ffc/qa/math-optimization/RESULTS-2026-10-04.md)
  as historical evidence. The [2026-10-30 qualification](../docs/superpowers/plans/2026-10-30-math-optimization-qualification.md)
  records 120 strict / 129 mathematical accepted regions and their exact inputs
  and budget limits; local fixtures and candidate counts do not qualify arbitrary
  cryptographic transformations or speedups.
- Before enabling any upstream modification, qualify the upstream build and
  classify public/secret inputs and constant-time requirements.
- Keep underflow detection unsupported until a tested tiny-and-inexact event
  detector and a versioned policy are available.
- Keep unsupported source regions and numerical combinations diagnostic-only.
- Replace the vendored, qualified SDK archive only after reproducing its
  consumer result and updating its exact source revision, hashes and notices.
