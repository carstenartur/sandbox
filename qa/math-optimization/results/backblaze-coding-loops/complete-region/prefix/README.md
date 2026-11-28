# Four-input prefix on two Backblaze encoding loops

Ordinary Apply generated two changes for upstream commit `d3c481dc69471e0c47ff6f67f33d53bde941675e`. For at least four inputs, the changed method executes the first four iterations in source order, then continues its original loop at four. For fewer inputs it runs the original loop from zero. This schedule preserves the order of mutable Java array operations. The checked rewrite uses the generic lookup operation for the current table contents.

With the explicit ordered-input-prefix option, both Java profiles produced byte-identical overlays. The standard configuration keeps the rewrite disabled and still recognizes both source regions. The normal scanner parsed all 12 implementations per profile, applied two edits, checked exact Undo and compiled the resulting Java. All registered encoders passed public parity and corruption checks. The low-level fixture checked 13 buffer shapes and input counts per class and profile, including aliased buffers, empty ranges, odd input counts, exceptions and preceding writes. The two recognized encoders also matched the table oracle after a single-bit mutation. The [source hashes](sources.tsv) and [exact generated patch](generated.patch) identify the timed implementations.

## Complete public encode throughput

Median payload MB/s across three independent JVMs. Brackets show the minimum and maximum fork rates. Each row compares the generated source with the original of the same class. Candidate names give their loop order from outermost to innermost. The underlying source classes are `ByteOutputInputTableCodingLoop` and `OutputByteInputTableCodingLoop`. Published precision here is one decimal, with six-decimal ratios and higher-precision rates retained in the [comparisons](comparisons.csv).

| Loop order | Geometry | Original MB/s | Apply MB/s | Median change | Fork ranges |
| --- | --- | ---: | ---: | ---: | --- |
| position, output, input | 17+3 × 200,000 | 266.8 [222.9, 276.0] | 280.1 [247.6, 295.3] | +5.0% | overlap |
| position, output, input | 17+3 × 4,096 | 275.4 [257.4, 275.6] | 276.5 [260.1, 300.1] | +0.4% | overlap |
| position, output, input | 10+4 × 1,048,576 | 76.3 [67.3, 76.9] | 87.0 [80.4, 89.5] | +14.0% | separated |
| output, position, input | 17+3 × 200,000 | 300.2 [291.4, 336.5] | 364.6 [350.7, 381.4] | +21.5% | separated |
| output, position, input | 17+3 × 4,096 | 331.7 [302.8, 366.0] | 387.3 [316.2, 390.2] | +16.8% | overlap |
| output, position, input | 10+4 × 1,048,576 | 95.3 [88.5, 98.5] | 148.2 [138.4, 165.5] | +55.5% | separated |

Both source changes passed the prespecified median non-regression condition on all three geometries. Three of six same-class comparisons had separated ranges. The other three were inconclusive by the [predeclared criterion](../TIMING.md).

The upstream default, `InputOutputByteTableCodingLoop`, was the fastest of all twelve original implementations on each geometry. It achieved 592.0 [507.2, 609.5], 608.3 [539.0, 625.2] and 448.5 [379.4, 475.4] MB/s respectively. Its slowest fork exceeded the fastest generated fork for each geometry. The known workload and shared runner limit generalization to other source families and machines.

## Replay

Use the pinned [SDK build](../../../../../../sandbox_math_cleanup/lib/qualification.json) and JDK 25.0.5+8. Run from the Sandbox checkout using a fresh output path. The timing replay on this nine-CPU Linux runner inherited affinity to logical CPUs 7 and 8, used one benchmark JVM at a time and reserved neither CPU exclusively.

```sh
taskset -c 7,8 ./mvnw -f qa/math-optimization/pom.xml test -Dtest=ReedSolomonCodingLoopsTest \
  -Dsandbox.math.backblaze=true -Dsandbox.math.backblaze.prefixCandidate=true \
  -Dsandbox.math.backblaze.appliedTiming=true \
  -Dsandbox.math.backblaze.output="$(pwd)/qa/math-optimization/target/backblaze-prefix"
```

On hosts with different logical CPU IDs, record the chosen affinity. The recorded timing preceded the default-off decision. The final fixture checks that explicit opt-in in both profiles produces the exact two timed hashes. Without that opt-in each profile parses 12 classes, detects two complete reductions and applies zero changes. Both profiles are checked before timing. The fixture verifies each generated source digest, deduplicates equal overlays and emits `timing/` below the first applicable profile. A run with zero source edits refuses paired timing.

The [126 JVM forks](forks.csv) preserve all 378 windows in execution order. The [42 summaries](summary.csv) include elapsed time and per-call allocation for every variant and geometry. The [18 comparisons](comparisons.csv) include the original class, default and best original for every candidate and geometry. The [runtime configuration](environment.json) and [15-second runner samples](runtime-load.tsv) document host conditions during the run. At samples with completed forks, the one-minute load average ranged from 3.99 to 7.57 and the selected CPUs reported 1.8% steal time across those intervals. Busy and steal counters cover both CPUs, including unrelated activity. The three observed fork values per case provide a descriptive range.
