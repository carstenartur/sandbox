window.BENCHMARK_DATA = {
  "lastUpdate": 1770052067508,
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
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "bc8aad695932ea4ba6a4be4149a40cf525ef4964",
          "message": "Bump org.eclipse.jdt:org.eclipse.jdt.core from 3.40.0 to 3.44.0 (#581)",
          "timestamp": "2026-02-02T13:41:56+01:00",
          "tree_id": "b19a4d77b4332fb9de44ed641a4b0f4dff78945b",
          "url": "https://github.com/carstenartur/sandbox/commit/bc8aad695932ea4ba6a4be4149a40cf525ef4964"
        },
        "date": 1770039738853,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"small\"} )",
            "value": 205.34628009449403,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"medium\"} )",
            "value": 551.3420133051753,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"large\"} )",
            "value": 3413.7837316761133,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"small\"} )",
            "value": 37.603344460283814,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"medium\"} )",
            "value": 192.48434572916653,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"large\"} )",
            "value": 1672.5828943343943,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildComplexLoopModel",
            "value": 0.06492793384381508,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildSimpleLoopModel",
            "value": 0.015418483948614423,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeCollect",
            "value": 0.06336395217821267,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeComplex",
            "value": 0.09715623983828378,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeForEach",
            "value": 0.050018408448739796,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithASTVisitor",
            "value": 76.29397554343936,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithRegex",
            "value": 39.399996037373654,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.createSimpleMethod",
            "value": 7.8432681221927725,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.newStyleMathMaxCheck",
            "value": 78.36873237297097,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.oldStyleMathMaxCheck",
            "value": 0.3766223430946105,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.queryHasSignature",
            "value": 80.9832809697436,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.queryIsListAdd",
            "value": 108.63360814316772,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.queryIsMathMax",
            "value": 78.03519722400843,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.complexNewStyleCheck",
            "value": 21.263270408166633,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.complexOldStyleCheck",
            "value": 0.3726925099506114,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.newStyleIsMathMax",
            "value": 79.01769226512927,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.newStyleModifierChecks",
            "value": 14.070673350884931,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.newStylePatternDetection",
            "value": 109.3143342815537,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.oldStyleIsMathMax",
            "value": 0.3720945764761937,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.oldStyleModifierChecks",
            "value": 0.37016577945014645,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.oldStylePatternDetection",
            "value": 0.3709466550549795,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.createParameterizedType",
            "value": 64.51580469441129,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.createSimpleType",
            "value": 23.953577833975146,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.newStyleTypeCheck",
            "value": 20.754807171285158,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.oldStyleTypeCheck",
            "value": 0.3733308410759841,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsClass",
            "value": 20.187469466181717,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsCollection",
            "value": 23.181689589067236,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsNumeric",
            "value": 0.3718877210535363,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsOptional",
            "value": 22.648917263660373,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsStream",
            "value": 21.183409259686794,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.builderAllocation",
            "value": 23.902157245614344,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.iterateRawStrings",
            "value": 23.753139693492436,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.iterateTypeInfos",
            "value": 24.69281142264902,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.rawCollectionCheck",
            "value": 0.3737080059679717,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.rawTypeComparison",
            "value": 0.3710811804588026,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.recordAllocation",
            "value": 3.9589470673221356,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.wrapperCollectionCheck",
            "value": 23.30362826739132,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.wrapperTypeComparison",
            "value": 39.735766247658134,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "24058631e70a22aece0b1482b9f4d88e9804eacb",
          "message": "Bump org.apache.maven.plugins:maven-surefire-plugin from 3.5.2 to 3.5.4 (#582)",
          "timestamp": "2026-02-02T17:06:15+01:00",
          "tree_id": "9de5e0496a472a93735d4d5a9cd81dbca4681c34",
          "url": "https://github.com/carstenartur/sandbox/commit/24058631e70a22aece0b1482b9f4d88e9804eacb"
        },
        "date": 1770052003850,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"small\"} )",
            "value": 200.1953913808091,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"medium\"} )",
            "value": 558.621149417019,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"large\"} )",
            "value": 3457.4304240183083,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"small\"} )",
            "value": 37.8128596104055,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"medium\"} )",
            "value": 200.50839664170175,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"large\"} )",
            "value": 1663.7254841752688,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildComplexLoopModel",
            "value": 0.0652814734308191,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildSimpleLoopModel",
            "value": 0.015554653182586281,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeCollect",
            "value": 0.06214920602049706,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeComplex",
            "value": 0.09437185826050523,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeForEach",
            "value": 0.047628316303386334,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithASTVisitor",
            "value": 76.60694935348977,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithRegex",
            "value": 39.41457211740667,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.createSimpleMethod",
            "value": 7.849026553196907,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.newStyleMathMaxCheck",
            "value": 78.1669349983633,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.oldStyleMathMaxCheck",
            "value": 0.37314904223495493,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.queryHasSignature",
            "value": 79.23014464524957,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.queryIsListAdd",
            "value": 106.79369815359573,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.queryIsMathMax",
            "value": 78.28024623473826,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.complexNewStyleCheck",
            "value": 21.00968307517442,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.complexOldStyleCheck",
            "value": 0.3726005704586286,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.newStyleIsMathMax",
            "value": 78.27846419727815,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.newStyleModifierChecks",
            "value": 13.914757843508653,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.newStylePatternDetection",
            "value": 106.76296051940423,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.oldStyleIsMathMax",
            "value": 0.369699375540011,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.oldStyleModifierChecks",
            "value": 0.3709158014746029,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.oldStylePatternDetection",
            "value": 0.37130887178006244,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.createParameterizedType",
            "value": 64.01643148175263,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.createSimpleType",
            "value": 23.778572334525247,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.newStyleTypeCheck",
            "value": 20.540378289858097,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.oldStyleTypeCheck",
            "value": 0.37212685226241593,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsClass",
            "value": 20.1070809465839,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsCollection",
            "value": 23.17578256004859,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsNumeric",
            "value": 0.3694259697450118,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsOptional",
            "value": 22.494516227950548,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsStream",
            "value": 20.998092889701955,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.builderAllocation",
            "value": 23.76222413581299,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.iterateRawStrings",
            "value": 23.733818366082705,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.iterateTypeInfos",
            "value": 24.608900849541573,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.rawCollectionCheck",
            "value": 0.37481081121882587,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.rawTypeComparison",
            "value": 0.3724437250322392,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.recordAllocation",
            "value": 3.910488604442693,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.wrapperCollectionCheck",
            "value": 22.996451213857988,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.wrapperTypeComparison",
            "value": 39.57982888585225,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "66b0338b84c196b38f1c1ea55d1066b589d14ef3",
          "message": "Bump org.apache.maven.plugins:maven-shade-plugin from 3.6.0 to 3.6.1 (#580)",
          "timestamp": "2026-02-02T17:06:27+01:00",
          "tree_id": "568264d748582fb66f690888a37ab74d4a97763a",
          "url": "https://github.com/carstenartur/sandbox/commit/66b0338b84c196b38f1c1ea55d1066b589d14ef3"
        },
        "date": 1770052021948,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"small\"} )",
            "value": 197.89616631207184,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"medium\"} )",
            "value": 561.3632037058677,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"large\"} )",
            "value": 3549.8675139510697,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"small\"} )",
            "value": 38.21467402373507,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"medium\"} )",
            "value": 197.33772022136472,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"large\"} )",
            "value": 1681.805355674598,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildComplexLoopModel",
            "value": 0.06601719014221431,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildSimpleLoopModel",
            "value": 0.01587804035089232,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeCollect",
            "value": 0.06268097255708001,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeComplex",
            "value": 0.09588563229829969,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeForEach",
            "value": 0.04964535367084698,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithASTVisitor",
            "value": 75.69251850624055,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithRegex",
            "value": 39.42629418753883,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.createSimpleMethod",
            "value": 7.868983538550465,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.newStyleMathMaxCheck",
            "value": 79.94552582995286,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.oldStyleMathMaxCheck",
            "value": 0.3740233551547785,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.queryHasSignature",
            "value": 79.55060485134717,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.queryIsListAdd",
            "value": 108.86818378960683,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.queryIsMathMax",
            "value": 78.05274458658826,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.complexNewStyleCheck",
            "value": 21.046412252130693,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.complexOldStyleCheck",
            "value": 0.3708615353630173,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.newStyleIsMathMax",
            "value": 79.32625966932889,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.newStyleModifierChecks",
            "value": 13.949098708709865,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.newStylePatternDetection",
            "value": 106.94413882101671,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.oldStyleIsMathMax",
            "value": 0.37181906533957565,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.oldStyleModifierChecks",
            "value": 0.3698376757999059,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.oldStylePatternDetection",
            "value": 0.37004846283987464,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.createParameterizedType",
            "value": 64.39821172631981,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.createSimpleType",
            "value": 24.022903040449883,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.newStyleTypeCheck",
            "value": 20.755140477064163,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.oldStyleTypeCheck",
            "value": 0.37198727338608895,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsClass",
            "value": 21.31381532471711,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsCollection",
            "value": 23.170899983733033,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsNumeric",
            "value": 0.37179746437623296,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsOptional",
            "value": 22.599071990040486,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsStream",
            "value": 21.205909922585004,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.builderAllocation",
            "value": 24.181760949427435,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.iterateRawStrings",
            "value": 23.728871331502194,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.iterateTypeInfos",
            "value": 24.62349404459869,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.rawCollectionCheck",
            "value": 0.3730205070825229,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.rawTypeComparison",
            "value": 0.3742647347678963,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.recordAllocation",
            "value": 3.92677874366196,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.wrapperCollectionCheck",
            "value": 23.025535059713015,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.wrapperTypeComparison",
            "value": 39.64515483782458,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "3e38b08c1744d32a22e737ff1ef2bf7be2305b6d",
          "message": "Bump org.apache.maven.plugins:maven-compiler-plugin (#579)",
          "timestamp": "2026-02-02T17:06:40+01:00",
          "tree_id": "63b9615c41c4925dbb2a98459265926c3925e1d5",
          "url": "https://github.com/carstenartur/sandbox/commit/3e38b08c1744d32a22e737ff1ef2bf7be2305b6d"
        },
        "date": 1770052053223,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"small\"} )",
            "value": 202.74335520782014,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"medium\"} )",
            "value": 545.957663447431,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithBindings ( {\"codeSize\":\"large\"} )",
            "value": 3418.092830240255,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"small\"} )",
            "value": 37.409837952553524,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"medium\"} )",
            "value": 195.32766971069947,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.ASTParsingBenchmark.parseASTWithoutBindings ( {\"codeSize\":\"large\"} )",
            "value": 1671.2281879783957,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildComplexLoopModel",
            "value": 0.06749486774959476,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.buildSimpleLoopModel",
            "value": 0.015564249450489223,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeCollect",
            "value": 0.06236465964991662,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeComplex",
            "value": 0.09540618307180233,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.LoopTransformBenchmark.generateStreamCodeForEach",
            "value": 0.047183862885389304,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithASTVisitor",
            "value": 76.52521063968702,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.PatternMatchingBenchmark.detectAssertionsWithRegex",
            "value": 41.076823267009686,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.createSimpleMethod",
            "value": 7.560581520535282,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.newStyleMathMaxCheck",
            "value": 79.26219508766965,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.oldStyleMathMaxCheck",
            "value": 0.3697337473481059,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.queryHasSignature",
            "value": 79.0233008733381,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.queryIsListAdd",
            "value": 286.03840216660876,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.MethodInfoBenchmark.queryIsMathMax",
            "value": 78.01231489624894,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.complexNewStyleCheck",
            "value": 21.035595044197738,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.complexOldStyleCheck",
            "value": 0.3728865138160239,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.newStyleIsMathMax",
            "value": 78.54178483910479,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.newStyleModifierChecks",
            "value": 13.987477759975993,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.newStylePatternDetection",
            "value": 107.17898561831052,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.oldStyleIsMathMax",
            "value": 0.36873866522212567,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.oldStyleModifierChecks",
            "value": 0.36867741228277884,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.PatternMatchingStyleBenchmark.oldStylePatternDetection",
            "value": 0.3681651777734775,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.createParameterizedType",
            "value": 63.717866955922354,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.createSimpleType",
            "value": 23.142983796307828,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.newStyleTypeCheck",
            "value": 20.522323714300907,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.oldStyleTypeCheck",
            "value": 0.37232016127796574,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsClass",
            "value": 20.02363797578708,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsCollection",
            "value": 23.029695218970314,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsNumeric",
            "value": 0.37501915303213673,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsOptional",
            "value": 22.48557837692303,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.TypeInfoBenchmark.queryIsStream",
            "value": 20.936433022985458,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.builderAllocation",
            "value": 23.802055944712162,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.iterateRawStrings",
            "value": 23.731920426576643,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.iterateTypeInfos",
            "value": 24.61443203647915,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.rawCollectionCheck",
            "value": 0.3725481795150974,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.rawTypeComparison",
            "value": 0.3724547892240252,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.recordAllocation",
            "value": 3.940711717850644,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.wrapperCollectionCheck",
            "value": 22.95661331988496,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.sandbox.benchmarks.astapi.WrapperOverheadBenchmark.wrapperTypeComparison",
            "value": 39.36096953893225,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ]
  }
}