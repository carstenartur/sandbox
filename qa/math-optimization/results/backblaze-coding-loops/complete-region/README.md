# Complete table/XOR regions in the upstream encoder

The ordinary scanner recognized a complete lookup, reduction and store in two of the twelve pinned implementations. Both profiles produced identical source spans, bound array roles and ordered accesses. The upstream code includes six table implementations. The other four start by writing the first input term to the output and accumulate subsequent terms there. The six remaining implementations call the exponent/logarithm multiplication routine.

| Implementation | Outer to inner axes | Source start | Length | Recorded accesses |
| --- | --- | ---: | ---: | ---: |
| ByteOutputInputTableCodingLoop | position, output, input | 502 | 481 | 7 reads, 1 write |
| OutputByteInputTableCodingLoop | output, position, input | 508 | 654 | 7 reads, 1 write |

All twelve files parsed and compiled under both profiles. The complete regions carry the generic unsigned byte table interpretation. An independent check on the actual upstream table verified all 65,536 products in the polynomial basis with modulus `0x11d`. On flipping bit zero of `table[1][1]`, the field check rejected the modified contents. The typed lookup evaluator and Java both changed the first exercised output byte by exactly one bit. This check applies to the values inspected during this run.

The public encoder check exercised every implementation on the same partial buffer, compared every byte with the upstream default and detected a parity corruption. The low-level check compared each implementation with its own compiled overlay on ordinary and empty ranges, aliased input/output, coefficient/output, table/output and duplicate output rows. It checked complete mutable buffers after short and null input rows, including partial writes at failure. It also exercised five extra input counts, including odd tails, zero and out-of-range counts. Independent bitwise polynomial multiplication checked disjoint buffers with their untouched guards. Tests execute sequentially.

| Profile | Parsed implementations | Complete table reductions | Applied edits |
| --- | ---: | ---: | ---: |
| Strict | 12 | 2 | 0 |
| Mathematical opt-in | 12 | 2 | 0 |

The [subsequent four-input prefix experiment](prefix/README.md) generated two source changes per profile under a separately checked iteration schedule. This transformation requires explicit opt-in. The default configuration retains the counts above. There was no generated candidate to time in the recognition-only run above. The [original throughput measurements](../baseline/README.md) cover only the unmodified upstream variants. The two recognized implementations and their source were available during development.

## Reproduce

Use JDK 25, the matching Regelsuche SDK and a new output directory. The harness checks out and verifies upstream commit `d3c481dc69471e0c47ff6f67f33d53bde941675e`. Use the optional property `sandbox.math.backblaze.mirror` to supply a local mirror with the original upstream URL as origin.

```sh
./mvnw -f qa/math-optimization/pom.xml test -Dtest=ReedSolomonCodingLoopsTest \
  -Dsandbox.math.backblaze=true \
  -Dsandbox.math.backblaze.output="$(pwd)/qa/math-optimization/target/backblaze-region"
```

The recorded run used JDK `25.0.5+8-LTS` with upstream target release 17. Source archive SHA-256 was `6673c22525244e510e2637e15786ced3879afa4d0ff38a00e8bf16dc6d7ac7e9`. Each profile writes the full source-bound records to `review.json`. The [two records shared by both profiles](regions.tsv) retain the individual source hashes and ordered access spans. See the [qualification protocol](PROTOCOL.md).
