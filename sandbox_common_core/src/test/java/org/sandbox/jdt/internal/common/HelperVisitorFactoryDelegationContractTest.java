/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.internal.common;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;

/**
 * Executable compatibility contract for the delegating convenience API in
 * {@link HelperVisitorFactory}.
 *
 * <p>The factory intentionally exposes a broad, regular set of overloads. A
 * reflection-driven test is less error-prone than maintaining several hundred
 * near-identical hand-written assertions and also detects newly added overloads
 * whose parameter contract is not covered here.</p>
 */
public class HelperVisitorFactoryDelegationContractTest {

	private static final String JAVA_SOURCE = """
			package sample;

			import java.io.ByteArrayInputStream;
			import java.io.IOException;
			import java.io.InputStream;
			import java.io.Serializable;
			import java.util.ArrayList;
			import java.util.List;
			import java.util.Map;
			import java.util.function.Function;
			import java.util.function.Supplier;

			class Parent {
				protected int base;
			}

			/**
			 * Fixture text.
			 * {@link Fixture#field}
			 * {@link Fixture#exercise(List)}
			 */
			@Deprecated
			@SuppressWarnings(value = { "unused" })
			public class Fixture<T extends Number> extends Parent {
				@Deprecated
				private String field = "value";
				static { int initialized = 0; }
				{ int initialized = 1; }

				enum Kind { ONE, TWO }
				record Pair(int left, int right) {}
				@interface Named { String value() default "named"; }

				Fixture() { this(1); }
				Fixture(int value) { super(); base = value; }

				@SuppressWarnings("unused")
				public <U extends CharSequence> int exercise(List<String> values) throws IOException {
					assert values != null : "values";
					int[] array = new int[] { 1, 2, 3 };
					int total = 0;
					char marker = 'x';
					String local = null;
					List<String> copy = new ArrayList<>();
					Map.Entry<String, String> entry = null;
					/* block comment */
					label:
					for (int index = 0; index < array.length; index++) {
						if (index == 0) {
							continue;
						}
						total += array[index];
						if (total > 10) {
							break label;
						}
					}
					for (String value : values) {
						total += value.length();
					}
					int index = 0;
					do {
						index++;
					} while (index < 1);
					while (index < 2) {
						++index;
					}
					Object object = values;
					if (object instanceof List<?> list) {
						total += list.size();
					}
					synchronized (this) {
						this.field = (String) field;
						total += super.base;
					}
					Runnable lambda = () -> System.out.println(field);
					Runnable anonymous = new Runnable() {
						@Override
						public void run() {
							field = "run";
						}
					};
					Runnable serial = (Runnable & Serializable) () -> { };
					Supplier<List<String>> constructor = ArrayList::new;
					Function<String, Integer> typeReference = String::length;
					Supplier<Integer> expressionReference = field::length;
					Supplier<String> superReference = super::toString;
					class LocalType { }
					Class<?> type = String.class;
					try (InputStream input = new ByteArrayInputStream(new byte[0])) {
						switch (total) {
							case 0 -> total = 1;
							default -> total = 2;
						}
						int result = switch (total) {
							case 1 -> 2;
							default -> {
								yield 3;
							}
						};
						if (result < 0) {
							throw new IOException("negative");
						}
					} catch (IOException | IllegalArgumentException exception) {
						total = -1;
					} finally {
						total++;
					}
					// line comment
					;
					return total > 0 ? total : -total;
				}
			}
			"""; //$NON-NLS-1$

	private static final String MODULE_SOURCE = """
			open module sample.module {
				requires transitive java.sql;
				exports sample.api;
				opens sample.internal;
				uses java.lang.Runnable;
				provides java.lang.Runnable with sample.Provider;
			}
			"""; //$NON-NLS-1$

	@Test
	void everyPublicCallOverloadDelegatesThroughHelperVisitor() {
		CompilationUnit javaUnit = parse(JAVA_SOURCE, "Fixture.java"); //$NON-NLS-1$
		CompilationUnit moduleUnit = parse(MODULE_SOURCE, "module-info.java"); //$NON-NLS-1$
		AtomicInteger callbackCount = new AtomicInteger();
		List<String> failures = new ArrayList<>();

		List<Method> callMethods = Arrays.stream(HelperVisitorFactory.class.getDeclaredMethods())
				.filter(method -> Modifier.isPublic(method.getModifiers()))
				.filter(method -> Modifier.isStatic(method.getModifiers()))
				.filter(method -> method.getName().startsWith("call")) //$NON-NLS-1$
				.sorted(Comparator.comparing(HelperVisitorFactoryDelegationContractTest::signature))
				.toList();

		assertTrue(callMethods.size() >= 200,
				() -> "Expected the complete HelperVisitorFactory call API, found only " + callMethods.size()); //$NON-NLS-1$

		for (Method method : callMethods) {
			CompilationUnit unit = requiresModuleUnit(method) ? moduleUnit : javaUnit;
			try {
				method.invoke(null, arguments(method, unit, callbackCount));
			} catch (InvocationTargetException exception) {
				Throwable cause = exception.getCause();
				failures.add(signature(method) + " -> " + cause.getClass().getName() + ": " + cause.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			} catch (ReflectiveOperationException | RuntimeException exception) {
				failures.add(signature(method) + " -> " + exception.getClass().getName() + ": " + exception.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		assertTrue(failures.isEmpty(), () -> "Factory delegation failures:\n" + String.join("\n", failures)); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(callbackCount.get() >= 100,
				() -> "The fixture should exercise real visitor callbacks, but observed only " + callbackCount.get()); //$NON-NLS-1$
	}

	private static Object[] arguments(Method method, CompilationUnit unit, AtomicInteger callbackCount) {
		Class<?>[] parameterTypes = method.getParameterTypes();
		Object[] arguments = new Object[parameterTypes.length];
		for (int index = 0; index < parameterTypes.length; index++) {
			arguments[index] = argument(method, parameterTypes[index], index, unit, callbackCount);
		}
		return arguments;
	}

	private static Object argument(Method method, Class<?> parameterType, int index, CompilationUnit unit,
			AtomicInteger callbackCount) {
		if (EnumSet.class.isAssignableFrom(parameterType)) {
			return EnumSet.of(VisitorEnum.CompilationUnit);
		}
		if (Set.class.isAssignableFrom(parameterType)) {
			return new HashSet<ASTNode>();
		}
		if (ReferenceHolder.class.isAssignableFrom(parameterType)) {
			return new ReferenceHolder<>();
		}
		if (BiPredicate.class.isAssignableFrom(parameterType)) {
			return (BiPredicate<ASTNode, ReferenceHolder<Object, Object>>) (node, holder) -> {
				callbackCount.incrementAndGet();
				return true;
			};
		}
		if (BiConsumer.class.isAssignableFrom(parameterType)) {
			return (BiConsumer<ASTNode, ReferenceHolder<Object, Object>>) (node, holder) -> callbackCount.incrementAndGet();
		}
		if (ASTNode.class.isAssignableFrom(parameterType)) {
			return matchingNode(parameterType, unit);
		}
		if (parameterType == Class.class) {
			return classArgument(method);
		}
		if (parameterType == String.class) {
			return stringArgument(method, index);
		}
		if (parameterType == String[].class) {
			return new String[0];
		}
		throw new IllegalArgumentException("Unsupported factory parameter type " + parameterType.getTypeName() //$NON-NLS-1$
				+ " in " + signature(method)); //$NON-NLS-1$
	}

	private static ASTNode matchingNode(Class<?> parameterType, CompilationUnit unit) {
		if (parameterType.isInstance(unit)) {
			return unit;
		}
		List<ASTNode> nodes = new ArrayList<>();
		unit.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				nodes.add(node);
			}
		});
		if (unit.getCommentList() != null) {
			nodes.addAll(unit.getCommentList());
		}
		return nodes.stream()
				.filter(parameterType::isInstance)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Fixture has no " + parameterType.getTypeName())); //$NON-NLS-1$
	}

	private static Class<?> classArgument(Method method) {
		String name = method.getName();
		if (name.contains("ClassInstanceCreation")) { //$NON-NLS-1$
			return ArrayList.class;
		}
		if (name.contains("MethodInvocation")) { //$NON-NLS-1$
			return String.class;
		}
		if (name.contains("VariableDeclarationStatement")) { //$NON-NLS-1$
			return String.class;
		}
		return Object.class;
	}

	private static String stringArgument(Method method, int parameterIndex) {
		String name = method.getName();
		int stringOrdinal = 0;
		int stringCount = 0;
		Class<?>[] parameterTypes = method.getParameterTypes();
		for (int index = 0; index < parameterTypes.length; index++) {
			if (parameterTypes[index] == String.class) {
				if (index < parameterIndex) {
					stringOrdinal++;
				}
				stringCount++;
			}
		}
		if (name.contains("ImportDeclaration")) { //$NON-NLS-1$
			return "java.util.List"; //$NON-NLS-1$
		}
		if (name.contains("Annotation")) { //$NON-NLS-1$
			return "java.lang.Deprecated"; //$NON-NLS-1$
		}
		if (name.contains("FieldDeclaration")) { //$NON-NLS-1$
			return stringOrdinal == 0 ? "java.lang.Deprecated" : "java.lang.Object"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (name.contains("MethodInvocation")) { //$NON-NLS-1$
			if (stringCount == 1) {
				return "length"; //$NON-NLS-1$
			}
			return stringOrdinal == 0 ? "java.lang.String" : "length"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (name.contains("TypeDeclaration")) { //$NON-NLS-1$
			return "java.lang.Object"; //$NON-NLS-1$
		}
		return "java.lang.Object"; //$NON-NLS-1$
	}

	private static boolean requiresModuleUnit(Method method) {
		String name = method.getName();
		return name.contains("Module") || name.endsWith("DirectiveVisitor"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static CompilationUnit parse(String source, String unitName) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		java.util.Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		parser.setEnvironment(new String[0], new String[0], null, true);
		parser.setBindingsRecovery(true);
		parser.setResolveBindings(true);
		parser.setStatementsRecovery(true);
		parser.setUnitName(unitName);
		return (CompilationUnit) parser.createAST(null);
	}

	private static String signature(Method method) {
		return method.getName() + Arrays.stream(method.getParameterTypes())
				.map(Class::getSimpleName)
				.collect(Collectors.joining(", ", "(", ")")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
