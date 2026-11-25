/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateDiagnostic;
import org.sandbox.jdt.cleanup.multifile.MultiFilePlanningBudget;
import org.sandbox.jdt.internal.common.MigrationSupportProvider;
import org.sandbox.jdt.internal.corext.fix.multifile.MixedResourcePlan.Registration;

/** Plans leaf-owner registrations while preserving the complete shared Vintage component. */
final class MixedResourcePlanner {
	private static final String RULE= "org.junit.Rule"; //$NON-NLS-1$
	private static final String CLASS_RULE= "org.junit.ClassRule"; //$NON-NLS-1$
	private static final String RESOURCE= "org.junit.rules.ExternalResource"; //$NON-NLS-1$
	private record Use(String handle, String fixture, FieldDeclaration field, boolean classRule, boolean registered) {}
	private record DeclaredRule(String handle, FieldDeclaration field, IVariableBinding variable, ITypeBinding type, boolean classRule, List<Expression> values) {}
	private final List<DeclaredRule> declaredRules= new ArrayList<>();
	private final Map<String, CompilationUnit> roots;
	private final Map<String, Set<String>> graph= new LinkedHashMap<>();
	private final Map<String, TypeDeclaration> types= new LinkedHashMap<>();
	private final Map<String, String> handles= new HashMap<>();
	private final Set<Use> uses= new LinkedHashSet<>();
	private final Map<String, IVariableBinding> priorFields= new HashMap<>();
	private final Set<String> identifiers= new HashSet<>();
	private final Map<String, List<Expression>> assignments= new HashMap<>();

	private MixedResourcePlanner(Map<String, CompilationUnit> roots) {
		this.roots= roots;
	}

	static MixedResourcePlan discover(IJavaProject project, Map<String, CompilationUnit> roots,
			boolean enabled, Set<String> exclusions, RefactoringStatus status, List<MultiFileCandidateDiagnostic> diagnostics,
			IProgressMonitor monitor) throws CoreException {
		var index= new MixedResourcePlanner(roots);
		index.index(monitor);
		Map<String, Set<Boolean>> scopes= new LinkedHashMap<>();
		Set<String> registered= new HashSet<>();
		for (var use : index.uses) {
			scopes.computeIfAbsent(use.fixture(), ignored -> new HashSet<>()).add(use.classRule());
			if (use.registered()) registered.add(use.fixture());
		}
		Map<String, List<ITypeBinding>> families= new LinkedHashMap<>();
		for (var declaration : index.types.values()) {
			var type= declaration.resolveBinding();
			String root= index.familyKey(type);
			if (root != null) {
				families.computeIfAbsent(root, ignored -> new ArrayList<>()).add(type);
				if (!index.declaredRules.isEmpty()) scopes.computeIfAbsent(root, ignored -> new HashSet<>());
			}
		}
		if (scopes.isEmpty()) return MixedResourcePlan.EMPTY;
		var references= new ExternalResourceReferenceSearch(List.of(project),
				roots.keySet().stream().map(handle -> (ICompilationUnit) JavaCore.create(handle)).toList());
		// An opaque opposite-scope value can be the only second usage of a shared fixture.
		// Establish this relationship before authorizing any single-scope superclass rewrite.
		for (var family : scopes.entrySet()) {
			var resource= index.types.get(family.getKey()).resolveBinding();
			for (var rule : index.declaredRules) {
				if (index.maySupply(resource, families.get(family.getKey()), rule, references, monitor)) {
					index.connect(rule.handle(), index.handles.get(family.getKey()));
					family.getValue().add(rule.classRule());
				}
			}
		}
		Set<String> mixed= new LinkedHashSet<>(registered);
		scopes.forEach((fixture, kinds) -> { if (kinds.size() > 1) mixed.add(fixture); });
		if (mixed.isEmpty()) return MixedResourcePlan.EMPTY;
		boolean provider= MigrationSupportProvider.available(project);
		boolean supported= enabled && provider;
		Map<String, Registration> registrations= new LinkedHashMap<>();
		Set<String> protectedUnits= new LinkedHashSet<>();
		List<JUnitBestEffortSupport.Gap> retained= new ArrayList<>();
		for (String fixture : mixed) {
			MultiFilePlanningBudget.checkCanceled(monitor);
			String handle= index.handles.get(fixture);
			Set<String> component= index.component(handle);
			if (!java.util.Collections.disjoint(component, exclusions)) {
				// The best-effort fallback can deliberately leave an entire unsupported component on Vintage.
				continue;
			}
			var resource= index.types.get(fixture);
			String id= "external-resource:" + resource.resolveBinding().getQualifiedName(); //$NON-NLS-1$
			boolean proven= false;
			if (provider && registered.contains(fixture)) {
				for (var use : index.uses) {
					if (!use.registered() || !fixture.equals(use.fixture())) continue;
					var field= index.priorFields.get(use.handle());
					if (closedOwner(field.getDeclaringClass(), field, references, monitor)) {
						proven= true;
						break;
					}
				}
			}
			for (Use use : index.uses) {
				if (!fixture.equals(use.fixture()) || use.registered() || use.field() == null || !supported) continue;
				Registration registration= index.registration(use, references, monitor);
				if (registration != null) {
					proven= true;
					registrations.put(use.handle(), registration);
					diagnostics.add(MultiFileCandidateDiagnostic.transformed(id + ":" + registration.fieldKey(), //$NON-NLS-1$
							use.handle(), "Registers the unchanged fixture through a deferred lifecycle adapter.", List.copyOf(component))); //$NON-NLS-1$
				}
			}
			if (!proven) {
				String message= "ExternalResource type " + resource.resolveBinding().getQualifiedName() //$NON-NLS-1$
						+ " is used by both @Rule and @ClassRule. A compatible migration support provider and a proven independent owner are required."; //$NON-NLS-1$
				diagnostics.add(MultiFileCandidateDiagnostic.rejected(id, handle, "MIXED_RULE_LIFECYCLE", message, List.copyOf(component))); //$NON-NLS-1$
				status.addFatalError(message);
			} else {
				protectedUnits.addAll(component);
			}
		}
		for (String handle : protectedUnits) {
			if (registrations.containsKey(handle) || index.uses.stream().anyMatch(use -> handle.equals(use.handle()) && use.registered())) continue;
			for (Object element : roots.get(handle).types()) {
				if (!(element instanceof TypeDeclaration type) || type.resolveBinding() == null) continue;
				String name= type.resolveBinding().getQualifiedName();
				String id= "external-resource:" + name; //$NON-NLS-1$
				String reason= "MIXED_RULE_CONTRACT_RETAINED"; //$NON-NLS-1$
				String message= "Retains the shared fixture or test hierarchy, including its JUnit 4 annotations and virtual hooks."; //$NON-NLS-1$
				retained.add(new JUnitBestEffortSupport.Gap(handle, type.resolveBinding().getKey(), name,
						type.getStartPosition(), id, reason, message, "Keep its Vintage contract until all rule peers and inherited uses can migrate together.")); //$NON-NLS-1$
				diagnostics.add(MultiFileCandidateDiagnostic.rejected(id, handle, reason, message, List.of(handle)));
			}
		}
		Map<String, String> fingerprints= new LinkedHashMap<>();
		if (!registrations.isEmpty()) {
			for (String handle : roots.keySet()) {
				var unit= (ICompilationUnit) JavaCore.create(handle);
				fingerprints.put(handle, JUnit4ParameterizedPlan.fingerprint(unit.getSource()));
			}
		}
		return new MixedResourcePlan(registrations, protectedUnits, fingerprints, retained);
	}

	private boolean maySupply(ITypeBinding resource, List<ITypeBinding> family, DeclaredRule rule, ExternalResourceReferenceSearch references,
			IProgressMonitor monitor) throws CoreException {
		if (!overlapsFamily(resource, family, rule.type())) return false;
		if (resource.isEqualTo(rule.type()) || rule.values().stream().anyMatch(value -> possibleValue(resource, family, value))) return true;
		if (rule.variable() == null) return rule.values().isEmpty();
		var field= rule.variable().getVariableDeclaration();
		return assignments.getOrDefault(field.getKey(), List.of()).stream().anyMatch(value -> possibleValue(resource, family, value))
				|| !Modifier.isFinal(field.getModifiers()) && (!(field.getJavaElement() instanceof IField model)
						|| !references.allWritesSelected(model, monitor));
	}

	private static boolean possibleValue(ITypeBinding resource, List<ITypeBinding> family, Expression value) {
		while (value instanceof ParenthesizedExpression parenthesized) value= parenthesized.getExpression();
		if (value instanceof NullLiteral) return false;
		var actual= value.resolveTypeBinding();
		if (actual == null || actual.isRecovered()) return true;
		return value instanceof ClassInstanceCreation ? actual.getErasure().isAssignmentCompatible(resource.getErasure())
				: overlapsFamily(resource, family, actual);
	}

	private static boolean overlapsFamily(ITypeBinding root, List<ITypeBinding> family, ITypeBinding declared) {
		if (declared == null || declared.isRecovered()) return true;
		// Generic arguments may hide a legal projection (including captures and intersection bounds).
		// Only rule out overlap when the erased hierarchy also excludes the value.
		var projection= declared.getErasure();
		for (var type : family) {
			var concrete= type.getErasure();
			if (concrete.isAssignmentCompatible(projection) || projection.isAssignmentCompatible(concrete)) return true;
		}
		// An unindexed or binary subtype of a non-final root may implement a narrower interface.
		var base= root.getErasure();
		return projection.isInterface() && !Modifier.isFinal(base.getModifiers())
				|| base.isCastCompatible(projection) || projection.isCastCompatible(base);
	}

	private void index(IProgressMonitor monitor) {
		for (var entry : roots.entrySet()) {
			MultiFilePlanningBudget.checkCanceled(monitor);
			entry.getValue().accept(new ASTVisitor() {
				@Override public boolean visit(SimpleName name) {
					identifiers.add(name.getIdentifier());
					return true;
				}
				@Override public boolean visit(TypeDeclaration type) {
					ITypeBinding binding= type.resolveBinding();
					if (binding != null && !binding.isRecovered()) {
						types.put(binding.getKey(), type);
						handles.put(binding.getKey(), entry.getKey());
					}
					return true;
				}
			});
		}
		for (var entry : types.entrySet()) {
			var node= entry.getValue();
			String handle= handles.get(entry.getKey());
			var binding= node.resolveBinding();
			for (var parent= binding.getSuperclass(); parent != null; parent= parent.getSuperclass()) {
				String key= parent.getTypeDeclaration().getKey();
				var declaration= types.get(key);
				if (declaration != null && (resourceAncestor(parent) || JUnitBestEffortSupport.declaresJUnit4TestContract(declaration))) {
					connect(handle, handles.get(key));
				}
			}
		}
		for (var entry : roots.entrySet()) {
			MultiFilePlanningBudget.checkCanceled(monitor);
			entry.getValue().accept(new ASTVisitor() {
				@Override public boolean visit(FieldDeclaration field) {
					boolean instance= annotation(field.modifiers(), RULE) != null;
					boolean shared= annotation(field.modifiers(), CLASS_RULE) != null;
					if (instance || shared) {
						for (Object value : field.fragments()) {
							var fragment= (VariableDeclarationFragment) value;
							if (fragment.resolveBinding() == null) continue;
							declaredRules.add(new DeclaredRule(entry.getKey(), field, fragment.resolveBinding(), fragment.resolveBinding().getType(), shared,
									fragment.getInitializer() == null ? List.of() : List.of(fragment.getInitializer())));
							addUse(entry.getKey(), field, fragment.resolveBinding().getType(), shared, false);
							if (fragment.getInitializer() != null) {
								addUse(entry.getKey(), field, fragment.getInitializer().resolveTypeBinding(), shared, false);
							}
						}
					}
					return true;
				}
				@Override public boolean visit(Assignment assignment) {
					if (referencedField(assignment.getLeftHandSide()) instanceof IVariableBinding binding && binding.isField()) {
						assignments.computeIfAbsent(binding.getVariableDeclaration().getKey(), ignored -> new ArrayList<>())
								.add(assignment.getRightHandSide());
					}
					return true;
				}
				@Override public boolean visit(MethodDeclaration method) {
					boolean shared= annotation(method.modifiers(), CLASS_RULE) != null;
					if ((shared || annotation(method.modifiers(), RULE) != null) && method.getReturnType2() != null) {
						var result= method.getReturnType2().resolveBinding();
						List<Expression> returned= new ArrayList<>();
						if (result != null) declaredRules.add(new DeclaredRule(entry.getKey(), null, null, result, shared, returned));
						addUse(entry.getKey(), null, result, shared, false);
						if (method.getBody() != null) method.getBody().accept(new ASTVisitor() {
							@Override public boolean visit(ReturnStatement statement) {
								if (statement.getExpression() != null) {
									returned.add(statement.getExpression());
									addUse(entry.getKey(), null, statement.getExpression().resolveTypeBinding(), shared, false);
								}
								return false;
							}
						});
						// A virtual rule factory can return a different implementation in a subclass.
						var binding= method.resolveBinding();
						if (binding == null || !(Modifier.isStatic(method.getModifiers()) || Modifier.isFinal(method.getModifiers())
								|| Modifier.isPrivate(method.getModifiers()) || Modifier.isFinal(binding.getDeclaringClass().getModifiers()))) returned.clear();
					}
					return true;
				}
				@Override public boolean visit(MethodInvocation invocation) {
					var field= registeredFixture(invocation);
					if (field != null) {
						priorFields.put(entry.getKey(), field);
						addUse(entry.getKey(), null, field.getType(), Modifier.isStatic(field.getModifiers()), true);
					}
					return true;
				}
			});
		}
		for (var rule : declaredRules) {
			if (rule.variable() == null) continue;
			for (var expression : assignments.getOrDefault(rule.variable().getVariableDeclaration().getKey(), List.of())) {
				addUse(rule.handle(), rule.field(), expression.resolveTypeBinding(), rule.classRule(), false);
			}
		}
	}

	/** Recognize a working prior migration on an otherwise executable Jupiter owner. */
	private IVariableBinding registeredFixture(MethodInvocation call) {
		var factory= call.resolveMethodBinding();
		if (!deferredFactory(factory) || call.arguments().size() != 3
				|| !(supplierField(call) instanceof IVariableBinding field) || !field.isField()
				|| !(call.getParent() instanceof VariableDeclarationFragment fragment)
				|| !(fragment.getParent() instanceof FieldDeclaration extension)
				|| !(extension.getParent() instanceof TypeDeclaration owner)
				|| !Modifier.isPublic(field.getModifiers()) || !standaloneOwner(owner) || !owner.resolveBinding().isEqualTo(field.getDeclaringClass())
				|| extension.fragments().size() != 1 || extension.getType().resolveBinding() == null
				|| !"org.junit.jupiter.api.extension.Extension".equals(extension.getType().resolveBinding().getQualifiedName())
				|| !Modifier.isFinal(extension.getModifiers()) || Modifier.isPrivate(extension.getModifiers())
				|| Modifier.isStatic(extension.getModifiers()) != Modifier.isStatic(field.getModifiers())
				|| Modifier.isStatic(field.getModifiers()) != "perClass".equals(factory.getName()) //$NON-NLS-1$
				|| annotation(extension.modifiers(), MixedResourcePlan.REGISTER) == null
				|| annotation(owner.modifiers(), MixedResourcePlan.ISOLATED) == null
				|| !(annotation(owner.modifiers(), MixedResourcePlan.TEST_INSTANCE) instanceof SingleMemberAnnotation lifecycle)
				|| !(lifecycle.getValue() instanceof Name value) || !(value.resolveBinding() instanceof IVariableBinding policy)
				|| !policy.isEnumConstant() || !"PER_METHOD".equals(policy.getName()) //$NON-NLS-1$
				|| !resolvesHook(call.arguments().get(1), "before") || !resolvesHook(call.arguments().get(2), "after") //$NON-NLS-1$ //$NON-NLS-2$
				|| !currentValues(owner, field.getVariableDeclaration().getKey(), roots.keySet())) return null;
		return simpleContract(owner, extension, true) && priorMethodOrder(owner) ? field : null;
	}

	private static boolean deferredFactory(IMethodBinding method) {
		if (method == null || method.isRecovered() || !MigrationSupportProvider.FACTORY.equals(method.getDeclaringClass().getQualifiedName())
				|| !Set.of("perTest", "perClass").contains(method.getName())) return false; //$NON-NLS-1$ //$NON-NLS-2$
		return Arrays.stream(method.getMethodDeclaration().getParameterTypes()).map(type -> type.getErasure().getQualifiedName())
				.toList().equals(List.of("java.util.function.Supplier", "org.junit.jupiter.api.function.ThrowingConsumer", "java.util.function.Consumer")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static boolean resourceAncestor(ITypeBinding type) {
		for (var current= type; current != null && !current.isRecovered(); current= current.getSuperclass()) {
			if (RESOURCE.equals(current.getQualifiedName())) return true;
		}
		return false;
	}

	private void addUse(String handle, FieldDeclaration field, ITypeBinding type, boolean classRule, boolean registered) {
		String fixture= familyKey(type);
		if (fixture != null) {
			uses.add(new Use(handle, fixture, field, classRule, registered));
			connect(handle, handles.get(fixture));
		}
	}

	private String familyKey(ITypeBinding type) {
		for (var current= type; current != null && !current.isRecovered(); current= current.getSuperclass()) {
			var parent= current.getSuperclass();
			if (parent != null && RESOURCE.equals(parent.getQualifiedName())) {
				String key= current.getTypeDeclaration().getKey();
				return handles.containsKey(key) ? key : null;
			}
		}
		return null;
	}

	private void connect(String left, String right) {
		if (right == null || left.equals(right)) return;
		graph.computeIfAbsent(left, ignored -> new LinkedHashSet<>()).add(right);
		graph.computeIfAbsent(right, ignored -> new LinkedHashSet<>()).add(left);
	}

	private Set<String> component(String first) {
		Set<String> visited= new LinkedHashSet<>();
		var pending= new ArrayDeque<String>();
		pending.add(first);
		while (!pending.isEmpty()) {
			String next= pending.removeFirst();
			if (visited.add(next)) pending.addAll(graph.getOrDefault(next, Set.of()));
		}
		return visited;
	}

	private Registration registration(Use use, ExternalResourceReferenceSearch references, IProgressMonitor monitor) throws CoreException {
		FieldDeclaration field= use.field();
		if (!(field.getParent() instanceof TypeDeclaration owner) || !eligibleRule(owner, field, use.classRule(), roots.keySet())) return null;
		var variable= (VariableDeclarationFragment) field.fragments().getFirst();
		var binding= variable.resolveBinding();
		if (!closedOwner(owner.resolveBinding(), binding, references, monitor)) return null;
		String fieldName= variable.getName().getIdentifier();
		String registrationName= fresh(fieldName + "Extension"); //$NON-NLS-1$
		String parameter= fresh("receiver"); //$NON-NLS-1$
		if (parameter.equals(registrationName)) parameter= fresh(parameter + "Extension"); //$NON-NLS-1$
		var order= methodOrder(owner, "org.junit.Test"); //$NON-NLS-1$
		var registration= new Registration(binding.getKey(), (use.classRule() ? owner.getName().getIdentifier() : "this") + "." + fieldName, //$NON-NLS-1$ //$NON-NLS-2$
				registrationName, parameter, use.classRule(), order);
		var unit= (ICompilationUnit) JavaCore.create(use.handle());
		return resolvesRegistration(unit, owner, field, registration, roots.keySet()) ? registration : null;
	}

	private static boolean standaloneOwner(TypeDeclaration owner) {
		return owner.getParent() instanceof CompilationUnit unit && unit.types().size() == 1 && owner.getTypes().length == 0
				&& owner.resolveBinding() != null && !owner.resolveBinding().isRecovered() && owner.getSuperclassType() == null
				&& owner.superInterfaceTypes().isEmpty() && Modifier.isPublic(owner.getModifiers()) && !Modifier.isAbstract(owner.getModifiers());
	}

	private static boolean eligibleRule(TypeDeclaration owner, FieldDeclaration field, boolean classRule, Set<String> selected) {
		if (!standaloneOwner(owner) || field.fragments().size() != 1 || !Modifier.isPublic(field.getModifiers())
				|| Modifier.isStatic(field.getModifiers()) != classRule || annotation(field.modifiers(), classRule ? CLASS_RULE : RULE) == null) return false;
		var variable= ((VariableDeclarationFragment) field.fragments().getFirst()).resolveBinding();
		return variable != null && !variable.isRecovered() && variable.getType().getJavaElement() instanceof IType type
				&& type.getCompilationUnit() != null && resourceAncestor(variable.getType()) && simpleContract(owner, field, false)
				&& currentValues(owner, variable.getVariableDeclaration().getKey(), selected);
	}

	private static List<String> methodOrder(TypeDeclaration owner, String annotationName) {
		// JUnit 4 MethodSorter.DEFAULT uses signed name hashes, then names. All supported tests are parameterless.
		var names= Arrays.stream(owner.getMethods()).filter(method -> annotation(method.modifiers(), annotationName) != null)
				.map(method -> method.getName().getIdentifier())
				.sorted(java.util.Comparator.comparingInt(String::hashCode).thenComparing(String::compareTo)).toList();
		return names.size() > 1 ? names : List.of();
	}

	private static boolean priorMethodOrder(TypeDeclaration owner) {
		var names= methodOrder(owner, "org.junit.jupiter.api.Test"); //$NON-NLS-1$
		var annotation= annotation(owner.modifiers(), "org.junit.jupiter.api.TestMethodOrder"); //$NON-NLS-1$
		if (names.isEmpty()) return annotation == null && Arrays.stream(owner.getMethods())
				.noneMatch(method -> annotation(method.modifiers(), "org.junit.jupiter.api.Order") != null); //$NON-NLS-1$
		if (!(annotation instanceof SingleMemberAnnotation orderer) || !(orderer.getValue() instanceof TypeLiteral sorter)
				|| sorter.getType().resolveBinding() == null || !"org.junit.jupiter.api.MethodOrderer.OrderAnnotation" //$NON-NLS-1$
						.equals(sorter.getType().resolveBinding().getQualifiedName())) return false;
		for (var method : owner.getMethods()) {
			if (annotation(method.modifiers(), "org.junit.jupiter.api.Test") == null) continue; //$NON-NLS-1$
			int expected= names.indexOf(method.getName().getIdentifier()) + 1;
			if (!(annotation(method.modifiers(), "org.junit.jupiter.api.Order") instanceof SingleMemberAnnotation rank) //$NON-NLS-1$
					|| !Integer.valueOf(expected).equals(rank.getValue().resolveConstantExpressionValue())) return false;
		}
		return true;
	}

	static boolean currentOwnerProof(ICompilationUnit unit, Registration registration, Set<String> selected,
			ExternalResourceReferenceSearch references) throws CoreException {
		var parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		var original= (CompilationUnit) parser.createAST(null);
		if (original.types().size() != 1 || !(original.types().getFirst() instanceof TypeDeclaration owner)
				|| !registration.orderedTests().equals(methodOrder(owner, "org.junit.Test"))) return false; //$NON-NLS-1$
		for (var field : owner.getFields()) {
			if (field.fragments().size() != 1) continue;
			var binding= ((VariableDeclarationFragment) field.fragments().getFirst()).resolveBinding();
			if (binding != null && registration.fieldKey().equals(binding.getVariableDeclaration().getKey())) {
				return eligibleRule(owner, field, registration.classRule(), selected)
						&& closedOwner(owner.resolveBinding(), binding, references, null)
						&& resolvesRegistration(unit, owner, field, registration, selected);
			}
		}
		return false;
	}

	// A generated name must not capture existing uses, including statically imported fields.
	private String fresh(String candidate) {
		while (identifiers.contains(candidate)) candidate+= "Extension"; //$NON-NLS-1$
		return candidate;
	}

	private static boolean closedOwner(ITypeBinding owner, IVariableBinding binding, ExternalResourceReferenceSearch references,
			IProgressMonitor monitor) throws CoreException {
		return owner != null && owner.getJavaElement() instanceof IType javaType
				&& binding.getJavaElement() instanceof IField modelField
				&& javaType.newTypeHierarchy(monitor).getAllSubtypes(javaType).length == 0
				&& (Modifier.isFinal(binding.getModifiers()) || new ExternalResourceReferenceSearch(List.of(javaType.getJavaProject()),
						List.of(javaType.getCompilationUnit())).allWritesSelected(modelField, monitor))
				&& references.allDiscoveryReferencesSelected(javaType, monitor);
	}

	static boolean platformDiscoveryReference(ASTNode reference) {
		for (var node= reference; node != null; node= node.getParent()) {
			if (node instanceof Javadoc || node instanceof ImportDeclaration) return true;
			// A previous static companion names its own class solely to qualify the original field.
			if (node instanceof LambdaExpression supplier && supplier.getParent() instanceof MethodInvocation factory
					&& factory.arguments().size() == 3 && factory.arguments().getFirst() == supplier
					&& deferredFactory(factory.resolveMethodBinding()) && supplierField(factory) instanceof IVariableBinding field
					&& Modifier.isStatic(field.getModifiers())) return true;
			if (node instanceof TypeLiteral) {
				var parent= node.getParent();
				if (parent instanceof ArrayInitializer) parent= parent.getParent();
				if (parent instanceof MemberValuePair pair && "value".equals(pair.getName().getIdentifier())) parent= parent.getParent(); //$NON-NLS-1$
				if (!(parent instanceof Annotation select) || !(select.getParent() instanceof TypeDeclaration suite)) return false;
				return suite.getSuperclassType() == null && suite.superInterfaceTypes().isEmpty() && suite.bodyDeclarations().isEmpty()
						&& annotation(suite.modifiers(), "org.junit.platform.suite.api.Suite") != null //$NON-NLS-1$
						&& annotation(suite.modifiers(), "org.junit.platform.suite.api.SelectClasses") == select //$NON-NLS-1$
						&& suite.modifiers().stream().filter(Annotation.class::isInstance).count() == 2;
			}
		}
		return false;
	}

	private static boolean simpleContract(TypeDeclaration owner, FieldDeclaration selected, boolean converted) {
		boolean[] allowed= { true };
		int[] rules= { 0 }, tests= { 0 };
		owner.accept(new ASTVisitor() {
			@Override public void preVisit(ASTNode node) {
				if (node instanceof MethodDeclaration method && method.getParent() == owner) {
					if (method.isConstructor() && (!Modifier.isPublic(method.getModifiers()) || !method.parameters().isEmpty()))
						allowed[0]= false;
					if ("suite".equals(method.getName().getIdentifier()) && method.parameters().isEmpty()) allowed[0]= false; //$NON-NLS-1$
				}
				if (observesTestContract(node)) allowed[0]= false;
				if (!(node instanceof Annotation annotation)) return;
				var binding= annotation.resolveTypeBinding();
				if (binding == null || binding.isRecovered()) { allowed[0]= false; return; }
				String name= binding.getQualifiedName();
				if (converted ? name.equals(MixedResourcePlan.REGISTER) : name.equals(RULE) || name.equals(CLASS_RULE)) {
					rules[0]++;
					if (annotation.getParent() != selected) allowed[0]= false;
				} else if ((converted ? "org.junit.jupiter.api.Test" : "org.junit.Test").equals(name)) { //$NON-NLS-1$ //$NON-NLS-2$
					tests[0]++;
					if (!(annotation.getParent() instanceof MethodDeclaration method) || method.getParent() != owner
							|| !Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())
							|| method.resolveBinding() == null || !"void".equals(method.resolveBinding().getReturnType().getName()) //$NON-NLS-1$
							|| !method.parameters().isEmpty() || annotation.resolveAnnotationBinding().getDeclaredMemberValuePairs().length != 0)
						allowed[0]= false;
				} else if (converted && annotation.getParent() == owner && Set.of(MixedResourcePlan.TEST_INSTANCE,
						MixedResourcePlan.ISOLATED, "org.junit.jupiter.api.TestMethodOrder").contains(name)) { //$NON-NLS-1$
					// The registration and the explicit method-order proof check these values.
				} else if (converted && "org.junit.jupiter.api.Order".equals(name) && annotation.getParent() instanceof MethodDeclaration method //$NON-NLS-1$
						&& method.getParent() == owner && annotation(method.modifiers(), "org.junit.jupiter.api.Test") != null) { //$NON-NLS-1$
					// Checked against the precise execution rank.
				} else if (testAnnotation(binding, new HashSet<>())) {
					allowed[0]= false;
				}
			}
		});
		return allowed[0] && rules[0] == 1 && tests[0] != 0;
	}

	private static boolean observesTestContract(ASTNode node) {
		IMethodBinding call= switch (node) {
			case MethodInvocation invocation -> invocation.resolveMethodBinding();
			case SuperMethodInvocation invocation -> invocation.resolveMethodBinding();
			case MethodReference reference -> reference.resolveMethodBinding();
			case ClassInstanceCreation creation -> creation.resolveConstructorBinding();
			case ConstructorInvocation invocation -> invocation.resolveConstructorBinding();
			case SuperConstructorInvocation invocation -> invocation.resolveConstructorBinding();
			default -> null;
		};
		if (call == null) return false;
		for (var type= call.getDeclaringClass(); type != null; type= type.getSuperclass()) {
			String name= type.getQualifiedName();
			if (name.startsWith("org.junit.runner.") || name.startsWith("org.junit.runners.") || name.startsWith("junit.framework.") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					|| name.startsWith("java.lang.reflect.") || name.equals("java.lang.Class") //$NON-NLS-1$ //$NON-NLS-2$
					|| name.equals("java.lang.Object") && "getClass".equals(call.getName())) return true; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return false;
	}

	private static boolean testAnnotation(ITypeBinding binding, Set<String> seen) {
		if (binding == null || binding.isRecovered()) return true;
		String name= binding.getQualifiedName();
		if (name.startsWith("org.junit.")) return true; //$NON-NLS-1$
		if (name.startsWith("java.lang.") || !seen.add(name)) return false; //$NON-NLS-1$
		return Arrays.stream(binding.getAnnotations()).anyMatch(meta -> testAnnotation(meta.getAnnotationType(), seen));
	}

	private static IBinding referencedField(Expression expression) {
		while (expression instanceof ParenthesizedExpression parenthesized) expression= parenthesized.getExpression();
		if (expression instanceof Name name) return name.resolveBinding();
		if (expression instanceof FieldAccess access) return access.resolveFieldBinding();
		return expression instanceof SuperFieldAccess access ? access.resolveFieldBinding() : null;
	}

	private static boolean ordinaryValue(Expression expression, Set<String> selected) {
		if (expression == null || expression instanceof NullLiteral) return true;
		if (!(expression instanceof ClassInstanceCreation creation) || creation.getAnonymousClassDeclaration() != null) return false;
		for (var type= creation.resolveTypeBinding(); type != null && !type.isRecovered(); type= type.getSuperclass()) {
			if (RESOURCE.equals(type.getQualifiedName())) return true;
			if (!(type.getTypeDeclaration().getJavaElement() instanceof IType source) || source.getCompilationUnit() == null
					|| !selected.contains(source.getCompilationUnit().getPrimary().getHandleIdentifier())) return false;
			if (Arrays.stream(type.getDeclaredMethods()).anyMatch(method -> "apply".equals(method.getName()))) return false; //$NON-NLS-1$
		}
		return false;
	}

	private static boolean resolvesRegistration(ICompilationUnit unit, TypeDeclaration owner, FieldDeclaration field,
			Registration registration, Set<String> selected) throws JavaModelException {
		int classStart= owner.getStartPosition();
		int offset= field.getStartPosition() + field.getLength();
		String source= unit.getSource();
		if (classStart < 0 || offset < classStart || offset > source.length()) return false;
		String generated= "\n" + registration.declaration() + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setProject(unit.getJavaProject());
		parser.setUnitName(unit.getPath().toString());
		String annotations= "@" + MixedResourcePlan.ISOLATED + " @" + MixedResourcePlan.TEST_INSTANCE //$NON-NLS-1$ //$NON-NLS-2$
				+ "(" + MixedResourcePlan.PER_METHOD + ") "; //$NON-NLS-1$ //$NON-NLS-2$
		if (!registration.orderedTests().isEmpty()) annotations+= Registration.METHOD_ORDER + " "; //$NON-NLS-1$
		// Insert after Javadoc so it remains attached to the original public declaration.
		if (owner.getJavadoc() != null) classStart= owner.getJavadoc().getStartPosition() + owner.getJavadoc().getLength();
		parser.setSource((source.substring(0, classStart) + annotations + source.substring(classStart, offset)
				+ generated + source.substring(offset)).toCharArray());
		parser.setResolveBindings(true);
		var probe= (CompilationUnit) parser.createAST(null);
		if (Arrays.stream(probe.getProblems()).anyMatch(problem -> problem.isError())) return false;
		var inserted= NodeFinder.perform(probe, offset + annotations.length() + 1, generated.length() - 2);
		if (!(inserted instanceof FieldDeclaration candidate) || !(candidate.getParent() instanceof TypeDeclaration parent)
				|| !parent.resolveBinding().getQualifiedName().equals(owner.resolveBinding().getQualifiedName())) return false;
		var variable= (VariableDeclarationFragment) candidate.fragments().getFirst();
		if (!(variable.getInitializer() instanceof MethodInvocation invocation)) return false;
		var factory= invocation.resolveMethodBinding();
		return deferredFactory(factory)
				&& annotation(candidate.modifiers(), MixedResourcePlan.REGISTER) != null
				&& supplierField(invocation) instanceof IVariableBinding supplied
				&& registration.fieldKey().equals(supplied.getVariableDeclaration().getKey())
				&& currentValues(parent, registration.fieldKey(), selected)
				&& resolvesHook(invocation.arguments().get(1), "before") && resolvesHook(invocation.arguments().get(2), "after"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static IBinding supplierField(MethodInvocation factory) {
		return factory.arguments().getFirst() instanceof LambdaExpression supplier && supplier.getBody() instanceof Expression expression
				? referencedField(expression) : null;
	}

	private static boolean currentValues(TypeDeclaration owner, String fieldKey, Set<String> selected) {
		boolean[] ordinary= { true };
		owner.accept(new ASTVisitor() {
			@Override public boolean visit(VariableDeclarationFragment fragment) {
				if (fragment.resolveBinding() != null && fieldKey.equals(fragment.resolveBinding().getKey())) {
					ordinary[0] &= ordinaryValue(fragment.getInitializer(), selected);
				}
				return true;
			}
			@Override public boolean visit(Assignment assignment) {
				if (referencedField(assignment.getLeftHandSide()) instanceof IVariableBinding variable
						&& fieldKey.equals(variable.getVariableDeclaration().getKey())) {
					ordinary[0] &= ordinaryValue(assignment.getRightHandSide(), selected);
				}
				return true;
			}
		});
		return ordinary[0];
	}

	private static boolean resolvesHook(Object callback, String name) {
		if (!(callback instanceof LambdaExpression lambda) || lambda.parameters().size() != 1
				|| !(lambda.parameters().getFirst() instanceof VariableDeclaration parameter)
				|| !(lambda.getBody() instanceof MethodInvocation call) || !(call.getExpression() instanceof SimpleName receiver)
				|| parameter.resolveBinding() == null || !parameter.resolveBinding().isEqualTo(receiver.resolveBinding())) return false;
		var method= call.resolveMethodBinding();
		if (method == null || method.isRecovered() || method.getParameterTypes().length != 0) return false;
		for (var current= method.getDeclaringClass(); current != null; current= current.getSuperclass()) {
			if (RESOURCE.equals(current.getQualifiedName())) {
				return Arrays.stream(current.getDeclaredMethods()).anyMatch(hook -> name.equals(hook.getName())
						&& hook.getParameterTypes().length == 0 && (method.isEqualTo(hook) || method.overrides(hook)));
			}
		}
		return false;
	}

	private static Annotation annotation(List<?> modifiers, String name) {
		for (Object item : modifiers) {
			if (item instanceof Annotation annotation && annotation.resolveTypeBinding() != null
					&& name.equals(annotation.resolveTypeBinding().getQualifiedName())) return annotation;
		}
		return null;
	}
}
