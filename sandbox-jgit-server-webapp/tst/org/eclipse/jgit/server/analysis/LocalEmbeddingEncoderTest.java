/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static org.junit.Assert.*;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;

public class LocalEmbeddingEncoderTest {
  @Test public void constructionStatusBlankInputAndDisabledModeDoNotLoadAModel() throws Exception {
    var loads = new AtomicInteger();
    LocalEmbeddingEncoder.Loader loader = () -> { loads.incrementAndGet(); return model(); };
    try (var lazy = new LocalEmbeddingEncoder(true, loader); var disabled = new LocalEmbeddingEncoder(false, loader)) {
      assertEquals(EmbeddingEncoder.Status.NOT_INITIALIZED, lazy.status());
      assertEquals(EmbeddingEncoder.Status.DISABLED, disabled.status());
      assertNull(lazy.embed(" ")); assertNull(lazy.embed(null)); assertNull(disabled.embed("query"));
      assertEquals(0, loads.get());
      assertEquals(384, lazy.embed("query").length);
      assertEquals(EmbeddingEncoder.Status.AVAILABLE, lazy.status()); assertEquals(1, loads.get());
    }
    try (var productionLoader = new LocalEmbeddingEncoder(false, "unused-model", null)) {
      assertEquals(EmbeddingEncoder.Status.DISABLED, productionLoader.status());
      assertNull(productionLoader.embed("query"));
    }
  }

  @Test public void unavailableOptionalModelIsAttemptedOnceAndFatalDefectsRemainVisible() throws Exception {
    for (boolean nativeFailure : new boolean[] {false, true}) {
      var attempts = new AtomicInteger();
      try (var encoder = new LocalEmbeddingEncoder(true, () -> {
        attempts.incrementAndGet();
        if (nativeFailure) throw new UnsatisfiedLinkError("test missing runtime");
        throw new IOException("test missing model");
      })) {
        assertNull(encoder.embed("first")); assertNull(encoder.embed("second"));
        assertEquals(1, attempts.get()); assertEquals(EmbeddingEncoder.Status.UNAVAILABLE, encoder.status());
      }
    }
    try (var encoder = new LocalEmbeddingEncoder(true, () -> { throw new IllegalStateException("programming defect"); })) {
      assertThrows(IllegalStateException.class, () -> encoder.embed("query"));
    }
    try (var encoder = new LocalEmbeddingEncoder(true, () -> { throw new OutOfMemoryError("test fatal"); })) {
      assertThrows(OutOfMemoryError.class, () -> encoder.embed("query"));
    }
  }

  @Test public void preservesMetadataInputAndBoundsPredictionWithoutSplittingUnicode() throws Exception {
    assertEquals("Example: Documentation\nMethods: run()\nPackage: example",
        LocalEmbeddingEncoder.inputText("Example", "Documentation", "run()", "example"));
    assertEquals("run()", LocalEmbeddingEncoder.inputText(null, "", "run()", null));
    var input = new AtomicReference<String>();
    try (var encoder = new LocalEmbeddingEncoder(true, () -> new LocalEmbeddingEncoder.Model() {
      @Override public float[] embed(String text) { input.set(text); return new float[384]; }
      @Override public void close() {}
    })) {
      encoder.embed("a".repeat(2047) + "\uD83D\uDE00tail");
      assertEquals(2047, input.get().length());
    }
  }

  @Test public void closeWaitsForPredictionAndCannotReinitialize() throws Exception {
    var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var closed = new AtomicInteger();
    var encoder = new LocalEmbeddingEncoder(true, () -> new LocalEmbeddingEncoder.Model() {
      @Override public float[] embed(String text) throws IOException { entered.countDown(); await(release); return new float[384]; }
      @Override public void close() { closed.incrementAndGet(); }
    });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var prediction = executor.submit(() -> encoder.embed("query")); await(entered);
      var closing = executor.submit(() -> { encoder.close(); return true; });
      assertThrows(TimeoutException.class, () -> closing.get(100, TimeUnit.MILLISECONDS));
      assertEquals(0, closed.get()); release.countDown();
      assertEquals(384, prediction.get(10, TimeUnit.SECONDS).length);
      assertTrue(closing.get(10, TimeUnit.SECONDS)); assertEquals(1, closed.get());
      assertEquals(EmbeddingEncoder.Status.CLOSED, encoder.status()); assertNull(encoder.embed("later"));
      encoder.close(); assertEquals(1, closed.get());
    } finally { release.countDown(); encoder.close(); }
    var unused = new LocalEmbeddingEncoder(true, () -> { throw new AssertionError("must remain lazy"); });
    unused.close(); assertNull(unused.embed("query"));
  }

  @Test public void predictionFailureDoesNotDisableLaterRequestsAndPreservesInterruption() throws Exception {
    for (IOException failure : java.util.List.of(new IOException("prediction"), new java.net.SocketTimeoutException("timeout"),
        new java.io.InterruptedIOException("cancelled"), new IOException(new InterruptedException("cancelled")))) {
      var closed = new AtomicInteger(); var attempts = new AtomicInteger();
      try (var encoder = new LocalEmbeddingEncoder(true, () -> new LocalEmbeddingEncoder.Model() {
        @Override public float[] embed(String text) throws IOException {
          if (attempts.getAndIncrement() == 0) throw failure;
          return new float[384];
        }
        @Override public void close() { closed.incrementAndGet(); }
      })) {
        assertNull(encoder.embed("first"));
        assertEquals(EmbeddingEncoder.Status.AVAILABLE, encoder.status());
        assertEquals((failure instanceof java.io.InterruptedIOException && !(failure instanceof java.net.SocketTimeoutException))
            || failure.getCause() instanceof InterruptedException,
            Thread.currentThread().isInterrupted());
        Thread.interrupted();
        assertEquals(384, encoder.embed("second").length); assertEquals(2, attempts.get());
      } finally { Thread.interrupted(); }
      assertEquals(1, closed.get());
    }
  }

  private static LocalEmbeddingEncoder.Model model() {
    return new LocalEmbeddingEncoder.Model() {
      @Override public float[] embed(String text) { return new float[384]; }
      @Override public void close() {}
    };
  }
  private static void await(CountDownLatch latch) throws IOException {
    try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IOException("Timed out"); }
    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IOException(failure); }
  }
}
