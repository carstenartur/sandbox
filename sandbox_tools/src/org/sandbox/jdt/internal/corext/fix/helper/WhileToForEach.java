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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.TypeLocation;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.AbortSearchException;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.ConvertLoopOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.ast.api.expr.MethodInvocationExpr;
import org.sandbox.ast.api.expr.SimpleNameExpr;
import org.sandbox.ast.api.jdt.JDTConverter;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseIteratorToForLoopFixCore;

/**
 * Find: while (it.hasNext()){ System.out.println(it.next()); }
 *
 * Rewrite: for(Object o:collection) { System.out.println(o); });
 *
 */
public class WhileToForEach extends AbstractTool<WhileLoopToChangeHit> {

	@Override
	public void find(UseIteratorToForLoopFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed,
			boolean createForOnlyIfVarUsed) {
		ReferenceHolder<ASTNode, WhileLoopToChangeHit> dataholder= new ReferenceHolder<>();
		Map<ASTNode, WhileLoopToChangeHit> operationsMap= new LinkedHashMap<>();
		WhileLoopToChangeHit invalidHit= new WhileLoopToChangeHit(true);
		HelperVisitor.callVariableDeclarationStatementVisitor(Iterator.class, compilationUnit, dataholder,
				nodesprocessed, (init_iterator, holder_a) -> {
					List<Object> computeVarName= computeVarName(init_iterator);
					MethodInvocation iteratorCall= computeIteratorCall(init_iterator);
					if (computeVarName != null && iteratorCall != null) {
						Statement iteratorAssignment= ASTNodes.getFirstAncestorOrNull(iteratorCall,
								Statement.class);
						HelperVisitor.callWhileStatementVisitor(init_iterator.getParent(), dataholder, nodesprocessed,
								(whilestatement, holder) -> {
									String name= computeNextVarname(whilestatement);
									if (computeVarName.get(0).equals(name)
											&& iteratorCall.getStartPosition() < whilestatement.getStartPosition()) {
										WhileLoopToChangeHit hit= holder.computeIfAbsent(whilestatement,
												k -> new WhileLoopToChangeHit());
										if (!createForOnlyIfVarUsed) {
											hit.iteratorDeclaration= init_iterator;
											hit.iteratorCall= iteratorAssignment;
											hit.iteratorName= name;
											if (computeVarName.size() == 1) {
												hit.self= true;
											} else {
												hit.collectionExpression= (Expression) computeVarName.get(1);
											}
											hit.whileStatement= whilestatement;
											if (hit.self) {
												hit.loopVarName= ConvertLoopOperation.modifyBaseName("i"); //$NON-NLS-1$
											} else {
												String collectionId= JDTConverter.identifierOf(hit.collectionExpression)
														.orElse("element"); //$NON-NLS-1$
												hit.loopVarName= ConvertLoopOperation.modifyBaseName(collectionId);
											}
											operationsMap.put(whilestatement, hit);
										}
										HelperVisitor.callMethodInvocationVisitor(whilestatement.getBody(), dataholder,
												nodesprocessed, (mi, holder2) -> {
													String identifier= mi.getExpression() instanceof SimpleName sn ? sn.getIdentifier() : null;
													if (identifier != null) {
														if (!name.equals(identifier)) {
															return true;
														}
														MethodInvocationExpr miExpr= JDTConverter.convert(mi);
														String method= miExpr.methodName().orElse(""); //$NON-NLS-1$
														WhileLoopToChangeHit previousHit= operationsMap
																.get(whilestatement);
														if (previousHit != null && (previousHit == invalidHit
																|| previousHit.nextFound || !method.equals("next"))) { //$NON-NLS-1$
															operationsMap.put(whilestatement, invalidHit);
															return true;
														}
														if (ASTNodes.getFirstAncestorOrNull(mi,
																ExpressionStatement.class) != null
																&& createForOnlyIfVarUsed) {
															operationsMap.put(whilestatement, invalidHit);
															return true;
														}
														hit.nextFound= true;
														hit.iteratorName= name;
														hit.iteratorDeclaration= init_iterator;
														hit.iteratorCall= iteratorAssignment;
														hit.whileStatement= whilestatement;
														hit.loopVarDeclaration= mi;
														if (computeVarName.size() == 1) {
															hit.self= true;
														} else {
															hit.collectionExpression= (Expression) computeVarName
																	.get(1);
														}
														VariableDeclarationStatement typedAncestor= ASTNodes
																.getTypedAncestor(mi,
																		VariableDeclarationStatement.class);
														if (typedAncestor != null) {
															ITypeBinding iteratorTypeArgument= computeTypeArgument(
																	init_iterator);
															ITypeBinding varTypeBinding= typedAncestor.getType()
																	.resolveBinding();
															if (varTypeBinding == null || iteratorTypeArgument == null
																	|| (!varTypeBinding.isEqualTo(iteratorTypeArgument)
																			&& !Bindings.isSuperType(varTypeBinding,
																					iteratorTypeArgument))) {
																operationsMap.put(whilestatement, invalidHit);
																return true;
															}
															VariableDeclarationFragment vdf= (VariableDeclarationFragment) typedAncestor
																	.fragments().get(0);
															hit.loopVarName= vdf.getName().getIdentifier();
														} else {
															if (hit.self) {
																hit.loopVarName= ConvertLoopOperation
																		.modifyBaseName("i"); //$NON-NLS-1$
															} else {
																String collectionId= JDTConverter.identifierOf(hit.collectionExpression)
																		.orElse("element"); //$NON-NLS-1$
																hit.loopVarName= ConvertLoopOperation.modifyBaseName(collectionId);
															}
															hit.nextWithoutVariableDeclaration= true;
														}
														operationsMap.put(whilestatement, hit);
														HelperVisitor<ReferenceHolder<ASTNode, WhileLoopToChangeHit>, ASTNode, WhileLoopToChangeHit> helperVisitor= holder
																.getHelperVisitor();
														helperVisitor.nodesprocessed.add(whilestatement);
														holder2.remove(whilestatement);
													}
													return true;
												});
									}
									return true;
								});
					}
					return true;
				});
		for (WhileLoopToChangeHit hit : operationsMap.values()) {
			if (!hit.isInvalid && validate(hit)) {
				operations.add(fixcore.rewrite(hit));
			}
		}
	}

	private static boolean validate(final WhileLoopToChangeHit hit) {
		ASTNode iterDeclarationParent= hit.iteratorDeclaration.getParent();
		List<StructuralPropertyDescriptor> descs= iterDeclarationParent.structuralPropertiesForType();
		boolean hasStatements= false;
		for (StructuralPropertyDescriptor desc : descs) {
			if (desc.getId().equals("statements")) { //$NON-NLS-1$
				hasStatements= true;
				break;
			}
		}
		if (!hasStatements) {
			return false;
		}
		ReferenceHolder<ASTNode, WhileLoopToChangeHit> dataholder= new ReferenceHolder<>();
		Set<ASTNode> nodesprocessed= new HashSet<>();
		VariableDeclarationFragment iterDeclFragment= (VariableDeclarationFragment) hit.iteratorDeclaration.fragments()
				.get(0);
		IVariableBinding iterBinding= iterDeclFragment.resolveBinding();
		if (iterBinding == null) {
			return false;
		}
		HelperVisitor.callMethodInvocationVisitor(iterDeclarationParent, dataholder, nodesprocessed, (mi, holder2) -> {
			String receiverIdentifier= mi.getExpression() instanceof SimpleName sn ? sn.getIdentifier() : null;
			if (receiverIdentifier != null && receiverIdentifier.equals(hit.iteratorName)) {
				if (mi.getStartPosition() < hit.whileStatement.getStartPosition()) {
					hit.isInvalid= true;
					return false;
				}
			} else {
				MethodInvocationExpr miExpr= JDTConverter.convert(mi);
				if (miExpr.isMethodNamed("iterator")) { //$NON-NLS-1$
					ASTNode assignment= ASTNodes.getFirstAncestorOrNull(mi, Assignment.class);
					if (assignment instanceof Assignment) {
						Expression leftSide= ((Assignment) assignment).getLeftHandSide();
						String assignedVarId= JDTConverter.identifierOf(leftSide)
								.orElse(null);
						if (assignedVarId != null && assignedVarId.equals(hit.iteratorName)) {
							Statement stmt= ASTNodes.getFirstAncestorOrNull(assignment, Statement.class);
							if (stmt == null || stmt.getParent() != hit.whileStatement.getParent()) {
								hit.isInvalid= true;
								return false;
							}
						}
					}
				}
			}
			return true;
		});
		return !hit.isInvalid;
	}

	private static String computeNextVarname(WhileStatement whilestatement) {
		Expression exp= whilestatement.getExpression();
		if (exp instanceof MethodInvocation mi) {
			MethodInvocationExpr miExpr= JDTConverter.convert(mi);
			if (miExpr.methodName().filter(name -> name.equals("hasNext")).isPresent()) { //$NON-NLS-1$
				return miExpr.receiver()
						.flatMap(receiver -> receiver.asSimpleName())
						.flatMap(SimpleNameExpr::resolveVariable)
						.map(var -> var.name())
						.orElse(null);
			}
		}
		return null;
	}

	private static List<Object> computeVarName(VariableDeclarationStatement node_a) {
		List<Object> objectList= new ArrayList<>();
		if (node_a.fragments().size() > 1) {
			return null;
		}
		VariableDeclarationFragment bli= (VariableDeclarationFragment) node_a.fragments().get(0);
		objectList.add(bli.getName().getIdentifier());
		Expression exp= bli.getInitializer();
		if (exp == null) {
			exp= computeIteratorCall(node_a);
		}
		MethodInvocation mi= ASTNodes.as(exp, MethodInvocation.class);
		if (mi == null) {
			return null;
		}
		MethodInvocationExpr miExpr= JDTConverter.convert(mi);
		if (!miExpr.isMethodNamed("iterator")) { //$NON-NLS-1$
			return null;
		}
		ITypeBinding iterableAncestor= null;
		IMethodBinding miBinding= mi.resolveMethodBinding();
		if (miBinding != null) {
			iterableAncestor= ASTNodes.findImplementedType(miBinding.getDeclaringClass(),
					Iterable.class.getCanonicalName());
		}
		if (iterableAncestor == null || iterableAncestor.isRawType()) {
			return null;
		}
		Expression sn= ASTNodes.as(mi.getExpression(), Expression.class);
		if (sn != null) {
			objectList.add(sn);
		}
		return objectList;
	}

	private static MethodInvocation computeIteratorCall(VariableDeclarationStatement node_a) {
		VariableDeclarationFragment bli= (VariableDeclarationFragment) node_a.fragments().get(0);
		Expression exp= bli.getInitializer();
		if (exp == null) {
			IBinding bliBinding= bli.getName().resolveBinding();
			if (bliBinding == null) {
				return null;
			}
			ASTNode parent= node_a.getParent();
			ReferenceHolder<ASTNode, Object> dataholder= new ReferenceHolder<>();
			Set<ASTNode> nodesprocessed= new HashSet<>();
			final Object Invalid= new Object();
			try {
				HelperVisitor.callAssignmentVisitor(parent, dataholder, nodesprocessed, (assignment, holder2) -> {
					if (assignment.getStartPosition() > node_a.getStartPosition()) {
						Expression leftSide= assignment.getLeftHandSide();
						SimpleName sn= ASTNodes.as(leftSide, SimpleName.class);
						if (sn != null) {
							IBinding binding= sn.resolveBinding();
							if (binding.isEqualTo(bliBinding)) {
								MethodInvocation mi= ASTNodes.as(assignment.getRightHandSide(), MethodInvocation.class);
								if (mi == null) {
									dataholder.put(node_a, Invalid);
									throw new AbortSearchException();
								}
								MethodInvocationExpr miExpr= JDTConverter.convert(mi);
								if (!miExpr.isMethodNamed("iterator") || (dataholder.get(node_a) != null)) { //$NON-NLS-1$
									dataholder.put(node_a, Invalid);
									throw new AbortSearchException();
								}
								dataholder.put(node_a, mi);
							}
						}
					}
					return true;
				});
			} catch (AbortSearchException e) {
				// do nothing
			}
			Object holderObject= dataholder.get(node_a);
			if (holderObject == Invalid || holderObject == null) {
				return null;
			}
			return (MethodInvocation) holderObject;
		}
		return ASTNodes.as(exp, MethodInvocation.class);
	}

	private static ITypeBinding computeTypeArgument(VariableDeclarationStatement node_a) {
		VariableDeclarationFragment bli= (VariableDeclarationFragment) node_a.fragments().get(0);
		Expression exp= bli.getInitializer();
		if (exp == null) {
			IBinding bliBinding= bli.getName().resolveBinding();
			if (bliBinding == null) {
				return null;
			}
			ASTNode parent= node_a.getParent();
			ReferenceHolder<ASTNode, Object> dataholder= new ReferenceHolder<>();
			Set<ASTNode> nodesprocessed= new HashSet<>();
			final Object Invalid= new Object();
			try {
				HelperVisitor.callAssignmentVisitor(parent, dataholder, nodesprocessed, (assignment, holder2) -> {
					if (assignment.getStartPosition() > node_a.getStartPosition()) {
						Expression leftSide= assignment.getLeftHandSide();
						SimpleName sn= ASTNodes.as(leftSide, SimpleName.class);
						if (sn != null) {
							IBinding binding= sn.resolveBinding();
							if (binding.isEqualTo(bliBinding)) {
								MethodInvocation mi= ASTNodes.as(assignment.getRightHandSide(), MethodInvocation.class);
								if (mi == null) {
									dataholder.put(node_a, Invalid);
									throw new AbortSearchException();
								}
								MethodInvocationExpr miExpr= JDTConverter.convert(mi);
								if (!miExpr.isMethodNamed("iterator")) { //$NON-NLS-1$
									dataholder.put(node_a, Invalid);
									throw new AbortSearchException();
								}
								dataholder.put(node_a, mi);
							}
						}
					}
					return true;
				});
			} catch (AbortSearchException e) {
				// do nothing
			}
			Object holderObject= dataholder.get(node_a);
			if (holderObject == Invalid || holderObject == null) {
				return null;
			}
			exp= (Expression) dataholder.get(node_a);
		}
		MethodInvocation mi= ASTNodes.as(exp, MethodInvocation.class);
		if (mi != null) {
			MethodInvocationExpr miExpr= JDTConverter.convert(mi);
			if (miExpr.isMethodNamed("iterator")) { //$NON-NLS-1$
				ITypeBinding iterableAncestor= null;
				IMethodBinding miBinding= mi.resolveMethodBinding();
				if (miBinding != null) {
					iterableAncestor= ASTNodes.findImplementedType(miBinding.getDeclaringClass(),
							Iterable.class.getCanonicalName());
				}
				if (iterableAncestor != null) {
					ITypeBinding[] typeArgs= iterableAncestor.getTypeArguments();
					if (typeArgs.length > 0) {
						return typeArgs[0];
					}
				}
			}
		} else {
			ITypeBinding varTypeBinding= node_a.getType().resolveBinding();
			if (varTypeBinding != null) {
				ITypeBinding[] typeArgs= varTypeBinding.getTypeArguments();
				if (typeArgs.length > 0) {
					return typeArgs[0];
				}
			}
		}
		return node_a.getAST().resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
	}

	@Override
	public void rewrite(UseIteratorToForLoopFixCore upp, final WhileLoopToChangeHit hit,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();

		ImportRewrite importRewrite= cuRewrite.getImportRewrite();
		ImportRemover remover= cuRewrite.getImportRemover();

		EnhancedForStatement newEnhancedForStatement= ast.newEnhancedForStatement();

		SingleVariableDeclaration result= ast.newSingleVariableDeclaration();

		SimpleName name= ast.newSimpleName(hit.loopVarName);
		result.setName(name);

		String looptargettype;
		Type type;
		ITypeBinding varBinding= null;
		if (hit.nextWithoutVariableDeclaration || !hit.nextFound) {
			type= null;
		} else {
			Expression expression= hit.loopVarDeclaration.getExpression();
			SimpleName variable= ASTNodes.as(expression, SimpleName.class);
			looptargettype= variable.resolveTypeBinding().getErasure().getQualifiedName();
			VariableDeclarationStatement typedAncestor= ASTNodes.getTypedAncestor(hit.loopVarDeclaration,
					VariableDeclarationStatement.class);
			type= typedAncestor.getType();
			varBinding= type.resolveBinding();
		}
		if (type == null || varBinding == null) {
			looptargettype= "java.lang.Object"; //$NON-NLS-1$
			ITypeBinding binding= computeTypeArgument(hit.iteratorDeclaration);
			Type collectionType= null;
			if (binding != null) {
				looptargettype= binding.getErasure().getQualifiedName();
				if (binding.isParameterizedType()) {
					collectionType= handleParametrizedType(binding, ast, cuRewrite);
				}
			}
			if (collectionType == null) {
				collectionType= ast.newSimpleType(addImport(looptargettype, cuRewrite, ast));
			}
			result.setType(collectionType);
		} else {
			Type importType= importType(varBinding, hit.iteratorDeclaration, importRewrite,
					(CompilationUnit) hit.iteratorDeclaration.getRoot(), TypeLocation.LOCAL_VARIABLE);
			remover.registerAddedImports(importType);

			result.setType(importType);
		}
		newEnhancedForStatement.setParameter(result);
		if (hit.self) {
			ThisExpression newThisExpression= ast.newThisExpression();
			newEnhancedForStatement.setExpression(newThisExpression);
		} else {
			Expression loopExpression= (Expression) rewrite.createCopyTarget(hit.collectionExpression);
			newEnhancedForStatement.setExpression(loopExpression);
		}
		ASTNodes.removeButKeepComment(rewrite, hit.iteratorDeclaration, group);
		remover.registerRemovedNode(hit.iteratorDeclaration.getType());
		if (hit.iteratorCall != hit.iteratorDeclaration) {
			ASTNodes.removeButKeepComment(rewrite, hit.iteratorCall, group);
			remover.registerRemovedNode(hit.iteratorCall);
		}
		if (hit.nextFound) {
			if (hit.nextWithoutVariableDeclaration) {
				// remove it.next(); expression statements
				ASTNode loopVarDeclaration= hit.loopVarDeclaration;
				while (loopVarDeclaration.getParent() instanceof ParenthesizedExpression) {
					loopVarDeclaration= loopVarDeclaration.getParent();
				}
				if (loopVarDeclaration.getLocationInParent() == ExpressionStatement.EXPRESSION_PROPERTY) {
					rewrite.remove(loopVarDeclaration.getParent(), group);
					remover.registerRemovedNode(loopVarDeclaration);
				} else {
					ASTNodes.replaceButKeepComment(rewrite, hit.loopVarDeclaration, name, group);
					remover.registerRemovedNode(hit.loopVarDeclaration);
				}
			} else {
				ASTNode node= ASTNodes.getTypedAncestor(hit.loopVarDeclaration, VariableDeclarationStatement.class);
				ASTNodes.removeButKeepComment(rewrite, node, group);
				remover.registerRemovedNode(node);
			}
		}
		newEnhancedForStatement.setBody(ASTNodes.createMoveTarget(rewrite, hit.whileStatement.getBody()));
		ASTNodes.replaceButKeepComment(rewrite, hit.whileStatement, newEnhancedForStatement, group);
		remover.registerRemovedNode(hit.whileStatement.getExpression());
		remover.applyRemoves(importRewrite);
	}

	private Type handleParametrizedType(ITypeBinding binding, AST ast, CompilationUnitRewrite cuRewrite) {
		ITypeBinding[] args= binding.getTypeArguments();
		String looptargettype= binding.getErasure().getQualifiedName();
		Type collectionType= ast.newSimpleType(addImport(looptargettype, cuRewrite, ast));
		if (binding.isParameterizedType()) {
			ParameterizedType pType= ast.newParameterizedType(collectionType);
			Collection<Type> typeArgs= new ArrayList<>();
			for (ITypeBinding arg : args) {
				Type argType= null;
				if (arg.isParameterizedType()) {
					argType= handleParametrizedType(arg, ast, cuRewrite);
				} else {
					looptargettype= arg.getQualifiedName();
					argType= ast.newSimpleType(addImport(looptargettype, cuRewrite, ast));
				}
				typeArgs.add(argType);
			}
			pType.typeArguments().addAll(typeArgs);
			collectionType= pType;
		}
		return collectionType;
	}

	private static Type importType(final ITypeBinding toImport, final ASTNode accessor, ImportRewrite imports,
			final CompilationUnit compilationUnit, TypeLocation location) {
		ImportRewriteContext importContext= new ContextSensitiveImportRewriteContext(compilationUnit,
				accessor.getStartPosition(), imports);
		return imports.addImport(toImport, compilationUnit.getAST(), importContext, location);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "\nfor (String s : strings) {\n\n	System.out.println(s);\n}\n\n"; //$NON-NLS-1$
		}
		return "Iterator it = lists.iterator();\nwhile (it.hasNext()) {\n    String s = (String) it.next();\n	System.out.println(s);\n}\n\n"; //$NON-NLS-1$
	}
}
