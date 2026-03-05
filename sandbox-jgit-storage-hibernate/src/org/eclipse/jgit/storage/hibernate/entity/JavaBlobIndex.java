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

import java.time.Instant;

import org.eclipse.jgit.storage.hibernate.search.EmbeddingService;
import org.hibernate.annotations.Nationalized;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.VectorSimilarity;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.VectorField;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
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
@Table(name = "java_blob_index", indexes = {
		@Index(name = "idx_blob_repo", columnList = "repository_name"),
		@Index(name = "idx_blob_blob_oid", columnList = "blob_object_id"),
		@Index(name = "idx_blob_commit_oid", columnList = "commit_object_id"),
		@Index(name = "idx_blob_repo_commit", columnList = "repository_name, commit_object_id") })
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
	@Nationalized
	@Column(name = "file_path", length = 1024)
	private String filePath;

	@Nationalized
	@KeywordField
	@Column(name = "package_name", length = 512)
	private String packageName;

	@FullTextField(analyzer = "dotQualifiedName")
	@Nationalized
	@Column(name = "declared_types", length = 65535)
	private String declaredTypes;

	@FullTextField(analyzer = "dotQualifiedName")
	@Nationalized
	@Column(name = "fully_qualified_names", length = 65535)
	private String fullyQualifiedNames;

	@FullTextField(analyzer = "javaIdentifier")
	@Nationalized
	@Column(name = "declared_methods", length = 65535)
	private String declaredMethods;

	@FullTextField(analyzer = "javaIdentifier")
	@Nationalized
	@Column(name = "declared_fields", length = 65535)
	private String declaredFields;

	@FullTextField(analyzer = "dotQualifiedName")
	@Nationalized
	@Column(name = "extends_types", length = 65535)
	private String extendsTypes;

	@FullTextField(analyzer = "dotQualifiedName")
	@Nationalized
	@Column(name = "implements_types", length = 65535)
	private String implementsTypes;

	@FullTextField(analyzer = "dotQualifiedName")
	@Nationalized
	@Column(name = "import_statements", length = 65535)
	private String importStatements;

	@FullTextField(analyzer = "javaSourceEcj")
	@Nationalized
	@Column(name = "source_snippet", length = 65535)
	private String sourceSnippet;

	@KeywordField
	@Nationalized
	@Column(name = "project_name", length = 512)
	private String projectName;

	@FullTextField(analyzer = "javaIdentifier")
	@Nationalized
	@Column(name = "simple_class_name", length = 512)
	private String simpleClassName;

	@KeywordField
	@Column(name = "type_kind", length = 32)
	private String typeKind;

	@KeywordField
	@Column(name = "visibility", length = 64)
	private String visibility;

	@FullTextField(analyzer = "dotQualifiedName")
	@Nationalized
	@Column(name = "annotations", length = 65535)
	private String annotations;

	@GenericField
	@Column(name = "line_count")
	private int lineCount;

	@FullTextField(analyzer = "commitMessage")
	@Nationalized
	@Column(name = "type_documentation", length = 2000)
	private String typeDocumentation;

	@FullTextField(analyzer = "javaIdentifier")
	@Nationalized
	@Column(name = "method_signatures", length = 65535)
	private String methodSignatures;

	@FullTextField(analyzer = "dotQualifiedName")
	@Nationalized
	@Column(name = "referenced_types", length = 65535)
	private String referencedTypes;

	@FullTextField(analyzer = "genericContent")
	@Nationalized
	@Column(name = "string_literals", length = 65535)
	private String stringLiterals;

	@GenericField
	@Column(name = "has_main_method")
	private boolean hasMainMethod;

	@KeywordField
	@Nationalized
	@Column(name = "commit_author")
	private String commitAuthor;

	@GenericField
	@Column(name = "commit_date")
	private Instant commitDate;

	@VectorField(dimension = EmbeddingService.EMBEDDING_DIMENSION,
			searchable = Searchable.YES,
			vectorSimilarity = VectorSimilarity.COSINE)
	@Lob
	@Convert(converter = FloatArrayConverter.class)
	@Column(name = "semantic_embedding")
	private float[] semanticEmbedding;

	@GenericField
	@Column(name = "has_embedding")
	private boolean hasEmbedding;

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

	/**
	 * Get the project name.
	 *
	 * @return the projectName
	 */
	public String getProjectName() {
		return projectName;
	}

	/**
	 * Set the project name.
	 *
	 * @param projectName
	 *            the project name
	 */
	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	/**
	 * Get the simple class name.
	 *
	 * @return the simpleClassName
	 */
	public String getSimpleClassName() {
		return simpleClassName;
	}

	/**
	 * Set the simple class name.
	 *
	 * @param simpleClassName
	 *            the simple class name
	 */
	public void setSimpleClassName(String simpleClassName) {
		this.simpleClassName = simpleClassName;
	}

	/**
	 * Get the type kind (class, interface, enum, annotation).
	 *
	 * @return the typeKind
	 */
	public String getTypeKind() {
		return typeKind;
	}

	/**
	 * Set the type kind.
	 *
	 * @param typeKind
	 *            the type kind
	 */
	public void setTypeKind(String typeKind) {
		this.typeKind = typeKind;
	}

	/**
	 * Get the visibility modifier string.
	 *
	 * @return the visibility
	 */
	public String getVisibility() {
		return visibility;
	}

	/**
	 * Set the visibility modifier string.
	 *
	 * @param visibility
	 *            the visibility
	 */
	public void setVisibility(String visibility) {
		this.visibility = visibility;
	}

	/**
	 * Get the newline-separated annotation names.
	 *
	 * @return the annotations
	 */
	public String getAnnotations() {
		return annotations;
	}

	/**
	 * Set the newline-separated annotation names.
	 *
	 * @param annotations
	 *            newline-separated annotation names
	 */
	public void setAnnotations(String annotations) {
		this.annotations = annotations;
	}

	/**
	 * Get the line count.
	 *
	 * @return the lineCount
	 */
	public int getLineCount() {
		return lineCount;
	}

	/**
	 * Set the line count.
	 *
	 * @param lineCount
	 *            the line count
	 */
	public void setLineCount(int lineCount) {
		this.lineCount = lineCount;
	}

	/**
	 * Get the type documentation (Javadoc on primary type).
	 *
	 * @return the typeDocumentation
	 */
	public String getTypeDocumentation() {
		return typeDocumentation;
	}

	/**
	 * Set the type documentation.
	 *
	 * @param typeDocumentation
	 *            the type documentation, truncated to 2000 chars
	 */
	public void setTypeDocumentation(String typeDocumentation) {
		this.typeDocumentation = typeDocumentation;
	}

	/**
	 * Get the newline-separated method signatures.
	 *
	 * @return the methodSignatures
	 */
	public String getMethodSignatures() {
		return methodSignatures;
	}

	/**
	 * Set the newline-separated method signatures.
	 *
	 * @param methodSignatures
	 *            newline-separated method signatures
	 */
	public void setMethodSignatures(String methodSignatures) {
		this.methodSignatures = methodSignatures;
	}

	/**
	 * Get the newline-separated referenced types.
	 *
	 * @return the referencedTypes
	 */
	public String getReferencedTypes() {
		return referencedTypes;
	}

	/**
	 * Set the newline-separated referenced types.
	 *
	 * @param referencedTypes
	 *            newline-separated referenced type names
	 */
	public void setReferencedTypes(String referencedTypes) {
		this.referencedTypes = referencedTypes;
	}

	/**
	 * Get the newline-separated string literals.
	 *
	 * @return the stringLiterals
	 */
	public String getStringLiterals() {
		return stringLiterals;
	}

	/**
	 * Set the newline-separated string literals.
	 *
	 * @param stringLiterals
	 *            newline-separated string literal values
	 */
	public void setStringLiterals(String stringLiterals) {
		this.stringLiterals = stringLiterals;
	}

	/**
	 * Check if a main method was detected.
	 *
	 * @return true if a main method was found
	 */
	public boolean isHasMainMethod() {
		return hasMainMethod;
	}

	/**
	 * Set whether a main method was detected.
	 *
	 * @param hasMainMethod
	 *            true if a main method was found
	 */
	public void setHasMainMethod(boolean hasMainMethod) {
		this.hasMainMethod = hasMainMethod;
	}

	/**
	 * Get the commit author name.
	 *
	 * @return the commitAuthor
	 */
	public String getCommitAuthor() {
		return commitAuthor;
	}

	/**
	 * Set the commit author name.
	 *
	 * @param commitAuthor
	 *            the commit author
	 */
	public void setCommitAuthor(String commitAuthor) {
		this.commitAuthor = commitAuthor;
	}

	/**
	 * Get the commit date.
	 *
	 * @return the commitDate
	 */
	public Instant getCommitDate() {
		return commitDate;
	}

	/**
	 * Set the commit date.
	 *
	 * @param commitDate
	 *            the commit date
	 */
	public void setCommitDate(Instant commitDate) {
		this.commitDate = commitDate;
	}

	/**
	 * Get the semantic embedding vector.
	 *
	 * @return the 384-dimensional embedding, or {@code null} if not computed
	 */
	public float[] getSemanticEmbedding() {
		return semanticEmbedding;
	}

	/**
	 * Set the semantic embedding vector.
	 *
	 * @param semanticEmbedding
	 *            the 384-dimensional embedding
	 */
	public void setSemanticEmbedding(float[] semanticEmbedding) {
		this.semanticEmbedding = semanticEmbedding;
	}

	/**
	 * Check if a semantic embedding has been computed for this blob.
	 *
	 * @return {@code true} if an embedding is available
	 */
	public boolean isHasEmbedding() {
		return hasEmbedding;
	}

	/**
	 * Set whether a semantic embedding has been computed.
	 *
	 * @param hasEmbedding
	 *            {@code true} if an embedding is available
	 */
	public void setHasEmbedding(boolean hasEmbedding) {
		this.hasEmbedding = hasEmbedding;
	}
}
