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
package org.eclipse.jgit.storage.hibernate.search.strategies;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jgit.storage.hibernate.search.BlobIndexData;
import org.eclipse.jgit.storage.hibernate.search.FileTypeStrategy;
import org.eclipse.jgit.storage.hibernate.search.JavaStructureVisitor;

/**
 * Strategy for extracting structural metadata from Java source files using
 * JDT's AST parser.
 */
public class JavaFileStrategy implements FileTypeStrategy {

	private static final Logger LOG = Logger
			.getLogger(JavaFileStrategy.class.getName());

	private static final int MAX_SNIPPET_LENGTH = 65535;

	@Override
	public Set<String> supportedExtensions() {
		return Set.of(".java"); //$NON-NLS-1$
	}

	@Override
	public Set<String> supportedFilenames() {
		return Collections.emptySet();
	}

	@Override
	public BlobIndexData extract(String source, String filePath) {
		BlobIndexData data = new BlobIndexData();
		data.setFileType("java"); //$NON-NLS-1$
		data.setSourceSnippet(truncate(source, MAX_SNIPPET_LENGTH));

		try {
			@SuppressWarnings("deprecation")
			ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
			parser.setResolveBindings(false);
			parser.setSource(source.toCharArray());
			parser.setKind(ASTParser.K_COMPILATION_UNIT);

			CompilationUnit cu = (CompilationUnit) parser.createAST(null);

			if (cu.getPackage() != null) {
				data.setPackageOrNamespace(cu.getPackage().getName()
						.getFullyQualifiedName());
			}

			Map<String, String> importMap = buildImportMap(cu);
			data.setImportStatements(serializeImports(cu));

			JavaStructureVisitor visitor = new JavaStructureVisitor(
					importMap, data.getPackageOrNamespace());
			cu.accept(visitor);

			data.setDeclaredTypes(visitor.getTypes());
			data.setFullyQualifiedNames(visitor.getFQNs());
			data.setDeclaredMethods(visitor.getMethods());
			data.setDeclaredFields(visitor.getFields());
			data.setExtendsTypes(visitor.getSuperTypes());
			data.setImplementsTypes(visitor.getInterfaces());
		} catch (Exception e) {
			// Graceful degradation: return partial results on parse errors
			LOG.log(Level.WARNING,
					"Failed to parse Java source: {0}: {1} - returning partial results", //$NON-NLS-1$
					new Object[] { filePath, e.getMessage() });
		}

		return data;
	}

	@Override
	public String fileType() {
		return "java"; //$NON-NLS-1$
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
}
