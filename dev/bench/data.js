window.BENCHMARK_DATA = {
  "lastUpdate": 1769986354810,
  "repoUrl": "https://github.com/carstenartur/sandbox",
  "entries": {
    "JMH Benchmarks": [
      {
        "commit": {
          "author": {
            "email": "198982749+Copilot@users.noreply.github.com",
            "name": "Copilot",
            "username": "Copilot"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "f9b87d537ddf973332ac4f4463c321f1eadc88e0",
          "message": "Add JMH benchmarking module with GitHub Actions integration (#557)",
          "timestamp": "2026-02-01T03:17:05+01:00",
          "tree_id": "6800c2eb703e074d14c0d44ce67ca7e9f7014f06",
          "url": "https://github.com/carstenartur/sandbox/commit/f9b87d537ddf973332ac4f4463c321f1eadc88e0"
        },
        "date": 1769913308623,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"small\"} )",
            "value": 199.17647138268637,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"medium\"} )",
            "value": 528.73807286653,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"large\"} )",
            "value": 3263.161227609995,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"small\"} )",
            "value": 34.10358374428512,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"medium\"} )",
            "value": 174.98511963882663,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"large\"} )",
            "value": 1485.7320654701382,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildComplexLoopModel",
            "value": 0.06480765903540069,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildSimpleLoopModel",
            "value": 0.01517266429736996,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeCollect",
            "value": 0.06336227229342449,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeComplex",
            "value": 0.09550756339209371,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeForEach",
            "value": 0.04722731590892757,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithASTVisitor",
            "value": 76.29784891149943,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithRegex",
            "value": 41.16064672294314,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "198982749+Copilot@users.noreply.github.com",
            "name": "Copilot",
            "username": "Copilot"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "e1058bcbd976a7ee13c543571fdee830c6358609",
          "message": "Fix version synchronization for standard Maven modules in release workflow (#558)",
          "timestamp": "2026-02-01T03:35:25+01:00",
          "tree_id": "6f77c2a8a5fa574c884867ae216927355995f9df",
          "url": "https://github.com/carstenartur/sandbox/commit/e1058bcbd976a7ee13c543571fdee830c6358609"
        },
        "date": 1769914410201,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"small\"} )",
            "value": 193.46104987817174,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"medium\"} )",
            "value": 656.5914784532399,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"large\"} )",
            "value": 4478.450061227692,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"small\"} )",
            "value": 40.975692401332445,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"medium\"} )",
            "value": 258.6958794919127,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"large\"} )",
            "value": 2419.568392705706,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildComplexLoopModel",
            "value": 0.0702398718710799,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildSimpleLoopModel",
            "value": 0.024466659595064034,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeCollect",
            "value": 0.048704554676516305,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeComplex",
            "value": 0.07365521678422403,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeForEach",
            "value": 0.041900489291598286,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithASTVisitor",
            "value": 118.84522675233379,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithRegex",
            "value": 32.624736001459084,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "198982749+Copilot@users.noreply.github.com",
            "name": "Copilot",
            "username": "Copilot"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "4e93858aa164a6b61d7de547c96f618d32509edc",
          "message": "Fix benchmark workflow: install parent POM before building submodules (#563)",
          "timestamp": "2026-02-01T07:18:29+01:00",
          "tree_id": "20e673b03fa2c2f5a342f40f9bb21876071f5c4c",
          "url": "https://github.com/carstenartur/sandbox/commit/4e93858aa164a6b61d7de547c96f618d32509edc"
        },
        "date": 1769927797166,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"small\"} )",
            "value": 211.22110880717938,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"medium\"} )",
            "value": 550.9164639695115,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"large\"} )",
            "value": 3261.372251036035,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"small\"} )",
            "value": 34.03270938589038,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"medium\"} )",
            "value": 171.60349673806974,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"large\"} )",
            "value": 1463.993703168903,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildComplexLoopModel",
            "value": 0.06376155103286427,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildSimpleLoopModel",
            "value": 0.015582985603438987,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeCollect",
            "value": 0.06429637385156042,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeComplex",
            "value": 0.09590353150463787,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeForEach",
            "value": 0.049092499264701515,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithASTVisitor",
            "value": 75.50860724679961,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithRegex",
            "value": 41.117720037365075,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "198982749+Copilot@users.noreply.github.com",
            "name": "Copilot",
            "username": "Copilot"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "168c18c49c9eb83495efca4db6582099e48e4fce",
          "message": "Fix version synchronization between modules (#561)",
          "timestamp": "2026-02-01T07:53:53+01:00",
          "tree_id": "c52226d36fec7c1a601e39bf06899aeec9ba214d",
          "url": "https://github.com/carstenartur/sandbox/commit/168c18c49c9eb83495efca4db6582099e48e4fce"
        },
        "date": 1769929951118,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"small\"} )",
            "value": 199.89852693688985,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"medium\"} )",
            "value": 542.1240259056907,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"large\"} )",
            "value": 3301.790416017594,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"small\"} )",
            "value": 34.636693442967356,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"medium\"} )",
            "value": 171.59807133899903,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"large\"} )",
            "value": 1461.5759092595433,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildComplexLoopModel",
            "value": 0.06371491859185333,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildSimpleLoopModel",
            "value": 0.015642114559278532,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeCollect",
            "value": 0.06230363132848911,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeComplex",
            "value": 0.09477155434985013,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeForEach",
            "value": 0.04757499693040562,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithASTVisitor",
            "value": 74.75616533583705,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithRegex",
            "value": 39.46478179330243,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "41898282+github-actions[bot]@users.noreply.github.com",
            "name": "github-actions[bot]",
            "username": "github-actions[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "42da06d4619df67a8695058c30ff0300072fb5dc",
          "message": "Prepare for next development iteration 1.2.5-SNAPSHOT (#575)",
          "timestamp": "2026-02-01T17:47:08+01:00",
          "tree_id": "1079f266bbb4d0e1aa2f8f08d2d51cdaae682cf3",
          "url": "https://github.com/carstenartur/sandbox/commit/42da06d4619df67a8695058c30ff0300072fb5dc"
        },
        "date": 1769965545180,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"small\"} )",
            "value": 199.54900278296404,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"medium\"} )",
            "value": 542.1867325643358,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"large\"} )",
            "value": 3313.8824626965206,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"small\"} )",
            "value": 36.60117532855325,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"medium\"} )",
            "value": 168.7658687045799,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"large\"} )",
            "value": 1497.7714274832254,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildComplexLoopModel",
            "value": 0.06524501366185442,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildSimpleLoopModel",
            "value": 0.015529524180540679,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeCollect",
            "value": 0.06540768487498985,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeComplex",
            "value": 0.09542405716071538,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeForEach",
            "value": 0.04999896782512532,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithASTVisitor",
            "value": 76.87808133151918,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithRegex",
            "value": 41.12994721844106,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "198982749+Copilot@users.noreply.github.com",
            "name": "Copilot",
            "username": "Copilot"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "dffa0f07e73b21e00b708a1730a8d170beacb103",
          "message": "Add fluent, type-safe AST wrapper API with Java 21 records (#576)",
          "timestamp": "2026-02-01T22:51:58+01:00",
          "tree_id": "e1c2581f0b170ee10f157abab8ecbce2f1a1126f",
          "url": "https://github.com/carstenartur/sandbox/commit/dffa0f07e73b21e00b708a1730a8d170beacb103"
        },
        "date": 1769986340566,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"small\"} )",
            "value": 195.10545388577447,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"medium\"} )",
            "value": 554.2646539587006,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"large\"} )",
            "value": 3288.662300482808,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"small\"} )",
            "value": 36.20145649283031,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"medium\"} )",
            "value": 172.69796223901034,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"large\"} )",
            "value": 1504.4561865095857,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildComplexLoopModel",
            "value": 0.06404598027853152,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildSimpleLoopModel",
            "value": 0.015640680888380262,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeCollect",
            "value": 0.06297753122798527,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeComplex",
            "value": 0.09473707313259185,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeForEach",
            "value": 0.049546998929809585,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithASTVisitor",
            "value": 75.96561374461396,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithRegex",
            "value": 39.3746101071241,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.createSimpleMethod",
            "value": 7.876007408294228,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.newStyleMathMaxCheck",
            "value": 78.32850443536593,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.oldStyleMathMaxCheck",
            "value": 0.3695351074582286,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.queryHasSignature",
            "value": 81.0302760909021,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.queryIsListAdd",
            "value": 107.4236941796942,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.queryIsMathMax",
            "value": 81.2351562256776,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.complexNewStyleCheck",
            "value": 21.079234201066747,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.complexOldStyleCheck",
            "value": 0.37595370395104866,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.newStyleIsMathMax",
            "value": 78.21192651457174,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.newStyleModifierChecks",
            "value": 13.948438021922925,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.newStylePatternDetection",
            "value": 107.65889353955029,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.oldStyleIsMathMax",
            "value": 0.3725525206454169,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.oldStyleModifierChecks",
            "value": 0.3704973176441599,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.oldStylePatternDetection",
            "value": 0.37031400939867043,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.createParameterizedType",
            "value": 64.25565950794307,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.createSimpleType",
            "value": 23.880069075810077,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.newStyleTypeCheck",
            "value": 20.59747067578219,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.oldStyleTypeCheck",
            "value": 0.37185830337303094,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsClass",
            "value": 20.08373799168255,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsCollection",
            "value": 23.065962775283303,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsNumeric",
            "value": 0.37187236880599805,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsOptional",
            "value": 22.603679817915115,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsStream",
            "value": 21.057531065754922,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.builderAllocation",
            "value": 23.890164346478464,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.iterateRawStrings",
            "value": 23.680806872195443,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.iterateTypeInfos",
            "value": 24.600148784047384,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.rawCollectionCheck",
            "value": 0.37217553479050614,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.rawTypeComparison",
            "value": 0.36808694944222714,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.recordAllocation",
            "value": 3.9089934257970924,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.wrapperCollectionCheck",
            "value": 23.036947376317528,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.wrapperTypeComparison",
            "value": 39.68527164363236,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ]
  }
}