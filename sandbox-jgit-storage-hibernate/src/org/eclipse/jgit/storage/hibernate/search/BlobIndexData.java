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
package org.eclipse.jgit.storage.hibernate.search;

/**
 * Data transfer object that holds searchable metadata extracted from a file.
 * <p>
 * Each {@link FileTypeStrategy} populates the fields it can extract. Fields
 * that are not applicable to a particular file type remain {@code null}.
 * </p>
 */
public class BlobIndexData {

	private String fileType;

	private String packageOrNamespace;

	private String declaredTypes;

	private String fullyQualifiedNames;

	private String declaredMethods;

	private String declaredFields;

	private String extendsTypes;

	private String implementsTypes;

	private String importStatements;

	private String sourceSnippet;

	/**
	 * Get the file type identifier.
	 *
	 * @return the file type
	 */
	public String getFileType() {
		return fileType;
	}

	/**
	 * Set the file type identifier.
	 *
	 * @param fileType
	 *            the file type
	 */
	public void setFileType(String fileType) {
		this.fileType = fileType;
	}

	/**
	 * Get the package or namespace.
	 *
	 * @return the package or namespace
	 */
	public String getPackageOrNamespace() {
		return packageOrNamespace;
	}

	/**
	 * Set the package or namespace.
	 *
	 * @param packageOrNamespace
	 *            the package or namespace
	 */
	public void setPackageOrNamespace(String packageOrNamespace) {
		this.packageOrNamespace = packageOrNamespace;
	}

	/**
	 * Get the declared types.
	 *
	 * @return the declared types
	 */
	public String getDeclaredTypes() {
		return declaredTypes;
	}

	/**
	 * Set the declared types.
	 *
	 * @param declaredTypes
	 *            the declared types
	 */
	public void setDeclaredTypes(String declaredTypes) {
		this.declaredTypes = declaredTypes;
	}

	/**
	 * Get the fully qualified names.
	 *
	 * @return the fully qualified names
	 */
	public String getFullyQualifiedNames() {
		return fullyQualifiedNames;
	}

	/**
	 * Set the fully qualified names.
	 *
	 * @param fullyQualifiedNames
	 *            the fully qualified names
	 */
	public void setFullyQualifiedNames(String fullyQualifiedNames) {
		this.fullyQualifiedNames = fullyQualifiedNames;
	}

	/**
	 * Get the declared methods.
	 *
	 * @return the declared methods
	 */
	public String getDeclaredMethods() {
		return declaredMethods;
	}

	/**
	 * Set the declared methods.
	 *
	 * @param declaredMethods
	 *            the declared methods
	 */
	public void setDeclaredMethods(String declaredMethods) {
		this.declaredMethods = declaredMethods;
	}

	/**
	 * Get the declared fields.
	 *
	 * @return the declared fields
	 */
	public String getDeclaredFields() {
		return declaredFields;
	}

	/**
	 * Set the declared fields.
	 *
	 * @param declaredFields
	 *            the declared fields
	 */
	public void setDeclaredFields(String declaredFields) {
		this.declaredFields = declaredFields;
	}

	/**
	 * Get the extends types.
	 *
	 * @return the extends types
	 */
	public String getExtendsTypes() {
		return extendsTypes;
	}

	/**
	 * Set the extends types.
	 *
	 * @param extendsTypes
	 *            the extends types
	 */
	public void setExtendsTypes(String extendsTypes) {
		this.extendsTypes = extendsTypes;
	}

	/**
	 * Get the implements types.
	 *
	 * @return the implements types
	 */
	public String getImplementsTypes() {
		return implementsTypes;
	}

	/**
	 * Set the implements types.
	 *
	 * @param implementsTypes
	 *            the implements types
	 */
	public void setImplementsTypes(String implementsTypes) {
		this.implementsTypes = implementsTypes;
	}

	/**
	 * Get the import statements.
	 *
	 * @return the import statements
	 */
	public String getImportStatements() {
		return importStatements;
	}

	/**
	 * Set the import statements.
	 *
	 * @param importStatements
	 *            the import statements
	 */
	public void setImportStatements(String importStatements) {
		this.importStatements = importStatements;
	}

	/**
	 * Get the source snippet.
	 *
	 * @return the source snippet
	 */
	public String getSourceSnippet() {
		return sourceSnippet;
	}

	/**
	 * Set the source snippet.
	 *
	 * @param sourceSnippet
	 *            the source snippet
	 */
	public void setSourceSnippet(String sourceSnippet) {
		this.sourceSnippet = sourceSnippet;
	}
}
