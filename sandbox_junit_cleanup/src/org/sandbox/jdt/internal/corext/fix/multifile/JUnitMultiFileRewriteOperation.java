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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.internal.corext.fix.helper.lib.ExternalResourceRefactorer;

/** Applies the local part of a coordinated JUnit migration plan. */
final class JUnitMultiFileRewriteOperation extends CompilationUnitRewriteOperationWithSourceRange {

	record FieldEdit(String bindingKey, boolean classRule, Annotation ruleAnnotation) {
	}

	record ResourceTypeEdit(String bindingKey, boolean classRule) {
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

			void addResourceType(TypeDeclaration type, String bindingKey, boolean classRule) {
				resourceTypes.put(type, new ResourceTypeEdit(bindingKey, classRule));
				typeKeys.add(bindingKey);
			}

			ResolvedEdits build() {
				return new ResolvedEdits(root, Map.copyOf(fields), Map.copyOf(resourceTypes), Set.copyOf(fieldKeys),
						Set.copyOf(typeKeys));
			}
		}
	}

	private final ResolvedEdits edits;

	JUnitMultiFileRewriteOperation(ResolvedEdits edits) {
		this.edits= edits;
	}

	@Override
	public void rewriteASTInternal(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel)
			throws CoreException {
		TextEditGroup group= createTextEditGroup("Migrate named ExternalResource and its rule fields", cuRewrite); //$NON-NLS-1$
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ImportRewrite imports= cuRewrite.getImportRewrite();

		for (Map.Entry<FieldDeclaration, FieldEdit> entry : edits.fields().entrySet()) {
			rewriteRuleField(entry.getKey(), entry.getValue(), rewrite, ast, imports, group);
		}
		removeUnusedRuleImports(imports);

		for (Map.Entry<TypeDeclaration, ResourceTypeEdit> entry : edits.resourceTypes().entrySet()) {
			ExternalResourceRefactorer.modifyExternalResourceClass(entry.getKey(), null, entry.getValue().classRule(),
					rewrite, ast, group, imports);
		}
	}

	private void rewriteRuleField(FieldDeclaration field, FieldEdit edit, ASTRewrite rewrite, AST ast,
			ImportRewrite imports, TextEditGroup group) {
		rewrite.remove(edit.ruleAnnotation(), group);
		if (!hasRegisterExtension(field)) {
			String annotationName= imports.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION);
			MarkerAnnotation annotation= ast.newMarkerAnnotation();
			annotation.setTypeName(ast.newName(annotationName));
			ListRewrite modifiers= rewrite.getListRewrite(field, FieldDeclaration.MODIFIERS2_PROPERTY);
			modifiers.insertFirst(annotation, group);
		}
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
