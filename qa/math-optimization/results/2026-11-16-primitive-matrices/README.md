# Primitive matrices for Java recurrences — 2026-11-16

The three complete generated `int` methods took less time than their original
loops at around one million iterations. The primitive matrix generator also
had lower times than the previous BigInteger generator for the affine and
product cases. For the geometric case, the two generated-method intervals
overlapped.

| Fixture | Original loop (µs/call) | Previous generator (µs/call) | Primitive generator (µs/call) |
| --- | ---: | ---: | ---: |
| Affine, `sum = 3 * sum + i` | 815.184 ± 64.741 | 149.403 ± 99.811 | 0.590 ± 0.043 |
| Geometric accumulation, `sum += power; power *= 3` | 434.565 ± 50.815 | 66.406 ± 78.014 | 0.709 ± 0.204 |
| Product, `sum *= 3` | 440.340 ± 103.801 | 23.950 ± 22.158 | 0.306 ± 0.070 |

The table gives JMH means and 99.9% confidence half-widths. JMH 1.37 used
OpenJDK 25.0.5+8-LTS, one thread pinned to logical CPU 8, two JVM forks, three
300 ms warmup iterations and five 300 ms measurement iterations per fork.
There was other verification activity on the host. The previous generator's
intervals were wide, especially for the geometric case. These timings are
local observations for the selected large inputs.

The existing finite-series qualification compiled the complete original and
ordinary Apply output at Java target 17. Preparation ran the SDK and Java
source checks and verified exact Undo. Each JMH trial compared the two compiled
methods on 96 inputs, including overflow seeds and counts around the guard.
The timed calls used counts from 1,000,000 to 1,000,015. The original files
and profitability thresholds were identical across revisions. The measured
generated calls include the range guards and the retained original loops.
The [October report](../2026-10-31-finite-series/README.md) describes the
original fixture set and its measurements.

## Run identity

The [published implementation](https://github.com/carstenartur/sandbox/commit/d666c711fec2fe1d4de84d09fec92d2ca9798e64)
has the measured implementation's exact Git tree
`4814531e30f31b9ed6b23215b5470e57e898e33c`. The Sandbox commit in the table
records the local commit from the measurement receipt.

Both clean checkouts used the same vendored SDK SHA-256
`d5b078bbd4780278bb443be3610bcdad058c993a5b3b36ebf5902cb85d082b7a`.

| Receipt | Previous generator | Primitive generator |
| --- | --- | --- |
| Sandbox commit | `f24d1d049601a3d3fece8d6d8ef754c04e3d5d04` | `18f25a45cf89fabd887bd13d5afb77021bcef213` |
| Fixture manifest SHA-256 | `d3eac5b1bd597a270bc016acfa867e033836a866e997449d1b24a300c913dea4` | `d3e30c6df509b46067ac1f787669fbcfb5b3982d739fad5b975f72e52b611568` |
| Mathematics core class closure SHA-256 | `3050c33feec4043295cbf19b3c6b7d8b1477a9bd650868a1e3756f212312e77d` | `c120ead6e1de738dd43ec74e3abd667985fb482aaa5f0c9f7fc5e439fafbfd05` |
| Raw JMH JSON SHA-256 | `ac878dff48a1741016d6b58e8e056fa518c6772dc83d705d8ecef541fa8d3d5c` | `f6b44facc57423c28f0de87c625aac75b4e8072fbb66d69ae305aebc1c9354d6` |

## Reproduction

From each checkout's root, run with Java 25 and Maven. Use the previous
generator commit for its generated-method timing and the published primitive
generator commit for its generated and original methods.

```sh
work=$(mktemp -d)
mvn -f qa/math-optimization/pom.xml -DskipTests compile \
  org.apache.maven.plugins:maven-dependency-plugin:3.11.0:build-classpath \
  -Dmdep.includeScope=test -Dmdep.outputFile="$work/classpath"
classpath="$PWD/qa/math-optimization/target/classes:$(cat "$work/classpath")"
java -Xmx512m -cp "$classpath" org.sandbox.math.qa.FiniteSeriesQualification "$work/fixtures"
fixture_hash=$(sha256sum "$work/fixtures/benchmark.properties" | cut -d' ' -f1)
# Choose FiniteSeriesBenchmark\.generated$ on the previous generator commit.
benchmark='FiniteSeriesBenchmark\.(generated|original)$'
java -Xmx512m -cp "$classpath" org.openjdk.jmh.Main "$benchmark" \
  -p "fixtureDirectory=$work/fixtures" -p "fixtureManifestSha256=$fixture_hash" \
  -p family=affine,geometric,product -p count=1000000 \
  -foe true -f 2 -wi 3 -i 5 -w 300ms -r 300ms -rf json -rff "$work/jmh.json"
```
