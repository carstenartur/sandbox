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

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;

/**
 *
 * 
 */
public class RuleExternalResourceJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

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
		ImportRewrite importRemover = cuRewrite.getImportRewrite();
		for (Entry<Integer, JunitHolder> entry : hit.entrySet()) {
			JunitHolder mh = entry.getValue();
			FieldDeclaration minv = mh.getFieldDeclaration();
			for (Object modifier : minv.modifiers()) {
				if (modifier instanceof Annotation annotation) {
					process(annotation,cuRewrite.getRoot().getJavaElement().getJavaProject(),rewrite,ast);
				}
			}
//			addImport(ORG_JUNIT_JUPITER_API_AFTER_EACH, cuRewrite, ast);
//			ASTNodes.replaceButKeepComment(rewrite, minv, newAnnotation, group);
//			importRemover.removeImport(ORG_JUNIT_AFTER);
		}
	}

	public void process(Annotation node,IJavaProject jproject,ASTRewrite rewrite,AST ast) {
		// Prüfen, ob die Annotation @Rule ist
		ITypeBinding annotationBinding = node.resolveTypeBinding();
		if (annotationBinding != null && annotationBinding.getQualifiedName().equals(ORG_JUNIT_RULE)) {
			// Finde das Feld oder die Methode, die mit @Rule annotiert ist
			ASTNode parent = node.getParent();
			if (parent instanceof FieldDeclaration) {
				// Wenn es ein Feld ist, dann ist das der @Rule-Typ
				FieldDeclaration field = (FieldDeclaration) parent;
				ITypeBinding fieldTypeBinding = ((VariableDeclarationFragment) field.fragments().get(0)).resolveBinding().getType();
				// Prüfen, ob der Typ von ExternalResource erbt
				if (isExternalResource(fieldTypeBinding)) {
					ICompilationUnit externalClassCU = findCompilationUnitForType(fieldTypeBinding.getQualifiedName(),jproject);
					if (externalClassCU != null) {
						modifyExternalResourceClass(externalClassCU,rewrite,ast);
					}
				}
			}
		}
	}

	// Prüfen, ob der Typ von ExternalResource erbt
	private boolean isExternalResource(ITypeBinding typeBinding) {
		while (typeBinding != null) {
			if (typeBinding.getQualifiedName().equals(ORG_JUNIT_RULES_EXTERNAL_RESOURCE)) {
				return true;
			}
			typeBinding = typeBinding.getSuperclass();
		}
		return false;
	}

	// Die CompilationUnit für den Typ finden
	private ICompilationUnit findCompilationUnitForType(String qualifiedTypeName, IJavaProject javaProject) {
		try {
			IType type = javaProject.findType(qualifiedTypeName);
			if (type != null) {
				return type.getCompilationUnit();
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return null;
	}

	// Änderungen an der Klasse vornehmen, die von ExternalResource erbt
	private void modifyExternalResourceClass(ICompilationUnit cu,ASTRewrite rewriter,AST ast) {
		// Erzeuge einen AST für die externe Klasse
		ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(cu);
		parser.setResolveBindings(true);

		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

		astRoot.accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration node) {
				if (node.getSuperclassType() != null) {
					ITypeBinding binding = node.getSuperclassType().resolveBinding();

					// Prüfen, ob es sich um ExternalResource handelt
					if (binding != null && binding.getQualifiedName().equals(ORG_JUNIT_RULES_EXTERNAL_RESOURCE)) {
						// Entferne "extends ExternalResource"
						rewriter.remove(node.getSuperclassType(), null);

						// Füge "implements BeforeAllCallback, AfterAllCallback" hinzu
						AST ast = node.getAST();
						ListRewrite listRewrite = rewriter.getListRewrite(node, TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY);
						listRewrite.insertLast(ast.newSimpleType(ast.newName("BeforeAllCallback")), null);
						listRewrite.insertLast(ast.newSimpleType(ast.newName("AfterAllCallback")), null);
					}
				}
				return super.visit(node);
			}

			@Override
			public boolean visit(MethodDeclaration node) {
				IMethodBinding binding = node.resolveBinding();

				// Prüfen, ob es sich um "before()" oder "after()" handelt
				if (binding.getName().equals("before") && binding.getParameterTypes().length == 0) {
					rewriter.set(node, MethodDeclaration.NAME_PROPERTY, node.getAST().newSimpleName("beforeAll"), null);
				} else if (binding.getName().equals("after") && binding.getParameterTypes().length == 0) {
					rewriter.set(node, MethodDeclaration.NAME_PROPERTY, node.getAST().newSimpleName("afterAll"), null);
				}

				return super.visit(node);
			}
		});

		// Änderungen anwenden
//		applyTextEdit(cu, rewriter.rewriteAST());
	}

	private void applyTextEdit(ICompilationUnit cu, TextEdit edit) {
		try {
			// Setze die Änderungen (TextEdit) direkt auf die CompilationUnit
			cu.applyTextEdit(edit, null);
			cu.reconcile(ICompilationUnit.NO_AST, false, null, null);
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return 
"""
@A
public void tearDown() throws Exception {
}
"""; //$NON-NLS-1$
		}
		return 
"""
@Rule
ExternalResource er=new ExternalResource(){
}					;
"""; //$NON-NLS-1$
	}
}
