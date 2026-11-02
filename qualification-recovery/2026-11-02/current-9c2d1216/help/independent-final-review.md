# Independent final public Help review — run 41500326710

**APPROVED. No remaining finding in the public Help capture, reproducibility or provenance evidence for head `36b8e60ce32b20d6d0e0d70ac2b58aafe59377d8`.**

Reviewer: `/root/review_constant_residue`, 2026-11-02. This review started no JVM, capture or test, and changed no repository source, screenshot, provenance payload or PR. It refreshed only the authorized workflow metadata report and added this receipt.

## Public execution and actual checkout

Fresh GitHub reads confirm [run 41500326710](https://github.com/carstenartur/sandbox/actions/runs/41500326710), attempt 1, and [job 125465842987](https://github.com/carstenartur/sandbox/actions/runs/41500326710/job/125465842987) are **completed / success**. The job completed at **18:38:48 UTC**. The capture, required-scenario verification, committed-screenshot reproduction check and artifact upload steps succeeded.

The full job log records the actual checkout `320282e477248ff45a7a4eab368126ec75994c11`. Its GitHub Git-commit response confirms both parents: main `ae693e5006e104824112880be7db92b3403bd93d` and public head `36b8e60ce32b20d6d0e0d70ac2b58aafe59377d8`.

All three identities have the same complete tree:

| Identity | Commit |
| --- | --- |
| Actual CI merge checkout | `320282e477248ff45a7a4eab368126ec75994c11` |
| Public PR head | `36b8e60ce32b20d6d0e0d70ac2b58aafe59377d8` |
| Independently checked clean local Native checkout | `80e16c7da0f094969d207815757d234b5a3bab11` |

**Tree: `c124a6e750303e0ba6e0a1c3322df5ceaf87fe6c`.** Public and CI tree identities were read directly from GitHub; the local identity and committed blobs were read from Git.

The actual `SandboxHelpScreenshotsMergeGateSWTBotTest` log reports **9 tests, 0 failures, 0 errors, 0 skips**, **451.1 seconds**. Maven reports **BUILD SUCCESS**, **11:13 min**, finish **18:36:51 UTC**. The following workflow step confirms the required scenario names in the executed JUnit report. This review confirms those log/step records; it does not claim that the screenshot artifact contains the raw JUnit XML.

## Artifact and complete ten-file equality

GitHub's current artifact listing binds artifact **13169890665**, `generated-help-screenshots-1548-41500326710`, to this exact run/head. Its published digest matches the actual **3,975,501-byte** local ZIP:

`1987be8c1cc562b9aa8e84a40efe845f0b38e2edaabc378fe2063a2dce55a5d2`

An independent in-memory ZIP/TAR check validated central/local headers, ZIP CRCs, gzip and TAR headers/checksums, safe unique paths, exact inventories, both manifests and every manifested payload. Results: **37 ZIP files**, **35 TAR files plus 28 directories**, **34 payloads**, no extra or missing manifested entry. The inner TAR digest is `4c2f53fe74b48d4454cd6085492936c783f6ab0d992ae22c18856075db12cf88`.

All **five full 1280×900 PNGs and five unmodified provenance JSONs** are byte-identical between the public ZIP, inner TAR, Root's extracted files and the clean local Native checkout. Independently computed Git blob IDs also match all ten committed entries in tree `c124a6e7...`; the shared public/CI tree therefore binds the exact payloads to the public code state.

The table paths are relative to `sandbox_math_cleanup_help/images/`:

| File | SHA-256 |
| --- | --- |
| 01-bouncy-castle-choice.png | `b7d41130a2b8fcbf7c80477eca4a5fca98100ac3034fe7f26ae9b90556056223` |
| 01-bouncy-castle-choice.provenance.json | `4d4d50fc205eea7c6e3bae002dde0831d0a5e51589b058266b4ed0cffd756972` |
| 02-bouncy-castle-majority.png | `9fe1c1111cbef3fe0ff138aa8dfd725c9f3497257d160fe9fe8b2d0866bd93a4` |
| 02-bouncy-castle-majority.provenance.json | `2d0918f5c608ed2174e40712345ea000aa1b299475f7fa787cce1e31540c8744` |
| 03-bouncy-castle-long.png | `92d47a8c164483fc55fbb653ed445b789653acc3a08a6209a9279987bb86159d` |
| 03-bouncy-castle-long.provenance.json | `22d5b03a3cbf1bbaa28917017f83f3d2fe873ae9466cdc24c6b0331322783f3b` |
| 04-bouncy-castle-swapped-choice.png | `094362aab9b6cea4ad134d318d187159fb3aed3a966b8fd73612b21c3364b793` |
| 04-bouncy-castle-swapped-choice.provenance.json | `f9b807a4ec896aaaff339cee25691340373fa5d48b97e0e6ad4ec33fb3596e30` |
| 05-bouncy-castle-modular-square.png | `3487f20290758fbe3458f12f9ee3cdad1f011b4ec51de3d6df7aa36cbb2fb1b3` |
| 05-bouncy-castle-modular-square.provenance.json | `0c758cbbd0f59406f3f56375545d1e87442bfea274989209a0df4a04e3c533d4` |

For each provenance file, I independently recomputed its screenshot, before-excerpt and after-excerpt hashes. All five record the same loaded-adapter fingerprint `08518a0b85228e81124c5523d3db97ac664c384d65c5898332622ed9b0ecdce4`, SDK JAR hash `ba0293dedb3fde9ca2f3042dcef8ae4d6bb2d576adedff8ba7a2b4d186f1cbe1` and SDK source `70c13fb580062b1fd653f35b81b2fb7ed1903c28`. Each profile has the expected 14 options and tail elision false.

The adapter base/source-state strings are descriptive metadata. The actual Git binding above comes from the public run, checkout log, GitHub commit trees and committed blob comparison; it is not inferred solely from those labels.

## Final saved evidence

- Full log: `recovery/native-help-41500326710.log`, **1,206,576 bytes**, SHA-256 `78a5412bf84fee16c1be6c93a5c918c3273fb1b34c5469eee0b5431ee293162a`.
- The stale jobs snapshot in `public-workflow-metadata.json` was refreshed from GitHub to the actual completed/success state. Final SHA-256: `0bfdbc3a56a1b53c6ef579337dfd309924212a1a09256b9572a663b420b23ea5`.
- Existing `root-validation.json` and `archive-validation.json` were left unchanged; their hashes are `6852ed7ae2448f43b0cc1d7549db2972bc372736b3fe42942a1475f3308c8226` and `c5707fcd9170a7b3f8cf6890c4e3b93cd452fd473efc4c5c75cac0cc23982ffc`.

This closes the requested public Help evidence review for the exact head/tree above. It makes no claim about other CI jobs, merging or release publication.

