# Historical partial recovery

These files are the literal text recovery made after the workspace maintenance
incident. They describe the earlier, lost checkout and its earlier runs. They
are not results of the reconstructed checkout. The original JMH JSON and its
40 primary samples, full corpus reports, three complete measured fixture
sources, and original Git objects were not recovered.

`provenance.json` records the basis of each text and the two generated source
files whose previously recorded SHA-256 values could be verified.
`SHA256SUMS` verifies the recovered archive itself; a new recovery checksum
does not establish byte identity to deleted files.

The paths below `repository-at-8ee0ffc/` and `workspace-evidence/` are preserved
verbatim. The reports still refer to missing artifacts. Fresh reconstructed
code and fresh execution results belong outside this historical directory.
No historical raw measurements have been reconstructed.
