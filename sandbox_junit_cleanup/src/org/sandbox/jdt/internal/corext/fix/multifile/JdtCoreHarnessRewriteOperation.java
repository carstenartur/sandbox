/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.eclipse.text.edits.TextEditGroup;

/** Applies exact source edits for the first direct JDT Core harness slice. */
final class JdtCoreHarnessRewriteOperation extends CompilationUnitRewriteOperationWithSourceRange {

	private enum Kind {
		ADD_BRIDGE,
		MIGRATE_FAMILY
	}

	private static final String JUPITER_BRIDGE_SOURCE= """
			@org.junit.jupiter.api.TestMethodOrder(Jupiter.JdtCoreMethodOrderer.class)
			@org.junit.jupiter.api.extension.ExtendWith(Jupiter.JdtCoreFilterCondition.class)
			public abstract static class Jupiter extends org.eclipse.test.performance.PerformanceTestCaseJunit5 {
				private String jdtCoreTestName;
				private boolean jdtCoreIndexerDisabled;
				private boolean jdtCoreFirstTest;

				@Override
				@org.junit.jupiter.api.BeforeEach
				public void setUp(org.junit.jupiter.api.TestInfo testInfo) throws Exception {
					super.setUp(testInfo);
					this.jdtCoreTestName= testInfo.getTestMethod().map(java.lang.reflect.Method::getName)
							.orElse(testInfo.getDisplayName());
					this.jdtCoreIndexerDisabled= org.eclipse.jdt.core.JavaCore.getPlugin() != null
							&& isIndexDisabledForTest();
					if (this.jdtCoreIndexerDisabled) {
						disableJdtCoreIndexer();
					}

					boolean firstRun= CURRENT_CLASS == null;
					this.jdtCoreFirstTest= firstRun || CURRENT_CLASS != getClass();
					if (this.jdtCoreFirstTest) {
						if (CURRENT_CLASS != null && RUN_GC) {
							runJdtCoreGarbageCollection();
						}
						CURRENT_CLASS= getClass();
						CURRENT_CLASS_NAME= getClass().getName();
						int marker= CURRENT_CLASS_NAME.indexOf(".tests.");
						if (marker >= 0) {
							CURRENT_CLASS_NAME= CURRENT_CLASS_NAME.substring(marker + 7);
						}
					}
					if (STORE_MEMORY != null && MEM_LOG_FILE != null) {
						if (firstRun) {
							runJdtCoreGarbageCollection();
						}
						if (ALL_TESTS_LOG && MEM_LOG_FILE.exists()) {
							writeMemoryStart();
						} else if (firstRun) {
							long total= Runtime.getRuntime().totalMemory();
							long used= total - Runtime.getRuntime().freeMemory();
							System.out.println("\talready used while starting: " + formatMemory(used));
						}
					}
				}

				@Override
				@org.junit.jupiter.api.AfterEach
				public void tearDown() throws Exception {
					try {
						super.tearDown();
						if (this.jdtCoreIndexerDisabled) {
							enableJdtCoreIndexer();
						}
						if (STORE_MEMORY != null && MEM_LOG_FILE != null
								&& (this.jdtCoreFirstTest || ALL_TESTS_LOG) && MEM_LOG_FILE.exists()) {
							writeMemoryEnd();
						}
					} finally {
						Thread.interrupted();
					}
				}

				public String getName() {
					return this.jdtCoreTestName;
				}

				public boolean isIndexDisabledForTest() {
					return true;
				}

				@Override
				public void startMeasuring() {
					super.startMeasuring();
				}

				@Override
				public void stopMeasuring() {
					super.stopMeasuring();
				}

				@Override
				public void commitMeasurements() {
					super.commitMeasurements();
				}

				@Override
				public void assertPerformance() {
					super.assertPerformance();
				}

				protected boolean isFirst() {
					return this.jdtCoreFirstTest;
				}

				private void disableJdtCoreIndexer() {
					while (org.eclipse.jdt.internal.core.JavaModelManager.getIndexManager().isEnabled()) {
						org.eclipse.jdt.internal.core.JavaModelManager.getIndexManager().disable();
					}
				}

				private void enableJdtCoreIndexer() {
					while (!org.eclipse.jdt.internal.core.JavaModelManager.getIndexManager().isEnabled()) {
						org.eclipse.jdt.internal.core.JavaModelManager.getIndexManager().enable();
					}
				}

				private void runJdtCoreGarbageCollection() {
					for (int iteration= 0; iteration < MAX_GC; iteration++) {
						long before= Runtime.getRuntime().freeMemory();
						System.gc();
						long delta= Runtime.getRuntime().freeMemory() - before;
						try {
							Thread.sleep(TIME_GC);
						} catch (InterruptedException exception) {
							Thread.currentThread().interrupt();
							return;
						}
						if (delta <= DELTA_GC) {
							return;
						}
					}
				}

				private void writeMemoryStart() throws java.io.FileNotFoundException {
					try (java.io.PrintStream stream= new java.io.PrintStream(
							new java.io.FileOutputStream(MEM_LOG_FILE, true))) {
						stream.print(CURRENT_CLASS_NAME);
						stream.print('\t');
						stream.print(this.jdtCoreTestName);
						stream.print('\t');
						writeMemoryValues(stream);
					}
				}

				private void writeMemoryEnd() throws java.io.FileNotFoundException {
					try (java.io.PrintStream stream= new java.io.PrintStream(
							new java.io.FileOutputStream(MEM_LOG_FILE, true))) {
						stream.print(CURRENT_CLASS_NAME);
						stream.print('\t');
						if (ALL_TESTS_LOG) {
							stream.print(".".repeat(Math.max(0, this.jdtCoreTestName.length() - 4)));
							stream.print("end:\t");
						}
						writeMemoryValues(stream);
					}
				}

				private static void writeMemoryValues(java.io.PrintStream stream) {
					long total= Runtime.getRuntime().totalMemory();
					long used= total - Runtime.getRuntime().freeMemory();
					stream.print(formatMemory(used));
					stream.print('\t');
					stream.print(formatMemory(total));
					stream.print('\t');
					stream.println(formatMemory(Runtime.getRuntime().maxMemory()));
				}

				private static String formatMemory(long number) {
					long quotient= number;
					int[] values= new int[10];
					int position= -1;
					do {
						long current= quotient;
						quotient= current / 1000L;
						values[++position]= (int) (current - quotient * 1000L);
					} while (quotient > 0 && position + 1 < values.length);
					StringBuilder result= new StringBuilder(Integer.toString(values[position]));
					for (int index= position - 1; index >= 0; index--) {
						result.append(',').append(String.format(java.util.Locale.ROOT, "%03d", values[index]));
					}
					return result.toString();
				}

				public static final class JdtCoreFilterCondition
						implements org.junit.jupiter.api.extension.ExecutionCondition {
					@Override
					public org.junit.jupiter.api.extension.ConditionEvaluationResult evaluateExecutionCondition(
							org.junit.jupiter.api.extension.ExtensionContext context) {
						java.util.Optional<java.lang.reflect.Method> selected= context.getTestMethod();
						if (selected.isEmpty()) {
							return org.junit.jupiter.api.extension.ConditionEvaluationResult.enabled("container");
						}
						String name= selected.orElseThrow().getName();
						java.util.List<String> testNames= java.util.Arrays.stream(context.getRequiredTestClass().getMethods())
								.filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
								.filter(method -> !java.lang.reflect.Modifier.isStatic(method.getModifiers()))
								.map(java.lang.reflect.Method::getName)
								.filter(candidate -> candidate.startsWith(METHOD_PREFIX)).distinct().toList();
						java.util.List<String> onlyNames= RUN_ONLY_ID == null ? java.util.List.of()
								: testNames.stream().filter(candidate -> candidate.substring(METHOD_PREFIX.length())
										.startsWith(RUN_ONLY_ID)).toList();
						boolean enabled= onlyNames.isEmpty() ? selectedByConfiguredFilter(name) : onlyNames.contains(name);
						return enabled
								? org.junit.jupiter.api.extension.ConditionEvaluationResult.enabled("selected by JDT Core filter")
								: org.junit.jupiter.api.extension.ConditionEvaluationResult.disabled("excluded by JDT Core filter");
					}

					private static boolean selectedByConfiguredFilter(String name) {
						if (TESTS_PREFIX == null && TESTS_NAMES == null && TESTS_NUMBERS == null && TESTS_RANGE == null) {
							return true;
						}
						if (TESTS_PREFIX != null && !name.startsWith(TESTS_PREFIX)) {
							return false;
						}
						if (TESTS_NAMES != null && java.util.Arrays.stream(TESTS_NAMES).anyMatch(name::contains)) {
							return true;
						}
						int number= testNumber(name);
						if (TESTS_NUMBERS != null && java.util.Arrays.stream(TESTS_NUMBERS)
								.anyMatch(candidate -> candidate == number)) {
							return true;
						}
						if (TESTS_RANGE != null && TESTS_RANGE.length == 2 && number >= 0
								&& (TESTS_RANGE[0] == -1 || number >= TESTS_RANGE[0])
								&& (TESTS_RANGE[1] == -1 || number <= TESTS_RANGE[1])) {
							return true;
						}
						return TESTS_NAMES == null && TESTS_NUMBERS == null && TESTS_RANGE == null;
					}

					private static int testNumber(String name) {
						int index= TESTS_PREFIX == null ? METHOD_PREFIX.length() : TESTS_PREFIX.length();
						while (index < name.length() && !Character.isDigit(name.charAt(index))) {
							index++;
						}
						while (index < name.length() && name.charAt(index) == '0') {
							index++;
						}
						int end= index;
						while (end < name.length() && Character.isDigit(name.charAt(end))) {
							end++;
						}
						if (end == index) {
							return -1;
						}
						try {
							return Integer.parseInt(name.substring(index, end));
						} catch (NumberFormatException exception) {
							return -1;
						}
					}
				}

				public static final class JdtCoreMethodOrderer implements org.junit.jupiter.api.MethodOrderer {
					@Override
					public void orderMethods(org.junit.jupiter.api.MethodOrdererContext context) {
						if (ORDERING == ALPHA_REVERSE_SORT) {
							context.getMethodDescriptors().sort((left, right) -> right.getMethod().getName()
									.compareTo(left.getMethod().getName()));
						} else if (ORDERING == ALPHABETICAL_SORT || ORDERING == NO_ORDER) {
							context.getMethodDescriptors().sort((left, right) -> left.getMethod().getName()
									.compareTo(right.getMethod().getName()));
						} else if (ORDERING == BYTECODE_DECLARATION_ORDER) {
							try {
								java.util.List<String> ordered= bytecodeDeclaredTestNames(context.getTestClass());
								context.getMethodDescriptors().sort(java.util.Comparator.comparingInt(descriptor -> {
									int index= ordered.indexOf(descriptor.getMethod().getName());
									return index < 0 ? Integer.MAX_VALUE : index;
								}));
							} catch (java.io.IOException exception) {
								context.getMethodDescriptors().sort((left, right) -> left.getMethod().getName()
										.compareTo(right.getMethod().getName()));
							}
						} else {
							java.util.Collections.shuffle(context.getMethodDescriptors(), new java.util.Random(ORDERING));
						}
					}
				}

				private static java.util.List<String> bytecodeDeclaredTestNames(Class<?> testClass)
						throws java.io.IOException {
					String simpleName= testClass.getName().substring(testClass.getName().lastIndexOf('.') + 1);
					try (java.io.DataInputStream input= new java.io.DataInputStream(new java.io.BufferedInputStream(
							testClass.getResourceAsStream(simpleName + ".class")))) {
						if (input.readInt() != 0xcafebabe) {
							throw new java.io.IOException("Invalid classfile magic");
						}
						input.readUnsignedShort();
						input.readUnsignedShort();
						int constantPoolCount= input.readUnsignedShort();
						String[] utf8= new String[constantPoolCount];
						for (int index= 1; index < constantPoolCount; index++) {
							int tag= input.readUnsignedByte();
							switch (tag) {
							case 1 -> utf8[index]= input.readUTF();
							case 3, 4 -> skipFully(input, 4);
							case 5, 6 -> { skipFully(input, 8); index++; }
							case 7, 8, 16, 19, 20 -> skipFully(input, 2);
							case 9, 10, 11, 12, 17, 18 -> skipFully(input, 4);
							case 15 -> skipFully(input, 3);
							default -> throw new java.io.IOException("Unknown constant-pool tag " + tag);
							}
						}
						skipFully(input, 6);
						int interfaces= input.readUnsignedShort();
						skipFully(input, interfaces * 2L);
						int fields= input.readUnsignedShort();
						for (int index= 0; index < fields; index++) {
							skipMember(input);
						}
						int methods= input.readUnsignedShort();
						java.util.List<String> result= new java.util.ArrayList<>();
						for (int index= 0; index < methods; index++) {
							int access= input.readUnsignedShort();
							String name= utf8[input.readUnsignedShort()];
							String descriptor= utf8[input.readUnsignedShort()];
							int attributes= input.readUnsignedShort();
							for (int attribute= 0; attribute < attributes; attribute++) {
								input.readUnsignedShort();
								skipFully(input, Integer.toUnsignedLong(input.readInt()));
							}
							if ((access & java.lang.reflect.Modifier.PUBLIC) != 0
									&& (access & java.lang.reflect.Modifier.STATIC) == 0
									&& "()V".equals(descriptor) && name.startsWith(METHOD_PREFIX)) {
								result.add(name);
							}
						}
						return result;
					}
				}

				private static void skipMember(java.io.DataInputStream input) throws java.io.IOException {
					skipFully(input, 6);
					int attributes= input.readUnsignedShort();
					for (int attribute= 0; attribute < attributes; attribute++) {
						input.readUnsignedShort();
						skipFully(input, Integer.toUnsignedLong(input.readInt()));
					}
				}

				private static void skipFully(java.io.DataInputStream input, long count) throws java.io.IOException {
					long remaining= count;
					while (remaining > 0) {
						long skipped= input.skip(remaining);
						if (skipped <= 0) {
							if (input.read() < 0) {
								throw new java.io.IOException("Unexpected end of classfile");
							}
							skipped= 1;
						}
						remaining-= skipped;
					}
				}
			}
			""";

	private final Kind kind;
	private final TypeDeclaration type;
	private final MethodDeclaration constructor;
	private final MethodDeclaration localSuite;

	private JdtCoreHarnessRewriteOperation(Kind kind, TypeDeclaration type,
			MethodDeclaration constructor, MethodDeclaration localSuite) {
		this.kind= kind;
		this.type= type;
		this.constructor= constructor;
		this.localSuite= localSuite;
	}

	static JdtCoreHarnessRewriteOperation addBridge(TypeDeclaration harnessType) {
		return new JdtCoreHarnessRewriteOperation(Kind.ADD_BRIDGE, harnessType, null, null);
	}

	static JdtCoreHarnessRewriteOperation migrateFamily(TypeDeclaration familyType,
			MethodDeclaration constructor, MethodDeclaration localSuite) {
		return new JdtCoreHarnessRewriteOperation(Kind.MIGRATE_FAMILY, familyType, constructor, localSuite);
	}

	@Override
	public void rewriteASTInternal(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel)
			throws CoreException {
		if (type == null) {
			throw failure("The planned JDT Core harness type is missing"); //$NON-NLS-1$
		}
		TextEditGroup group= createTextEditGroup(kind == Kind.ADD_BRIDGE
				? "Add the JDT Core Jupiter compatibility bridge" //$NON-NLS-1$
				: "Migrate a direct JDT Core TestCase family to Jupiter", cuRewrite); //$NON-NLS-1$
		if (kind == Kind.ADD_BRIDGE) {
			addBridge(cuRewrite, group);
		} else {
			migrateFamily(cuRewrite, group);
		}
	}

	private void addBridge(CompilationUnitRewrite cuRewrite, TextEditGroup group) throws CoreException {
		ITypeBinding binding= type.resolveBinding();
		if (binding == null || !JdtCoreHarnessPlanner.JDT_CORE_TEST_CASE.equals(
				binding.getErasure().getQualifiedName())) {
			throw failure("The planned bridge target is not the JDT Core custom TestCase"); //$NON-NLS-1$
		}
		for (TypeDeclaration nested : type.getTypes()) {
			if ("Jupiter".equals(nested.getName().getIdentifier())) { //$NON-NLS-1$
				throw failure("The JDT Core TestCase already contains a Jupiter bridge"); //$NON-NLS-1$
			}
		}
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		ASTNode placeholder= rewrite.createStringPlaceholder(JUPITER_BRIDGE_SOURCE, ASTNode.TYPE_DECLARATION);
		ListRewrite members= rewrite.getListRewrite(type, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		members.insertLast(placeholder, group);
	}

	private void migrateFamily(CompilationUnitRewrite cuRewrite, TextEditGroup group) throws CoreException {
		Type superclass= type.getSuperclassType();
		ITypeBinding binding= superclass == null ? null : superclass.resolveBinding();
		if (binding == null || !JdtCoreHarnessPlanner.JDT_CORE_TEST_CASE.equals(
				binding.getErasure().getQualifiedName())) {
			throw failure("The direct family no longer extends the expected JDT Core TestCase"); //$NON-NLS-1$
		}
		if (constructor == null) {
			throw failure("The planned JDT Core String constructor is missing"); //$NON-NLS-1$
		}
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		SimpleType replacement= ast.newSimpleType(ast.newName("TestCase.Jupiter")); //$NON-NLS-1$
		rewrite.replace(superclass, replacement, group);
		rewrite.remove(constructor, group);
		cuRewrite.getImportRemover().registerRemovedNode(constructor);
		if (localSuite != null) {
			rewrite.remove(localSuite, group);
			cuRewrite.getImportRemover().registerRemovedNode(localSuite);
		}
		cuRewrite.getImportRemover().applyRemoves(cuRewrite.getImportRewrite());
	}

	@Override
	public String getAdditionalInfo() {
		return kind == Kind.ADD_BRIDGE
				? "Adds a nested Jupiter bridge while retaining the original JUnit 3 harness for unmigrated families." //$NON-NLS-1$
				: "Moves one closed direct JDT Core TestCase family to the nested Jupiter bridge."; //$NON-NLS-1$
	}

	private static CoreException failure(String message) {
		return new CoreException(new Status(IStatus.ERROR, "sandbox_junit_cleanup", message)); //$NON-NLS-1$
	}
}
