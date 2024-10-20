/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;

/**
 *
 * 
 */
public class RuleExternalResourceJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	private static final String ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK = "org.junit.jupiter.api.extension.BeforeEachCallback";
	private static final String ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK = "org.junit.jupiter.api.extension.AfterEachCallback";
	private static final String ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";
	private static final String ORG_JUNIT_RULES_EXTERNAL_RESOURCE = "org.junit.rules.ExternalResource";
	private static final String ORG_JUNIT_RULE = "org.junit.Rule";

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataholder = new ReferenceHolder<>();
		HelperVisitor.callFieldDeclarationVisitor(ORG_JUNIT_RULE, ORG_JUNIT_RULES_EXTERNAL_RESOURCE, compilationUnit, dataholder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, FieldDeclaration node,
			ReferenceHolder<Integer, JunitHolder> dataholder) {
		JunitHolder mh = new JunitHolder();
		VariableDeclarationFragment fragment = (VariableDeclarationFragment) node.fragments().get(0);
		ITypeBinding binding = fragment.resolveBinding().getType();
		if(binding == null) {
			return false;
		}
		if("org.junit.rules.TestName".equals(binding.getQualifiedName())) {
			return false;
		}
		if("org.junit.rules.TemporaryFolder".equals(binding.getQualifiedName())) {
			return false;
		}
		mh.minv = node;
		dataholder.put(dataholder.size(), mh);
		operations.add(fixcore.rewrite(dataholder));
		return false;
	}

	@Override
	public void rewrite(JUnitCleanUpFixCore upp, final ReferenceHolder<Integer, JunitHolder> hit,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		AST ast = cuRewrite.getRoot().getAST();
		ImportRewrite importrewriter = cuRewrite.getImportRewrite();
		for (Entry<Integer, JunitHolder> entry : hit.entrySet()) {
			JunitHolder mh = entry.getValue();
			FieldDeclaration minv = mh.getFieldDeclaration();
			for (Object modifier : minv.modifiers()) {
				if (modifier instanceof Annotation annotation) {
					process(annotation,cuRewrite.getRoot().getJavaElement().getJavaProject(),rewrite,ast,group,importrewriter,cuRewrite.getRoot());
				}
			}
		}
	}

	public void process(Annotation node,IJavaProject jproject,ASTRewrite rewrite,AST ast, TextEditGroup group, ImportRewrite importrewriter, CompilationUnit compilationUnit) {
		ITypeBinding annotationBinding = node.resolveTypeBinding();
		if (annotationBinding != null && annotationBinding.getQualifiedName().equals(ORG_JUNIT_RULE)) {
			ASTNode parent = node.getParent();
			if (parent instanceof FieldDeclaration) {
				FieldDeclaration field = (FieldDeclaration) parent;
				ITypeBinding fieldTypeBinding = ((VariableDeclarationFragment) field.fragments().get(0)).resolveBinding().getType();
				if (isExternalResource(fieldTypeBinding)) {
					if(!fieldTypeBinding.isAnonymous()) {
						rewrite.remove(field, group);
						ASTNode parentNode = field.getParent();
						while (parentNode != null && !(parentNode instanceof TypeDeclaration)) {
							parentNode = parentNode.getParent();
						}
						if (parentNode != null && parentNode instanceof TypeDeclaration) {
							TypeDeclaration parentClass = (TypeDeclaration) parentNode;
							SingleMemberAnnotation newAnnotation = ast.newSingleMemberAnnotation();
							newAnnotation.setTypeName(ast.newName("ExtendWith")); 
							final TypeLiteral newTypeLiteral = ast.newTypeLiteral();
							newTypeLiteral.setType(ast.newSimpleType(ast.newSimpleName(fieldTypeBinding.getName())));
							newAnnotation.setValue(newTypeLiteral);
							ListRewrite modifierListRewrite = rewrite.getListRewrite(parentClass, TypeDeclaration.MODIFIERS2_PROPERTY);
							modifierListRewrite.insertFirst(newAnnotation, group);
						}
						modifyExternalResourceClass(field, rewrite,ast, group);
						importrewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH);
						importrewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);
						importrewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK);
						importrewriter.removeImport(ORG_JUNIT_RULES_EXTERNAL_RESOURCE);
						importrewriter.removeImport(ORG_JUNIT_RULE);
					}
				}
			}
		}
	}

	private boolean isExternalResource(ITypeBinding typeBinding) {
		while (typeBinding != null) {
			if (typeBinding.getQualifiedName().equals(ORG_JUNIT_RULES_EXTERNAL_RESOURCE)) {
				return true;
			}
			typeBinding = typeBinding.getSuperclass();
		}
		return false;
	}

	private void modifyExternalResourceClass(FieldDeclaration field, ASTRewrite rewriter,AST ast, TextEditGroup group) {
		field.getParent().accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration node) {
				if (node.getSuperclassType() != null) {
					ITypeBinding binding = node.getSuperclassType().resolveBinding();
					if (binding != null && binding.getQualifiedName().equals(ORG_JUNIT_RULES_EXTERNAL_RESOURCE)) {
						rewriter.remove(node.getSuperclassType(), group);
						AST ast = node.getAST();
						ListRewrite listRewrite = rewriter.getListRewrite(node, TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY);
						listRewrite.insertLast(ast.newSimpleType(ast.newName("BeforeEachCallback")), group);
						listRewrite.insertLast(ast.newSimpleType(ast.newName("AfterEachCallback")), group);
						for (MethodDeclaration method : node.getMethods()) {
							if (method.getName().getIdentifier().equals("before")) {
								rewriter.replace(method.getName(), ast.newSimpleName("beforeEach"), group);
							}
							if (method.getName().getIdentifier().equals("after")) {
								rewriter.replace(method.getName(), ast.newSimpleName("afterEach"), group);
							}
						}
					}
				}
				return super.visit(node);
			}
		});
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return 
"""
ExtendWith(MyTest.MyExternalResource.class)
public class MyTest {

	final class MyExternalResource implements BeforeEachCallback, AfterEachCallback {
		@Override
		protected void beforeEach() throws Throwable {
		}

		@Override
		protected void afterEach() {
		}
	}
"""; //$NON-NLS-1$
		}
		return 
"""
public class MyTest {

	final class MyExternalResource extends ExternalResource {
		@Override
		protected void before() throws Throwable {
		}

		@Override
		protected void after() {
		}
	}
	
	@Rule
	public ExternalResource er= new MyExternalResource();
"""; //$NON-NLS-1$
	}
}
