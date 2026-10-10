from pathlib import Path
import sys
root = Path(sys.argv[1])
path = root / 'sandbox_math_cleanup/src/org/sandbox/jdt/internal/corext/fix/math/MathExplanation.java'
text = path.read_text()
before = 'kinds.stream().anyMatch(NumericKind::integral)'
assert text.count(before) == 1
path.write_text(text.replace(before, 'kinds.stream().anyMatch(kind -> kind != NumericKind.BIG_INTEGER && kind.integral())'))
path = root / 'sandbox_math_cleanup_test/src/org/sandbox/jdt/internal/ui/fix/MathematicalWorkbenchSWTBotTest.java'
text = path.read_text()
before = 'assertEquals(9,json.path("requestedOptions").size());'
assert text.count(before) == 1
path.write_text(text.replace(before, 'assertEquals(10,json.path("requestedOptions").size());'))
