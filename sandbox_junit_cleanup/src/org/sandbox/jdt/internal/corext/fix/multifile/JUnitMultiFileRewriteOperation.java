/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ANNOTATION_REGISTER_EXTENSION;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_CLASS_RULE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.internal.corext.fix.helper.lib.ExternalResourceCompatibilityRefactorer;
import org.sandbox.jdt.internal.corext.fix.helper.lib.ExternalResourceRefactorer;

/** Applies the local part of a coordinated JUnit migration plan. */
final class JUnitMultiFileRewriteOperation extends CompilationUnitRewriteOperationWithSourceRange {

	private static final String ANNOTATION_ISOLATED= "Isolated"; //$NON-NLS-1$
	private static final String ORG_JUNIT_JUPITER_API_PARALLEL_ISOLATED=
			"org.junit.jupiter.api.parallel.Isolated"; //$NON-NLS-1$

	record FieldEdit(String bindingKey, boolean classRule, Annotation ruleAnnotation) {
	}

	record ResourceTypeEdit(String bindingKey, boolean classRule, boolean preserveJUnit4Compatibility) {
	}

	record ResolvedEdits(CompilationUnit root, Map<FieldDeclaration, FieldEdit> fields,
			Map<TypeDeclaration, ResourceTypeEdit> resourceTypes, Set<String> fieldKeys, Set<String> typeKeys) {

		static Builder builder(CompilationUnit root) {
			return new Builder(root);
		}

		static final class Builder {
			private final CompilationUnit root;
			private final Map<FieldDeclaration, FieldEdit> fields= new LinkedHashMap<>();
			private final Map<TypeDeclaration, ResourceTypeEdit> resourceTypes= new LinkedHashMap<>();
			private final Set<String> fieldKeys= new LinkedHashSet<>();
			private final Set<String> typeKeys= new LinkedHashSet<>();

			Builder(CompilationUnit root) {
				this.root= root;
			}

			void addField(FieldDeclaration field, String bindingKey, boolean classRule, Annotation ruleAnnotation) {
				if (ruleAnnotation != null) {
					fields.put(field, new FieldEdit(bindingKey, classRule, ruleAnnotation));
					fieldKeys.add(bindingKey);
				}
			}

			void addResourceType(TypeDeclaration type, String bindingKey, boolean classRule,
					boolean preserveJUnit4Compatibility) {
				resourceTypes.put(type,
						new ResourceTypeEdit(bindingKey, classRule, preserveJUnit4Compatibility));
				typeKeys.add(bindingKey);
			}

			ResolvedEdits build() {
				return new ResolvedEdits(root, Map.copyOf(fields), Map.copyOf(resourceTypes), Set.copyOf(fieldKeys),
						Set.copyOf(typeKeys));
			}
		}
	}

	private final ResolvedEdits edits;
	private final Set<String> compatibleResourceTypes;

	JUnitMultiFileRewriteOperation(ResolvedEdits edits, Set<String> compatibleResourceTypes) {
		this.edits= edits;
		this.compatibleResourceTypes= Set.copyOf(compatibleResourceTypes);
	}

	@Override
	public void rewriteASTInternal(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel)
			throws CoreException {
		TextEditGroup group= createTextEditGroup("Migrate named ExternalResource and its rule fields", cuRewrite); //$NON-NLS-1$
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ImportRewrite imports= cuRewrite.getImportRewrite();
		Set<TypeDeclaration> isolatedTypes= new LinkedHashSet<>();

		for (Map.Entry<FieldDeclaration, FieldEdit> entry : edits.fields().entrySet()) {
			rewriteResourceFieldType(entry.getKey(), cuRewrite, group);
			rewriteRuleField(entry.getKey(), entry.getValue(), rewrite, ast, imports, group, isolatedTypes);
		}
		removeUnusedRuleImports(imports);

		for (Map.Entry<TypeDeclaration, ResourceTypeEdit> entry : edits.resourceTypes().entrySet()) {
			ResourceTypeEdit edit= entry.getValue();
			if (edit.preserveJUnit4Compatibility()) {
				ExternalResourceCompatibilityRefactorer.addJupiterCallbacks(entry.getKey(), edit.classRule(),
						rewrite, ast, group, imports);
			} else {
				ExternalResourceRefactorer.modifyExternalResourceClass(entry.getKey(), null, edit.classRule(),
						rewrite, ast, group, imports);
			}
		}
	}

	/** Keep the field assignable when the coordinated plan removes its JUnit 4 base. */
	private void rewriteResourceFieldType(FieldDeclaration field, CompilationUnitRewrite cuRewrite,
			TextEditGroup group) throws CoreException {
		ITypeBinding declared= field.getType().resolveBinding();
		if (declared == null || declared.isRecovered()
				|| !(ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(declared.getErasure().getQualifiedName())
						|| "org.junit.rules.TestRule".equals(declared.getErasure().getQualifiedName()))) { //$NON-NLS-1$
			return;
		}
		if (field.fragments().size() != 1
				|| !(field.fragments().get(0) instanceof VariableDeclarationFragment fragment)
				|| !(fragment.getInitializer() instanceof ClassInstanceCreation creation)) {
			throw new CoreException(new Status(IStatus.ERROR, "sandbox_junit_cleanup", //$NON-NLS-1$
					"Cannot resolve the planned ExternalResource field initializer")); //$NON-NLS-1$
		}
		ITypeBinding resource= creation.resolveTypeBinding();
		if (resource == null || resource.isRecovered() || resource.isAnonymous()) {
			throw new CoreException(new Status(IStatus.ERROR, "sandbox_junit_cleanup", //$NON-NLS-1$
					"Cannot resolve the planned named ExternalResource type")); //$NON-NLS-1$
		}
		for (ITypeBinding type= resource; type != null; type= type.getSuperclass()) {
			String key= JUnitMigrationPlan.typeKey(type);
			if (key != null && compatibleResourceTypes.contains(key)) {
				return; // This chain intentionally retains its JUnit 4 superclass.
			}
		}
		ImportRewrite imports= cuRewrite.getImportRewrite();
		var context= new ContextSensitiveImportRewriteContext(field.getType(), imports);
		// Use the inferred binding, not the constructor's syntax: a diamond is legal
		// in the initializer but not in a field type. Scope-aware imports also retain
		// qualification when another source type has the same simple name.
		var replacement= imports.addImport(resource, field.getAST(), context);
		cuRewrite.getASTRewrite().replace(field.getType(), replacement, group);
		cuRewrite.getImportRemover().registerRemovedNode(field.getType());
	}

	private void rewriteRuleField(FieldDeclaration field, FieldEdit edit, ASTRewrite rewrite, AST ast,
			ImportRewrite imports, TextEditGroup group, Set<TypeDeclaration> isolatedTypes) {
		rewrite.remove(edit.ruleAnnotation(), group);
		if (!hasRegisterExtension(field)) {
			String annotationName= imports.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION);
			MarkerAnnotation annotation= ast.newMarkerAnnotation();
			annotation.setTypeName(ast.newName(annotationName));
			ListRewrite modifiers= rewrite.getListRewrite(field, FieldDeclaration.MODIFIERS2_PROPERTY);
			modifiers.insertFirst(annotation, group);
		}
		isolateOwningTestType(field, rewrite, ast, imports, group, isolatedTypes);
	}

	private void isolateOwningTestType(FieldDeclaration field, ASTRewrite rewrite, AST ast,
			ImportRewrite imports, TextEditGroup group, Set<TypeDeclaration> isolatedTypes) {
		ASTNode current= field.getParent();
		while (current != null && !(current instanceof TypeDeclaration)) {
			current= current.getParent();
		}
		if (current == null) {
			return;
		}
		TypeDeclaration type= (TypeDeclaration) current;
		if (!isolatedTypes.add(type) || hasIsolated(type)) {
			return;
		}

		String annotationName= imports.addImport(ORG_JUNIT_JUPITER_API_PARALLEL_ISOLATED);
		MarkerAnnotation annotation= ast.newMarkerAnnotation();
		annotation.setTypeName(ast.newName(annotationName));
		ListRewrite modifiers= rewrite.getListRewrite(type, TypeDeclaration.MODIFIERS2_PROPERTY);
		modifiers.insertFirst(annotation, group);
	}

	private boolean hasRegisterExtension(FieldDeclaration field) {
		for (Object modifier : field.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				ITypeBinding binding= annotation.resolveTypeBinding();
				if (binding != null
						&& ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION.equals(binding.getQualifiedName())) {
					return true;
				}
				if (binding == null && ANNOTATION_REGISTER_EXTENSION.equals(annotation.getTypeName().getFullyQualifiedName())) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean hasIsolated(TypeDeclaration type) {
		for (Object modifier : type.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				String name= annotation.getTypeName().getFullyQualifiedName();
				if (ORG_JUNIT_JUPITER_API_PARALLEL_ISOLATED.equals(name)) {
					return true;
				}
				ITypeBinding binding= annotation.resolveTypeBinding();
				if (binding != null && ORG_JUNIT_JUPITER_API_PARALLEL_ISOLATED.equals(binding.getQualifiedName())) {
					return true;
				}
				if (ANNOTATION_ISOLATED.equals(name) && importsIsolated(type)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean importsIsolated(TypeDeclaration type) {
		if (!(type.getRoot() instanceof CompilationUnit root)) {
			return false;
		}
		for (Object element : root.imports()) {
			if (element instanceof ImportDeclaration declaration && !declaration.isStatic()
					&& !declaration.isOnDemand()
					&& ORG_JUNIT_JUPITER_API_PARALLEL_ISOLATED.equals(declaration.getName().getFullyQualifiedName())) {
				return true;
			}
		}
		return false;
	}

	private void removeUnusedRuleImports(ImportRewrite imports) {
		Set<Annotation> planned= edits.fields().values().stream().map(FieldEdit::ruleAnnotation)
				.collect(java.util.stream.Collectors.toSet());
		boolean[] remainingRule= { false };
		boolean[] remainingClassRule= { false };
		edits.root().accept(new ASTVisitor() {
			@Override
			public boolean visit(MarkerAnnotation node) {
				inspect(node);
				return true;
			}

			@Override
			public boolean visit(org.eclipse.jdt.core.dom.SingleMemberAnnotation node) {
				inspect(node);
				return true;
			}

			@Override
			public boolean visit(org.eclipse.jdt.core.dom.NormalAnnotation node) {
				inspect(node);
				return true;
			}

			private void inspect(Annotation annotation) {
				if (planned.contains(annotation)) {
					return;
				}
				ITypeBinding binding= annotation.resolveTypeBinding();
				if (binding == null) {
					return;
				}
				if (ORG_JUNIT_RULE.equals(binding.getQualifiedName())) {
					remainingRule[0]= true;
				} else if (ORG_JUNIT_CLASS_RULE.equals(binding.getQualifiedName())) {
					remainingClassRule[0]= true;
				}
			}
		});
		if (!remainingRule[0]) {
			imports.removeImport(ORG_JUNIT_RULE);
		}
		if (!remainingClassRule[0]) {
			imports.removeImport(ORG_JUNIT_CLASS_RULE);
		}
	}
}
