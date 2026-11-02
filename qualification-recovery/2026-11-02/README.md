# Qualification evidence, 2026-11-02

Each qualification remains bound to the source identity in `manifest.json`; newer source is not labeled as a rerun of older evidence. Public integration and pending release gates are recorded separately.

## Complete archives

- `full-test-retention-7fcd719.parts/`: 60 ordered raw parts of the complete 46,526,174-byte full-test/corpus/retention archive. Its manifest records every part and the original SHA-256.
- `current-evidence-7fcd719-ee20d376.parts/`: five ordered raw parts of the 619,504-byte JMH, independent-review and genuine local Help archive. Its internal SHA256SUMS verifies all 61 entries apart from the checksum file itself.
- `native-freshness-review-298def9-evidence.tar.gz`: the later comment-freshness RED/GREEN evidence. The additional independent-review receipt is exposed separately.

Download the raw part files, concatenate in the exact order in their manifest without separators, and verify every part plus the reconstructed size/SHA-256 before extracting. Do not concatenate GitHub API JSON or base64 response text. The unsplit original Git SHA is recorded for verification; only the listed part blobs are claimed to exist remotely.

## Directly readable evidence

`current-evidence/reviews/` and `current-evidence/jmh/results/` expose exact copies from the compact archive. The full checksum list/source inventory describe the complete reconstructed archive, not only these conveniently browsable copies. The first public Help comparison and later freshness-fix receipts have separate directories.

Earlier successful and failed attempts remain attributed to their original sources. Current local qualification is not a claim that a public PR, distribution gate or release has completed.
