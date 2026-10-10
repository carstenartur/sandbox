from pathlib import Path
import sys
root = Path(sys.argv[1])
path = root / 'sandbox_math_cleanup/src/org/sandbox/jdt/internal/corext/fix/math/MathExplanation.java'
text = path.read_text()
before = 'kinds.stream().anyMatch(NumericKind::integral)'
assert text.count(before) == 1
path.write_text(text.replace(before, 'kinds.stream().anyMatch(kind -> kind != NumericKind.BIG_INTEGER && kind.integral())'))
for name in ('MathematicalWorkbenchSWTBotTest.java', 'MathematicalReportTest.java'):
    path = root / 'sandbox_math_cleanup_test/src/org/sandbox/jdt/internal/ui/fix' / name
    text = path.read_text()
    before = 'assertEquals(9,json.path("requestedOptions").size());'
    assert text.count(before) == 1
    path.write_text(text.replace(before, 'assertEquals(10,json.path("requestedOptions").size());\n  assertEquals("NONTRIVIAL",json.path("requestedOptions").path(MathCleanUpOptions.EXPLANATIONS).asText());'))
path = root / 'sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/MathematicalExplanationTest.java'
text = path.read_text()
anchor = '        assertTrue(generated.contains("// Verified mathematics"), generated);'
assert text.count(anchor) == 1
path.write_text(text.replace(anchor, '        System.out.println("ACTUAL_EXPLAINED_JAVA=" + generated);\n' + anchor))
path = root / 'docs/ECLIPSE_HELP.md'
text = path.read_text()
old = '''The Mathematics Help TOC includes four native before/after previews, increasing
in source-calculation complexity. One unchanged method comes from Bouncy Castle
production code; three inputs come from the pinned JDT Core/UI regression suites.
See `sandbox_eclipse_help_swtbot_test/fixtures/mathematics/README.md` for exact
provenance and licensing. These are statically known calculations, not runtime
performance evidence or claims of whole-project optimization.'''
new = '''The Mathematics Help TOC includes four native before/after previews of
runtime-dependent calculations in complete, unchanged Bouncy Castle production
classes. See `sandbox_eclipse_help_swtbot_test/fixtures/mathematics/README.md` for
the pinned source-archive identity, provenance and licensing. The old constant-only
examples are withdrawn; source names and expected fixture outputs do not select
optimizer rules. These examples are regression evidence, not measured throughput
or claims of whole-project optimization.'''
assert text.count(old) == 1
path.write_text(text.replace(old, new))
path = root / 'sandbox_math_cleanup_help/html/cleanup.html'
text = path.read_text()
old = '''<p><a href="examples.html"><strong>Four real before/after screenshots, from a
constant product to nested calculations with multiple outputs</strong></a> show
the actual Eclipse cleanup preview, exact upstream sources and the required
settings. They include Bouncy Castle production code and Eclipse JDT regression
fixtures, with their different roles clearly identified.</p>'''
new = '''<p><a href="examples.html"><strong>Four real before/after screenshots of
runtime-dependent Bouncy Castle calculations</strong></a> show the actual Eclipse
cleanup preview, complete pinned production inputs and the required settings.
They replace the former constant-only examples. Expected fixture results are test
data, never inputs to mathematical candidate generation.</p>'''
assert text.count(old) == 1
path.write_text(text.replace(old, new))
