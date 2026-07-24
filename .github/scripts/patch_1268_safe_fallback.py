#!/usr/bin/env python3
"""One-off branch repair for the concrete collector fallback regression."""

from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def patch_handler() -> None:
    path = Path(
        "sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/"
        "SafeEnhancedForHandler.java"
    )
    text = path.read_text(encoding="utf-8")
    old = """		if (model.getTerminal() instanceof CollectTerminal collectTerminal) {
			VariableDeclarationStatement accumulator= findFreshAccumulator(loop, collectTerminal.targetVariable());
			if (accumulator != null) {
				if (!collectTerminal.targetVariable().equals(
						CollectPatternDetector.isEmptyCollectionDeclaration(accumulator))) {
					return false;
				}
				CollectTerminal preserved= ConcreteCollectionFactory.preserveFactory(accumulator, collectTerminal);
				if (preserved == null) {
					return false;
				}
				model.setTerminal(preserved);
			}
		}
"""
    new = """		if (model.getTerminal() instanceof CollectTerminal collectTerminal) {
			VariableDeclarationStatement adjacent= findAdjacentAccumulator(loop, collectTerminal.targetVariable());
			VariableDeclarationStatement fresh= findFreshAccumulator(loop, collectTerminal.targetVariable());
			if (fresh != null) {
				CollectTerminal preserved= ConcreteCollectionFactory.preserveFactory(fresh, collectTerminal);
				if (preserved == null) {
					return false;
				}
				model.setTerminal(preserved);
			} else if (adjacent != null
					&& !ConcreteCollectionFactory.hasSupportedConcreteType(adjacent, collectTerminal)) {
				return false;
			}
		}
"""
    text = replace_once(text, old, new, "collector scheduling block")

    old = """	private VariableDeclarationStatement findFreshAccumulator(EnhancedForStatement loop, String targetVariable) {
		if (targetVariable == null || !(loop.getParent() instanceof Block block)) {
			return null;
		}
		@SuppressWarnings(\"unchecked\") //$NON-NLS-1$
		List<Statement> statements= block.statements();
		int loopIndex= statements.indexOf(loop);
		if (loopIndex <= 0) {
			return null;
		}
		Statement previous= statements.get(loopIndex - 1);
		if (!(previous instanceof VariableDeclarationStatement declaration)
				|| declaration.fragments().size() != 1) {
			return null;
		}
		VariableDeclarationFragment fragment=
				(VariableDeclarationFragment) declaration.fragments().get(0);
		return targetVariable.equals(fragment.getName().getIdentifier()) ? declaration : null;
	}
"""
    new = """	private VariableDeclarationStatement findFreshAccumulator(EnhancedForStatement loop, String targetVariable) {
		VariableDeclarationStatement declaration= findAdjacentAccumulator(loop, targetVariable);
		return declaration != null && targetVariable.equals(
				CollectPatternDetector.isEmptyCollectionDeclaration(declaration)) ? declaration : null;
	}

	private VariableDeclarationStatement findAdjacentAccumulator(EnhancedForStatement loop, String targetVariable) {
		if (targetVariable == null || !(loop.getParent() instanceof Block block)) {
			return null;
		}
		@SuppressWarnings(\"unchecked\") //$NON-NLS-1$
		List<Statement> statements= block.statements();
		int loopIndex= statements.indexOf(loop);
		if (loopIndex <= 0) {
			return null;
		}
		Statement previous= statements.get(loopIndex - 1);
		if (!(previous instanceof VariableDeclarationStatement declaration)
				|| declaration.fragments().size() != 1) {
			return null;
		}
		VariableDeclarationFragment fragment=
				(VariableDeclarationFragment) declaration.fragments().get(0);
		return targetVariable.equals(fragment.getName().getIdentifier()) ? declaration : null;
	}
"""
    text = replace_once(text, old, new, "accumulator lookup method")
    path.write_text(text, encoding="utf-8")


def patch_factory() -> None:
    path = Path(
        "sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/"
        "ConcreteCollectionFactory.java"
    )
    text = path.read_text(encoding="utf-8")
    helper = """	/**
	 * Returns whether the adjacent declaration uses one modeled JDK collection type.
	 * Constructor arguments are allowed because callers using this predicate retain
	 * the original declaration and only replace the loop with a direct forEach.
	 */
	static boolean hasSupportedConcreteType(VariableDeclarationStatement declaration, CollectTerminal terminal) {
		if (declaration == null || terminal == null || declaration.fragments().size() != 1) {
			return false;
		}
		VariableDeclarationFragment fragment= (VariableDeclarationFragment) declaration.fragments().get(0);
		Expression initializer= fragment.getInitializer();
		if (!(initializer instanceof ClassInstanceCreation creation) || hasAnonymousClass(creation)) {
			return false;
		}
		ITypeBinding createdType= creation.resolveTypeBinding();
		ITypeBinding declaredType= declaration.getType().resolveBinding();
		if (createdType == null || declaredType == null) {
			return false;
		}
		ITypeBinding createdErasure= createdType.getErasure();
		if (createdErasure == null || declaredType.getErasure() == null) {
			return false;
		}
		CollectTerminal.CollectorType supportedType= SUPPORTED.get(createdErasure.getQualifiedName());
		return supportedType == terminal.collectorType()
				&& createdType.isAssignmentCompatible(declaredType);
	}

"""
    if helper not in text:
        marker = """	/**
	 * Returns a qualified constructor reference for one exactly modeled initializer.
"""
        if text.count(marker) != 1:
            raise SystemExit("factory helper insertion marker not found exactly once")
        text = text.replace(marker, helper + marker, 1)
    path.write_text(text, encoding="utf-8")


def patch_safety_tests() -> None:
    path = Path(
        "sandbox_functional_converter_test/src/org/sandbox/jdt/ui/tests/quickfix/"
        "AccumulatorAndSequenceSafetyTest.java"
    )
    text = path.read_text(encoding="utf-8")
    old = """	@Test
	void capacityConstructorFailsClosed() throws CoreException {
		assertNoChange(\"\"\"
				package test;
				import java.util.*;
				class E {
					ArrayList<String> copy(List<String> source) {
						ArrayList<String> result = new ArrayList<>(16);
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				\"\"\");
	}
"""
    new = """	@Test
	void capacityConstructorPreservesDeclarationAndConvertsLoop() throws CoreException {
		assertExpected(\"\"\"
				package test;
				import java.util.*;
				class E {
					ArrayList<String> copy(List<String> source) {
						ArrayList<String> result = new ArrayList<>(16);
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				\"\"\", \"\"\"
				package test;
				import java.util.*;
				class E {
					ArrayList<String> copy(List<String> source) {
						ArrayList<String> result = new ArrayList<>(16);
						source.forEach(item -> result.add(item));
						return result;
					}
				}
				\"\"\");
	}
"""
    text = replace_once(text, old, new, "capacity-constructor regression")

    old = """	@Test
	void comparatorConstructorFailsClosed() throws CoreException {
		assertNoChange(\"\"\"
				package test;
				import java.util.*;
				class E {
					TreeSet<String> copy(List<String> source) {
						TreeSet<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				\"\"\");
	}
"""
    new = """	@Test
	void comparatorConstructorPreservesDeclarationAndConvertsLoop() throws CoreException {
		assertExpected(\"\"\"
				package test;
				import java.util.*;
				class E {
					TreeSet<String> copy(List<String> source) {
						TreeSet<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				\"\"\", \"\"\"
				package test;
				import java.util.*;
				class E {
					TreeSet<String> copy(List<String> source) {
						TreeSet<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
						source.forEach(item -> result.add(item));
						return result;
					}
				}
				\"\"\");
	}
"""
    text = replace_once(text, old, new, "comparator-constructor regression")
    path.write_text(text, encoding="utf-8")


def patch_multiple_loop_expectation() -> None:
    path = Path(
        "sandbox_functional_converter_test/src/org/sandbox/jdt/ui/tests/quickfix/"
        "AdditionalLoopPatternsTest.java"
    )
    text = path.read_text(encoding="utf-8")
    method_start = text.index(
        "public void testMultipleLoopsPopulatingList_preservesConcreteAccumulator()"
    )
    expected_start = text.index('\n\t\tString expected = """', method_start)
    expected_end = text.index(
        '\n\t\tassertConversion("RuleChainBuilder.java", given, expected);', expected_start
    )
    expected = '''
		String expected = """
package test1;
import java.util.*;
public class RuleChainBuilder {
	private List<MethodRule> methodRules = new ArrayList<>();
	private List<TestRule> testRules = new ArrayList<>();
	private Map<Object, Integer> orderValues = new HashMap<>();
	private static final Comparator<RuleEntry> ENTRY_COMPARATOR = Comparator.comparingInt(e -> e.order);

	private List<RuleEntry> getSortedEntries() {
		List<RuleEntry> ruleEntries = new ArrayList<RuleEntry>(
				methodRules.size() + testRules.size());
		methodRules.forEach(
				rule -> ruleEntries.add(new RuleEntry(rule, RuleEntry.TYPE_METHOD_RULE, orderValues.get(rule))));
		testRules
				.forEach(rule -> ruleEntries.add(new RuleEntry(rule, RuleEntry.TYPE_TEST_RULE, orderValues.get(rule))));
		Collections.sort(ruleEntries, ENTRY_COMPARATOR);
		return ruleEntries;
	}

	interface MethodRule {}
	interface TestRule {}

	static class RuleEntry {
		static final int TYPE_METHOD_RULE = 1;
		static final int TYPE_TEST_RULE = 2;
		Object rule;
		int type;
		int order;
		RuleEntry(Object rule, int type, Integer order) {
			this.rule = rule;
			this.type = type;
			this.order = order != null ? order : 0;
		}
	}
}
				""";'''
    text = text[:expected_start] + expected + text[expected_end:]
    path.write_text(text, encoding="utf-8")


def main() -> None:
    patch_handler()
    patch_factory()
    patch_safety_tests()
    patch_multiple_loop_expectation()


if __name__ == "__main__":
    main()
