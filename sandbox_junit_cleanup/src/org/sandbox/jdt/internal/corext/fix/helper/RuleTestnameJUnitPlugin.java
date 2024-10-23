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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
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
public class RuleTestnameJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataholder = new ReferenceHolder<>();
		HelperVisitor.callFieldDeclarationVisitor(ORG_JUNIT_RULE, ORG_JUNIT_RULES_TEST_NAME, compilationUnit, dataholder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, FieldDeclaration node,
			ReferenceHolder<Integer, JunitHolder> dataholder) {
		JunitHolder mh = new JunitHolder();
		VariableDeclarationFragment fragment = (VariableDeclarationFragment) node.fragments().get(0);
		ITypeBinding binding = fragment.resolveBinding().getType();
		if(binding != null && ORG_JUNIT_RULES_TEST_NAME.equals(binding.getQualifiedName())) {
			mh.minv = node;
			dataholder.put(dataholder.size(), mh);
			operations.add(fixcore.rewrite(dataholder));
		}
		return false;
	}

	@Override
	public void rewrite(JUnitCleanUpFixCore upp, final ReferenceHolder<Integer, JunitHolder> hit,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewriter = cuRewrite.getASTRewrite();
		AST ast = cuRewrite.getRoot().getAST();
		ImportRewrite importrewriter = cuRewrite.getImportRewrite();
		for (Entry<Integer, JunitHolder> entry : hit.entrySet()) {
			JunitHolder mh = entry.getValue();
			FieldDeclaration node = mh.getFieldDeclaration();
			rewriter.remove(node, group);
			TypeDeclaration parentClass = (TypeDeclaration) node.getParent();
			addBeforeEachInitMethod(parentClass, rewriter, group);
			addTestNameField(parentClass, rewriter, group);
			for (MethodDeclaration method : parentClass.getMethods()) {
				if (method.getBody() != null) {
					method.getBody().accept(new ASTVisitor() {
						@Override
						public boolean visit(MethodInvocation node) {
							if (node.getExpression() != null && node.getExpression().resolveTypeBinding().getQualifiedName().equals("org.junit.rules.TestName")) {
								SimpleName newFieldAccess = ast.newSimpleName("testName");
								rewriter.replace(node, newFieldAccess, group);
							}
							return super.visit(node);
						}
					});
				}
			}
			importrewriter.addImport(ORG_JUNIT_JUPITER_API_TEST_INFO);
			importrewriter.addImport(ORG_JUNIT_JUPITER_API_BEFORE_EACH);
			importrewriter.removeImport(ORG_JUNIT_RULE);
			importrewriter.removeImport(ORG_JUNIT_RULES_TEST_NAME);
		}
	}

	private void addTestNameField(TypeDeclaration parentClass, ASTRewrite rewriter, TextEditGroup group) {
		AST ast = parentClass.getAST();
		VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName("testName"));

		FieldDeclaration fieldDeclaration = ast.newFieldDeclaration(fragment);
		fieldDeclaration.setType(ast.newSimpleType(ast.newName("String")));
		fieldDeclaration.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));

		ListRewrite listRewrite = rewriter.getListRewrite(parentClass, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		listRewrite.insertFirst(fieldDeclaration, group);
	}

	private void addBeforeEachInitMethod(TypeDeclaration parentClass, ASTRewrite rewriter, TextEditGroup group) {
		AST ast = parentClass.getAST();

		MethodDeclaration methodDeclaration = ast.newMethodDeclaration();
		methodDeclaration.setName(ast.newSimpleName("init"));
		methodDeclaration.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));
		
		SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
		param.setType(ast.newSimpleType(ast.newName("TestInfo")));
		param.setName(ast.newSimpleName("testInfo"));
		methodDeclaration.parameters().add(param);

		Block body = ast.newBlock();
		Assignment assignment = ast.newAssignment();
		FieldAccess fieldAccess = ast.newFieldAccess();
		fieldAccess.setExpression(ast.newThisExpression());
		fieldAccess.setName(ast.newSimpleName("testName"));
		assignment.setLeftHandSide(fieldAccess);

		MethodInvocation methodInvocation = ast.newMethodInvocation();
		methodInvocation.setExpression(ast.newSimpleName("testInfo"));
		methodInvocation.setName(ast.newSimpleName("getDisplayName"));

		assignment.setRightHandSide(methodInvocation);

		ExpressionStatement statement = ast.newExpressionStatement(assignment);
		body.statements().add(statement);
		methodDeclaration.setBody(body);

		MarkerAnnotation beforeEachAnnotation = ast.newMarkerAnnotation();
		beforeEachAnnotation.setTypeName(ast.newName("BeforeEach"));

		ListRewrite listRewrite = rewriter.getListRewrite(parentClass, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		listRewrite.insertFirst(methodDeclaration, group);

		listRewrite = rewriter.getListRewrite(methodDeclaration, MethodDeclaration.MODIFIERS2_PROPERTY);
		listRewrite.insertFirst(beforeEachAnnotation, group);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return 
"""
	private String testName;

	@BeforeEach
	void init(TestInfo testInfo) {
		this.testName = testInfo.getDisplayName();
	}
	@Test
	public void test(){
		System.out.println("Test name: " + testName);
	}
"""; //$NON-NLS-1$
		}
		return 
"""
	@Rule
	public TestName tn = new TestName();

	@Test
	public void test(){
		System.out.println("Test name: " + tn.getMethodName());
	}
"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RuleTestname"; //$NON-NLS-1$
	}
}
