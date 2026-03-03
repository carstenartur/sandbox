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
package org.eclipse.jgit.storage.hibernate.entity;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity for indexing structural metadata extracted from Java source blobs.
 * <p>
 * Fields are populated by parsing {@code .java} files with JDT's AST parser
 * (without bindings). This enables searching by type name, method name, field
 * name, fully qualified name, and inheritance relationships without requiring
 * a full classpath.
 * </p>
 */
@Indexed
@Entity
@Table(name = "java_blob_index")
public class JavaBlobIndex {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@KeywordField
	@Column(name = "repository_name", nullable = false)
	private String repositoryName;

	@KeywordField
	@Column(name = "blob_object_id", length = 40, nullable = false)
	private String blobObjectId;

	@KeywordField
	@Column(name = "commit_object_id", length = 40, nullable = false)
	private String commitObjectId;

	@KeywordField
	@Column(name = "file_type", length = 32)
	private String fileType;

	@FullTextField(analyzer = "javaPath")
	@Column(name = "file_path", length = 1024)
	private String filePath;

	@KeywordField
	@Column(name = "package_name", length = 512)
	private String packageName;

	@FullTextField(analyzer = "dotQualifiedName")
	@Column(name = "declared_types", length = 65535)
	private String declaredTypes;

	@FullTextField(analyzer = "dotQualifiedName")
	@Column(name = "fully_qualified_names", length = 65535)
	private String fullyQualifiedNames;

	@FullTextField(analyzer = "javaIdentifier")
	@Column(name = "declared_methods", length = 65535)
	private String declaredMethods;

	@FullTextField(analyzer = "javaIdentifier")
	@Column(name = "declared_fields", length = 65535)
	private String declaredFields;

	@FullTextField(analyzer = "dotQualifiedName")
	@Column(name = "extends_types", length = 65535)
	private String extendsTypes;

	@FullTextField(analyzer = "dotQualifiedName")
	@Column(name = "implements_types", length = 65535)
	private String implementsTypes;

	@FullTextField(analyzer = "dotQualifiedName")
	@Column(name = "import_statements", length = 65535)
	private String importStatements;

	@FullTextField(analyzer = "javaSourceEcj")
	@Column(name = "source_snippet", length = 65535)
	private String sourceSnippet;

	/** Default constructor for JPA. */
	public JavaBlobIndex() {
	}

	/**
	 * Get the primary key.
	 *
	 * @return the id
	 */
	public Long getId() {
		return id;
	}

	/**
	 * Set the primary key.
	 *
	 * @param id
	 *            the id
	 */
	public void setId(Long id) {
		this.id = id;
	}

	/**
	 * Get the repository name.
	 *
	 * @return the repositoryName
	 */
	public String getRepositoryName() {
		return repositoryName;
	}

	/**
	 * Set the repository name.
	 *
	 * @param repositoryName
	 *            the repository name
	 */
	public void setRepositoryName(String repositoryName) {
		this.repositoryName = repositoryName;
	}

	/**
	 * Get the blob SHA-1.
	 *
	 * @return the blobObjectId
	 */
	public String getBlobObjectId() {
		return blobObjectId;
	}

	/**
	 * Set the blob SHA-1.
	 *
	 * @param blobObjectId
	 *            the SHA-1 hex string
	 */
	public void setBlobObjectId(String blobObjectId) {
		this.blobObjectId = blobObjectId;
	}

	/**
	 * Get the commit SHA-1 that introduced this blob.
	 *
	 * @return the commitObjectId
	 */
	public String getCommitObjectId() {
		return commitObjectId;
	}

	/**
	 * Set the commit SHA-1.
	 *
	 * @param commitObjectId
	 *            the SHA-1 hex string
	 */
	public void setCommitObjectId(String commitObjectId) {
		this.commitObjectId = commitObjectId;
	}

	/**
	 * Get the file type (e.g., "java", "xml", "properties").
	 *
	 * @return the fileType
	 */
	public String getFileType() {
		return fileType;
	}

	/**
	 * Set the file type.
	 *
	 * @param fileType
	 *            the file type identifier
	 */
	public void setFileType(String fileType) {
		this.fileType = fileType;
	}

	/**
	 * Get the file path within the repository.
	 *
	 * @return the filePath
	 */
	public String getFilePath() {
		return filePath;
	}

	/**
	 * Set the file path.
	 *
	 * @param filePath
	 *            the relative path
	 */
	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	/**
	 * Get the package name.
	 *
	 * @return the packageName
	 */
	public String getPackageName() {
		return packageName;
	}

	/**
	 * Set the package name.
	 *
	 * @param packageName
	 *            the Java package name
	 */
	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	/**
	 * Get the newline-separated declared type names.
	 *
	 * @return the declaredTypes
	 */
	public String getDeclaredTypes() {
		return declaredTypes;
	}

	/**
	 * Set the newline-separated declared type names.
	 *
	 * @param declaredTypes
	 *            newline-separated type names
	 */
	public void setDeclaredTypes(String declaredTypes) {
		this.declaredTypes = declaredTypes;
	}

	/**
	 * Get the newline-separated fully qualified names.
	 *
	 * @return the fullyQualifiedNames
	 */
	public String getFullyQualifiedNames() {
		return fullyQualifiedNames;
	}

	/**
	 * Set the newline-separated fully qualified names.
	 *
	 * @param fullyQualifiedNames
	 *            newline-separated FQNs
	 */
	public void setFullyQualifiedNames(String fullyQualifiedNames) {
		this.fullyQualifiedNames = fullyQualifiedNames;
	}

	/**
	 * Get the newline-separated declared method names.
	 *
	 * @return the declaredMethods
	 */
	public String getDeclaredMethods() {
		return declaredMethods;
	}

	/**
	 * Set the newline-separated declared method names.
	 *
	 * @param declaredMethods
	 *            newline-separated method names
	 */
	public void setDeclaredMethods(String declaredMethods) {
		this.declaredMethods = declaredMethods;
	}

	/**
	 * Get the newline-separated declared field names.
	 *
	 * @return the declaredFields
	 */
	public String getDeclaredFields() {
		return declaredFields;
	}

	/**
	 * Set the newline-separated declared field names.
	 *
	 * @param declaredFields
	 *            newline-separated field names
	 */
	public void setDeclaredFields(String declaredFields) {
		this.declaredFields = declaredFields;
	}

	/**
	 * Get the newline-separated supertypes.
	 *
	 * @return the extendsTypes
	 */
	public String getExtendsTypes() {
		return extendsTypes;
	}

	/**
	 * Set the newline-separated supertypes.
	 *
	 * @param extendsTypes
	 *            newline-separated type names
	 */
	public void setExtendsTypes(String extendsTypes) {
		this.extendsTypes = extendsTypes;
	}

	/**
	 * Get the newline-separated implemented interfaces.
	 *
	 * @return the implementsTypes
	 */
	public String getImplementsTypes() {
		return implementsTypes;
	}

	/**
	 * Set the newline-separated implemented interfaces.
	 *
	 * @param implementsTypes
	 *            newline-separated type names
	 */
	public void setImplementsTypes(String implementsTypes) {
		this.implementsTypes = implementsTypes;
	}

	/**
	 * Get the newline-separated import statements.
	 *
	 * @return the importStatements
	 */
	public String getImportStatements() {
		return importStatements;
	}

	/**
	 * Set the newline-separated import statements.
	 *
	 * @param importStatements
	 *            newline-separated imports
	 */
	public void setImportStatements(String importStatements) {
		this.importStatements = importStatements;
	}

	/**
	 * Get the source code snippet (first 64KB).
	 *
	 * @return the sourceSnippet
	 */
	public String getSourceSnippet() {
		return sourceSnippet;
	}

	/**
	 * Set the source code snippet.
	 *
	 * @param sourceSnippet
	 *            the source text
	 */
	public void setSourceSnippet(String sourceSnippet) {
		this.sourceSnippet = sourceSnippet;
	}
}
