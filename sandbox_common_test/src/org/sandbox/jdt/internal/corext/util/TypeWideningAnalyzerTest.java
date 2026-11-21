/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Complete, binding-resolved sources derived from the SWT corpus failures. */
class TypeWideningAnalyzerTest {

	static Stream<Arguments> constrainedUses() {
		return Stream.of(
				Arguments.of("constructor argument", body("String candidate = \"a\"; candidate.length(); new String(candidate);")),
				Arguments.of("parenthesized method argument", """
						class Sample {
						    void accept(String value) { }
						    void run() { String candidate = "a"; candidate.length(); accept((candidate)); }
						}
						"""),
				Arguments.of("method reference", body("String candidate = \"a\"; candidate.length(); java.util.function.Supplier<String> call = candidate::trim;")),
				Arguments.of("conditional return", """
						class Sample { String run() {
						    String candidate = "a";
						    return candidate.length() == 0 ? null : candidate;
						} }
						"""),
				Arguments.of("parenthesized return", "class Sample { String run() { String candidate = \"a\"; candidate.length(); return (candidate); } }"),
				Arguments.of("lambda result", body("String candidate = \"a\"; candidate.length(); java.util.function.Supplier<String> call = () -> candidate;")),
				Arguments.of("array initializer", body("String candidate = \"a\"; candidate.length(); String[] values = {candidate};")),
				Arguments.of("alias initializer", body("String candidate = \"a\"; candidate.length(); String alias = candidate;")),
				Arguments.of("throw contract", body("RuntimeException candidate = new RuntimeException(); candidate.getMessage(); throw candidate;")),
				Arguments.of("string switch", body("String candidate = \"a\"; candidate.length(); switch (candidate) { case \"a\": break; default: break; }")),
				Arguments.of("unboxing", body("Integer candidate = Integer.valueOf(1); candidate.toString(); int value = candidate;")),
				Arguments.of("compound assignment", body("String candidate = \"a\"; candidate.length(); candidate += \"b\";")),
				Arguments.of("pattern instanceof", """
						class Sample {
						    interface Wide { void ping(); }
						    static class Base implements Wide { public void ping() { } }
						    static class Child extends Base { }
						    void run(Base input) {
						        Base candidate = input; candidate.ping();
						        if (candidate instanceof Child child) child.ping();
						    }
						}
						"""),
				Arguments.of("newly redundant declaration cast", """
						class Sample {
						    static class Base { public void ping() { } }
						    static class Child extends Base { }
						    void run(Base input) { Child candidate = (Child) input; candidate.ping(); }
						}
						"""),
				Arguments.of("protected inherited clone", """
						class Sample {
						    static class Base { }
						    static class Child extends Base implements Cloneable {
						        @Override public Object clone() { return new Child(); }
						    }
						    void run() { Child candidate = new Child(); candidate.clone(); }
						}
						"""),
				Arguments.of("broader checked exception", """
						class Sample {
						    static class Base { public void ping() throws java.io.IOException { } }
						    static class Child extends Base { @Override public void ping() { } }
						    void run() { Child candidate = new Child(); candidate.ping(); }
						}
						"""),
				Arguments.of("intersected inherited exception contract", """
						class Sample {
						    interface First { void ping() throws java.io.IOException; }
						    interface Second { void ping() throws java.sql.SQLException; }
						    interface Both extends First, Second { }
						    void run(Both input) { Both candidate = input; candidate.ping(); }
						}
						"""),
				Arguments.of("hidden field identity", """
						class Sample {
						    static class Base { public int count = 1; }
						    static class Child extends Base { public int count = 2; }
						    int run() { Child candidate = new Child(); return candidate.count; }
						}
						"""),
				Arguments.of("inherited type argument annotation", """
						class Sample {
						    @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE) @interface Mark { }
						    static class Base<T> { public T get() { return null; } }
						    static class Child<T> extends Base<@Mark T> { }
						    void run(Child<String> input) { Child<String> candidate = input; candidate.get(); }
						}
						"""),
				Arguments.of("explicit local type annotation", """
						class Sample {
						    @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE) @interface Mark { }
						    static class Base { public void ping() { } }
						    static class Child extends Base { }
						    void run() { @Mark Child candidate = new Child(); candidate.ping(); }
						}
						"""));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("constrainedUses")
	void preservesUnsupportedOrNarrowerTypeContracts(String description, String source) {
		CompilationUnit unit = parse(source);
		assertTrue(candidate(unit).isEmpty(), description);
	}

	static Stream<Arguments> safeUses() {
		return Stream.of(
				Arguments.of("map receiver", body("java.util.HashMap<String, Integer> candidate = new java.util.HashMap<>(); candidate.put(\"a\", 1); candidate.size();")),
				Arguments.of("parenthesized receiver and null comparison", body("String candidate = \"a\"; if (candidate != null) (candidate).length();")),
				Arguments.of("simple reassignment", "class Sample { int run() { String candidate = null; candidate = \"a\"; return candidate.length(); } }"),
				Arguments.of("iteration", body("java.util.List<String> candidate = new java.util.ArrayList<>(); candidate.size(); for (String value : candidate) value.length();")),
				Arguments.of("necessary downcast", """
						class Sample {
						    static class Base { public void ping() { } }
						    static class Child extends Base { }
						    void run(Object input) { Child candidate = (Child) input; candidate.ping(); }
						}
						"""));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("safeUses")
	void retainsProvenWideningWithoutNewCompilerDiagnostics(String description, String source) {
		CompilationUnit before = parse(source);
		var result = candidate(before).orElseThrow(() -> new AssertionError(description));
		Type[] declarationType = new Type[1];
		before.accept(new ASTVisitor() {
			@Override public boolean visit(VariableDeclarationFragment fragment) {
				if ("candidate".equals(fragment.getName().getIdentifier()))
					declarationType[0] = ((VariableDeclarationStatement) fragment.getParent()).getType();
				return true;
			}
		});
		Type type = declarationType[0];
		String after = source.substring(0, type.getStartPosition()) + result.getWidestType().getQualifiedName()
				+ source.substring(type.getStartPosition() + type.getLength());
		Map<String, Long> baseline = diagnostics(before);
		diagnostics(parse(after)).forEach((key, count) -> assertTrue(count <= baseline.getOrDefault(key, 0L), description + ": " + key));
	}

	private static java.util.Optional<TypeWideningAnalyzer.TypeWideningResult> candidate(CompilationUnit unit) {
		return TypeWideningAnalyzer.analyzeCompilationUnit(unit).values().stream()
				.filter(result -> "candidate".equals(result.getVariableBinding().getName())).findFirst();
	}

	private static String body(String statements) { return "class Sample { void run() { " + statements + " } }"; }

	private static CompilationUnit parse(String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setUnitName("Sample.java");
		parser.setEnvironment(new String[0], null, null, true);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(false);
		Map<String, String> options = new HashMap<>(JavaCore.getOptions());
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		options.put(JavaCore.COMPILER_PB_MAX_PER_UNIT, "0");
		options.replaceAll((key, severity) -> key.startsWith(JavaCore.PLUGIN_ID + ".compiler.problem.")
				&& (JavaCore.IGNORE.equals(severity) || JavaCore.INFO.equals(severity)) ? JavaCore.WARNING : severity);
		parser.setCompilerOptions(options);
		CompilationUnit unit = (CompilationUnit) parser.createAST(null);
		for (var problem : unit.getProblems()) assertFalse(problem.isError(), problem::getMessage);
		return unit;
	}

	private static Map<String, Long> diagnostics(CompilationUnit unit) {
		return Arrays.stream(unit.getProblems()).filter(problem -> problem.isWarning() || problem.isError())
				.collect(Collectors.groupingBy(problem -> problem.getID() + ":" + Arrays.toString(problem.getArguments()), Collectors.counting()));
	}
}
