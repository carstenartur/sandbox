/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Parses pattern strings into AST nodes.
 * 
 * <p>This parser handles both expression and statement patterns by embedding them
 * in a minimal wrapper structure (fake class and method) to create valid Java code
 * that can be parsed by the Eclipse JDT parser.</p>
 * 
 * @since 1.2.2
 */
public class PatternParser {
	
	/**
	 * Parses a pattern into an AST node.
	 * 
	 * @param pattern the pattern to parse
	 * @return the parsed AST node (Expression, Statement, Annotation, MethodInvocation, ImportDeclaration, 
	 *         FieldDeclaration, ClassInstanceCreation, or MethodDeclaration), or {@code null} if parsing fails
	 */
	public ASTNode parse(Pattern pattern) {
		if (pattern == null) {
			return null;
		}
		
		String patternValue = pattern.getValue();
		PatternKind kind = pattern.getKind();
		
		if (kind == PatternKind.EXPRESSION) {
			return parseExpression(patternValue);
		} else if (kind == PatternKind.STATEMENT) {
			return parseStatement(patternValue);
		} else if (kind == PatternKind.ANNOTATION) {
			return parseAnnotation(patternValue);
		} else if (kind == PatternKind.METHOD_CALL) {
			return parseMethodCall(patternValue);
		} else if (kind == PatternKind.IMPORT) {
			return parseImport(patternValue);
		} else if (kind == PatternKind.FIELD) {
			return parseField(patternValue);
		} else if (kind == PatternKind.CONSTRUCTOR) {
			return parseConstructor(patternValue);
		} else if (kind == PatternKind.METHOD_DECLARATION) {
			return parseMethodDeclaration(patternValue);
		}
		
		return null;
	}
	
	/**
	 * Parses an expression pattern.
	 * 
	 * @param expressionSnippet the expression snippet (e.g., {@code "$x + 1"})
	 * @return the parsed Expression node, or {@code null} if parsing fails
	 */
	private Expression parseExpression(String expressionSnippet) {
		// Wrap the expression in a minimal method context
		String source = "class _Pattern { void _method() { _result = " + expressionSnippet + "; } }"; //$NON-NLS-1$ //$NON-NLS-2$
		
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setCompilerOptions(JavaCore.getOptions());
		
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		
		// Navigate to the expression: CompilationUnit -> TypeDeclaration -> MethodDeclaration -> Block -> ExpressionStatement -> Assignment -> right-hand side
		if (cu.types().isEmpty()) {
			return null;
		}
		
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		if (typeDecl.getMethods().length == 0) {
			return null;
		}
		
		MethodDeclaration method = typeDecl.getMethods()[0];
		if (method.getBody() == null || method.getBody().statements().isEmpty()) {
			return null;
		}
		
		Statement stmt = (Statement) method.getBody().statements().get(0);
		if (stmt instanceof org.eclipse.jdt.core.dom.ExpressionStatement) {
			org.eclipse.jdt.core.dom.ExpressionStatement exprStmt = (org.eclipse.jdt.core.dom.ExpressionStatement) stmt;
			Expression expr = exprStmt.getExpression();
			
			// If it's an assignment, extract the right-hand side
			if (expr instanceof org.eclipse.jdt.core.dom.Assignment) {
				org.eclipse.jdt.core.dom.Assignment assignment = (org.eclipse.jdt.core.dom.Assignment) expr;
				return assignment.getRightHandSide();
			}
			
			return expr;
		}
		
		return null;
	}
	
	/**
	 * Parses a statement pattern.
	 * 
	 * @param statementSnippet the statement snippet (e.g., {@code "if ($cond) $then;"})
	 * @return the parsed Statement node, or {@code null} if parsing fails
	 */
	private Statement parseStatement(String statementSnippet) {
		// Wrap the statement in a minimal method context
		String source = "class _Pattern { void _method() { " + statementSnippet + " } }"; //$NON-NLS-1$ //$NON-NLS-2$
		
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setCompilerOptions(JavaCore.getOptions());
		
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		
		// Navigate to the statement: CompilationUnit -> TypeDeclaration -> MethodDeclaration -> Block -> Statement
		if (cu.types().isEmpty()) {
			return null;
		}
		
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		if (typeDecl.getMethods().length == 0) {
			return null;
		}
		
		MethodDeclaration method = typeDecl.getMethods()[0];
		if (method.getBody() == null || method.getBody().statements().isEmpty()) {
			return null;
		}
		
		return (Statement) method.getBody().statements().get(0);
	}
	
	/**
	 * Parses an annotation pattern.
	 * 
	 * @param annotationSnippet the annotation snippet (e.g., {@code "@Before"}, {@code "@Test(expected=$ex)"})
	 * @return the parsed Annotation node, or {@code null} if parsing fails
	 * @since 1.2.3
	 */
	private Annotation parseAnnotation(String annotationSnippet) {
		// Wrap the annotation in a minimal class context
		String source = annotationSnippet + " class _Pattern {}"; //$NON-NLS-1$
		
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setCompilerOptions(JavaCore.getOptions());
		
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		
		// Navigate to the annotation: CompilationUnit -> TypeDeclaration -> modifiers
		if (cu.types().isEmpty()) {
			return null;
		}
		
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		if (typeDecl.modifiers().isEmpty()) {
			return null;
		}
		
		Object firstModifier = typeDecl.modifiers().get(0);
		if (!(firstModifier instanceof Annotation)) {
			return null;
		}
		
		return (Annotation) firstModifier;
	}
	
	/**
	 * Parses a method call pattern.
	 * 
	 * @param methodCallSnippet the method call snippet (e.g., {@code "Assert.assertEquals($a, $b)"})
	 * @return the parsed MethodInvocation node, or {@code null} if parsing fails
	 * @since 1.2.3
	 */
	private MethodInvocation parseMethodCall(String methodCallSnippet) {
		// Wrap the method call in a minimal method context
		String source = "class _Pattern { void _method() { " + methodCallSnippet + "; } }"; //$NON-NLS-1$ //$NON-NLS-2$
		
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setCompilerOptions(JavaCore.getOptions());
		
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		
		// Navigate to the method invocation: CompilationUnit -> TypeDeclaration -> MethodDeclaration -> Block -> ExpressionStatement -> Expression
		if (cu.types().isEmpty()) {
			return null;
		}
		
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		if (typeDecl.getMethods().length == 0) {
			return null;
		}
		
		MethodDeclaration method = typeDecl.getMethods()[0];
		if (method.getBody() == null || method.getBody().statements().isEmpty()) {
			return null;
		}
		
		Statement stmt = (Statement) method.getBody().statements().get(0);
		if (stmt instanceof org.eclipse.jdt.core.dom.ExpressionStatement) {
			org.eclipse.jdt.core.dom.ExpressionStatement exprStmt = (org.eclipse.jdt.core.dom.ExpressionStatement) stmt;
			Expression expr = exprStmt.getExpression();
			
			if (expr instanceof MethodInvocation) {
				return (MethodInvocation) expr;
			}
		}
		
		return null;
	}
	
	/**
	 * Parses an import pattern.
	 * 
	 * @param importSnippet the import snippet (e.g., {@code "import org.junit.Assert"})
	 * @return the parsed ImportDeclaration node, or {@code null} if parsing fails
	 * @since 1.2.3
	 */
	private ImportDeclaration parseImport(String importSnippet) {
		// Ensure the import statement ends with a semicolon
		String importStatement = importSnippet.endsWith(";") ? importSnippet : importSnippet + ";"; //$NON-NLS-1$ //$NON-NLS-2$
		String source = importStatement + " class _Pattern {}"; //$NON-NLS-1$
		
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setCompilerOptions(JavaCore.getOptions());
		
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		
		// Navigate to the import: CompilationUnit -> imports
		if (cu.imports().isEmpty()) {
			return null;
		}
		
		return (ImportDeclaration) cu.imports().get(0);
	}
	
	/**
	 * Parses a field pattern.
	 * 
	 * @param fieldSnippet the field snippet (e.g., {@code "@Rule public TemporaryFolder $name"})
	 * @return the parsed FieldDeclaration node, or {@code null} if parsing fails
	 * @since 1.2.3
	 */
	private FieldDeclaration parseField(String fieldSnippet) {
		// Ensure the field declaration ends with a semicolon
		String fieldStatement = fieldSnippet.endsWith(";") ? fieldSnippet : fieldSnippet + ";"; //$NON-NLS-1$ //$NON-NLS-2$
		String source = "class _Pattern { " + fieldStatement + " }"; //$NON-NLS-1$ //$NON-NLS-2$
		
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setCompilerOptions(JavaCore.getOptions());
		
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		
		// Navigate to the field: CompilationUnit -> TypeDeclaration -> FieldDeclaration
		if (cu.types().isEmpty()) {
			return null;
		}
		
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		if (typeDecl.getFields().length == 0) {
			return null;
		}
		
		return typeDecl.getFields()[0];
	}
	
	/**
	 * Parses a constructor pattern.
	 * 
	 * @param constructorSnippet the constructor snippet (e.g., {@code "new String($bytes, $enc)"})
	 * @return the parsed ClassInstanceCreation node, or {@code null} if parsing fails
	 * @since 1.2.5
	 */
	private ClassInstanceCreation parseConstructor(String constructorSnippet) {
		// Wrap the constructor expression in a minimal method context
		String source = "class _Pattern { void _method() { Object _result = " + constructorSnippet + "; } }"; //$NON-NLS-1$ //$NON-NLS-2$
		
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setCompilerOptions(JavaCore.getOptions());
		
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		
		// Navigate to the constructor: CompilationUnit -> TypeDeclaration -> MethodDeclaration -> Block -> VariableDeclarationStatement -> VariableDeclarationFragment -> initializer (ClassInstanceCreation)
		if (cu.types().isEmpty()) {
			return null;
		}
		
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		if (typeDecl.getMethods().length == 0) {
			return null;
		}
		
		MethodDeclaration method = typeDecl.getMethods()[0];
		if (method.getBody() == null || method.getBody().statements().isEmpty()) {
			return null;
		}
		
		Statement stmt = (Statement) method.getBody().statements().get(0);
		if (stmt instanceof org.eclipse.jdt.core.dom.VariableDeclarationStatement) {
			org.eclipse.jdt.core.dom.VariableDeclarationStatement varDeclStmt = (org.eclipse.jdt.core.dom.VariableDeclarationStatement) stmt;
			if (!varDeclStmt.fragments().isEmpty()) {
				Object fragment = varDeclStmt.fragments().get(0);
				if (fragment instanceof org.eclipse.jdt.core.dom.VariableDeclarationFragment) {
					org.eclipse.jdt.core.dom.VariableDeclarationFragment varFrag = (org.eclipse.jdt.core.dom.VariableDeclarationFragment) fragment;
					Expression initializer = varFrag.getInitializer();
					if (initializer instanceof ClassInstanceCreation) {
						return (ClassInstanceCreation) initializer;
					}
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Parses a method declaration pattern.
	 * 
	 * @param methodSnippet the method declaration snippet (e.g., {@code "void dispose()"}, {@code "void $name($params$)"})
	 * @return the parsed MethodDeclaration node, or {@code null} if parsing fails
	 * @since 1.2.6
	 */
	private MethodDeclaration parseMethodDeclaration(String methodSnippet) {
		// Normalize the snippet: add empty body if not present
		String normalizedSnippet = methodSnippet.trim();
		if (!normalizedSnippet.endsWith("}") && !normalizedSnippet.endsWith(";")) { //$NON-NLS-1$ //$NON-NLS-2$
			normalizedSnippet = normalizedSnippet + " {}"; //$NON-NLS-1$
		}
		
		// Handle multi-placeholder parameters: "$params$" -> "Object... $params$"
		// This makes the pattern syntactically valid for the Java parser
		normalizedSnippet = normalizedSnippet.replaceAll("\\(\\s*\\$([a-zA-Z_][a-zA-Z0-9_]*)\\$\\s*\\)", "(Object... \\$$1\\$)"); //$NON-NLS-1$ //$NON-NLS-2$
		
		// Wrap the method declaration in a class context
		String source = "class _Pattern { " + normalizedSnippet + " }"; //$NON-NLS-1$ //$NON-NLS-2$
		
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setCompilerOptions(JavaCore.getOptions());
		
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		
		// Navigate to the method: CompilationUnit -> TypeDeclaration -> MethodDeclaration
		if (cu.types().isEmpty()) {
			return null;
		}
		
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		if (typeDecl.getMethods().length == 0) {
			return null;
		}
		
		return typeDecl.getMethods()[0];
	}
}
