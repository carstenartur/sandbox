# Current integrated qualification: 2026-11-02

This checkpoint preserves complete, source-bound qualification on commit `9c2d121686dd2bfcd774d820312ca603d8fc4b4f`, tree `d9736c8cd1e397508784e7d79fd1cb08ac978312`.

Native prerequisite #1792 merged as `7458aa94907e7bbff5259fdeaf864fbb57a223c7`, tree `c124a6e750303e0ba6e0a1c3322df5ceaf87fe6c`. The BLS publication candidate `e7616695a9b5a70b26df9b58c8eb7891ffc08e5f` has tree `652696bc384c8a6c809a70499d13fe8e51d8cc8f`. [The complete source mapping](publication-mapping.md) checks all 3,556 tracked entries: only five genuine Help-provenance JSON files differ from the tested combined source. Production, QA, SDK, profiles, tests and PNGs are unchanged.

## Results

- [Full clean qualification](full-review.md): **1,144 tests in 130 suites, 0 failures, 0 errors, 1 documented optional skip**; both complete corpus scans, true Maven/wrapper exit 0 and unchanged source.
- [Retention](retention-review.md): **16 tests**, all **146 strict and 149 mathematical baseline regions retained**, four mathematical additions, no lost regions or retained-region cost changes.
- [Current measurement review](performance-review.md): **22 raw rows, 220 primary samples, 44 forks**, independently reconstructed means and 99.9% confidence intervals. G2/32 is INCONCLUSIVE; G2/256 and pairing are HIGHER_TIME. Eight SWU pairs are INCONCLUSIVE. [Raw results and reports](jmh/) retain their actual measured source.
- [Final public Help review](help/independent-final-review.md): run **41500326710** on final Native head, **9/9 scenarios**, all ten mathematical PNG/JSON files identical to committed Git blobs; complete ZIP/TAR/manifests verified.

[qualification.json](qualification.json) records identities, counts, archive sizes and hashes. Creation receipts retain their original pre-publication `published:false` fields; this Git checkpoint anchors the confirmed complete raw parts.

## Complete archives

| Archive | Bytes | SHA-256 | Raw parts |
|---|---:|---|---:|
| Complete tests, corpus, retention, JMH and source mapping | 52,199,315 | `253eb11d40ef97685b5469de49e353c889b784e179f95274b26686fac4765def` | [67 parts](complete-parts/manifest.json) |
| Complete public and local Help evidence | 9,982,181 | `a4b301298d1592708f7fc287da6f4ae4023a3c138700762f4f9e5e923349b29e` | [13 parts](help-parts/manifest.json) |

Each part is raw binary archive data. Concatenate in manifest index order; do not base64-decode it. In a checkout containing these directories:

```sh
(cd complete-parts && sha256sum -c SHA256SUMS)
cat complete-parts/part[0-9][0-9][0-9] > complete-current-evidence-9c2d1216.tar.gz
printf '%s  %s\n' 253eb11d40ef97685b5469de49e353c889b784e179f95274b26686fac4765def complete-current-evidence-9c2d1216.tar.gz | sha256sum -c -
gzip -t complete-current-evidence-9c2d1216.tar.gz
```

Apply the same procedure to `help-parts` using its manifest's archive name, size and hash. Each archive contains a complete original-path inventory and SHA256SUMS. Extract into a fresh directory and run `sha256sum -c SHA256SUMS` there.

The current complete archive includes **11,167 original input files**, the entire current QA target, completed run receipts/logs, independent reviews and complete tracked-source mapping. The first full test attempt is explicitly retained as an incomplete process-exit receipt chain; the successful clean retry is authoritative. Its separately preserved old target is the declared omission because the complete retry qualifies the same source. Original public Help ZIPs/logs and both complete local Help captures are contained in the separate Help archive.

These observations establish no general speedup, isolated candidate allocation or cryptographic constant-time claim. Earlier `7fcd719` measurements elsewhere in this evidence branch remain historical, with their original source attribution.

[Independent complete-archive verification](archive-verification/review.md) is **APPROVED**: all 12,470 members, 11,167 original inputs, 11,168 SHA256SUMS entries, completed run receipts and the source mapping were verified. The review records the separate copy-stage observation without attributing it to the qualified inputs or final archive.
