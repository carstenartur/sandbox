window.BENCHMARK_DATA = {
  "lastUpdate": 1769927803618,
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
      }
    ]
  }
}