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
package org.eclipse.jgit.storage.hibernate.service;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jgit.storage.hibernate.entity.JavaBlobIndex;
import org.eclipse.jgit.storage.hibernate.search.JavaStructureVisitor;

/**
 * Extracts structural metadata from Java source code using JDT's
 * {@link ASTParser} without bindings.
 * <p>
 * This extractor parses Java source files into an AST and visits the tree to
 * collect package names, declared types, methods, fields, supertypes,
 * interfaces, and import statements. No classpath or binding resolution is
 * required.
 * </p>
 */
public class JavaBlobExtractor {

	private static final Logger LOG = Logger
			.getLogger(JavaBlobExtractor.class.getName());

	private static final int MAX_SNIPPET_LENGTH = 65535;

	/**
	 * Extract structural metadata from a Java source file.
	 *
	 * @param source
	 *            the Java source code
	 * @param filePath
	 *            the file path within the repository
	 * @param repoName
	 *            the repository name
	 * @param blobOid
	 *            the blob object SHA-1
	 * @param commitOid
	 *            the commit object SHA-1
	 * @return a populated {@link JavaBlobIndex} entity
	 */
	public JavaBlobIndex extract(String source, String filePath,
			String repoName, String blobOid, String commitOid) {
		JavaBlobIndex idx = new JavaBlobIndex();
		idx.setRepositoryName(repoName);
		idx.setBlobObjectId(blobOid);
		idx.setCommitObjectId(commitOid);
		idx.setFilePath(filePath);
		idx.setSourceSnippet(truncate(source, MAX_SNIPPET_LENGTH));

		if (!filePath.endsWith(".java")) { //$NON-NLS-1$
			return idx;
		}

		try {
			@SuppressWarnings("deprecation")
			ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
			parser.setResolveBindings(false);
			parser.setSource(source.toCharArray());
			parser.setKind(ASTParser.K_COMPILATION_UNIT);

			CompilationUnit cu = (CompilationUnit) parser.createAST(null);

			// Extract package
			if (cu.getPackage() != null) {
				idx.setPackageName(cu.getPackage().getName()
						.getFullyQualifiedName());
			}

			// Build import map for FQN resolution of simple names
			Map<String, String> importMap = buildImportMap(cu);
			idx.setImportStatements(serializeImports(cu));

			// Walk AST for types, methods, fields, extends, implements
			JavaStructureVisitor visitor = new JavaStructureVisitor(importMap,
					idx.getPackageName());
			cu.accept(visitor);

			idx.setDeclaredTypes(visitor.getTypes());
			idx.setFullyQualifiedNames(visitor.getFQNs());
			idx.setDeclaredMethods(visitor.getMethods());
			idx.setDeclaredFields(visitor.getFields());
			idx.setExtendsTypes(visitor.getSuperTypes());
			idx.setImplementsTypes(visitor.getInterfaces());
			idx.setAnnotations(visitor.getAnnotations());
			idx.setTypeKind(visitor.getTypeKind());
			idx.setVisibility(visitor.getVisibility());
			idx.setSimpleClassName(extractSimpleClassName(filePath));
			idx.setProjectName(extractProjectName(filePath));
			idx.setLineCount(countLines(source));
			idx.setTypeDocumentation(visitor.getTypeDocumentation());
			idx.setMethodSignatures(visitor.getMethodSignatures());
			idx.setReferencedTypes(visitor.getReferencedTypes());
			idx.setStringLiterals(visitor.getStringLiterals());
			idx.setHasMainMethod(visitor.hasMainMethod());
		} catch (Exception e) {
			// Graceful degradation: return partial results on parse errors
			LOG.log(Level.WARNING,
					"Failed to parse Java source: {0}: {1} - returning partial results", //$NON-NLS-1$
					new Object[] { filePath, e.getMessage() });
		}

		return idx;
	}

	private static Map<String, String> buildImportMap(CompilationUnit cu) {
		Map<String, String> importMap = new HashMap<>();
		for (Object imp : cu.imports()) {
			if (imp instanceof ImportDeclaration importDecl) {
				String fqn = importDecl.getName().getFullyQualifiedName();
				if (!importDecl.isOnDemand()) {
					String simpleName = fqn
							.substring(fqn.lastIndexOf('.') + 1);
					importMap.put(simpleName, fqn);
				}
			}
		}
		return importMap;
	}

	private static String serializeImports(CompilationUnit cu) {
		StringBuilder sb = new StringBuilder();
		for (Object imp : cu.imports()) {
			if (imp instanceof ImportDeclaration importDecl) {
				if (sb.length() > 0) {
					sb.append('\n');
				}
				sb.append(importDecl.getName().getFullyQualifiedName());
				if (importDecl.isOnDemand()) {
					sb.append(".*"); //$NON-NLS-1$
				}
			}
		}
		return sb.toString();
	}

	private static String truncate(String text, int maxLength) {
		if (text == null) {
			return null;
		}
		if (text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength);
	}

	private static String extractProjectName(String filePath) {
		int srcIdx = filePath.indexOf("/src/"); //$NON-NLS-1$
		if (srcIdx < 0) {
			srcIdx = filePath.indexOf("/tst/"); //$NON-NLS-1$
		}
		if (srcIdx > 0) {
			String beforeSrc = filePath.substring(0, srcIdx);
			int lastSlash = beforeSrc.lastIndexOf('/');
			return lastSlash >= 0 ? beforeSrc.substring(lastSlash + 1)
					: beforeSrc;
		}
		return null;
	}

	private static String extractSimpleClassName(String filePath) {
		int lastSlash = filePath.lastIndexOf('/');
		String filename = lastSlash >= 0 ? filePath.substring(lastSlash + 1)
				: filePath;
		if (filename.endsWith(".java")) { //$NON-NLS-1$
			return filename.substring(0,
					filename.length() - ".java".length()); //$NON-NLS-1$
		}
		return filename;
	}

	private static int countLines(String source) {
		if (source == null || source.isEmpty()) {
			return 0;
		}
		int count = 1;
		for (int i = 0; i < source.length(); i++) {
			if (source.charAt(i) == '\n') {
				count++;
			}
		}
		return count;
	}
}
