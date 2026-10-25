/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import ai.djl.MalformedModelException;
import ai.djl.engine.EngineException;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.translate.TranslateException;

/** Owns a lazy local DJL/ONNX model independently of the released storage modules. */
public final class LocalEmbeddingEncoder implements EmbeddingEncoder {
  private static final Logger LOG = Logger.getLogger(LocalEmbeddingEncoder.class.getName());
  private static final String DEFAULT_MODEL = "sentence-transformers/all-MiniLM-L6-v2";
  @FunctionalInterface interface Loader { Model load() throws IOException; }
  interface Model extends Closeable { float[] embed(String text) throws IOException; }
  private final Loader loader;
  private volatile Status status;
  private Model model;

  public LocalEmbeddingEncoder() {
    this(!"false".equalsIgnoreCase(System.getenv("JGIT_EMBEDDING_ENABLED")),
        environment("JGIT_EMBEDDING_MODEL_NAME", DEFAULT_MODEL), System.getenv("JGIT_EMBEDDING_MODEL_DIR"));
  }

  public LocalEmbeddingEncoder(boolean enabled, String modelName, String modelDirectory) {
    this(enabled, () -> loadDjl(modelName, modelDirectory));
  }

  LocalEmbeddingEncoder(boolean enabled, Loader loader) {
    this.loader = Objects.requireNonNull(loader);
    status = enabled ? Status.NOT_INITIALIZED : Status.DISABLED;
  }

  @Override public Status status() { return status; }

  /** Serializes model initialization/prediction with close; ordinary status reads never wait. */
  @Override public synchronized float[] embed(String text) {
    if (text == null || text.isBlank() || status == Status.DISABLED || status == Status.UNAVAILABLE || status == Status.CLOSED)
      return null;
    if (status == Status.NOT_INITIALIZED) {
      try {
        model = Objects.requireNonNull(loader.load(), "model"); status = Status.AVAILABLE;
      } catch (IOException | EngineException | LinkageError unavailable) {
        unavailable(unavailable); return null;
      }
    }
    try {
      return model.embed(truncate(text));
    } catch (IOException failedPrediction) {
      preserveInterruption(failedPrediction);
      LOG.log(Level.WARNING, "Optional embedding prediction failed; later inputs may still be encoded", failedPrediction);
      return null;
    } catch (EngineException | LinkageError unavailable) {
      unavailable(unavailable);
      return null;
    }
  }

  private void unavailable(Throwable failure) {
    status = Status.UNAVAILABLE;
    preserveInterruption(failure);
    LOG.log(Level.WARNING, "Optional local embeddings unavailable; text search remains available", failure);
  }

  private static void preserveInterruption(Throwable failure) {
    var visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
    for (Throwable current = failure; current != null && visited.add(current); current = current.getCause()) {
      if (current instanceof InterruptedException || (current instanceof java.io.InterruptedIOException
          && !(current instanceof java.net.SocketTimeoutException))) {
        Thread.currentThread().interrupt(); return;
      }
    }
  }

  @Override public synchronized void close() throws IOException {
    if (status == Status.CLOSED) return;
    status = Status.CLOSED;
    try { if (model != null) model.close(); }
    finally { model = null; }
  }

  /** Retains Sandbox's established class/documentation/method/package input formatting. */
  public static String inputText(String simpleName, String documentation, String signatures, String packageName) {
    var text = new StringBuilder();
    append(text, simpleName, ""); append(text, documentation, ": ");
    append(text, signatures, "\nMethods: "); append(text, packageName, "\nPackage: ");
    return text.toString();
  }

  private static void append(StringBuilder target, String value, String separator) {
    if (value == null || value.isEmpty()) return;
    if (!target.isEmpty()) target.append(separator);
    target.append(value);
  }

  private static String truncate(String text) {
    if (text.length() <= 2048) return text;
    int end = 2048;
    if (Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) end--;
    return text.substring(0, end);
  }

  private static String environment(String name, String fallback) {
    String value = System.getenv(name); return value == null || value.isEmpty() ? fallback : value;
  }

  private static Model loadDjl(String modelName, String modelDirectory) throws IOException {
    var criteria = Criteria.builder().setTypes(String.class, float[].class)
        .optModelUrls("djl://ai.djl.huggingface.pytorch/" + modelName).optEngine("OnnxRuntime")
        .optTranslatorFactory(new ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory());
    if (modelDirectory != null && !modelDirectory.isEmpty()) {
      System.setProperty("DJL_CACHE_DIR", Path.of(modelDirectory).toAbsolutePath().toString());
    }
    try {
      var loaded = criteria.build().loadModel();
      return new Model() {
        @Override public float[] embed(String text) throws IOException {
          try (var predictor = loaded.newPredictor()) { return predictor.predict(text); }
          catch (TranslateException failure) { throw new IOException("Local embedding prediction failed", failure); }
        }
        @Override public void close() { loaded.close(); }
      };
    } catch (ModelNotFoundException | MalformedModelException failure) {
      throw new IOException("Local embedding model could not be loaded", failure);
    }
  }
}
