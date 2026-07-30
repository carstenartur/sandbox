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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;

import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateDiagnostic;
import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateOutcome;
import org.sandbox.jdt.cleanup.multifile.RelatedCompilationUnitSearch;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3SemanticSupport;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.MethodKind;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.MethodMigration;
import org.sandbox.jdt.internal.corext.fix.multifile.JdtCoreHarnessInventory.Family;
import org.sandbox.jdt.internal.corext.fix.multifile.JdtCoreHarnessInventory.FamilyKind;

/**
 * Inventories the source-level concepts of the custom Eclipse JDT Core JUnit 3
 * harness before any framework rewrite is attempted.
 */
final class JdtCoreHarnessPlanner {

	static final String JDT_CORE_TEST_CASE= "org.eclipse.jdt.core.tests.junit.extension.TestCase"; //$NON-NLS-1$
	static final String JDT_CORE_SUITE_OF_TEST_CASES= "org.eclipse.jdt.core.tests.model.SuiteOfTestCases"; //$NON-NLS-1$
	static final String JDT_CORE_ABSTRACT_COMPILER_TEST= "org.eclipse.jdt.core.tests.util.AbstractCompilerTest"; //$NON-NLS-1$

	private static final Set<String> HARNESS_BASE_TYPES= Set.of(JDT_CORE_TEST_CASE,
			JDT_CORE_SUITE_OF_TEST_CASES, "org.eclipse.jdt.core.tests.model.AbstractJavaModelTests", //$NON-NLS-1$
			JDT_CORE_ABSTRACT_COMPILER_TEST);
	private static final Set<String> DIRECT_SLICE_REJECTED_METHODS= Set.of(
			"setUp", "tearDown", "setUpSuite", "tearDownSuite", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			"runTest", "runBare", "run", "setUpTest"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	private static final Set<String> SUPPORTED_PERFORMANCE_METHODS= Set.of(
			"startMeasuring", "stopMeasuring", "commitMeasurements", "assertPerformance", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			"assertPerformanceInRelativeBand", "tagAsSummary", "tagAsGlobalSummary", "setComment"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

	record Result(JdtCoreHarnessInventory inventory,
			List<JdtCoreDirectFamilyMigration> directMigrations,
			List<MultiFileCandidateDiagnostic> diagnostics) {
		Result {
			inventory= inventory == null ? JdtCoreHarnessInventory.empty() : inventory;
			directMigrations= directMigrations == null ? List.of() : List.copyOf(directMigrations);
			diagnostics= diagnostics == null ? List.of() : List.copyOf(diagnostics);
		}
	}

	private record SourceType(String unitHandle, TypeDeclaration declaration,
			ITypeBinding binding, IType javaType) {
		String key() {
			return JUnitMigrationPlan.typeKey(binding);
		}

		String name() {
			return binding.getQualifiedName();
		}
	}

	private record DirectAssessment(boolean applicable, String reasonCode, String message,
			List<String> relatedHandles, JdtCoreDirectFamilyMigration migration) {
		static DirectAssessment applicable(List<String> relatedHandles,
				JdtCoreDirectFamilyMigration migration) {
			return new DirectAssessment(true, "JDT_CORE_DIRECT_SLICE_APPLICABLE", //$NON-NLS-1$
					"The direct JDT Core TestCase family has a closed, explicit Slice A shape.", //$NON-NLS-1$
					relatedHandles, migration);
		}

		static DirectAssessment rejected(String reasonCode, String message, List<String> relatedHandles) {
			return new DirectAssessment(false, reasonCode, message, relatedHandles, null);
		}
	}

	private JdtCoreHarnessPlanner() {
	}

	static Result create(IJavaProject project, ICompilationUnit[] selectedUnits,
			Map<String, CompilationUnit> rootsByHandle, boolean closedScope, IProgressMonitor monitor)
			throws CoreException {
		Map<String, SourceType> sourceTypes= collectSourceTypes(rootsByHandle);
		SourceType harness= sourceTypes.values().stream()
				.filter(type -> JDT_CORE_TEST_CASE.equals(type.name())).findFirst().orElse(null);
		if (harness == null) {
			return new Result(JdtCoreHarnessInventory.empty(), List.of(), List.of());
		}

		List<ICompilationUnit> selectedList= Arrays.asList(selectedUnits);
		List<Family> families= new ArrayList<>();
		List<JdtCoreDirectFamilyMigration> migrations= new ArrayList<>();
		List<MultiFileCandidateDiagnostic> diagnostics= new ArrayList<>();
		for (SourceType type : sourceTypes.values().stream()
				.filter(candidate -> !HARNESS_BASE_TYPES.contains(candidate.name()))
				.filter(candidate -> inherits(candidate.binding(), JDT_CORE_TEST_CASE))
				.sorted(Comparator.comparing(SourceType::name)).toList()) {
			FamilyKind kind= familyKind(type.binding());
			List<String> baseRelated= List.of(harness.unitHandle(), type.unitHandle());
			if (kind == FamilyKind.COMPILER_COMPLIANCE) {
				addRejected(type, kind, "JDT_CORE_COMPLIANCE_MATRIX_REQUIRED", //$NON-NLS-1$
						"The family is multiplied through AbstractCompilerTest compliance suites and requires an explicit Jupiter test-template migration.", //$NON-NLS-1$
						baseRelated, families, diagnostics);
				continue;
			}
			if (kind == FamilyKind.SUITE_STATE) {
				addRejected(type, kind, "JDT_CORE_SUITE_STATE_REQUIRED", //$NON-NLS-1$
						"The family relies on SuiteOfTestCases once-per-suite setup and mutable field transfer between fresh test instances.", //$NON-NLS-1$
						baseRelated, families, diagnostics);
				continue;
			}

			DirectAssessment assessment= assessDirectFamily(project, type, harness, selectedList,
					closedScope, monitor);
			families.add(new Family(type.unitHandle(), type.key(), type.name(), kind,
					assessment.applicable(), assessment.reasonCode(), assessment.message(),
					assessment.relatedHandles()));
			if (assessment.migration() != null) {
				migrations.add(assessment.migration());
			}
			String candidateId= "jdt-core-harness:" + type.name(); //$NON-NLS-1$
			if (assessment.applicable()) {
				diagnostics.add(new MultiFileCandidateDiagnostic(candidateId, type.unitHandle(),
						MultiFileCandidateOutcome.APPLICABLE, assessment.reasonCode(), assessment.message(),
						assessment.relatedHandles()));
			} else {
				diagnostics.add(MultiFileCandidateDiagnostic.rejected(candidateId, type.unitHandle(),
						assessment.reasonCode(), assessment.message(), assessment.relatedHandles()));
			}
		}
		return new Result(new JdtCoreHarnessInventory(families), migrations, diagnostics);
	}

	private static void addRejected(SourceType type, FamilyKind kind, String reasonCode, String message,
			List<String> relatedHandles, List<Family> families,
			List<MultiFileCandidateDiagnostic> diagnostics) {
		families.add(new Family(type.unitHandle(), type.key(), type.name(), kind, false,
				reasonCode, message, relatedHandles));
		diagnostics.add(MultiFileCandidateDiagnostic.rejected("jdt-core-harness:" + type.name(), //$NON-NLS-1$
				type.unitHandle(), reasonCode, message, relatedHandles));
	}

	private static DirectAssessment assessDirectFamily(IJavaProject project, SourceType type,
			SourceType harness, List<ICompilationUnit> selectedUnits, boolean closedScope,
			IProgressMonitor monitor) throws CoreException {
		List<String> baseRelated= List.of(harness.unitHandle(), type.unitHandle());
		ITypeBinding superclass= type.binding().getSuperclass();
		if (superclass == null || !JDT_CORE_TEST_CASE.equals(superclass.getErasure().getQualifiedName())) {
			return DirectAssessment.rejected("JDT_CORE_CUSTOM_BASE_REQUIRED", //$NON-NLS-1$
					"The first harness slice supports only concrete families directly extending the custom JDT Core TestCase.", //$NON-NLS-1$
					baseRelated);
		}
		if (Modifier.isAbstract(type.binding().getModifiers())) {
			return DirectAssessment.rejected("JDT_CORE_ABSTRACT_FAMILY_BASE", //$NON-NLS-1$
					"Abstract custom TestCase bases require their complete descendant family to be migrated together.", //$NON-NLS-1$
					baseRelated);
		}
		if (!closedScope) {
			return DirectAssessment.rejected("INCOMPLETE_JDT_CORE_HARNESS_SCOPE", //$NON-NLS-1$
					"The selected source scope is not closed for the JDT Core harness family.", baseRelated); //$NON-NLS-1$
		}

		List<MethodDeclaration> constructors= Arrays.stream(type.declaration().getMethods())
				.filter(MethodDeclaration::isConstructor).toList();
		if (constructors.size() != 1 || !isSimpleNamedConstructor(constructors.get(0))) {
			return DirectAssessment.rejected("JDT_CORE_NAMED_CONSTRUCTOR_REQUIRED", //$NON-NLS-1$
					"Slice A requires exactly the conventional String constructor whose only statement is super(name).", //$NON-NLS-1$
					baseRelated);
		}
		String constructorKey= methodKey(constructors.get(0));
		if (constructorKey == null) {
			return DirectAssessment.rejected("UNRESOLVED_JDT_CORE_CONSTRUCTOR", //$NON-NLS-1$
					"The conventional JDT Core String constructor has no stable binding key.", baseRelated); //$NON-NLS-1$
		}

		List<MethodMigration> testMethods= new ArrayList<>();
		String suiteMethodKey= null;
		for (MethodDeclaration method : type.declaration().getMethods()) {
			if (method.isConstructor()) {
				continue;
			}
			String name= method.getName().getIdentifier();
			if ("suite".equals(name)) { //$NON-NLS-1$
				if (!isExactLocalSuite(method, type.binding())) {
					return DirectAssessment.rejected("JDT_CORE_CUSTOM_SUITE_REQUIRED", //$NON-NLS-1$
							"The local suite() method is not the removable buildTestSuite(Self.class) form.", //$NON-NLS-1$
							baseRelated);
				}
				suiteMethodKey= methodKey(method);
				if (suiteMethodKey == null) {
					return DirectAssessment.rejected("UNRESOLVED_JDT_CORE_LOCAL_SUITE", //$NON-NLS-1$
							"The removable local suite() method has no stable binding key.", baseRelated); //$NON-NLS-1$
				}
				continue;
			}
			if (DIRECT_SLICE_REJECTED_METHODS.contains(name)) {
				return DirectAssessment.rejected("JDT_CORE_LIFECYCLE_OR_RUN_HOOK_REQUIRED", //$NON-NLS-1$
						"The family overrides " + name + "() and requires an explicit Jupiter lifecycle/harness mapping.", //$NON-NLS-1$ //$NON-NLS-2$
						baseRelated);
			}
			if (name.startsWith("test")) { //$NON-NLS-1$
				String key= methodKey(method);
				if (!JUnit3SemanticSupport.isExactTestMethod(method) || key == null) {
					return DirectAssessment.rejected("UNSUPPORTED_JDT_CORE_TEST_SIGNATURE", //$NON-NLS-1$
							"A test-prefixed method does not satisfy the exact public non-static void zero-argument contract.", //$NON-NLS-1$
							baseRelated);
				}
				testMethods.add(new MethodMigration(key, MethodKind.TEST));
			}
		}
		if (testMethods.isEmpty()) {
			return DirectAssessment.rejected("NO_JDT_CORE_TEST_METHODS", //$NON-NLS-1$
					"The concrete harness family declares no exact JUnit 3 test methods.", baseRelated); //$NON-NLS-1$
		}

		String unsupportedInvocation= unsupportedHarnessInvocation(type.declaration(), suiteMethodKey);
		if (unsupportedInvocation != null) {
			return DirectAssessment.rejected("JDT_CORE_CUSTOM_HELPER_REQUIRED", //$NON-NLS-1$
					unsupportedInvocation, baseRelated);
		}
		JUnit3AssertionInventory.Result assertions= JUnit3AssertionInventory.analyze(type.declaration());
		if (!assertions.supported()) {
			return DirectAssessment.rejected("UNSUPPORTED_JDT_CORE_ASSERTION_OR_EXECUTION", //$NON-NLS-1$
					assertions.rejectionReason(), baseRelated);
		}

		RelatedCompilationUnitSearch.Result references= RelatedCompilationUnitSearch.findReferences(project,
				List.of(type.javaType()), List.of(type.javaType().getCompilationUnit()), selectedUnits, monitor);
		if (!references.complete()) {
			List<String> related= new ArrayList<>(baseRelated);
			related.addAll(references.compilationUnits().stream().map(IJavaElement::getHandleIdentifier).toList());
			return DirectAssessment.rejected("INCOMPLETE_JDT_CORE_HARNESS_REFERENCES", //$NON-NLS-1$
					"The direct family has unresolved, binary, or out-of-scope references: " //$NON-NLS-1$
							+ String.join("; ", references.rejectionReasons()), related); //$NON-NLS-1$
		}
		List<String> externalHandles= references.compilationUnits().stream()
				.map(IJavaElement::getHandleIdentifier)
				.filter(handle -> !type.unitHandle().equals(handle))
				.sorted().toList();
		if (!externalHandles.isEmpty()) {
			List<String> related= new ArrayList<>(baseRelated);
			related.addAll(externalHandles);
			return DirectAssessment.rejected("JDT_CORE_AGGREGATE_SUITE_REQUIRED", //$NON-NLS-1$
					"The family is referenced by aggregate suite or harness source that must be migrated in the same slice.", //$NON-NLS-1$
					related);
		}
		JdtCoreDirectFamilyMigration migration= new JdtCoreDirectFamilyMigration(harness.unitHandle(),
				harness.key(), type.unitHandle(), type.key(), constructorKey, suiteMethodKey,
				testMethods, assertions.invocations());
		return DirectAssessment.applicable(baseRelated, migration);
	}

	private static String unsupportedHarnessInvocation(TypeDeclaration declaration, String suiteMethodKey) {
		String[] rejection= new String[1];
		declaration.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration method) {
				return suiteMethodKey == null || !suiteMethodKey.equals(methodKey(method));
			}

			@Override
			public boolean visit(MethodInvocation invocation) {
				IMethodBinding binding= invocation.resolveMethodBinding();
				if (binding == null) {
					return true;
				}
				ITypeBinding declaring= binding.getDeclaringClass();
				String owner= declaring == null ? "" : declaring.getErasure().getQualifiedName(); //$NON-NLS-1$
				String name= binding.getName();
				if (JDT_CORE_TEST_CASE.equals(owner)) {
					rejection[0]= "The direct family calls custom JDT Core harness method " + name //$NON-NLS-1$
							+ "(), which is not yet represented by the Jupiter bridge."; //$NON-NLS-1$
					return false;
				}
				if (owner.startsWith("org.eclipse.test.performance.") //$NON-NLS-1$
						&& !SUPPORTED_PERFORMANCE_METHODS.contains(name)) {
					rejection[0]= "The direct family calls unsupported performance harness method " //$NON-NLS-1$
							+ owner + "." + name + "()."; //$NON-NLS-1$ //$NON-NLS-2$
					return false;
				}
				return true;
			}
		});
		return rejection[0];
	}

	private static boolean isSimpleNamedConstructor(MethodDeclaration constructor) {
		if (constructor.parameters().size() != 1 || constructor.getBody() == null
				|| constructor.getBody().statements().size() != 1) {
			return false;
		}
		SingleVariableDeclaration parameter= (SingleVariableDeclaration) constructor.parameters().get(0);
		ITypeBinding parameterType= parameter.getType().resolveBinding();
		if (parameterType == null || !"java.lang.String".equals(parameterType.getErasure().getQualifiedName())) { //$NON-NLS-1$
			return false;
		}
		Object statement= constructor.getBody().statements().get(0);
		if (!(statement instanceof SuperConstructorInvocation invocation) || invocation.arguments().size() != 1) {
			return false;
		}
		Expression argument= (Expression) invocation.arguments().get(0);
		if (!(argument instanceof SimpleName simpleName)) {
			return false;
		}
		IVariableBinding parameterBinding= parameter.resolveBinding();
		return parameterBinding != null && parameterBinding.equals(simpleName.resolveBinding());
	}

	private static boolean isExactLocalSuite(MethodDeclaration method, ITypeBinding owner) {
		if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())
				|| !method.parameters().isEmpty() || method.getBody() == null
				|| method.getBody().statements().size() != 1) {
			return false;
		}
		IMethodBinding methodBinding= method.resolveBinding();
		ITypeBinding returnType= methodBinding == null ? null : methodBinding.getReturnType();
		if (returnType == null || !"junit.framework.Test".equals(returnType.getErasure().getQualifiedName())) { //$NON-NLS-1$
			return false;
		}
		Object statement= method.getBody().statements().get(0);
		if (!(statement instanceof ReturnStatement returnStatement)
				|| !(returnStatement.getExpression() instanceof MethodInvocation invocation)
				|| !"buildTestSuite".equals(invocation.getName().getIdentifier()) //$NON-NLS-1$
				|| invocation.arguments().size() != 1) {
			return false;
		}
		Object argument= invocation.arguments().get(0);
		if (!(argument instanceof TypeLiteral literal)) {
			return false;
		}
		ITypeBinding selectedType= literal.getType().resolveBinding();
		return selectedType != null && JUnitMigrationPlan.typeKey(owner)
				.equals(JUnitMigrationPlan.typeKey(selectedType));
	}

	private static String methodKey(MethodDeclaration method) {
		IMethodBinding binding= method.resolveBinding();
		return binding == null ? null : binding.getMethodDeclaration().getKey();
	}

	private static FamilyKind familyKind(ITypeBinding binding) {
		if (inherits(binding, JDT_CORE_ABSTRACT_COMPILER_TEST)) {
			return FamilyKind.COMPILER_COMPLIANCE;
		}
		if (inherits(binding, JDT_CORE_SUITE_OF_TEST_CASES)) {
			return FamilyKind.SUITE_STATE;
		}
		return FamilyKind.DIRECT_TEST_CASE;
	}

	private static boolean inherits(ITypeBinding binding, String qualifiedName) {
		ITypeBinding current= binding;
		while (current != null) {
			if (qualifiedName.equals(current.getErasure().getQualifiedName())) {
				return true;
			}
			current= current.getSuperclass();
		}
		return false;
	}

	private static Map<String, SourceType> collectSourceTypes(Map<String, CompilationUnit> rootsByHandle) {
		Map<String, SourceType> result= new LinkedHashMap<>();
		for (Map.Entry<String, CompilationUnit> entry : rootsByHandle.entrySet()) {
			entry.getValue().accept(new ASTVisitor() {
				@Override
				public boolean visit(TypeDeclaration node) {
					ITypeBinding binding= node.resolveBinding();
					String key= JUnitMigrationPlan.typeKey(binding);
					IJavaElement element= binding == null ? null : binding.getErasure().getJavaElement();
					if (key != null && element instanceof IType type && type.getCompilationUnit() != null) {
						result.put(key, new SourceType(entry.getKey(), node, binding, type));
					}
					return true;
				}
			});
		}
		return result;
	}
}
