/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Plugin to migrate JUnit 4 @RunWith(Parameterized.class) to JUnit 5 @ParameterizedTest.
 * 
 * This transformation handles:
 * - Removing @RunWith(Parameterized.class) annotation
 * - Converting @Parameters method to return Stream<Arguments>
 * - Removing constructor and parameter fields
 * - Adding @ParameterizedTest and @MethodSource to test methods
 * - Adding method parameters to test methods
 */
public class ParameterizedTestJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder = new ReferenceHolder<>();
		
		// Find @RunWith(Parameterized.class) annotations
		HelperVisitor.callSingleMemberAnnotationVisitor(ORG_JUNIT_RUNWITH, compilationUnit, dataHolder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Annotation node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		
		// Check if this is @RunWith(Parameterized.class)
		if (node instanceof SingleMemberAnnotation mynode) {
			Expression value = mynode.getValue();
			if (value instanceof TypeLiteral myvalue) {
				Type type = myvalue.getType();
				if (type != null) {
					ITypeBinding typeBinding = type.resolveBinding();
					if (typeBinding != null) {
						String runnerQualifiedName = typeBinding.getQualifiedName();
						if (ORG_JUNIT_RUNNERS_PARAMETERIZED.equals(runnerQualifiedName)) {
							// Found a parameterized test class
							JunitHolder mh = new JunitHolder();
							mh.minv = node;
							mh.minvname = node.getTypeName().getFullyQualifiedName();
							mh.value = ORG_JUNIT_RUNNERS_PARAMETERIZED;
							
							// Get the containing type declaration to store for processing
							ASTNode parent = node.getParent();
							while (parent != null && !(parent instanceof TypeDeclaration)) {
								parent = parent.getParent();
							}
							if (parent != null) {
								mh.additionalInfo = parent;
							}
							
							dataHolder.put(dataHolder.size(), mh);
							operations.add(fixcore.rewrite(dataHolder));
							return false;
						}
					}
				}
			}
		}
		return false;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		
		Annotation runWithAnnotation = junitHolder.getAnnotation();
		TypeDeclaration typeDecl = (TypeDeclaration) junitHolder.additionalInfo;
		
		if (typeDecl == null) {
			return; // Cannot proceed without type declaration
		}
		
		// Step 1: Remove @RunWith annotation
		rewriter.remove(runWithAnnotation, group);
		
		// Step 2: Find @Parameters method and constructor to extract parameter info
		MethodDeclaration parametersMethod = null;
		MethodDeclaration constructor = null;
		List<SingleVariableDeclaration> constructorParams = new ArrayList<>();
		Set<String> paramFieldNames = new HashSet<>();
		
		for (MethodDeclaration method : typeDecl.getMethods()) {
			// Find @Parameters method
			for (Object modifier : method.modifiers()) {
				if (modifier instanceof Annotation) {
					Annotation annot = (Annotation) modifier;
					String annotName = annot.getTypeName().getFullyQualifiedName();
					if ("Parameters".equals(annotName) || ORG_JUNIT_RUNNERS_PARAMETERIZED_PARAMETERS.equals(annotName)) {
						parametersMethod = method;
						break;
					}
				}
			}
			
			// Find constructor
			// Note: If multiple constructors exist, this uses the last one found.
			// Typically, parameterized tests have only one constructor that accepts the test parameters.
			if (method.isConstructor()) {
				constructor = method;
				@SuppressWarnings("unchecked")
				List<SingleVariableDeclaration> params = method.parameters();
				constructorParams.addAll(params);
				
				// Extract field names from constructor body (fields being assigned from parameters)
				if (method.getBody() != null) {
					for (Object stmt : method.getBody().statements()) {
						if (stmt instanceof ExpressionStatement) {
							Expression expr = ((ExpressionStatement) stmt).getExpression();
							if (expr instanceof Assignment) {
								Assignment assign = (Assignment) expr;
								Expression lhs = assign.getLeftHandSide();
								if (lhs instanceof SimpleName) {
									paramFieldNames.add(((SimpleName) lhs).getIdentifier());
								} else if (lhs instanceof org.eclipse.jdt.core.dom.FieldAccess) {
									org.eclipse.jdt.core.dom.FieldAccess fa = (org.eclipse.jdt.core.dom.FieldAccess) lhs;
									paramFieldNames.add(fa.getName().getIdentifier());
								}
							}
						}
					}
				}
			}
		}
		
		// Step 3: Transform @Parameters method
		if (parametersMethod != null) {
			transformParametersMethod(parametersMethod, rewriter, ast, group, importRewriter);
		}
		
		// Step 4: Remove constructor
		if (constructor != null) {
			rewriter.remove(constructor, group);
		}
		
		// Step 5: Remove parameter fields
		ListRewrite fieldListRewrite = rewriter.getListRewrite(typeDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		for (Object bodyDecl : typeDecl.bodyDeclarations()) {
			if (bodyDecl instanceof FieldDeclaration) {
				FieldDeclaration field = (FieldDeclaration) bodyDecl;
				List<VariableDeclarationFragment> fragmentsToRemove = new ArrayList<>();
				for (Object frag : field.fragments()) {
					if (frag instanceof VariableDeclarationFragment) {
						VariableDeclarationFragment fragment = (VariableDeclarationFragment) frag;
						String fieldName = fragment.getName().getIdentifier();
						if (paramFieldNames.contains(fieldName)) {
							fragmentsToRemove.add(fragment);
						}
					}
				}
				if (!fragmentsToRemove.isEmpty()) {
					if (fragmentsToRemove.size() == field.fragments().size()) {
						// All fragments are parameter fields: remove entire declaration
						fieldListRewrite.remove(field, group);
					} else {
						// Only some fragments are parameter fields: remove them individually
						ListRewrite fragmentRewrite = rewriter.getListRewrite(field, FieldDeclaration.FRAGMENTS_PROPERTY);
						for (VariableDeclarationFragment fragment : fragmentsToRemove) {
							fragmentRewrite.remove(fragment, group);
						}
					}
				}
			}
		}
		
		// Step 6: Transform @Test methods to @ParameterizedTest
		String parametersMethodName = parametersMethod != null ? parametersMethod.getName().getIdentifier() : "data";
		for (MethodDeclaration method : typeDecl.getMethods()) {
			if (isTestMethod(method)) {
				transformTestMethod(method, constructorParams, parametersMethodName, rewriter, ast, group, importRewriter);
			}
		}
		
		// Step 7: Update imports
		importRewriter.removeImport(ORG_JUNIT_RUNWITH);
		importRewriter.removeImport(ORG_JUNIT_RUNNERS_PARAMETERIZED);
		importRewriter.removeImport(ORG_JUNIT_RUNNERS_PARAMETERIZED_PARAMETERS);
		importRewriter.removeImport("java.util.Arrays");
		importRewriter.removeImport("java.util.Collection");
		importRewriter.addImport(ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST);
		importRewriter.addImport(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE);
		// Note: Arguments is used with fully qualified name, not imported
		importRewriter.addImport("java.util.stream.Stream");
	}
	
	/**
	 * Transform @Parameters method to return Stream<Arguments>
	 */
	private void transformParametersMethod(MethodDeclaration method, ASTRewrite rewriter, AST ast,
			TextEditGroup group, ImportRewrite importRewriter) {
		
		// Remove @Parameters annotation
		ListRewrite modifiersRewrite = rewriter.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
		for (Object modifier : method.modifiers()) {
			if (modifier instanceof Annotation) {
				Annotation annot = (Annotation) modifier;
				String annotName = annot.getTypeName().getFullyQualifiedName();
				if ("Parameters".equals(annotName) || ORG_JUNIT_RUNNERS_PARAMETERIZED_PARAMETERS.equals(annotName)) {
					modifiersRewrite.remove((ASTNode) modifier, group);
				}
			}
		}
		
		// Change return type to Stream<org.junit.jupiter.params.provider.Arguments>
		// Use fully qualified Arguments name to avoid import conflicts
		Type streamType = ast.newSimpleType(ast.newSimpleName("Stream"));
		Type argumentsType = ast.newSimpleType(ast.newName(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS));
		Type newReturnType = ast.newParameterizedType(streamType);
		((org.eclipse.jdt.core.dom.ParameterizedType) newReturnType).typeArguments().add(argumentsType);
		rewriter.set(method, MethodDeclaration.RETURN_TYPE2_PROPERTY, newReturnType, group);
		
		// Transform method body: Arrays.asList(new Object[][]...) -> Stream.of(Arguments.of(...), ...)
		if (method.getBody() != null && !method.getBody().statements().isEmpty()) {
			Statement returnStmt = (Statement) method.getBody().statements().get(0);
			if (returnStmt instanceof org.eclipse.jdt.core.dom.ReturnStatement) {
				org.eclipse.jdt.core.dom.ReturnStatement retStmt = (org.eclipse.jdt.core.dom.ReturnStatement) returnStmt;
				Expression returnExpr = retStmt.getExpression();
				
				// Try to extract the array data
				List<Expression> dataRows = extractDataRows(returnExpr);
				if (!dataRows.isEmpty()) {
					// Create Stream.of(Arguments.of(...), Arguments.of(...), ...)
					MethodInvocation streamOf = ast.newMethodInvocation();
					streamOf.setExpression(ast.newSimpleName("Stream"));
					streamOf.setName(ast.newSimpleName("of"));
					
					for (Expression row : dataRows) {
						// Create Arguments.of(...) for each row using fully qualified name
						MethodInvocation argumentsOf = ast.newMethodInvocation();
						argumentsOf.setExpression(ast.newName(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS));
						argumentsOf.setName(ast.newSimpleName("of"));
						
						// Extract values from the row
						if (row instanceof ArrayInitializer) {
							ArrayInitializer arrayInit = (ArrayInitializer) row;
							for (Object expr : arrayInit.expressions()) {
								argumentsOf.arguments().add(rewriter.createCopyTarget((Expression) expr));
							}
						} else if (row instanceof ArrayCreation) {
							org.eclipse.jdt.core.dom.ArrayCreation arrayCreate = (org.eclipse.jdt.core.dom.ArrayCreation) row;
							if (arrayCreate.getInitializer() != null) {
								for (Object expr : arrayCreate.getInitializer().expressions()) {
									argumentsOf.arguments().add(rewriter.createCopyTarget((Expression) expr));
								}
							}
						}
						
						streamOf.arguments().add(argumentsOf);
					}
					
					org.eclipse.jdt.core.dom.ReturnStatement newReturnStmt = ast.newReturnStatement();
					newReturnStmt.setExpression(streamOf);
					rewriter.replace(retStmt, newReturnStmt, group);
				}
			}
		}
	}
	
	/**
	 * Extract data rows from Arrays.asList(new Object[][]{{...}, {...}})
	 * 
	 * Note: Currently only handles the specific pattern Arrays.asList(new Object[][] {...}).
	 * Other JUnit 4 Parameterized data formats are not yet supported:
	 * - Directly returning Object[][]
	 * - Collection.singletonList()
	 * - Other Collection implementations
	 * 
	 * @param expr The expression from @Parameters method return statement
	 * @return List of array expressions representing test data rows
	 */
	private List<Expression> extractDataRows(Expression expr) {
		List<Expression> rows = new ArrayList<>();
		
		// Handle Arrays.asList(new Object[][] {...})
		if (expr instanceof MethodInvocation) {
			MethodInvocation methodInv = (MethodInvocation) expr;
			if ("asList".equals(methodInv.getName().getIdentifier()) && !methodInv.arguments().isEmpty()) {
				Expression arg = (Expression) methodInv.arguments().get(0);
				
				// Check for new Object[][] {...}
				if (arg instanceof org.eclipse.jdt.core.dom.ArrayCreation) {
					org.eclipse.jdt.core.dom.ArrayCreation arrayCreate = (org.eclipse.jdt.core.dom.ArrayCreation) arg;
					if (arrayCreate.getInitializer() != null) {
						rows.addAll(arrayCreate.getInitializer().expressions());
					}
				}
			}
		}
		
		return rows;
	}
	
	/**
	 * Check if a method is a @Test method
	 */
	private boolean isTestMethod(MethodDeclaration method) {
		for (Object modifier : method.modifiers()) {
			if (modifier instanceof Annotation) {
				Annotation annot = (Annotation) modifier;
				String annotName = annot.getTypeName().getFullyQualifiedName();
				if ("Test".equals(annotName) || ORG_JUNIT_TEST.equals(annotName)) {
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Transform @Test method to @ParameterizedTest with @MethodSource
	 */
	private void transformTestMethod(MethodDeclaration method, List<SingleVariableDeclaration> constructorParams,
			String parametersMethodName, ASTRewrite rewriter, AST ast, TextEditGroup group, ImportRewrite importRewriter) {
		
		// Replace @Test with @ParameterizedTest
		ListRewrite modifiersRewrite = rewriter.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
		Annotation testAnnotation = null;
		for (Object modifier : method.modifiers()) {
			if (modifier instanceof Annotation) {
				Annotation annot = (Annotation) modifier;
				String annotName = annot.getTypeName().getFullyQualifiedName();
				if ("Test".equals(annotName) || ORG_JUNIT_TEST.equals(annotName)) {
					testAnnotation = annot;
					break;
				}
			}
		}
		
		if (testAnnotation != null) {
			// Create @ParameterizedTest
			MarkerAnnotation parameterizedTest = ast.newMarkerAnnotation();
			parameterizedTest.setTypeName(ast.newSimpleName(ANNOTATION_PARAMETERIZED_TEST));
			
			// Create @MethodSource("methodName")
			SingleMemberAnnotation methodSource = ast.newSingleMemberAnnotation();
			methodSource.setTypeName(ast.newSimpleName(ANNOTATION_METHOD_SOURCE));
			org.eclipse.jdt.core.dom.StringLiteral stringLiteral = ast.newStringLiteral();
			stringLiteral.setLiteralValue(parametersMethodName);
			methodSource.setValue(stringLiteral);
			
			// Replace @Test with @ParameterizedTest and add @MethodSource
			modifiersRewrite.replace(testAnnotation, parameterizedTest, group);
			modifiersRewrite.insertAfter(methodSource, parameterizedTest, group);
		}
		
		// Add parameters to method
		ListRewrite paramsRewrite = rewriter.getListRewrite(method, MethodDeclaration.PARAMETERS_PROPERTY);
		for (SingleVariableDeclaration param : constructorParams) {
			SingleVariableDeclaration newParam = ast.newSingleVariableDeclaration();
			newParam.setType((Type) rewriter.createCopyTarget(param.getType()));
			newParam.setName((SimpleName) rewriter.createCopyTarget(param.getName()));
			paramsRewrite.insertLast(newParam, group);
		}
		
		// Update imports
		importRewriter.removeImport(ORG_JUNIT_TEST);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					@ParameterizedTest
					@MethodSource("data")
					void testMultiply(int input, int expected) {
						assertEquals(expected, input * 2);
					}
					
					static Stream<Arguments> data() {
						return Stream.of(
							Arguments.of(1, 2),
							Arguments.of(2, 4),
							Arguments.of(3, 6)
						);
					}
					"""; //$NON-NLS-1$
		}
		return """
				@RunWith(Parameterized.class)
				public class MyParameterizedTest {
					private int input;
					private int expected;
					
					public MyParameterizedTest(int input, int expected) {
						this.input = input;
						this.expected = expected;
					}
					
					@Parameters
					public static Collection<Object[]> data() {
						return Arrays.asList(new Object[][] {
							{1, 2}, {2, 4}, {3, 6}
						});
					}
					
					@Test
					public void testMultiply() {
						assertEquals(expected, input * 2);
					}
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "ParameterizedTest"; //$NON-NLS-1$
	}
}
