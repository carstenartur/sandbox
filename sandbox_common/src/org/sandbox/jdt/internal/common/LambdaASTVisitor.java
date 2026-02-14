package org.sandbox.jdt.internal.common;

/*-
 * #%L
 * Sandbox common
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */


import org.eclipse.jdt.core.dom.*;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;

/**
 *
 * @author chammer
 *
 * @param <E> - type that extends HelpVisitorProvider that provides {@code HelperVisitor<V, T>}
 * @param <V> - type that HelperVisitor uses as map key type
 * @param <T> - type that HelperVisitor uses as map value type
 * @since 1.15
 */
@SuppressWarnings({ "unchecked" })
public class LambdaASTVisitor<E extends HelperVisitorProvider<V,T,E>, V, T> extends ASTVisitor {
	/**
	 *
	 */
	private final HelperVisitor<E,V,T> helperVisitor;

	/**
	 * @param helperVisitor - HelperVisitor
	 */
	LambdaASTVisitor(HelperVisitor<E,V,T> helperVisitor) {
		super(false);
		this.helperVisitor = helperVisitor;
	}

	LambdaASTVisitor(HelperVisitor<E,V,T> helperVisitor, boolean visitjavadoc) {
		super(visitjavadoc);
		this.helperVisitor = helperVisitor;
	}

	@Override
	public boolean visit(AnnotationTypeDeclaration node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.AnnotationTypeDeclaration)) {
			return this.helperVisitor.testPredicate(VisitorEnum.AnnotationTypeDeclaration, node);
		}
		return true;
	}

	@Override
	public boolean visit(AnnotationTypeMemberDeclaration node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.AnnotationTypeMemberDeclaration)) {
			return this.helperVisitor.testPredicate(VisitorEnum.AnnotationTypeMemberDeclaration, node);
		}
		return true;
	}

	@Override
	public boolean visit(AnonymousClassDeclaration node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.AnonymousClassDeclaration)) {
			return this.helperVisitor.testPredicate(VisitorEnum.AnonymousClassDeclaration, node);
		}
		return true;
	}

	@Override
	public boolean visit(ArrayAccess node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ArrayAccess)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ArrayAccess, node);
		}
		return true;
	}

	@Override
	public boolean visit(ArrayCreation node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ArrayCreation)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ArrayCreation, node);
		}
		return true;
	}

	@Override
	public boolean visit(ArrayInitializer node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ArrayInitializer)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ArrayInitializer, node);
		}
		return true;
	}

	@Override
	public boolean visit(ArrayType node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ArrayType)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ArrayType, node);
		}
		return true;
	}

	@Override
	public boolean visit(AssertStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.AssertStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.AssertStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(Assignment node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.Assignment)) {
			VisitorConfigData config = this.helperVisitor.getSupplierData().get(VisitorEnum.Assignment);
			if(config != null && config.getOperator() != null) {
				Assignment.Operator operator = Assignment.Operator.toOperator(config.getOperator());
				if(operator != null && !node.getOperator().equals(operator)) {
					return true;
				}
			}
			return this.helperVisitor.testPredicate(VisitorEnum.Assignment, node);
		}
		return true;
	}

	@Override
	public boolean visit(Block node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.Block)) {
			return this.helperVisitor.testPredicate(VisitorEnum.Block, node);
		}
		return true;
	}

	@Override
	public boolean visit(BlockComment node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.BlockComment)) {
			return this.helperVisitor.testPredicate(VisitorEnum.BlockComment, node);
		}
		return true;
	}

	@Override
	public boolean visit(BooleanLiteral node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.BooleanLiteral)) {
			return this.helperVisitor.testPredicate(VisitorEnum.BooleanLiteral, node);
		}
		return true;
	}

	@Override
	public boolean visit(BreakStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.BreakStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.BreakStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(CastExpression node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.CastExpression)) {
			return this.helperVisitor.testPredicate(VisitorEnum.CastExpression, node);
		}
		return true;
	}

	@Override
	public boolean visit(CatchClause node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.CatchClause)) {
			VisitorConfigData config = this.helperVisitor.getSupplierData().get(VisitorEnum.CatchClause);
			if(config != null) {
				Class<?> exceptionType = config.getExceptionType();
				if(exceptionType != null) {
					ITypeBinding binding= node.getException().getType().resolveBinding();
					if(binding != null) {
						if(!isTypeMatching(binding, exceptionType.getCanonicalName())) {
							return true;
						}
					} else {
						// Fallback to simple name matching when binding is null (e.g., in stub environments)
						String typeName = node.getException().getType().toString();
						String expectedSimpleName = exceptionType.getSimpleName();
						String expectedFullName = exceptionType.getCanonicalName();
						if(!typeName.equals(expectedSimpleName) && !typeName.equals(expectedFullName)) {
							return true;
						}
					}
				}
			}
			return this.helperVisitor.testPredicate(VisitorEnum.CatchClause, node);
		}
		return true;
	}

	@Override
	public boolean visit(CharacterLiteral node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.CharacterLiteral)) {
			return this.helperVisitor.testPredicate(VisitorEnum.CharacterLiteral, node);
		}
		return true;
	}

	@Override
	public boolean visit(ClassInstanceCreation node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ClassInstanceCreation)) {
			VisitorConfigData config = this.helperVisitor.getSupplierData().get(VisitorEnum.ClassInstanceCreation);
			if(config != null) {
				Class<?> typeof = config.getTypeof();
				if(typeof!=null) {
					ITypeBinding binding= node.resolveTypeBinding();
					if (binding == null || !typeof.getSimpleName().equals(binding.getName())) {
						return true;
					}
				}
				// Support filtering by fully qualified type name (String) to avoid deprecation warnings
				String typeofByName = config.getTypeofByName();
				if(typeofByName!=null) {
					// Extract simple name for comparison (same approach as TYPEOF)
					String simpleName = typeofByName.substring(typeofByName.lastIndexOf('.') + 1);
					ITypeBinding binding= node.resolveTypeBinding();
					if (binding == null || !simpleName.equals(binding.getName())) {
						return true;
					}
				}
			}
			return this.helperVisitor.testPredicate(VisitorEnum.ClassInstanceCreation, node);
		}
		return true;
	}

	@Override
	public boolean visit(CompilationUnit node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.CompilationUnit)) {
			return this.helperVisitor.testPredicate(VisitorEnum.CompilationUnit, node);
		}
		return true;
	}

	@Override
	public boolean visit(ConditionalExpression node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ConditionalExpression)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ConditionalExpression, node);
		}
		return true;
	}

	@Override
	public boolean visit(ConstructorInvocation node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ConstructorInvocation)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ConstructorInvocation, node);
		}
		return true;
	}

	@Override
	public boolean visit(ContinueStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ContinueStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ContinueStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(CreationReference node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.CreationReference)) {
			return this.helperVisitor.testPredicate(VisitorEnum.CreationReference, node);
		}
		return true;
	}

	@Override
	public boolean visit(Dimension node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.Dimension)) {
			return this.helperVisitor.testPredicate(VisitorEnum.Dimension, node);
		}
		return true;
	}

	@Override
	public boolean visit(DoStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.DoStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.DoStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(EmptyStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.EmptyStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.EmptyStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(EnhancedForStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.EnhancedForStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.EnhancedForStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(EnumConstantDeclaration node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.EnumConstantDeclaration)) {
			return this.helperVisitor.testPredicate(VisitorEnum.EnumConstantDeclaration, node);
		}
		return true;
	}

	@Override
	public boolean visit(EnumDeclaration node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.EnumDeclaration)) {
			return this.helperVisitor.testPredicate(VisitorEnum.EnumDeclaration, node);
		}
		return true;
	}

	@Override
	public boolean visit(ExportsDirective node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ExportsDirective)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ExportsDirective, node);
		}
		return true;
	}

	@Override
	public boolean visit(ExpressionMethodReference node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.BreakStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ExpressionMethodReference, node);
		}
		return true;
	}

	@Override
	public boolean visit(ExpressionStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ExpressionStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ExpressionStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(FieldAccess node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.FieldAccess)) {
			return this.helperVisitor.testPredicate(VisitorEnum.FieldAccess, node);
		}
		return true;
	}

	@Override
	public boolean visit(FieldDeclaration node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.FieldDeclaration)) {
			VisitorConfigData config = this.helperVisitor.getSupplierData().get(VisitorEnum.FieldDeclaration);
			if(config != null) {
				Class<?> typeof = config.getTypeof();
				if(typeof != null) {
					ITypeBinding binding= node.getType().resolveBinding();
					if(binding == null || !isTypeMatching(binding, typeof.getCanonicalName())) {
						return true;
					}
				}
				String superclassname = config.getSuperClassName();
				String annotationclass = config.getAnnotationName();
				if(superclassname != null && annotationclass != null) {
					boolean bothmatch=false;
					for (Object modifier : node.modifiers()) {
						if (modifier instanceof Annotation annotation) {
							ITypeBinding anotbinding = annotation.resolveTypeBinding();
							String annotationName = anotbinding.getQualifiedName();
							if (annotationName.equals(annotationclass)) {
								// Feld- oder Klassentyp des @Rule-Felds bestimmen
								VariableDeclarationFragment fragment = (VariableDeclarationFragment) node.fragments().get(0);
								ITypeBinding binding = fragment.resolveBinding().getType();
								// Prüfen, ob die Klasse von ExternalResource erbt
								if (isExternalResource(binding,superclassname)) {
									bothmatch=true;
								}
							}
						}
					}
					if(!bothmatch) {
						return true;
					}
				}
			}
			return this.helperVisitor.testPredicate(VisitorEnum.FieldDeclaration, node);
		}
		return true;
	}

	private static boolean isExternalResource(ITypeBinding typeBinding, String qualifiedname) {
		while (typeBinding != null) {
			if (typeBinding.getQualifiedName().equals(qualifiedname)) {
				return true;
			}
			typeBinding = typeBinding.getSuperclass();
		}
		return false;
	}
	
	@Override
	public boolean visit(ForStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ForStatement)) {
			VisitorConfigData config = this.helperVisitor.getSupplierData().get(VisitorEnum.ForStatement);
			if(config != null) {
				Class<?> typeof = config.getTypeof();
				if(typeof != null) {
					// Check if any initializer declares a variable of the specified type
					boolean typeMatches = false;
					for(Object init : node.initializers()) {
						if(init instanceof VariableDeclarationExpression vde) {
							ITypeBinding binding= vde.getType().resolveBinding();
							if(binding != null && isTypeMatching(binding, typeof.getCanonicalName())) {
								typeMatches = true;
								break;
							}
						}
					}
					if(!typeMatches) {
						return true;
					}
				}
			}
			return this.helperVisitor.testPredicate(VisitorEnum.ForStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(IfStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.IfStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.IfStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(ImportDeclaration node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ImportDeclaration)) {
			VisitorConfigData config = this.helperVisitor.getSupplierData().get(VisitorEnum.ImportDeclaration);
			if(config != null) {
				String data = config.getImportName();
				String fullyQualifiedName = node.getName().getFullyQualifiedName();
				if ((data!= null) && !fullyQualifiedName.equals(data)) {
					return true;
				}
			}
			return this.helperVisitor.testPredicate(VisitorEnum.ImportDeclaration, node);
		}
		return true;
	}

	@Override
	public boolean visit(InfixExpression node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.InfixExpression)) {
			VisitorConfigData config = this.helperVisitor.getSupplierData().get(VisitorEnum.InfixExpression);
			if(config != null && config.getOperator() != null) {
				InfixExpression.Operator operator = InfixExpression.Operator.toOperator(config.getOperator());
				if(operator != null && !node.getOperator().equals(operator)) {
					return true;
				}
			}
			return this.helperVisitor.testPredicate(VisitorEnum.InfixExpression, node);
		}
		return true;
	}

	@Override
	public boolean visit(Initializer node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.Initializer)) {
			return this.helperVisitor.testPredicate(VisitorEnum.Initializer, node);
		}
		return true;
	}

	@Override
	public boolean visit(InstanceofExpression node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.InstanceofExpression)) {
			return this.helperVisitor.testPredicate(VisitorEnum.InstanceofExpression, node);
		}
		return true;
	}

	@Override
	public boolean visit(IntersectionType node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.IntersectionType)) {
			return this.helperVisitor.testPredicate(VisitorEnum.IntersectionType, node);
		}
		return true;
	}

	@Override
	public boolean visit(Javadoc node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.Javadoc)) {
			return this.helperVisitor.testPredicate(VisitorEnum.Javadoc, node);
		}
		return true;
	}

	@Override
	public boolean visit(LabeledStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.LabeledStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.LabeledStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(LambdaExpression node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.LambdaExpression)) {
			return this.helperVisitor.testPredicate(VisitorEnum.LambdaExpression, node);
		}
		return true;
	}

	@Override
	public boolean visit(LineComment node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.LineComment)) {
			return this.helperVisitor.testPredicate(VisitorEnum.LineComment, node);
		}
		return true;
	}

	@Override
	public boolean visit(MarkerAnnotation node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.MarkerAnnotation)) {
			VisitorConfigData config = this.helperVisitor.getSupplierData().get(VisitorEnum.MarkerAnnotation);
			if(config != null) {
				String data = config.getAnnotationName();
				ITypeBinding binding = node.resolveTypeBinding();
				String fullyQualifiedName;
				if (binding != null) {
					fullyQualifiedName = binding.getQualifiedName();
				}else {
					fullyQualifiedName = node.getTypeName().getFullyQualifiedName();
				}
				if ((data!= null) && !fullyQualifiedName.equals(data)) {
					return true;
				}
			}
			return this.helperVisitor.testPredicate(VisitorEnum.MarkerAnnotation, node);
		}
		return true;
	}

	@Override
	public boolean visit(MemberRef node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.MemberRef)) {
			return this.helperVisitor.testPredicate(VisitorEnum.MemberRef, node);
		}
		return true;
	}

	@Override
	public boolean visit(MemberValuePair node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.MemberValuePair)) {
			return this.helperVisitor.testPredicate(VisitorEnum.MemberValuePair, node);
		}
		return true;
	}

	@Override
	public boolean visit(MethodRef node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.MethodRef)) {
			return this.helperVisitor.testPredicate(VisitorEnum.MethodRef, node);
		}
		return true;
	}

	@Override
	public boolean visit(MethodRefParameter node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.MethodRefParameter)) {
			return this.helperVisitor.testPredicate(VisitorEnum.MethodRefParameter, node);
		}
		return true;
	}

	@Override
	public boolean visit(MethodDeclaration node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.MethodDeclaration)) {
			VisitorConfigData config = this.helperVisitor.getSupplierData().get(VisitorEnum.MethodDeclaration);
			if(config != null) {
				String methodName = config.getMethodName();
				if(methodName != null && !node.getName().getIdentifier().equals(methodName)) {
					return true;
				}
			}
			return this.helperVisitor.testPredicate(VisitorEnum.MethodDeclaration, node);
		}
		return true;
	}

	@Override
	public boolean visit(MethodInvocation node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.MethodInvocation)) {
			VisitorConfigData config = this.helperVisitor.getSupplierData().get(VisitorEnum.MethodInvocation);
			if(config != null) {
				String data = config.getMethodName();
				if ((data!= null) && !node.getName().getIdentifier().equals(data)) {
					return true;
				}
				Class<?> typeof = config.getTypeof();
				String typeofByName = config.getTypeofByName();
				String canonicaltype = null;
				if(typeof != null) {
					canonicaltype = typeof.getCanonicalName();
				} else if(typeofByName != null) {
					canonicaltype = typeofByName;
				}
				if(canonicaltype != null) {
					String[] parameterTypesQualifiedNames = config.getParamTypeNames();
					if(parameterTypesQualifiedNames==null) {
						if (!usesGivenSignature(node, canonicaltype, data)) {
							return true;
						}
					} else
						if (!ASTNodes.usesGivenSignature(node, canonicaltype, data, parameterTypesQualifiedNames)) {
							return true;
						}
				}
			}
			return this.helperVisitor.testPredicate(VisitorEnum.MethodInvocation, node);
		}
		return true;
	}

	private static boolean usesGivenSignature(MethodInvocation node, String canonicaltype, String methodName) {
		IMethodBinding methodBinding= node.resolveMethodBinding();
		if(methodBinding==null) {
			if(!methodName.equals(node.getName().getIdentifier())){
				return false;
			}
		} else {
			if(!methodName.equals(methodBinding.getName())){
				return false;
			}
		}
		if(isClassQualifiedNameMatching(node,canonicaltype)){
			return true;
		}
		return false;
	}

	/**
	 * @param methodInvocation
	 * @param qualifiedName
	 * @return result
	 */
	public static boolean isClassQualifiedNameMatching(MethodInvocation methodInvocation, String qualifiedName) {
		Expression expression = methodInvocation.getExpression();
		if (expression != null) {
			ITypeBinding typeBinding = expression.resolveTypeBinding();
			if (typeBinding != null) {
				if (!typeBinding.isRecovered()) {
					return qualifiedName.equals(typeBinding.getQualifiedName());
				}
				if (expression instanceof SimpleName) {
					String startswith=typeBinding.toString().substring(9);
					startswith=startswith.substring(0, startswith.length()-1);
					return qualifiedName.endsWith(startswith);
				}
			}
		} else {
			IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
			if (methodBinding != null) {
				ITypeBinding declaringClass = methodBinding.getDeclaringClass();
				if (declaringClass != null) {
					return qualifiedName.equals(declaringClass.getQualifiedName());
				}
			}
		}
		return false;
	}

	/**
	 * Checks if the given type binding matches the specified qualified name.
	 * Handles both exact matches and inheritance checks.
	 *
	 * @param typeBinding the type binding to check
	 * @param qualifiedName the fully qualified name to match against
	 * @return true if the type matches, false otherwise
	 */
	private static boolean isTypeMatching(ITypeBinding typeBinding, String qualifiedName) {
		if (typeBinding == null || qualifiedName == null) {
			return false;
		}
		// For generic types, use erasure to get the raw type
		ITypeBinding erasedType = typeBinding.getErasure();
		// Check for exact match or simple name match
		if (qualifiedName.equals(erasedType.getQualifiedName()) || 
		    qualifiedName.equals(erasedType.getName())) {
			return true;
		}
		// Check superclasses and interfaces
		return isExternalResource(erasedType, qualifiedName);
	}

	@Override
	public boolean visit(Modifier node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.Modifier)) {
			return this.helperVisitor.testPredicate(VisitorEnum.Modifier, node);
		}
		return true;
	}

	@Override
	public boolean visit(ModuleDeclaration node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ModuleDeclaration)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ModuleDeclaration, node);
		}
		return true;
	}

	@Override
	public boolean visit(ModuleModifier node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ModuleModifier)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ModuleModifier, node);
		}
		return true;
	}

	@Override
	public boolean visit(NameQualifiedType node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.NameQualifiedType)) {
			return this.helperVisitor.testPredicate(VisitorEnum.NameQualifiedType, node);
		}
		return true;
	}

	@Override
	public boolean visit(NormalAnnotation node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.NormalAnnotation)) {
			VisitorConfigData config = this.helperVisitor.getSupplierData().get(VisitorEnum.NormalAnnotation);
			if(config != null) {
				String data = config.getAnnotationName();
				ITypeBinding binding = node.resolveTypeBinding();
				String fullyQualifiedName;
				if (binding != null) {
					fullyQualifiedName = binding.getQualifiedName();
				}else {
					fullyQualifiedName = node.getTypeName().getFullyQualifiedName();
				}
				if ((data!= null) && !fullyQualifiedName.equals(data)) {
					return true;
				}
			}
			return this.helperVisitor.testPredicate(VisitorEnum.NormalAnnotation, node);
		}
		return true;
	}

	@Override
	public boolean visit(NullLiteral node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.NullLiteral)) {
			return this.helperVisitor.testPredicate(VisitorEnum.NullLiteral, node);
		}
		return true;
	}

	@Override
	public boolean visit(NumberLiteral node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.NumberLiteral)) {
			return this.helperVisitor.testPredicate(VisitorEnum.NumberLiteral, node);
		}
		return true;
	}

	@Override
	public boolean visit(OpensDirective node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.OpensDirective)) {
			return this.helperVisitor.testPredicate(VisitorEnum.OpensDirective, node);
		}
		return true;
	}

	@Override
	public boolean visit(PackageDeclaration node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.PackageDeclaration)) {
			return this.helperVisitor.testPredicate(VisitorEnum.PackageDeclaration, node);
		}
		return true;
	}

	@Override
	public boolean visit(ParameterizedType node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ParameterizedType)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ParameterizedType, node);
		}
		return true;
	}

	@Override
	public boolean visit(ParenthesizedExpression node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ParenthesizedExpression)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ParenthesizedExpression, node);
		}
		return true;
	}

	@Override
	public boolean visit(PatternInstanceofExpression node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.PatternInstanceofExpression)) {
			return this.helperVisitor.testPredicate(VisitorEnum.PatternInstanceofExpression, node);
		}
		return true;
	}

	@Override
	public boolean visit(PostfixExpression node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.PostfixExpression)) {
			return this.helperVisitor.testPredicate(VisitorEnum.PostfixExpression, node);
		}
		return true;
	}

	@Override
	public boolean visit(PrefixExpression node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.PrefixExpression)) {
			return this.helperVisitor.testPredicate(VisitorEnum.PrefixExpression, node);
		}
		return true;
	}

	@Override
	public boolean visit(ProvidesDirective node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ProvidesDirective)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ProvidesDirective, node);
		}
		return true;
	}

	@Override
	public boolean visit(PrimitiveType node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.PrimitiveType)) {
			return this.helperVisitor.testPredicate(VisitorEnum.PrimitiveType, node);
		}
		return true;
	}

	@Override
	public boolean visit(QualifiedName node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.QualifiedName)) {
			return this.helperVisitor.testPredicate(VisitorEnum.QualifiedName, node);
		}
		return true;
	}

	@Override
	public boolean visit(QualifiedType node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.QualifiedType)) {
			return this.helperVisitor.testPredicate(VisitorEnum.QualifiedType, node);
		}
		return true;
	}

//	@Override
//	public boolean visit(ModuleQualifiedName node) {
//		if (this.helperVisitor.hasPredicate(VisitorEnum.ModuleQualifiedName)) {
//			return this.helperVisitor.testPredicate(VisitorEnum.ModuleQualifiedName, node);
//		}
//		return true;
//	}

	@Override
	public boolean visit(RequiresDirective node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.RequiresDirective)) {
			return this.helperVisitor.testPredicate(VisitorEnum.RequiresDirective, node);
		}
		return true;
	}

	@Override
	public boolean visit(RecordDeclaration node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.RecordDeclaration)) {
			return this.helperVisitor.testPredicate(VisitorEnum.RecordDeclaration, node);
		}
		return true;
	}

	@Override
	public boolean visit(ReturnStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ReturnStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ReturnStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(SimpleName node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.SimpleName)) {
			return this.helperVisitor.testPredicate(VisitorEnum.SimpleName, node);
		}
		return true;
	}

	@Override
	public boolean visit(SimpleType node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.SimpleType)) {
			return this.helperVisitor.testPredicate(VisitorEnum.SimpleType, node);
		}
		return true;
	}

	@Override
	public boolean visit(SingleMemberAnnotation node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.SingleMemberAnnotation)) {
			VisitorConfigData config = this.helperVisitor.getSupplierData().get(VisitorEnum.SingleMemberAnnotation);
			if(config != null) {
				String data = config.getAnnotationName();
				ITypeBinding binding = node.resolveTypeBinding();
				String fullyQualifiedName;
				if (binding != null) {
					fullyQualifiedName = binding.getQualifiedName();
				}else {
					fullyQualifiedName = node.getTypeName().getFullyQualifiedName();
				}
				if ((data!= null) && !fullyQualifiedName.equals(data)) {
					return true;
				}
			}
			return this.helperVisitor.testPredicate(VisitorEnum.SingleMemberAnnotation, node);
		}
		return true;
	}

	@Override
	public boolean visit(SingleVariableDeclaration node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.SingleVariableDeclaration)) {
			return this.helperVisitor.testPredicate(VisitorEnum.SingleVariableDeclaration, node);
		}
		return true;
	}

	@Override
	public boolean visit(StringLiteral node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.StringLiteral)) {
			return this.helperVisitor.testPredicate(VisitorEnum.StringLiteral, node);
		}
		return true;
	}

	@Override
	public boolean visit(SuperConstructorInvocation node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.SuperConstructorInvocation)) {
			return this.helperVisitor.testPredicate(VisitorEnum.SuperConstructorInvocation, node);
		}
		return true;
	}

	@Override
	public boolean visit(SuperFieldAccess node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.SuperFieldAccess)) {
			return this.helperVisitor.testPredicate(VisitorEnum.SuperFieldAccess, node);
		}
		return true;
	}

	@Override
	public boolean visit(SuperMethodInvocation node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.SuperMethodInvocation)) {
			VisitorConfigData config = this.helperVisitor.getSupplierData().get(VisitorEnum.SuperMethodInvocation);
			if(config != null) {
				String methodName = config.getMethodName();
				if(methodName != null && !node.getName().getIdentifier().equals(methodName)) {
					return true;
				}
			}
			return this.helperVisitor.testPredicate(VisitorEnum.SuperMethodInvocation, node);
		}
		return true;
	}

	@Override
	public boolean visit(SuperMethodReference node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.SuperMethodReference)) {
			return this.helperVisitor.testPredicate(VisitorEnum.SuperMethodReference, node);
		}
		return true;
	}

	@Override
	public boolean visit(SwitchCase node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.SwitchCase)) {
			return this.helperVisitor.testPredicate(VisitorEnum.SwitchCase, node);
		}
		return true;
	}

	@Override
	public boolean visit(SwitchExpression node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.SwitchExpression)) {
			return this.helperVisitor.testPredicate(VisitorEnum.SwitchExpression, node);
		}
		return true;
	}

	@Override
	public boolean visit(SwitchStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.SwitchStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.SwitchStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(SynchronizedStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.SynchronizedStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.SynchronizedStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(TagElement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.TagElement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.TagElement, node);
		}
		return true;
	}

	@Override
	public boolean visit(TextBlock node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.TextBlock)) {
			return this.helperVisitor.testPredicate(VisitorEnum.TextBlock, node);
		}
		return true;
	}

	@Override
	public boolean visit(TextElement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.TextElement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.TextElement, node);
		}
		return true;
	}

	@Override
	public boolean visit(ThisExpression node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ThisExpression)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ThisExpression, node);
		}
		return true;
	}

	@Override
	public boolean visit(ThrowStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.ThrowStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.ThrowStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(TryStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.TryStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.TryStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.TypeDeclaration)) {
			VisitorConfigData config = this.helperVisitor.getSupplierData().get(VisitorEnum.TypeDeclaration);
			if(config != null) {
				String typeName = config.getTypeName();
				if(typeName != null && !node.getName().getIdentifier().equals(typeName)) {
					return true;
				}
				String superclassname = config.getSuperClassName();
				if(superclassname != null) {
					boolean bothmatch=false;
					ITypeBinding binding = node.resolveBinding();
					if (isExternalResource(binding,superclassname)) {
						bothmatch=true;
					}
					if(!bothmatch) {
						return true;
					}
				}
			}
			return this.helperVisitor.testPredicate(VisitorEnum.TypeDeclaration, node);
		}
		return true;
	}

	@Override
	public boolean visit(TypeDeclarationStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.TypeDeclarationStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.TypeDeclarationStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(TypeLiteral node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.TypeLiteral)) {
			return this.helperVisitor.testPredicate(VisitorEnum.TypeLiteral, node);
		}
		return true;
	}

	@Override
	public boolean visit(TypeMethodReference node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.TypeMethodReference)) {
			return this.helperVisitor.testPredicate(VisitorEnum.TypeMethodReference, node);
		}
		return true;
	}

	@Override
	public boolean visit(TypeParameter node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.TypeParameter)) {
			return this.helperVisitor.testPredicate(VisitorEnum.TypeParameter, node);
		}
		return true;
	}

	@Override
	public boolean visit(UnionType node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.UnionType)) {
			return this.helperVisitor.testPredicate(VisitorEnum.UnionType, node);
		}
		return true;
	}

	@Override
	public boolean visit(UsesDirective node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.UsesDirective)) {
			return this.helperVisitor.testPredicate(VisitorEnum.UsesDirective, node);
		}
		return true;
	}

	@Override
	public boolean visit(VariableDeclarationExpression node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.VariableDeclarationExpression)) {
			return this.helperVisitor.testPredicate(VisitorEnum.VariableDeclarationExpression, node);
		}
		return true;
	}

	@Override
	public boolean visit(VariableDeclarationStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.VariableDeclarationStatement)) {
			VisitorConfigData config = this.helperVisitor.getSupplierData().get(VisitorEnum.VariableDeclarationStatement);
			if(config != null) {
				Class<?> data = config.getTypeof();
				if (data!= null) {
					VariableDeclarationFragment bli = (VariableDeclarationFragment) node.fragments().get(0);
					IVariableBinding resolveBinding = bli.resolveBinding();
					if(resolveBinding!=null) {
						String qualifiedName = resolveBinding.getType().getErasure().getQualifiedName();
						if (!data.getCanonicalName().equals(qualifiedName)) {
							return true;
						}
					}
				}
			}
			return this.helperVisitor.testPredicate(VisitorEnum.VariableDeclarationStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(VariableDeclarationFragment node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.VariableDeclarationFragment)) {
			return this.helperVisitor.testPredicate(VisitorEnum.VariableDeclarationFragment, node);
		}
		return true;
	}

	@Override
	public boolean visit(WhileStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.WhileStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.WhileStatement, node);
		}
		return true;
	}

	@Override
	public boolean visit(WildcardType node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.WildcardType)) {
			return this.helperVisitor.testPredicate(VisitorEnum.WildcardType, node);
		}
		return true;
	}

	@Override
	public boolean visit(YieldStatement node) {
		if (this.helperVisitor.hasPredicate(VisitorEnum.YieldStatement)) {
			return this.helperVisitor.testPredicate(VisitorEnum.YieldStatement, node);
		}
		return true;
	}

	@Override
	public void endVisit(AnnotationTypeDeclaration node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.AnnotationTypeDeclaration)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.AnnotationTypeDeclaration, node);
		}
	}

	@Override
	public void endVisit(AnnotationTypeMemberDeclaration node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.AnnotationTypeMemberDeclaration)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.AnnotationTypeMemberDeclaration, node);
		}
	}

	@Override
	public void endVisit(AnonymousClassDeclaration node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.AnonymousClassDeclaration)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.AnonymousClassDeclaration, node);
		}
	}

	@Override
	public void endVisit(ArrayAccess node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ArrayAccess)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ArrayAccess, node);
		}
	}

	@Override
	public void endVisit(ArrayCreation node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ArrayCreation)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ArrayCreation, node);
		}
	}

	@Override
	public void endVisit(ArrayInitializer node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ArrayInitializer)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ArrayInitializer, node);
		}
	}

	@Override
	public void endVisit(ArrayType node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ArrayType)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ArrayType, node);
		}
	}

	@Override
	public void endVisit(AssertStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.AssertStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.AssertStatement, node);
		}
	}

	@Override
	public void endVisit(Assignment node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.Assignment)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.Assignment, node);
		}
	}

	@Override
	public void endVisit(Block node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.Block)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.Block, node);
		}
	}

	@Override
	public void endVisit(BlockComment node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.BlockComment)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.BlockComment, node);
		}
	}

	@Override
	public void endVisit(BooleanLiteral node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.BooleanLiteral)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.BooleanLiteral, node);
		}
	}

	@Override
	public void endVisit(BreakStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.BreakStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.BreakStatement, node);
		}
	}

	@Override
	public void endVisit(CastExpression node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.CastExpression)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.CastExpression, node);
		}
	}

	@Override
	public void endVisit(CatchClause node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.CatchClause)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.CatchClause, node);
		}
	}

	@Override
	public void endVisit(CharacterLiteral node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.CharacterLiteral)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.CharacterLiteral, node);
		}
	}

	@Override
	public void endVisit(ClassInstanceCreation node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ClassInstanceCreation)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ClassInstanceCreation, node);
		}
	}

	@Override
	public void endVisit(CompilationUnit node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.CompilationUnit)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.CompilationUnit, node);
		}
	}

	@Override
	public void endVisit(ConditionalExpression node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ConditionalExpression)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ConditionalExpression, node);
		}
	}

	@Override
	public void endVisit(ConstructorInvocation node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ConstructorInvocation)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ConstructorInvocation, node);
		}
	}

	@Override
	public void endVisit(ContinueStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ContinueStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ContinueStatement, node);
		}
	}

	@Override
	public void endVisit(CreationReference node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.CreationReference)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.CreationReference, node);
		}
	}

	@Override
	public void endVisit(Dimension node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.Dimension)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.Dimension, node);
		}
	}

	@Override
	public void endVisit(DoStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.DoStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.DoStatement, node);
		}
	}

	@Override
	public void endVisit(EmptyStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.EmptyStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.EmptyStatement, node);
		}
	}

	@Override
	public void endVisit(EnhancedForStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.EnhancedForStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.EnhancedForStatement, node);
		}
	}

	@Override
	public void endVisit(EnumConstantDeclaration node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.EnumConstantDeclaration)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.EnumConstantDeclaration, node);
		}
	}

	@Override
	public void endVisit(EnumDeclaration node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.EnumDeclaration)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.EnumDeclaration, node);
		}
	}

	@Override
	public void endVisit(ExportsDirective node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ExportsDirective)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ExportsDirective, node);
		}
	}

	@Override
	public void endVisit(ExpressionMethodReference node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ExpressionMethodReference)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ExpressionMethodReference, node);
		}
	}

	@Override
	public void endVisit(ExpressionStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ExpressionStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ExpressionStatement, node);
		}
	}

	@Override
	public void endVisit(FieldAccess node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.FieldAccess)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.FieldAccess, node);
		}
	}

	@Override
	public void endVisit(FieldDeclaration node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.FieldDeclaration)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.FieldDeclaration, node);
		}
	}

	@Override
	public void endVisit(ForStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ForStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ForStatement, node);
		}
	}

	@Override
	public void endVisit(IfStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.IfStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.IfStatement, node);
		}
	}

	@Override
	public void endVisit(ImportDeclaration node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ImportDeclaration)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ImportDeclaration, node);
		}
	}

	@Override
	public void endVisit(InfixExpression node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.InfixExpression)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.InfixExpression, node);
		}
	}

	@Override
	public void endVisit(Initializer node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.Initializer)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.Initializer, node);
		}
	}

	@Override
	public void endVisit(InstanceofExpression node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.InstanceofExpression)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.InstanceofExpression, node);
		}
	}

	@Override
	public void endVisit(IntersectionType node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.IntersectionType)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.IntersectionType, node);
		}
	}

	@Override
	public void endVisit(Javadoc node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.Javadoc)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.Javadoc, node);
		}
	}

	@Override
	public void endVisit(LabeledStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.LabeledStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.LabeledStatement, node);
		}
	}

	@Override
	public void endVisit(LambdaExpression node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.LambdaExpression)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.LambdaExpression, node);
		}
	}

	@Override
	public void endVisit(LineComment node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.LineComment)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.LineComment, node);
		}
	}

	@Override
	public void endVisit(MarkerAnnotation node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.MarkerAnnotation)) {
			VisitorConfigData config = this.helperVisitor.getConsumerData().get(VisitorEnum.MarkerAnnotation);
			if(config != null) {
				String data = config.getAnnotationName();
				ITypeBinding binding = node.resolveTypeBinding();
				String fullyQualifiedName;
				if (binding != null) {
					fullyQualifiedName = binding.getQualifiedName();
				}else {
					fullyQualifiedName = node.getTypeName().getFullyQualifiedName();
				}
				if ((data!= null) && !fullyQualifiedName.equals(data)) {
					return;
				}
			}
			this.helperVisitor.acceptConsumer(VisitorEnum.MarkerAnnotation, node);
		}
	}

	@Override
	public void endVisit(MemberRef node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.MemberRef)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.MemberRef, node);
		}
	}

	@Override
	public void endVisit(MemberValuePair node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.MemberValuePair)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.MemberValuePair, node);
		}
	}

	@Override
	public void endVisit(MethodRef node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.MethodRef)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.MethodRef, node);
		}
	}

	@Override
	public void endVisit(MethodRefParameter node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.MethodRefParameter)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.MethodRefParameter, node);
		}
	}

	@Override
	public void endVisit(MethodDeclaration node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.MethodDeclaration)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.MethodDeclaration, node);
		}
	}

	@Override
	public void endVisit(MethodInvocation node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.MethodInvocation)) {
			VisitorConfigData config = this.helperVisitor.getConsumerData().get(VisitorEnum.MethodInvocation);
			if(config != null) {
				String data = config.getMethodName();
				if ((data!= null) && !node.getName().getIdentifier().equals(data)) {
					return;
				}
				Class<?> typeof = config.getTypeof();
				String typeofByName = config.getTypeofByName();
				String canonicaltype = null;
				if(typeof != null) {
					canonicaltype = typeof.getCanonicalName();
				} else if(typeofByName != null) {
					canonicaltype = typeofByName;
				}
				if(canonicaltype != null) {
					if (!ASTNodes.usesGivenSignature(node, canonicaltype, data)) {
						return;
					}
				}
			}
			this.helperVisitor.acceptConsumer(VisitorEnum.MethodInvocation, node);
		}
	}

	@Override
	public void endVisit(Modifier node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.Modifier)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.Modifier, node);
		}
	}

	@Override
	public void endVisit(ModuleDeclaration node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ModuleDeclaration)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ModuleDeclaration, node);
		}
	}

	@Override
	public void endVisit(ModuleModifier node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ModuleModifier)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ModuleModifier, node);
		}
	}

	@Override
	public void endVisit(NameQualifiedType node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.NameQualifiedType)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.NameQualifiedType, node);
		}
	}

	@Override
	public void endVisit(NormalAnnotation node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.NormalAnnotation)) {
			VisitorConfigData config = this.helperVisitor.getConsumerData().get(VisitorEnum.NormalAnnotation);
			if(config != null) {
				String data = config.getAnnotationName();
				ITypeBinding binding = node.resolveTypeBinding();
				String fullyQualifiedName;
				if (binding != null) {
					fullyQualifiedName = binding.getQualifiedName();
				}else {
					fullyQualifiedName = node.getTypeName().getFullyQualifiedName();
				}
				if ((data!= null) && !fullyQualifiedName.equals(data)) {
					return;
				}
			}
			this.helperVisitor.acceptConsumer(VisitorEnum.NormalAnnotation, node);
		}
	}

	@Override
	public void endVisit(NullLiteral node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.NullLiteral)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.NullLiteral, node);
		}
	}

	@Override
	public void endVisit(NumberLiteral node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.NumberLiteral)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.NumberLiteral, node);
		}
	}

	@Override
	public void endVisit(OpensDirective node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.OpensDirective)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.OpensDirective, node);
		}
	}

	@Override
	public void endVisit(PackageDeclaration node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.PackageDeclaration)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.PackageDeclaration, node);
		}
	}

	@Override
	public void endVisit(ParameterizedType node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ParameterizedType)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ParameterizedType, node);
		}
	}

	@Override
	public void endVisit(ParenthesizedExpression node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ParenthesizedExpression)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ParenthesizedExpression, node);
		}
	}

	@Override
	public void endVisit(PatternInstanceofExpression node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.PatternInstanceofExpression)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.PatternInstanceofExpression, node);
		}
	}

	@Override
	public void endVisit(PostfixExpression node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.PostfixExpression)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.PostfixExpression, node);
		}
	}

	@Override
	public void endVisit(PrefixExpression node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.PrefixExpression)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.PrefixExpression, node);
		}
	}

	@Override
	public void endVisit(ProvidesDirective node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ProvidesDirective)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ProvidesDirective, node);
		}
	}

	@Override
	public void endVisit(PrimitiveType node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.PrimitiveType)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.PrimitiveType, node);
		}
	}

	@Override
	public void endVisit(QualifiedName node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.QualifiedName)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.QualifiedName, node);
		}
	}

	@Override
	public void endVisit(QualifiedType node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.QualifiedType)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.QualifiedType, node);
		}
	}

	@Override
	public void endVisit(ModuleQualifiedName node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ModuleQualifiedName)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ModuleQualifiedName, node);
		}
	}

	@Override
	public void endVisit(RequiresDirective node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.RequiresDirective)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.RequiresDirective, node);
		}
	}

	@Override
	public void endVisit(RecordDeclaration node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.RecordDeclaration)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.RecordDeclaration, node);
		}
	}

	@Override
	public void endVisit(ReturnStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ReturnStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ReturnStatement, node);
		}
	}

	@Override
	public void endVisit(SimpleName node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.SimpleName)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.SimpleName, node);
		}
	}

	@Override
	public void endVisit(SimpleType node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.SimpleType)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.SimpleType, node);
		}
	}

	@Override
	public void endVisit(SingleMemberAnnotation node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.SingleMemberAnnotation)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.SingleMemberAnnotation, node);
		}
	}

	@Override
	public void endVisit(SingleVariableDeclaration node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.SingleVariableDeclaration)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.SingleVariableDeclaration, node);
		}
	}

	@Override
	public void endVisit(StringLiteral node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.StringLiteral)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.StringLiteral, node);
		}
	}

	@Override
	public void endVisit(SuperConstructorInvocation node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.SuperConstructorInvocation)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.SuperConstructorInvocation, node);
		}
	}

	@Override
	public void endVisit(SuperFieldAccess node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.SuperFieldAccess)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.SuperFieldAccess, node);
		}
	}

	@Override
	public void endVisit(SuperMethodInvocation node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.SuperMethodInvocation)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.SuperMethodInvocation, node);
		}
	}

	@Override
	public void endVisit(SuperMethodReference node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.SuperMethodReference)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.SuperMethodReference, node);
		}
	}

	@Override
	public void endVisit(SwitchCase node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.SwitchCase)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.SwitchCase, node);
		}
	}

	@Override
	public void endVisit(SwitchExpression node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.SwitchExpression)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.SwitchExpression, node);
		}
	}

	@Override
	public void endVisit(SwitchStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.SwitchStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.SwitchStatement, node);
		}
	}

	@Override
	public void endVisit(SynchronizedStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.SynchronizedStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.SynchronizedStatement, node);
		}
	}

	@Override
	public void endVisit(TagElement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.TagElement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.TagElement, node);
		}
	}

	@Override
	public void endVisit(TextBlock node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.TextBlock)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.TextBlock, node);
		}
	}

	@Override
	public void endVisit(TextElement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.TextElement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.TextElement, node);
		}
	}

	@Override
	public void endVisit(ThisExpression node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ThisExpression)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ThisExpression, node);
		}
	}

	@Override
	public void endVisit(ThrowStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.ThrowStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.ThrowStatement, node);
		}
	}

	@Override
	public void endVisit(TryStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.TryStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.TryStatement, node);
		}
	}

	@Override
	public void endVisit(TypeDeclaration node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.TypeDeclaration)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.TypeDeclaration, node);
		}
	}

	@Override
	public void endVisit(TypeDeclarationStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.TypeDeclarationStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.TypeDeclarationStatement, node);
		}
	}

	@Override
	public void endVisit(TypeLiteral node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.TypeLiteral)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.TypeLiteral, node);
		}
	}

	@Override
	public void endVisit(TypeMethodReference node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.TypeMethodReference)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.TypeMethodReference, node);
		}
	}

	@Override
	public void endVisit(TypeParameter node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.TypeParameter)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.TypeParameter, node);
		}
	}

	@Override
	public void endVisit(UnionType node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.UnionType)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.UnionType, node);
		}
	}

	@Override
	public void endVisit(UsesDirective node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.UsesDirective)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.UsesDirective, node);
		}
	}

	@Override
	public void endVisit(VariableDeclarationExpression node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.VariableDeclarationExpression)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.VariableDeclarationExpression, node);
		}
	}

	@Override
	public void endVisit(VariableDeclarationStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.VariableDeclarationStatement)) {
			VisitorConfigData config = this.helperVisitor.getConsumerData().get(VisitorEnum.VariableDeclarationStatement);
			if(config != null) {
				Class<?> data = config.getTypeof();
				if (data!= null) {
					VariableDeclarationFragment bli = (VariableDeclarationFragment) node.fragments().get(0);
					IVariableBinding resolveBinding = bli.resolveBinding();
					if(resolveBinding!=null) {
						String qualifiedName = resolveBinding.getType().getErasure().getQualifiedName();
						if (!data.getCanonicalName().equals(qualifiedName)) {
							return;
						}
					}
				}
			}
			this.helperVisitor.acceptConsumer(VisitorEnum.VariableDeclarationStatement, node);
		}
	}

	@Override
	public void endVisit(VariableDeclarationFragment node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.VariableDeclarationFragment)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.VariableDeclarationFragment, node);
		}
	}

	@Override
	public void endVisit(WhileStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.WhileStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.WhileStatement, node);
		}
	}

	@Override
	public void endVisit(WildcardType node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.WildcardType)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.WildcardType, node);
		}
	}

	@Override
	public void endVisit(YieldStatement node) {
		if (this.helperVisitor.hasConsumer(VisitorEnum.YieldStatement)) {
			this.helperVisitor.acceptConsumer(VisitorEnum.YieldStatement, node);
		}
	}
}
