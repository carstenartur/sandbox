# Upstream encoder throughput

All twelve original encoders at Backblaze commit `d3c481dc69471e0c47ff6f67f33d53bde941675e` completed all workloads and matched the upstream default byte for byte. The timings measure public `ReedSolomon.encodeParity` on Temurin 25.0.5+8 (AMD EPYC 9V74, shared machine). The table shows median decimal payload MB/s across three JVM forks. Parentheses contain the minimum and maximum fork rates.

| Workload | Upstream default InputOutputByteTable | Calibration choice OutputInputByteTable |
| --- | ---: | ---: |
| Calibration, 17 data + 3 parity shards, 200,000 bytes/shard | 504.5 (276.0–515.8) | 527.9 (327.1–612.1) |
| Reserved, 17 + 3, 4 KiB | 470.3 (246.4–570.2) | 525.3 (278.4–593.8) |
| Reserved, 10 + 4, 1 MiB | 312.5 (295.7–405.2) | 183.8 (171.8–204.9) |

The calibration choice ranked first on the small reserved workload and second on the large one. Both finalists allocated a median of 32 bytes per complete encoder call in all three configurations. The broad overlapping fork ranges on the first two workloads limit fine-grained rankings. The large workload changes both shard count and shard length. These figures establish the cost of existing alternatives and show the risk of choosing a loop on a single input geometry. A speedup attributable to a generated rewrite would require timing that rewrite separately.

## Second complete run

The documented Maven/JUnit entry point completed another 108 forks on the same
host with the same order, workload settings and unmodified upstream code. See
the [second fork records](replay/forks.csv) and [second summary](replay/summary.csv).
On calibration, the default reached a median 548.9 MB/s and the original
calibration choice reached 520.8 MB/s. Thus the ordering of the calibration
winner changed between runs. On the 1 MiB workload the default reached
444.7 MB/s versus 240.5 MB/s for the first run’s calibration choice.
The tables keep each run separate. Both full source scans again yielded the
same two extracted regions and zero accepted edits per profile.

## Reproduction

With Java 25 and the [qualified Regelsuche SDK](../../../../../sandbox_math_cleanup/lib/README.md), run the following from the Sandbox checkout. Use a fresh output directory.

```sh
./mvnw -f qa/math-optimization/pom.xml test -Dtest=ReedSolomonCodingLoopsTest \
  -Dsandbox.math.backblaze=true -Dsandbox.math.backblaze.timing=true \
  -Dsandbox.math.backblaze.output="$(pwd)/qa/math-optimization/target/backblaze-measurement"
```

The JUnit fixture fetches and verifies the pinned source. Its optional timing helper compiles the sources and the benchmark with `javac --release 21` on JDK 25. It then launches one JVM per workload, variant and repetition and writes `timing/forks.csv` and `timing/summary.csv`. It reads only the first three columns of the archived fork records to replay the original shuffled order. Each JVM checks all buffer contents before and after timing, including data bytes and the untouched prefix/suffix. One encode processes `data shard count × bytes per shard` payload bytes.

* [Frozen protocol](PROTOCOL.md) and [machine and runtime](environment.json)
* [All 108 fork records with 324 raw windows](forks.csv), with operation count, elapsed nanoseconds and allocated bytes for each window
* [All 36 aggregated comparisons](summary.csv), including elapsed nanoseconds and allocated bytes per complete encode
* [Measured Java harness](ReedSolomonEncodingBaseline.java)
