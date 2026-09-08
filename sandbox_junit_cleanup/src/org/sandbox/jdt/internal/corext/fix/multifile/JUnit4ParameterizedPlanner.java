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

import static org.sandbox.jdt.internal.corext.fix.multifile.JUnit4ParameterizedPlan.*;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateDiagnostic;
import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateOutcome;
import org.sandbox.jdt.cleanup.multifile.MultiFilePlanningBudget;
import org.sandbox.jdt.internal.corext.fix.helper.ParameterizedMigrationEligibility;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan.NodeKey;

/** Discovers closed source components; never executes a provider or changes source. */
final class JUnit4ParameterizedPlanner {

	private static final String PARAMETERS= "org.junit.runners.Parameterized.Parameters"; //$NON-NLS-1$
	private static final String PARAMETER_ANNOTATION= "org.junit.runners.Parameterized.Parameter"; //$NON-NLS-1$
	private static final String JUNIT_TEST= "org.junit.Test"; //$NON-NLS-1$
	private static final String OBJECT= "java.lang.Object"; //$NON-NLS-1$

	record Result(List<JUnit4ParameterizedPlan> plans, List<MultiFileCandidateDiagnostic> diagnostics) {
		Result {
			plans= List.copyOf(plans);
			diagnostics= List.copyOf(diagnostics);
		}
	}

	private final Map<String, CompilationUnit> roots;
	private final Map<String, TypeDeclaration> types= new LinkedHashMap<>();
	private final Map<String, MethodDeclaration> methods= new LinkedHashMap<>();
	private final Map<ASTNode, String> handlesByRoot= new IdentityHashMap<>();
	private final Map<String, String> fingerprints;
	private final boolean sourcesStable;
	private final IProgressMonitor monitor;

	private JUnit4ParameterizedPlanner(Map<String, CompilationUnit> roots, Map<String, String> snapshots,
			IProgressMonitor monitor)
			throws CoreException {
		this.roots= new TreeMap<>(roots);
		this.monitor= monitor;
		fingerprints= Map.copyOf(snapshots);
		boolean stable= snapshots.keySet().equals(roots.keySet());
		for (Map.Entry<String, CompilationUnit> entry : this.roots.entrySet()) {
			MultiFilePlanningBudget.checkCanceled(monitor);
			handlesByRoot.put(entry.getValue(), entry.getKey());
			if (entry.getValue().getJavaElement() instanceof ICompilationUnit unit) {
				String source= unit.getSource();
				stable &= source != null
						&& JUnit4ParameterizedPlan.fingerprint(source).equals(snapshots.get(entry.getKey()));
			} else {
				stable= false;
			}
			entry.getValue().accept(new ASTVisitor() {
				@Override
				public boolean visit(TypeDeclaration node) {
					ITypeBinding binding= node.resolveBinding();
					if (resolved(binding)) {
						types.put(binding.getTypeDeclaration().getKey(), node);
					}
					return true;
				}

				@Override
				public boolean visit(MethodDeclaration node) {
					IMethodBinding binding= node.resolveBinding();
					if (resolved(binding)) {
						methods.put(binding.getMethodDeclaration().getKey(), node);
					}
					return true;
				}
			});
		}
		sourcesStable= stable;
	}

	static Result discover(Map<String, CompilationUnit> roots, Map<String, String> sourceFingerprints,
			boolean closedScope, IProgressMonitor monitor)
			throws CoreException {
		return new JUnit4ParameterizedPlanner(roots, sourceFingerprints, monitor).discover(closedScope);
	}

	/** Capture before parsing so an old AST cannot be paired with a newer source fingerprint. */
	static Map<String, String> captureSources(ICompilationUnit[] units, IProgressMonitor monitor)
			throws CoreException {
		Map<String, String> snapshots= new LinkedHashMap<>();
		for (ICompilationUnit unit : units) {
			MultiFilePlanningBudget.checkCanceled(monitor);
			String source= unit.getPrimary().getSource();
			if (source != null) {
				snapshots.put(unit.getPrimary().getHandleIdentifier(), JUnit4ParameterizedPlan.fingerprint(source));
			}
		}
		return Map.copyOf(snapshots);
	}

	private Result discover(boolean closedScope) {
		List<JUnit4ParameterizedPlan> plans= new ArrayList<>();
		List<MultiFileCandidateDiagnostic> diagnostics= new ArrayList<>();
		for (Map.Entry<String, CompilationUnit> entry : roots.entrySet()) {
			entry.getValue().accept(new ASTVisitor() {
				@Override
				public boolean visit(TypeDeclaration node) {
					MultiFilePlanningBudget.checkCanceled(monitor);
					if (!candidate(node)) {
						return true;
					}
					ITypeBinding binding= node.resolveBinding();
					String identity= resolved(binding) ? binding.getQualifiedName()
							: entry.getKey() + "#" + node.getName().getIdentifier(); //$NON-NLS-1$
					String id= "parameterized:" + identity; //$NON-NLS-1$
					try {
						require(closedScope, "INCOMPLETE_SCOPE", //$NON-NLS-1$
								"Select the complete editable provider and test hierarchy before planning."); //$NON-NLS-1$
						require(sourcesStable, "SOURCE_CHANGED", //$NON-NLS-1$
								"The selected sources changed or were unavailable during AST creation. Retry planning on a stable source scope."); //$NON-NLS-1$
						JUnit4ParameterizedPlan plan= plan(node);
						plans.add(plan);
						diagnostics.add(new MultiFileCandidateDiagnostic(id, entry.getKey(),
								MultiFileCandidateOutcome.FOUND, "PARAMETERIZED_RUNTIME_VERIFICATION_REQUIRED", //$NON-NLS-1$
								"Parameterized source roles and ordered injection relations were discovered. " //$NON-NLS-1$
										+ "A coordinated executor must verify identity, multiplicity, names and results " //$NON-NLS-1$
										+ "with the JDT JUnit runtime before this prepared plan can be applied.", //$NON-NLS-1$
								new ArrayList<>(plan.compilationUnits().values())));
					} catch (Rejected e) {
						diagnostics.add(MultiFileCandidateDiagnostic.rejected(id, entry.getKey(), e.code,
								"No coordinated Parameterized plan was produced: " + e.getMessage(), //$NON-NLS-1$
								List.of(entry.getKey())));
					}
					return true;
				}
			});
		}
		return new Result(plans, diagnostics);
	}

	private JUnit4ParameterizedPlan plan(TypeDeclaration testClass) {
		require(ParameterizedMigrationEligibility.hasParameterizedRunner(testClass), "RUNNER_UNRESOLVED", //$NON-NLS-1$
				"Resolve @RunWith to the standard JUnit 4 Parameterized runner."); //$NON-NLS-1$
		require(testClass.isPackageMemberTypeDeclaration() && !testClass.isInterface()
				&& Modifier.isPublic(testClass.getModifiers()) && !Modifier.isAbstract(testClass.getModifiers()),
				"TEST_CLASS_UNSUPPORTED", "Select a public concrete top-level Parameterized test class."); //$NON-NLS-1$ //$NON-NLS-2$
		List<TypeDeclaration> hierarchy= hierarchy(testClass);
		SemanticRewritePlan.Builder builder= SemanticRewritePlan.builder(CONTRACT);
		Map<NodeKey, String> owners= new LinkedHashMap<>();
		NodeKey testKey= add(builder, owners, testClass, RUNNER_CLASS);
		List<MethodDeclaration> providers= new ArrayList<>();
		List<MethodDeclaration> tests= new ArrayList<>();
		Map<Integer, VariableDeclarationFragment> fields= new TreeMap<>();
		Set<String> testNames= new HashSet<>();
		for (TypeDeclaration type : hierarchy) {
			validateSource(type);
			checkAnnotations(type);
			require(type.typeParameters().isEmpty(), "GENERIC_HIERARCHY", //$NON-NLS-1$
					"Resolve generic test hierarchy substitutions before migration."); //$NON-NLS-1$
			for (MethodDeclaration method : type.getMethods()) {
				checkAnnotations(method);
				if (annotation(method, PARAMETERS) != null) {
					providers.add(method);
				}
				if (annotation(method, JUNIT_TEST) != null) {
					require(testNames.add(method.getName().getIdentifier()), "TEST_OVERRIDE", //$NON-NLS-1$
							"Resolve overridden or hidden parameterized test methods before migration."); //$NON-NLS-1$
					require(Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())
							&& method.parameters().isEmpty() && method.getBody() != null
							&& resolved(method.resolveBinding())
							&& "void".equals(method.resolveBinding().getReturnType().getName()), //$NON-NLS-1$
							"TEST_METHOD_UNSUPPORTED", "JUnit 4 test methods must be public void instance methods without parameters."); //$NON-NLS-1$ //$NON-NLS-2$
					tests.add(method);
				}
			}
			for (FieldDeclaration field : type.getFields()) {
				checkAnnotations(field);
				Annotation parameter= annotation(field, PARAMETER_ANNOTATION);
				if (parameter == null) {
					continue;
				}
				require(field.fragments().size() == 1 && Modifier.isPublic(field.getModifiers())
						&& !Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()),
						"FIELD_UNSUPPORTED", "Use one public non-final instance field per @Parameter declaration."); //$NON-NLS-1$ //$NON-NLS-2$
				Object index= value(parameter, "value"); //$NON-NLS-1$
				require(index instanceof Integer && ((Integer) index).intValue() >= 0, "FIELD_INDEX", //$NON-NLS-1$
						"Resolve each @Parameter index to a non-negative integer."); //$NON-NLS-1$
				require(fields.put((Integer) index, (VariableDeclarationFragment) field.fragments().get(0)) == null,
						"DUPLICATE_FIELD_INDEX", "Give each injected field a unique contiguous index starting at zero."); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		require(providers.size() == 1, "PROVIDER_NOT_UNIQUE", //$NON-NLS-1$
				"Select exactly one unambiguous local or inherited @Parameters provider."); //$NON-NLS-1$
		require(!tests.isEmpty(), "NO_TESTS", "No supported JUnit 4 test methods were found."); //$NON-NLS-1$ //$NON-NLS-2$
		List<ITypeBinding> parameterTypes= injection(testClass, fields, builder, owners, testKey);
		MethodDeclaration provider= providers.get(0);
		require(Modifier.isPublic(provider.getModifiers()), "PROVIDER_NOT_PUBLIC", //$NON-NLS-1$
				"JUnit 4 requires a public @Parameters provider."); //$NON-NLS-1$
		validateDisplayName(provider);
		NodeKey providerKey= add(builder, owners, provider, PROVIDER);
		builder.relate(testKey, HAS_PROVIDER, providerKey);
		MethodDeclaration data= providerData(provider, builder, owners, new HashSet<>());
		validateRows(data, parameterTypes);
		builder.putString(providerKey, "conversionStrategy", "OBJECT_ARRAY_ROWS"); //$NON-NLS-1$ //$NON-NLS-2$
		builder.putString(testKey, "displayNameSemantics", "JUNIT4_MESSAGE_FORMAT_ZERO_BASED"); //$NON-NLS-1$ //$NON-NLS-2$
		for (MethodDeclaration method : tests) {
			builder.relate(testKey, HAS_TEST, add(builder, owners, method, TEST));
		}
		return new JUnit4ParameterizedPlan(testKey, builder.build(), owners, fingerprints);
	}

	private static void validateDisplayName(MethodDeclaration provider) {
		Object name= value(annotation(provider, PARAMETERS), "name"); //$NON-NLS-1$
		require(name instanceof String, "DISPLAY_NAME_UNRESOLVED", //$NON-NLS-1$
				"Resolve the @Parameters display-name pattern before migration."); //$NON-NLS-1$
		try {
			new MessageFormat(((String) name).replace("{index}", "0")); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (IllegalArgumentException e) {
			throw new Rejected("PARAMETERIZED_DISPLAY_NAME_UNSUPPORTED", //$NON-NLS-1$
					"The @Parameters name is not a valid JUnit 4 MessageFormat pattern; correct it before migration."); //$NON-NLS-1$
		}
	}

	private List<ITypeBinding> injection(TypeDeclaration type, Map<Integer, VariableDeclarationFragment> fields,
			SemanticRewritePlan.Builder builder, Map<NodeKey, String> owners, NodeKey testKey) {
		List<MethodDeclaration> constructors= java.util.Arrays.stream(type.getMethods())
				.filter(MethodDeclaration::isConstructor).toList();
		List<ITypeBinding> parameterTypes= new ArrayList<>();
		if (!fields.isEmpty()) {
			require(constructors.isEmpty() || constructors.size() == 1
					&& constructors.get(0).parameters().isEmpty() && Modifier.isPublic(constructors.get(0).getModifiers()),
					"MIXED_INJECTION", "Field injection requires one public zero-argument constructor."); //$NON-NLS-1$ //$NON-NLS-2$
			for (int index= 0; index < fields.size(); index++) {
				VariableDeclarationFragment field= fields.get(Integer.valueOf(index));
				require(field != null, "FIELD_INDEX_GAP", "Parameter field indices must be contiguous starting at zero."); //$NON-NLS-1$ //$NON-NLS-2$
				IVariableBinding binding= field.resolveBinding();
				require(resolved(binding), "BINDING_UNRESOLVED", "Resolve every injected field binding."); //$NON-NLS-1$ //$NON-NLS-2$
				parameterTypes.add(binding.getType());
				builder.relate(testKey, HAS_FIELD, add(builder, owners, field, FIELD));
			}
		} else {
			require(constructors.size() == 1 && Modifier.isPublic(constructors.get(0).getModifiers())
					&& !constructors.get(0).parameters().isEmpty(), "CONSTRUCTOR_NOT_UNIQUE", //$NON-NLS-1$
					"Constructor injection requires exactly one public constructor with parameters."); //$NON-NLS-1$
			MethodDeclaration constructor= constructors.get(0);
			NodeKey constructorKey= add(builder, owners, constructor, CONSTRUCTOR);
			builder.relate(testKey, HAS_CONSTRUCTOR, constructorKey);
			for (Object item : constructor.parameters()) {
				SingleVariableDeclaration parameter= (SingleVariableDeclaration) item;
				IVariableBinding binding= parameter.resolveBinding();
				require(!parameter.isVarargs() && resolved(binding), "PARAMETER_UNSUPPORTED", //$NON-NLS-1$
						"Resolve each constructor parameter; varargs require a dedicated conversion strategy."); //$NON-NLS-1$
				parameterTypes.add(binding.getType());
				builder.relate(constructorKey, HAS_PARAMETER, add(builder, owners, parameter, PARAMETER));
			}
		}
		return parameterTypes;
	}

	private List<TypeDeclaration> hierarchy(TypeDeclaration leaf) {
		List<TypeDeclaration> hierarchy= new ArrayList<>();
		Set<String> seen= new HashSet<>();
		ITypeBinding binding= leaf.resolveBinding();
		while (binding != null && !OBJECT.equals(binding.getQualifiedName())) {
			MultiFilePlanningBudget.checkCanceled(monitor);
			require(resolved(binding) && seen.add(binding.getKey()), "HIERARCHY_UNRESOLVED", //$NON-NLS-1$
					"Resolve the complete test superclass chain."); //$NON-NLS-1$
			TypeDeclaration source= types.get(binding.getTypeDeclaration().getKey());
			require(source != null, "HIERARCHY_OUTSIDE_SCOPE", //$NON-NLS-1$
					"Include every editable test superclass; binary hierarchies cannot be migrated."); //$NON-NLS-1$
			hierarchy.add(source);
			binding= binding.getSuperclass();
		}
		return hierarchy;
	}

	private MethodDeclaration providerData(MethodDeclaration provider, SemanticRewritePlan.Builder builder,
			Map<NodeKey, String> owners, Set<String> visited) {
		MultiFilePlanningBudget.checkCanceled(monitor);
		validateSource(provider);
		require(resolved(provider.resolveBinding()) && visited.add(provider.resolveBinding().getKey()),
				"PROVIDER_CYCLE", "Resolve provider delegation without recursive cycles."); //$NON-NLS-1$ //$NON-NLS-2$
		require(Modifier.isStatic(provider.getModifiers()) && provider.parameters().isEmpty()
				&& provider.typeParameters().isEmpty() && provider.getBody() != null
				&& provider.getBody().statements().size() == 1
				&& provider.getBody().statements().get(0) instanceof ReturnStatement,
				"PROVIDER_BODY_UNSUPPORTED", "Use a static no-argument provider returning a literal Object[][] matrix or a source provider delegate."); //$NON-NLS-1$ //$NON-NLS-2$
		Expression expression= ((ReturnStatement) provider.getBody().statements().get(0)).getExpression();
		if (expression instanceof MethodInvocation invocation && !arraysAsList(invocation)) {
			IMethodBinding binding= invocation.resolveMethodBinding();
			Expression receiver= invocation.getExpression();
			boolean typeReceiver= receiver == null
					|| receiver instanceof Name name && name.resolveBinding() instanceof ITypeBinding;
			require(resolved(binding) && Modifier.isStatic(binding.getModifiers()) && invocation.arguments().isEmpty()
					&& typeReceiver,
					"PROVIDER_DELEGATE_UNRESOLVED", "Resolve the static no-argument provider delegation."); //$NON-NLS-1$ //$NON-NLS-2$
			MethodDeclaration delegate= methods.get(binding.getMethodDeclaration().getKey());
			require(delegate != null, "PROVIDER_OUTSIDE_SCOPE", //$NON-NLS-1$
					"Include the editable delegated provider; binary or unselected providers are rejected."); //$NON-NLS-1$
			NodeKey delegateKey= add(builder, owners, delegate, PROVIDER);
			builder.relate(NodeKey.from(provider), DELEGATES_TO, delegateKey);
			return providerData(delegate, builder, owners, visited);
		}
		return provider;
	}

	private void validateRows(MethodDeclaration provider, List<ITypeBinding> parameters) {
		Expression expression= ((ReturnStatement) provider.getBody().statements().get(0)).getExpression();
		if (expression instanceof MethodInvocation invocation && arraysAsList(invocation)) {
			require(invocation.arguments().size() == 1, "PROVIDER_BODY_UNSUPPORTED", //$NON-NLS-1$
					"Arrays.asList must wrap exactly one Object[][] initializer."); //$NON-NLS-1$
			expression= (Expression) invocation.arguments().get(0);
		}
		require(expression instanceof ArrayCreation, "PROVIDER_BODY_UNSUPPORTED", //$NON-NLS-1$
				"Only a literal Object[][] matrix has a proven row conversion in this planner."); //$NON-NLS-1$
		ArrayCreation matrix= (ArrayCreation) expression;
		ITypeBinding type= matrix.resolveTypeBinding();
		require(resolved(type) && type.getDimensions() == 2 && OBJECT.equals(type.getElementType().getQualifiedName())
				&& matrix.getInitializer() != null, "PROVIDER_BODY_UNSUPPORTED", //$NON-NLS-1$
				"The provider must return an initialized Object[][] matrix."); //$NON-NLS-1$
		for (Object row : matrix.getInitializer().expressions()) {
			MultiFilePlanningBudget.checkCanceled(monitor);
			ArrayInitializer values= row instanceof ArrayInitializer initializer ? initializer
					: row instanceof ArrayCreation creation ? creation.getInitializer() : null;
			require(values != null && values.expressions().size() == parameters.size(), "ROW_ARITY", //$NON-NLS-1$
					"Every parameter row must have exactly one value per injection parameter."); //$NON-NLS-1$
			for (int index= 0; index < parameters.size(); index++) {
				Expression argument= (Expression) values.expressions().get(index);
				ITypeBinding target= parameters.get(index);
				ITypeBinding source= argument.resolveTypeBinding();
				boolean compatible= resolved(target) && (argument instanceof NullLiteral ? !target.isPrimitive()
						: argument.resolveConstantExpressionValue() != null && resolved(source)
								&& source.isAssignmentCompatible(target));
				require(compatible, "CONVERSION_UNPROVEN", //$NON-NLS-1$
						"Use resolved constant arguments assignable to their injection types; implicit Jupiter conversions are not assumed equivalent."); //$NON-NLS-1$
			}
		}
	}

	private NodeKey add(SemanticRewritePlan.Builder builder, Map<NodeKey, String> owners, ASTNode node, String role) {
		NodeKey key= NodeKey.from(node);
		require(key != null, "BINDING_UNRESOLVED", "Resolve every planned declaration binding."); //$NON-NLS-1$ //$NON-NLS-2$
		String owner= handlesByRoot.get(node.getRoot());
		require(owner != null, "SOURCE_OUTSIDE_SCOPE", "Include every planned source declaration."); //$NON-NLS-1$ //$NON-NLS-2$
		owners.put(key, owner);
		builder.add(key, role);
		return key;
	}

	private static void validateSource(ASTNode node) {
		CompilationUnit root= (CompilationUnit) node.getRoot();
		for (IProblem problem : root.getProblems()) {
			require(!problem.isError(), "SOURCE_ERRORS", "Resolve compilation errors in every participating source file."); //$NON-NLS-1$ //$NON-NLS-2$
		}
		require(root.getJavaElement() instanceof ICompilationUnit, "SOURCE_UNAVAILABLE", //$NON-NLS-1$
				"A workspace compilation unit is required for source snapshot validation."); //$NON-NLS-1$
		ICompilationUnit unit= (ICompilationUnit) root.getJavaElement();
		require(!unit.isReadOnly() && unit.getResource() != null
				&& (unit.getResource().getResourceAttributes() == null
						|| !unit.getResource().getResourceAttributes().isReadOnly()), "SOURCE_READ_ONLY", //$NON-NLS-1$
				"Make every participating source file editable before migration."); //$NON-NLS-1$
	}

	private static boolean candidate(TypeDeclaration type) {
		if (ParameterizedMigrationEligibility.hasParameterizedRunner(type)) {
			return true;
		}
		for (Object item : type.modifiers()) {
			if (item instanceof Annotation annotation) {
				String name= annotation.getTypeName().getFullyQualifiedName();
				if (("RunWith".equals(name) || "org.junit.runner.RunWith".equals(name)) //$NON-NLS-1$ //$NON-NLS-2$
						&& annotation.toString().contains("Parameterized")) { //$NON-NLS-1$
					return true;
				}
			}
		}
		return false;
	}

	private static Annotation annotation(BodyDeclaration declaration, String name) {
		for (Object item : declaration.modifiers()) {
			if (item instanceof Annotation annotation && resolved(annotation.resolveTypeBinding())
					&& name.equals(annotation.resolveTypeBinding().getQualifiedName())) {
				return annotation;
			}
		}
		return null;
	}

	private static void checkAnnotations(BodyDeclaration declaration) {
		for (Object item : declaration.modifiers()) {
			if (!(item instanceof Annotation annotation)) {
				continue;
			}
			ITypeBinding binding= annotation.resolveTypeBinding();
			require(resolved(binding), "BINDING_UNRESOLVED", "Resolve all annotations before classifying execution hooks."); //$NON-NLS-1$ //$NON-NLS-2$
			String name= binding.getQualifiedName();
			if (name.startsWith("org.junit.")) { //$NON-NLS-1$
				require(Set.of(PARAMETERS, PARAMETER_ANNOTATION, JUNIT_TEST, "org.junit.runner.RunWith", //$NON-NLS-1$
						"org.junit.Before", "org.junit.After", "org.junit.BeforeClass", "org.junit.AfterClass") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
						.contains(name), "EXECUTION_HOOK_UNSUPPORTED", //$NON-NLS-1$
						"Custom runners, rules, parameter hooks and mixed JUnit generations need a dedicated semantic contract."); //$NON-NLS-1$
			}
		}
	}

	private static Object value(Annotation annotation, String member) {
		IAnnotationBinding binding= annotation.resolveAnnotationBinding();
		if (binding != null) {
			for (IMemberValuePairBinding pair : binding.getAllMemberValuePairs()) {
				if (member.equals(pair.getName())) {
					return pair.getValue();
				}
			}
		}
		return null;
	}

	private static boolean arraysAsList(MethodInvocation invocation) {
		IMethodBinding binding= invocation.resolveMethodBinding();
		return resolved(binding) && resolved(binding.getDeclaringClass())
				&& "java.util.Arrays".equals(binding.getDeclaringClass().getQualifiedName()) //$NON-NLS-1$
				&& "asList".equals(binding.getName()); //$NON-NLS-1$
	}

	private static boolean resolved(IBinding binding) {
		return binding != null && !binding.isRecovered() && binding.getKey() != null;
	}

	private static void require(boolean condition, String code, String message) {
		if (!condition) {
			throw new Rejected("PARAMETERIZED_" + code, message); //$NON-NLS-1$
		}
	}

	private static final class Rejected extends RuntimeException {
		private static final long serialVersionUID= 1L;
		private final String code;

		Rejected(String code, String message) {
			super(message);
			this.code= code;
		}
	}
}
