/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.core.cleanupapp;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MemberRef;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

/** Captures the planner's atomic authorizations and dependencies in both versions of the actual JDT preview. */
final class MigrationBatchDependencies {
	record Group(String reason, List<String> paths) {}

	/** The host calls this last in the same precondition pass, before JDT releases the plans. */
	static final class CandidateObserver extends AbstractCleanUp {
		private final ICleanUp[] cleanups;
		private final List<Group> groups= new ArrayList<>();

		CandidateObserver(ICleanUp[] cleanups) {
			this.cleanups= cleanups.clone();
		}

		@Override
		public RefactoringStatus checkPreConditions(IJavaProject project, ICompilationUnit[] units, IProgressMonitor monitor)
				throws CoreException {
			groups.clear();
			for (ICleanUp cleanup : cleanups) {
				try {
					var capability= cleanup.getClass().getMethod("getCoordinatedCleanUpPreview", IJavaProject.class); //$NON-NLS-1$
					var candidates= (Collection<?>) capability.invoke(cleanup, project);
					for (Object object : candidates) {
						var candidate= (Map<?, ?>) object;
						var participating= (Collection<?>) candidate.get("compilationUnits"); //$NON-NLS-1$
						groups.add(new Group(String.valueOf(candidate.get("id")), participating.stream() //$NON-NLS-1$
								.map(ICompilationUnit.class::cast).map(MigrationBatchDependencies::path).toList()));
					}
				} catch (NoSuchMethodException ignored) {
					// Ordinary JDT cleanups have no coordinated candidate metadata.
				} catch (InvocationTargetException | IllegalAccessException | RuntimeException e) {
					throw new CoreException(Status.error("Cannot inspect coordinated migration candidates", e)); //$NON-NLS-1$
				}
			}
			return new RefactoringStatus();
		}

		List<Group> groups() { return List.copyOf(groups); }
	}

	private enum ReferenceKind { SYMBOL, FUNCTIONAL_TARGET, ANNOTATION_MEMBER, ENUM_CONSTANT, INHERITED_DEFAULT }
	private record Site(String path, int offset, int nodeType, ReferenceKind kind, String memberName) {}
	private record Declaration(String path, String name, String shape) {}
	private record Use(String from, String key, String name, String unresolvedName) {}
	private static final class Model {
		final Map<String, Declaration> declarations= new HashMap<>();
		final Map<Site, String> witnesses= new HashMap<>();
		final Set<Use> uses= new HashSet<>();
		final Set<Group> mandatory= new HashSet<>();
		final Set<String> unresolvedDeclarations= new HashSet<>();
	}

	static List<Group> discover(IJavaProject project, List<MigrationBatches.SourceEdit> edits,
			List<ProjectWideCodeCleanupApplication.SourceSnapshot> sources, IProgressMonitor monitor) throws CoreException {
		if (edits.size() < 2) {
			return List.of();
		}
		Model before= parse(project, edits, false, List.of(), null, monitor);
		Model after= parse(project, edits, true, List.of(), null, monitor);
		var changed= new HashSet<>(before.declarations.keySet());
		changed.addAll(after.declarations.keySet());
		changed.removeIf(key -> java.util.Objects.equals(before.declarations.get(key), after.declarations.get(key)));
		var result= new HashSet<>(before.mandatory);
		result.addAll(after.mandatory);
		var unbound= new TreeSet<String>(before.unresolvedDeclarations);
		unbound.addAll(after.unresolvedDeclarations);
		if (!unbound.isEmpty()) {
			result.add(new Group("Unresolved declarations in " + unbound, edits.stream().map(MigrationBatches.SourceEdit::path).toList())); //$NON-NLS-1$
		}
		for (Model model : List.of(before, after)) {
			Map<String, List<Declaration>> byName= new HashMap<>();
			for (String key : changed) {
				var declaration= model.declarations.get(key);
				if (declaration != null) {
					byName.computeIfAbsent(declaration.name(), ignored -> new ArrayList<>()).add(declaration);
				}
			}
			for (Use use : model.uses) {
				if (use.unresolvedName() != null) {
					for (Declaration possible : byName.getOrDefault(use.unresolvedName(), List.of())) {
						link(result, use.from(), possible.path(), "unresolved reference to changed " + possible.name()); //$NON-NLS-1$
					}
				} else if (changed.contains(use.key())) {
					var target= model.declarations.get(use.key());
					if (target != null) {
						link(result, use.from(), target.path(), "reference to changed " + use.name()); //$NON-NLS-1$
					}
				}
			}
		}
		// A source can retain identical text while the same call binds to a
		// different changed declaration (for example a moved static import).
		Set<ICompilationUnit> edited= new HashSet<>();
		edits.forEach(edit -> edited.add(edit.source().unit()));
		var unchanged= sources.stream().map(ProjectWideCodeCleanupApplication.SourceSnapshot::unit)
				.filter(unit -> !edited.contains(unit)).toList();
		if (!changed.isEmpty() && !unchanged.isEmpty()) {
			var oldSites= parse(project, edits, false, unchanged, changed, monitor).witnesses;
			var newSites= parse(project, edits, true, unchanged, changed, monitor).witnesses;
			for (var entry : oldSites.entrySet()) {
				var oldTarget= before.declarations.get(entry.getValue());
				var newTarget= after.declarations.get(newSites.get(entry.getKey()));
				if (oldTarget != null && newTarget != null) {
					link(result, oldTarget.path(), newTarget.path(), "binding of " + oldTarget.name() + " in " + entry.getKey().path()); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
		}
		return List.copyOf(result);
	}

	private static Model parse(IJavaProject project, List<MigrationBatches.SourceEdit> edits, boolean proposed,
			List<ICompilationUnit> unchanged, Set<String> tracked, IProgressMonitor monitor) throws CoreException {
		WorkingCopyOwner owner= new WorkingCopyOwner() {};
		List<ICompilationUnit> copies= new ArrayList<>();
		try {
			for (var edit : edits) {
				if (monitor.isCanceled()) throw new OperationCanceledException();
				var copy= edit.source().unit().getWorkingCopy(owner, monitor);
				copies.add(copy);
				copy.getBuffer().setContents(proposed ? edit.afterText() : edit.beforeText());
			}
			Model model= new Model();
			Set<String> seen= new HashSet<>();
			ASTRequestor requestor= new ASTRequestor() {
				@Override public void acceptAST(ICompilationUnit unit, CompilationUnit ast) {
					String from= path(unit);
					seen.add(from);
					ast.accept(new DependencyVisitor(model, from, tracked));
				}
			};
			Map<Map<String, String>, List<ICompilationUnit>> optionGroups= new HashMap<>();
			var targets= tracked == null ? copies : unchanged;
			for (ICompilationUnit copy : targets) {
				optionGroups.computeIfAbsent(copy.getPrimary().getCustomOptions(), key -> new ArrayList<>()).add(copy);
			}
			for (var group : optionGroups.entrySet()) {
				ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
				parser.setProject(project);
				var options= new HashMap<>(project.getOptions(true));
				options.putAll(group.getKey());
				parser.setCompilerOptions(options);
				parser.setWorkingCopyOwner(owner);
				parser.setResolveBindings(true);
				parser.setBindingsRecovery(true);
				parser.createASTs(group.getValue().toArray(ICompilationUnit[]::new), new String[0], requestor, monitor);
			}
			if (seen.size() != targets.size()) throw new CoreException(Status.error("Incomplete JDT batch dependency analysis")); //$NON-NLS-1$
			return model;
		} finally {
			for (ICompilationUnit copy : copies) copy.discardWorkingCopy();
		}
	}

	private static final class DependencyVisitor extends ASTVisitor {
		private static final Set<String> NON_SIGNATURE_PROPERTIES= Set.of("javadoc", "body", "bodyDeclarations", "enumConstants"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		private final Model model;
		private final String from;
		private final Set<String> tracked;
		private boolean inJavadoc;
		private final List<ImportDeclaration> staticImports= new ArrayList<>();

		DependencyVisitor(Model model, String from, Set<String> tracked) {
			super(true); // Include resolved Javadoc and Markdown reference nodes.
			this.tracked= tracked;
			this.model= model;
			this.from= from;
		}

		@Override public void preVisit(ASTNode node) {
			if (node instanceof ImportDeclaration declaration && declaration.isStatic()) {
				staticImports.add(declaration);
			} else if (node instanceof Javadoc) {
				inJavadoc= true;
			} else if (node instanceof MethodRef reference) {
				use(node, reference.resolveBinding(), null, ReferenceKind.SYMBOL);
			} else if (node instanceof MemberRef reference) {
				use(node, reference.resolveBinding(), null, ReferenceKind.SYMBOL);
			} else if (node instanceof Annotation annotation) {
				var binding= annotation.resolveAnnotationBinding();
				if (binding != null) {
					use(node, binding.getAnnotationType(), null, ReferenceKind.SYMBOL);
					for (var pair : binding.getAllMemberValuePairs()) {
						if (pair.isDefault() || annotation instanceof SingleMemberAnnotation) {
							use(node, pair.getMethodBinding(), null, ReferenceKind.ANNOTATION_MEMBER);
						}
					}
				}
			} else if (node instanceof MemberValuePair pair) {
				var binding= pair.resolveMemberValuePairBinding();
				if (binding != null) use(node, binding.getMethodBinding(), null, ReferenceKind.ANNOTATION_MEMBER);
			} else if (node instanceof ClassInstanceCreation creation) {
				use(node, creation.resolveConstructorBinding(), null, ReferenceKind.SYMBOL);
			} else if (node instanceof ConstructorInvocation invocation) {
				use(node, invocation.resolveConstructorBinding(), null, ReferenceKind.SYMBOL);
			} else if (node instanceof SuperConstructorInvocation invocation) {
				use(node, invocation.resolveConstructorBinding(), null, ReferenceKind.SYMBOL);
			} else if (node instanceof MethodReference reference) {
				use(node, reference.resolveMethodBinding(), null, ReferenceKind.SYMBOL);
				functionalTarget(node, reference.resolveTypeBinding());
			} else if (node instanceof LambdaExpression lambda) {
				functionalTarget(node, lambda.resolveTypeBinding());
			} else if (node instanceof SwitchExpression expression) {
				enumSwitch(expression.getExpression());
			} else if (node instanceof SwitchStatement statement) {
				enumSwitch(statement.getExpression());
			} else if (node instanceof EnumConstantDeclaration constant) {
				use(node, constant.resolveConstructorBinding(), null, ReferenceKind.SYMBOL);
			}
			if (tracked != null) {
				if (node instanceof AbstractTypeDeclaration type) inheritedDefaults(node, type.resolveBinding(), new HashSet<>());
				if (node instanceof AnonymousClassDeclaration type) inheritedDefaults(node, type.resolveBinding(), new HashSet<>());
				return;
			}
			if (node instanceof AbstractTypeDeclaration type) {
				ITypeBinding binding= type.resolveBinding();
				declare(binding, type);
				if (binding != null) {
					ancestors(binding, new HashSet<>());
					for (var method : binding.getDeclaredMethods()) {
						if (method.isDefaultConstructor()) {
							model.declarations.put(method.getKey(), new Declaration(from, method.getName(), "implicit default constructor")); //$NON-NLS-1$
						}
					}
				}
			} else if (node instanceof AnonymousClassDeclaration type) {
				// The allocation resolves to the anonymous synthetic constructor.
				// Its resolved hierarchy identifies the actual superclass source.
				var binding= type.resolveBinding();
				if (binding != null) ancestors(binding, new HashSet<>());
			} else if (node instanceof MethodDeclaration method) {
				declare(method.resolveBinding(), method);
			} else if (node instanceof AnnotationTypeMemberDeclaration member) {
				declare(member.resolveBinding(), member);
			} else if (node instanceof VariableDeclarationFragment fragment && fragment.getParent() instanceof FieldDeclaration field) {
				declare(fragment.resolveBinding(), field);
			} else if (node instanceof EnumConstantDeclaration constant) {
				declare(constant.resolveVariable(), constant);
			} else if (node instanceof TypeLiteral literal && selectsTestType(literal)) {
				// Suite members and runners can change execution contract without a type-signature change.
				ITypeBinding type= literal.getType().resolveBinding();
				if (type != null) link(model.mandatory, from, sourcePath(type), "class literal " + type.getName()); //$NON-NLS-1$
			}
		}

		@Override public void endVisit(Javadoc comment) {
			inJavadoc= false;
		}

		private static boolean selectsTestType(TypeLiteral literal) {
			for (var parent= literal.getParent(); parent != null && !(parent instanceof BodyDeclaration); parent= parent.getParent()) {
				if (parent instanceof Annotation annotation) {
					var type= annotation.resolveTypeBinding();
					return type != null && Set.of("org.junit.runner.RunWith", "org.junit.runners.Suite.SuiteClasses", //$NON-NLS-1$ //$NON-NLS-2$
							"org.junit.platform.suite.api.SelectClasses").contains(type.getQualifiedName()); //$NON-NLS-1$
				}
			}
			return false;
		}

		private void inheritedDefaults(ASTNode site, ITypeBinding type, Set<String> seen) {
			if (type == null || !seen.add(type.getKey())) return;
			for (var method : type.getDeclaredMethods()) {
				var key= method.getMethodDeclaration().getKey();
				if (Modifier.isDefault(method.getModifiers()) && tracked.contains(key)) {
					var signature= method.getName() + Arrays.stream(method.getParameterTypes()).map(p -> p.getErasure().getKey()).toList();
					model.witnesses.put(new Site(from, site.getStartPosition(), site.getNodeType(), ReferenceKind.INHERITED_DEFAULT, signature), key);
				}
			}
			for (var parent : type.getInterfaces()) inheritedDefaults(site, parent, seen);
			inheritedDefaults(site, type.getSuperclass(), seen);
		}

		private void enumSwitch(Expression selector) {
			var type= selector.resolveTypeBinding();
			if (type == null || !type.isEnum()) return;
			// Exhaustiveness depends on constants that the switch never names.
			for (var field : type.getDeclaredFields()) {
				if (field.isEnumConstant()) use(selector, field, null, ReferenceKind.ENUM_CONSTANT);
			}
		}

		private void functionalTarget(ASTNode site, ITypeBinding target) {
			if (target != null) use(site, target.getFunctionalInterfaceMethod(), null, ReferenceKind.FUNCTIONAL_TARGET);
		}

		private void declare(IBinding binding, BodyDeclaration declaration) {
			if (binding == null || binding.isRecovered()) {
				model.unresolvedDeclarations.add(from);
				return;
			}
			var signature= new ArrayList<String>();
			for (Object value : declaration.structuralPropertiesForType()) {
				var property= (StructuralPropertyDescriptor) value;
				if (!NON_SIGNATURE_PROPERTIES.contains(property.getId())) {
					signature.add(property.getId() + "=" + declaration.getStructuralProperty(property)); //$NON-NLS-1$
				}
			}
			// A newly required element also changes the annotation type contract
			// at existing marker usages which have no previous member binding.
			if (binding instanceof ITypeBinding type && type.isAnnotation()) {
				var required= new TreeSet<String>();
				for (var member : type.getDeclaredMethods()) {
					if (member.getDefaultValue() == null) required.add(member.getName() + ":" + member.getReturnType().getKey()); //$NON-NLS-1$
				}
				signature.add("required=" + required); //$NON-NLS-1$
			}
			for (var annotation : binding.getAnnotations()) {
				signature.add(annotation.getAnnotationType().getQualifiedName());
			}
			model.declarations.put(binding.getKey(), new Declaration(from, binding.getName(), signature.toString()));
		}

		private void ancestors(ITypeBinding binding, Set<String> visited) {
			if (!visited.add(binding.getKey())) return;
			List<ITypeBinding> parents= new ArrayList<>(List.of(binding.getInterfaces()));
			if (binding.getSuperclass() != null) parents.add(binding.getSuperclass());
			for (ITypeBinding parent : parents) {
				String path= sourcePath(parent);
				if (path != null) {
					link(model.mandatory, from, path, "test/type hierarchy through " + parent.getName()); //$NON-NLS-1$
				}
				ancestors(parent, visited);
			}
		}

		@Override public boolean visit(SimpleName name) {
			if (!name.isDeclaration()) use(name, name.resolveBinding(), inJavadoc ? null : name.getIdentifier(), ReferenceKind.SYMBOL);
			return true;
		}

		private void overloads(ITypeBinding owner, IMethodBinding method, boolean staticOnly) {
			if (owner == null || !owner.isFromSource()) return;
			for (var peer : owner.getDeclaredMethods()) {
				if ((!staticOnly || Modifier.isStatic(peer.getModifiers())) && peer.getName().equals(method.getName()) && peer.isConstructor() == method.isConstructor())
					model.uses.add(new Use(from, peer.getMethodDeclaration().getKey(), peer.getName(), null));
			}
		}

		private void visibleOverloads(ITypeBinding type, IMethodBinding method, boolean staticOnly) {
			if (type == null) return;
			overloads(type, method, staticOnly);
			for (var parent : Bindings.getAllSuperTypes(type)) overloads(parent, method, staticOnly);
		}

		private void invocationOverloads(MethodInvocation call, IMethodBinding method) {
			var receiver= call.getExpression() == null ? ASTNodes.getEnclosingType(call) : call.getExpression().resolveTypeBinding();
			visibleOverloads(receiver, method, false);
			if (call.getExpression() != null || !Modifier.isStatic(method.getModifiers())
					|| receiver != null && Bindings.isSuperType(method.getDeclaringClass(), receiver)) return;
			for (var declaration : staticImports) {
				var name= declaration.getName();
				if (declaration.isOnDemand()) {
					visibleOverloads(name.resolveTypeBinding(), method, true);
				} else if (name instanceof QualifiedName single && single.getName().getIdentifier().equals(method.getName())) {
					visibleOverloads(single.getQualifier().resolveTypeBinding(), method, true);
				}
			}
		}

		private void use(ASTNode site, IBinding binding, String fallback, ReferenceKind kind) {
			if (binding == null || binding.isRecovered()) {
				if (tracked == null && fallback != null) model.uses.add(new Use(from, null, null, fallback));
				return;
			}
			if (binding instanceof IMethodBinding method) {
				binding= method.getMethodDeclaration();
				if (tracked == null) {
					overloads(method.getDeclaringClass(), method, false);
					if (site.getParent() instanceof MethodInvocation call && call.getName() == site) invocationOverloads(call, method);
				}
			}
			if (binding instanceof ITypeBinding type) binding= type.getTypeDeclaration();
			if (binding instanceof IVariableBinding variable) binding= variable.getVariableDeclaration();
			if (tracked == null) {
				model.uses.add(new Use(from, binding.getKey(), binding.getName(), null));
			} else if (tracked.contains(binding.getKey())) {
				String member= kind == ReferenceKind.ANNOTATION_MEMBER || kind == ReferenceKind.ENUM_CONSTANT ? binding.getName() : ""; //$NON-NLS-1$
				model.witnesses.put(new Site(from, site.getStartPosition(), site.getNodeType(), kind, member), binding.getKey());
			}
		}
	}

	private static String sourcePath(ITypeBinding type) {
		IJavaElement element= type.getTypeDeclaration().getJavaElement();
		if (element != null && element.getAncestor(IJavaElement.COMPILATION_UNIT) instanceof ICompilationUnit unit) {
			return path(unit);
		}
		return null;
	}

	private static String path(ICompilationUnit unit) {
		return unit.getPrimary().getPath().toPortableString();
	}

	private static void link(Set<Group> groups, String from, String to, String reason) {
		if (to != null && !from.equals(to)) groups.add(new Group(reason, List.of(from, to)));
	}
}
