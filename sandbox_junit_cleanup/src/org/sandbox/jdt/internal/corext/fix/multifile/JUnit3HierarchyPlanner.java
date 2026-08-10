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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3SemanticSupport.JUNIT3_TEST_CASE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateDiagnostic;
import org.sandbox.jdt.cleanup.multifile.RelatedCompilationUnitSearch;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3MigrationExclusions;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3SemanticSupport;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.MethodKind;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.MethodMigration;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.TypeMigration;

/** Classifies and plans ordinary closed JUnit 3 source hierarchies. */
final class JUnit3HierarchyPlanner {

	/** Mirrors JUnit 4's {@code org.junit.internal.MethodSorter.DEFAULT}. */
	private static final Comparator<MethodDeclaration> JUNIT3_METHOD_ORDER=
			Comparator.comparingInt((MethodDeclaration method) -> method.getName().getIdentifier().hashCode())
					.thenComparing(method -> method.getName().getIdentifier())
					.thenComparing(MethodDeclaration::toString);

	record Result(List<JUnit3HierarchyMigration> migrations,
			List<MultiFileCandidateDiagnostic> diagnostics, JUnitTestTypeInventory inventory) {
		Result {
			migrations= List.copyOf(migrations);
			diagnostics= List.copyOf(diagnostics);
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

	private record Classification(boolean supported, String reasonCode, String message,
			List<TypeMigration> typeMigrations, List<String> baselineHandles) {
		static Classification rejected(String reasonCode, String message) {
			return new Classification(false, reasonCode, message, List.of(), List.of());
		}

		static Classification supported(List<TypeMigration> migrations, List<String> baselineHandles) {
			return new Classification(true, "SUPPORTED_HIERARCHY", //$NON-NLS-1$
					"The complete ordinary JUnit 3 hierarchy can be migrated atomically.", //$NON-NLS-1$
					migrations, baselineHandles);
		}
	}

	private enum OverrideRemoval {
		REMOVE,
		KEEP,
		UNRESOLVED
	}

	private JUnit3HierarchyPlanner() {
	}

	static Result create(IJavaProject project, ICompilationUnit[] selectedUnits,
			Map<String, CompilationUnit> rootsByHandle, boolean closedScope,
			IProgressMonitor monitor) throws CoreException {
		JUnitTestTypeInventory inventory= JUnitTestTypeInventory.capture(project, monitor);
		Map<String, SourceType> sourceTypes= collectSourceTypes(rootsByHandle);
		List<SourceType> roots= sourceTypes.values().stream()
				.filter(JUnit3HierarchyPlanner::directlyExtendsTestCase)
				.sorted(Comparator.comparing(SourceType::name))
				.toList();
		if (roots.isEmpty()) {
			return new Result(List.of(), List.of(), inventory);
		}

		Set<String> selectedHandles= Arrays.stream(selectedUnits)
				.map(ICompilationUnit::getPrimary)
				.map(IJavaElement::getHandleIdentifier)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		List<ICompilationUnit> selectedList= Arrays.asList(selectedUnits);
		List<JUnit3HierarchyMigration> migrations= new ArrayList<>();
		List<MultiFileCandidateDiagnostic> diagnostics= new ArrayList<>();
		for (SourceType root : roots) {
			List<SourceType> hierarchy= hierarchy(root, sourceTypes.values());
			// A direct leaf remains the responsibility of the established local,
			// fail-closed cleanup. Coordinated planning is only needed once JUnit 3
			// identity or execution order is inherited across source types.
			if (hierarchy.size() == 1) {
				continue;
			}
			List<String> relatedHandles= hierarchy.stream().map(SourceType::unitHandle).distinct().sorted().toList();
			String candidateId= "junit3-hierarchy:" + root.name(); //$NON-NLS-1$
			if (!closedScope || !containsAllSourceSubtypes(root, selectedHandles)) {
				String message= "The selected source scope does not contain every source or binary subtype of " //$NON-NLS-1$
						+ root.name() + "."; //$NON-NLS-1$
				diagnostics.add(MultiFileCandidateDiagnostic.rejected(candidateId, root.unitHandle(),
						"INCOMPLETE_JUNIT3_HIERARCHY", message, relatedHandles)); //$NON-NLS-1$
				continue;
			}
			RelatedCompilationUnitSearch.Result references= RelatedCompilationUnitSearch.findReferences(project,
					hierarchy.stream().map(SourceType::javaType).toList(),
					hierarchy.stream().map(SourceType::javaType).map(IType::getCompilationUnit).toList(),
					selectedList, monitor);
			if (!references.complete()) {
				String message= "The JUnit 3 hierarchy has unresolved, binary, or out-of-scope references: " //$NON-NLS-1$
						+ String.join("; ", references.rejectionReasons()); //$NON-NLS-1$
				diagnostics.add(MultiFileCandidateDiagnostic.rejected(candidateId, root.unitHandle(),
						"INCOMPLETE_JUNIT3_REFERENCES", message, relatedHandles)); //$NON-NLS-1$
				continue;
			}
			Set<String> hierarchyHandles= new HashSet<>(relatedHandles);
			List<String> externalReferenceHandles= references.compilationUnits().stream()
					.map(IJavaElement::getHandleIdentifier)
					.filter(handle -> !hierarchyHandles.contains(handle))
					.sorted()
					.toList();
			if (!externalReferenceHandles.isEmpty()) {
				List<String> allRelated= new ArrayList<>(relatedHandles);
				allRelated.addAll(externalReferenceHandles);
				String message= "The hierarchy participates in suite or harness code outside the hierarchy and requires an explicit framework migration."; //$NON-NLS-1$
				diagnostics.add(MultiFileCandidateDiagnostic.rejected(candidateId, root.unitHandle(),
						"CUSTOM_JUNIT3_HARNESS", message, allRelated)); //$NON-NLS-1$
				continue;
			}

			Classification classification= classify(root, hierarchy, inventory);
			if (!classification.supported()) {
				diagnostics.add(MultiFileCandidateDiagnostic.rejected(candidateId, root.unitHandle(),
						classification.reasonCode(), classification.message(), relatedHandles));
				continue;
			}
			migrations.add(new JUnit3HierarchyMigration(root.name(), classification.typeMigrations(),
					classification.baselineHandles()));
			diagnostics.add(MultiFileCandidateDiagnostic.transformed(candidateId, root.unitHandle(),
					classification.message(), relatedHandles));
		}
		return new Result(migrations, diagnostics, inventory);
	}

	private static Map<String, SourceType> collectSourceTypes(Map<String, CompilationUnit> rootsByHandle) {
		Map<String, SourceType> result= new LinkedHashMap<>();
		for (Map.Entry<String, CompilationUnit> entry : rootsByHandle.entrySet()) {
			entry.getValue().accept(new ASTVisitor() {
				@Override
				public boolean visit(TypeDeclaration node) {
					ITypeBinding binding= node.resolveBinding();
					String key= JUnitMigrationPlan.typeKey(binding);
					IJavaElement element= binding == null ? null : binding.getJavaElement();
					if (key != null && element instanceof IType type && type.getCompilationUnit() != null) {
						result.put(key, new SourceType(entry.getKey(), node, binding, type));
					}
					return true;
				}
			});
		}
		return result;
	}

	private static boolean directlyExtendsTestCase(SourceType type) {
		ITypeBinding superclass= type.binding().getSuperclass();
		return superclass != null && JUNIT3_TEST_CASE.equals(superclass.getErasure().getQualifiedName());
	}

	private static List<SourceType> hierarchy(SourceType root, Collection<SourceType> allTypes) {
		String rootKey= root.key();
		return allTypes.stream()
				.filter(candidate -> isSameOrSubtype(candidate.binding(), rootKey))
				.sorted(Comparator.comparing(SourceType::name))
				.toList();
	}

	private static boolean isSameOrSubtype(ITypeBinding binding, String rootKey) {
		ITypeBinding current= binding;
		while (current != null) {
			if (rootKey.equals(JUnitMigrationPlan.typeKey(current))) {
				return true;
			}
			current= current.getSuperclass();
		}
		return false;
	}

	private static int hierarchyDepth(SourceType type, String rootKey) {
		int depth= 0;
		ITypeBinding current= type.binding();
		while (current != null && !rootKey.equals(JUnitMigrationPlan.typeKey(current))) {
			depth++;
			current= current.getSuperclass();
		}
		return current == null ? -1 : depth;
	}

	private static boolean containsAllSourceSubtypes(SourceType root, Set<String> selectedHandles) {
		try {
			ITypeHierarchy hierarchy= root.javaType().newTypeHierarchy(null);
			for (IType subtype : hierarchy.getAllSubtypes(root.javaType())) {
				ICompilationUnit unit= subtype.getCompilationUnit();
				if (unit == null || !unit.exists()
						|| !selectedHandles.contains(unit.getPrimary().getHandleIdentifier())) {
					return false;
				}
			}
			return true;
		} catch (JavaModelException e) {
			return false;
		}
	}

	private static Classification classify(SourceType root, List<SourceType> hierarchy,
			JUnitTestTypeInventory inventory) {
		Map<String, String> testOwners= new HashMap<>();
		Map<String, Integer> lifecycleDeclarations= new HashMap<>();
		List<TypeMigration> typeMigrations= new ArrayList<>();
		List<String> baselineHandles= new ArrayList<>();
		String rootKey= root.key();
		int maxDepth= hierarchy.stream().mapToInt(type -> hierarchyDepth(type, rootKey)).max().orElse(0);
		int maxTestsPerType= hierarchy.stream()
				.mapToInt(type -> (int) Arrays.stream(type.declaration().getMethods())
						.filter(JUnit3HierarchyPlanner::isTestPrefixed).count())
				.max().orElse(0);
		int orderStride= Math.max(1, maxTestsPerType + 1);

		for (SourceType type : hierarchy) {
			String excludedSuperType= JUnit3MigrationExclusions.excludedSuperType(type.binding());
			if (excludedSuperType != null) {
				return Classification.rejected(JUnit3MigrationExclusions.EXCLUDED_BASE_TYPE_REASON,
						"The hierarchy type " + type.name() + " derives from " + excludedSuperType //$NON-NLS-1$ //$NON-NLS-2$
								+ ", whose JUnit 3 execution contract must not be migrated to Jupiter."); //$NON-NLS-1$
			}
			if (!type.declaration().isPackageMemberTypeDeclaration() || !hasSingleTopLevelType(type)
					|| hasJUnitAnnotation(type.declaration())) {
				return Classification.rejected("UNSUPPORTED_JUNIT3_TYPE_SHAPE", //$NON-NLS-1$
						"Each supported hierarchy type must be the only top-level type in its compilation unit and must not already use JUnit annotations."); //$NON-NLS-1$
			}
			if (!Modifier.isAbstract(type.binding().getModifiers()) && inventory.contains(type.javaType())) {
				baselineHandles.add(type.javaType().getHandleIdentifier());
			}
			int depth= hierarchyDepth(type, rootKey);
			if (depth < 0) {
				return Classification.rejected("INCOMPLETE_JUNIT3_HIERARCHY", //$NON-NLS-1$
						"A planned hierarchy type is no longer connected to its JUnit 3 root."); //$NON-NLS-1$
			}
			Map<String, Integer> executionOrders= plannedExecutionOrders(type, maxDepth, depth, orderStride);
			List<MethodMigration> methods= new ArrayList<>();
			for (MethodDeclaration method : type.declaration().getMethods()) {
				JUnit3HarnessSemantics.Rejection harnessRejection=
						JUnit3HarnessSemantics.rejection(method).orElse(null);
				if (harnessRejection != null) {
					return Classification.rejected(harnessRejection.reasonCode(),
							harnessRejection.explanation());
				}
				if (hasJUnitAnnotation(method)) {
					return Classification.rejected("MIXED_JUNIT_GENERATIONS", //$NON-NLS-1$
							"The hierarchy already mixes annotation-driven and JUnit 3 execution semantics."); //$NON-NLS-1$
				}
				String name= method.getName().getIdentifier();
				String bindingKey= methodKey(method);
				if (name.startsWith("test")) { //$NON-NLS-1$
					if (!JUnit3SemanticSupport.isExactTestMethod(method) || bindingKey == null) {
						return Classification.rejected("INVALID_JUNIT3_TEST_SIGNATURE", //$NON-NLS-1$
								"A test-prefixed method does not satisfy the exact public non-static void test*() contract."); //$NON-NLS-1$
					}
					String previousOwner= testOwners.putIfAbsent(name, type.name());
					if (previousOwner != null) {
						return Classification.rejected("OVERRIDDEN_JUNIT3_TEST", //$NON-NLS-1$
								"The test method " + name + " is overridden across the hierarchy."); //$NON-NLS-1$ //$NON-NLS-2$
					}
					Integer executionOrder= executionOrders.get(bindingKey);
					if (executionOrder == null) {
						return Classification.rejected("UNRESOLVED_JUNIT3_TEST_ORDER", //$NON-NLS-1$
								"The exact JUnit 3 discovery order could not be assigned to " + name + "()."); //$NON-NLS-1$ //$NON-NLS-2$
					}
					methods.add(new MethodMigration(bindingKey, MethodKind.TEST, executionOrder.intValue()));
				} else if ("setUp".equals(name) || "tearDown".equals(name)) { //$NON-NLS-1$ //$NON-NLS-2$
					if (!JUnit3SemanticSupport.isLifecycleMethod(method, name) || bindingKey == null) {
						return Classification.rejected("INVALID_JUNIT3_LIFECYCLE", //$NON-NLS-1$
								"A lifecycle method does not satisfy the supported non-static parameterless void contract."); //$NON-NLS-1$
					}
					int count= lifecycleDeclarations.merge(name, Integer.valueOf(1), Integer::sum).intValue();
					if (count > 1) {
						return Classification.rejected("JUNIT3_LIFECYCLE_OVERRIDE_CHAIN", //$NON-NLS-1$
								"Lifecycle override chains require explicit semantic migration."); //$NON-NLS-1$
					}
					OverrideRemoval overrideRemoval= plannedOverrideRemoval(method);
					if (overrideRemoval == OverrideRemoval.UNRESOLVED) {
						return Classification.rejected("UNRESOLVED_JUNIT3_OVERRIDE_ANNOTATION", //$NON-NLS-1$
								"The lifecycle method " + name //$NON-NLS-1$
										+ " has an unresolved simple @Override annotation; exact annotation identity is required."); //$NON-NLS-1$
					}
					methods.add(new MethodMigration(bindingKey,
							"setUp".equals(name) ? MethodKind.BEFORE_EACH : MethodKind.AFTER_EACH, //$NON-NLS-1$
							0, overrideRemoval == OverrideRemoval.REMOVE));
				}
			}
			JUnit3AssertionInventory.Result assertions= JUnit3AssertionInventory.analyze(type.declaration());
			if (!assertions.supported()) {
				return Classification.rejected("CUSTOM_JUNIT3_API_USAGE", assertions.rejectionReason()); //$NON-NLS-1$
			}
			typeMigrations.add(new TypeMigration(type.unitHandle(), type.key(), type == root,
					methods, assertions.invocations()));
		}
		if (baselineHandles.isEmpty()) {
			return Classification.rejected("JDT_JUNIT_FINDER_MISMATCH", //$NON-NLS-1$
					"The configured JDT JUnit finder does not expose a concrete test type from the hierarchy."); //$NON-NLS-1$
		}
		baselineHandles.sort(String::compareTo);
		return Classification.supported(typeMigrations, baselineHandles);
	}


	private static Map<String, Integer> plannedExecutionOrders(SourceType type, int maxDepth, int depth,
			int stride) {
		Map<String, Integer> result= new HashMap<>();
		List<MethodDeclaration> orderedTests= Arrays.stream(type.declaration().getMethods())
				.filter(JUnit3HierarchyPlanner::isTestPrefixed)
				.sorted(JUNIT3_METHOD_ORDER)
				.toList();
		for (int index= 0; index < orderedTests.size(); index++) {
			String key= methodKey(orderedTests.get(index));
			long order= (long) (maxDepth - depth) * stride + index + 1L;
			if (key != null && order <= Integer.MAX_VALUE) {
				result.put(key, Integer.valueOf((int) order));
			}
		}
		return result;
	}

	private static boolean isTestPrefixed(MethodDeclaration method) {
		return method.getName().getIdentifier().startsWith("test"); //$NON-NLS-1$
	}

	private static boolean hasSingleTopLevelType(SourceType type) {
		return type.declaration().getRoot() instanceof CompilationUnit root && root.types().size() == 1;
	}

	/**
	 * Captures exact {@code java.lang.Override} presence while the immutable
	 * hierarchy plan is built. A simple unresolved {@code @Override} is ambiguous
	 * with a user-defined annotation and therefore rejects the candidate.
	 */
	private static OverrideRemoval plannedOverrideRemoval(MethodDeclaration method) {
		for (Object modifier : method.modifiers()) {
			if (!(modifier instanceof Annotation annotation)) {
				continue;
			}
			ITypeBinding binding= annotation.resolveTypeBinding();
			String writtenName= annotation.getTypeName().getFullyQualifiedName();
			if (binding != null && !binding.isRecovered()) {
				if ("java.lang.Override".equals(binding.getQualifiedName())) { //$NON-NLS-1$
					return OverrideRemoval.REMOVE;
				}
				continue;
			}
			if ("java.lang.Override".equals(writtenName)) { //$NON-NLS-1$
				return OverrideRemoval.REMOVE;
			}
			if ("Override".equals(writtenName)) { //$NON-NLS-1$
				return OverrideRemoval.UNRESOLVED;
			}
		}
		return OverrideRemoval.KEEP;
	}

	private static boolean hasJUnitAnnotation(BodyDeclaration declaration) {
		for (Object modifier : declaration.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				ITypeBinding binding= annotation.resolveTypeBinding();
				String name= binding == null ? annotation.getTypeName().getFullyQualifiedName()
						: binding.getQualifiedName();
				if (name.startsWith("org.junit.") || name.startsWith("junit.framework.")) { //$NON-NLS-1$ //$NON-NLS-2$
					return true;
				}
			}
		}
		return false;
	}

	private static String methodKey(MethodDeclaration method) {
		IMethodBinding binding= method.resolveBinding();
		return binding == null ? null : binding.getMethodDeclaration().getKey();
	}
}
