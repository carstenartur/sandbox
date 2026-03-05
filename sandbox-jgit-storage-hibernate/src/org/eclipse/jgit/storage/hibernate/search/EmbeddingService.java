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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;

/**
 * Service for generating semantic embeddings from text using a local ONNX
 * model.
 * <p>
 * Uses Deep Java Library (DJL) with ONNX Runtime to run the
 * {@code all-MiniLM-L6-v2} sentence-transformer model locally on CPU. The
 * model produces 384-dimensional float vectors suitable for cosine similarity
 * search via Hibernate Search's {@code @VectorField}.
 * </p>
 * <p>
 * The model is lazily initialized on first use and cached for the lifetime of
 * the service. If the model cannot be loaded (e.g., no network on first run,
 * corrupted cache), the service degrades gracefully — {@link #embed(String)}
 * returns {@code null} and callers should set {@code hasEmbedding = false}.
 * </p>
 *
 * <h3>Configuration (environment variables)</h3>
 * <ul>
 * <li>{@code JGIT_EMBEDDING_ENABLED} — set to {@code false} to disable
 * embedding generation entirely (default: {@code true})</li>
 * <li>{@code JGIT_EMBEDDING_MODEL_DIR} — local directory for cached model
 * files (default: DJL default cache {@code ~/.djl.ai/cache})</li>
 * <li>{@code JGIT_EMBEDDING_MODEL_NAME} — HuggingFace model ID (default:
 * {@code sentence-transformers/all-MiniLM-L6-v2})</li>
 * </ul>
 *
 * @see ai.djl.repository.zoo.ZooModel
 */
public class EmbeddingService {

	private static final Logger LOG = Logger
			.getLogger(EmbeddingService.class.getName());

	/** Embedding vector dimension produced by all-MiniLM-L6-v2. */
	public static final int EMBEDDING_DIMENSION = 384;

	/** Maximum token length supported by the model. */
	private static final int MAX_TOKEN_LENGTH = 512;

	/** Default model name. */
	private static final String DEFAULT_MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2"; //$NON-NLS-1$

	private final boolean enabled;

	private final String modelName;

	private final String modelDir;

	private volatile ZooModel<String, float[]> model;

	private volatile boolean initAttempted;

	private volatile boolean available;

	/**
	 * Create an embedding service with default configuration from environment
	 * variables.
	 */
	public EmbeddingService() {
		this(isEnabledFromEnv(), getModelNameFromEnv(), getModelDirFromEnv());
	}

	/**
	 * Create an embedding service with explicit configuration.
	 *
	 * @param enabled
	 *            whether embedding generation is enabled
	 * @param modelName
	 *            the HuggingFace model identifier
	 * @param modelDir
	 *            local model cache directory, or {@code null} for DJL default
	 */
	public EmbeddingService(boolean enabled, String modelName,
			String modelDir) {
		this.enabled = enabled;
		this.modelName = modelName;
		this.modelDir = modelDir;
	}

	/**
	 * Generate a semantic embedding vector for the given text.
	 * <p>
	 * The text is truncated to the model's maximum token length (512 tokens)
	 * before encoding. If the model is not available or embedding is disabled,
	 * returns {@code null}.
	 * </p>
	 *
	 * @param text
	 *            the input text to embed
	 * @return a 384-dimensional float array, or {@code null} if embedding is
	 *         unavailable
	 */
	public float[] embed(String text) {
		if (!enabled || text == null || text.isBlank()) {
			return null;
		}
		ensureInitialized();
		if (!available || model == null) {
			return null;
		}
		try {
			String truncated = truncateToTokenLimit(text);
			try (Predictor<String, float[]> predictor = model
					.newPredictor()) {
				return predictor.predict(truncated);
			}
		} catch (TranslateException e) {
			LOG.log(Level.WARNING,
					"Failed to generate embedding", e); //$NON-NLS-1$
			return null;
		}
	}

	/**
	 * Build the embedding input text from Java source metadata.
	 * <p>
	 * Combines class name, documentation, method signatures and package name
	 * into a single string optimized for semantic search.
	 * </p>
	 *
	 * @param simpleClassName
	 *            the simple class name (may be null)
	 * @param typeDocumentation
	 *            the Javadoc documentation (may be null)
	 * @param methodSignatures
	 *            newline-separated method signatures (may be null)
	 * @param packageName
	 *            the package name (may be null)
	 * @return the combined embedding input text
	 */
	public static String buildEmbeddingText(String simpleClassName,
			String typeDocumentation, String methodSignatures,
			String packageName) {
		StringBuilder sb = new StringBuilder();
		if (simpleClassName != null && !simpleClassName.isEmpty()) {
			sb.append(simpleClassName);
		}
		if (typeDocumentation != null && !typeDocumentation.isEmpty()) {
			if (sb.length() > 0) {
				sb.append(": "); //$NON-NLS-1$
			}
			sb.append(typeDocumentation);
		}
		if (methodSignatures != null && !methodSignatures.isEmpty()) {
			if (sb.length() > 0) {
				sb.append("\nMethods: "); //$NON-NLS-1$
			}
			sb.append(methodSignatures);
		}
		if (packageName != null && !packageName.isEmpty()) {
			if (sb.length() > 0) {
				sb.append("\nPackage: "); //$NON-NLS-1$
			}
			sb.append(packageName);
		}
		return sb.toString();
	}

	/**
	 * Check if the embedding service is available (model loaded successfully).
	 *
	 * @return {@code true} if embeddings can be generated
	 */
	public boolean isAvailable() {
		if (!enabled) {
			return false;
		}
		ensureInitialized();
		return available;
	}

	/**
	 * Check if embedding generation is enabled.
	 *
	 * @return {@code true} if embedding is enabled
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * Close the model and release resources.
	 */
	public void close() {
		if (model != null) {
			model.close();
			model = null;
			available = false;
		}
	}

	private synchronized void ensureInitialized() {
		if (initAttempted) {
			return;
		}
		initAttempted = true;
		try {
			LOG.log(Level.INFO,
					"Initializing embedding model: {0}", modelName); //$NON-NLS-1$
			Criteria.Builder<String, float[]> builder = Criteria.builder()
					.setTypes(String.class, float[].class)
					.optModelUrls(
							"djl://ai.djl.huggingface.pytorch/" //$NON-NLS-1$
									+ modelName)
					.optEngine("OnnxRuntime") //$NON-NLS-1$
					.optTranslatorFactory(
							new ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory());
			if (modelDir != null && !modelDir.isEmpty()) {
				Path dir = Paths.get(modelDir);
				System.setProperty("DJL_CACHE_DIR", //$NON-NLS-1$
						dir.toAbsolutePath().toString());
			}
			model = builder.build().loadModel();
			available = true;
			LOG.log(Level.INFO,
					"Embedding model loaded successfully: {0}", //$NON-NLS-1$
					modelName);
		} catch (ModelNotFoundException | MalformedModelException
				| IOException e) {
			LOG.log(Level.WARNING,
					"Failed to load embedding model — vector search disabled. " //$NON-NLS-1$
							+ "Full-text search remains functional.", //$NON-NLS-1$
					e);
			available = false;
		}
	}

	private static String truncateToTokenLimit(String text) {
		// Simple character-based approximation: ~4 chars per token for English
		int maxChars = MAX_TOKEN_LENGTH * 4;
		if (text.length() > maxChars) {
			return text.substring(0, maxChars);
		}
		return text;
	}

	private static boolean isEnabledFromEnv() {
		String val = System.getenv("JGIT_EMBEDDING_ENABLED"); //$NON-NLS-1$
		return val == null || !"false".equalsIgnoreCase(val); //$NON-NLS-1$
	}

	private static String getModelNameFromEnv() {
		String val = System.getenv("JGIT_EMBEDDING_MODEL_NAME"); //$NON-NLS-1$
		return val != null && !val.isEmpty() ? val : DEFAULT_MODEL_NAME;
	}

	private static String getModelDirFromEnv() {
		return System.getenv("JGIT_EMBEDDING_MODEL_DIR"); //$NON-NLS-1$
	}
}
