/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static org.junit.Assert.*;
import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;

public class AnalysisGenerationCacheTest {
  @Test public void reusesEqualSnapshotsAndReplacesOnlyAfterASuccessfulBuild() throws Exception {
    var created = new ArrayList<Generation>();
    try (var cache = new AnalysisGenerationCache<Integer, Generation>(2, 1)) {
      var builder = builder(created);
      assertEquals(1, (int) cache.read("repo", () -> 1, builder, Generation::value));
      assertEquals(1, (int) cache.read("repo", () -> 1, builder, Generation::value));
      assertEquals(1, created.size());
      assertThrows(IOException.class, () -> cache.read("repo", () -> 2,
          key -> { throw new IOException("Missing authoritative object"); }, Generation::value));
      assertEquals(0, created.getFirst().closed.get());
      assertEquals(1, (int) cache.read("repo", () -> 1, builder, Generation::value));
      assertEquals(2, (int) cache.read("repo", () -> 2, builder, Generation::value));
      assertEquals(1, created.getFirst().closed.get());
    }
    assertEquals(List.of(1, 1), created.stream().map(g -> g.closed.get()).toList());
  }

  @Test public void discardsAnInvalidatedBuildAndRefreshesItsSnapshotBeforeRetry() throws Exception {
    var building = new CountDownLatch(1); var release = new CountDownLatch(1);
    var current = new AtomicInteger(1); var created = new CopyOnWriteArrayList<Generation>();
    try (var cache = new AnalysisGenerationCache<Integer, Generation>(2, 1);
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var result = executor.submit(() -> cache.read("repo", current::get, key -> {
        var generation = new Generation(key); created.add(generation);
        if (key == 1) { building.countDown(); await(release); }
        return generation;
      }, Generation::value));
      await(building); current.set(2); cache.invalidateCanonical("repo"); release.countDown();
      assertEquals(2, (int) result.get(10, TimeUnit.SECONDS));
      assertEquals(List.of(1, 0), created.stream().map(g -> g.closed.get()).toList());
    } finally { release.countDown(); }
    assertTrue(created.stream().allMatch(g -> g.closed.get() == 1));
  }

  @Test public void sameRepositoryHasOneBuildAndCanonicalNamesAreNeverNormalizedAgain() throws Exception {
    var building = new CountDownLatch(1); var release = new CountDownLatch(1);
    var builds = new AtomicInteger();
    try (var cache = new AnalysisGenerationCache<Integer, Generation>(2, 2);
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      AnalysisGenerationCache.Builder<Integer, Generation> builder = key -> {
        builds.incrementAndGet(); building.countDown(); await(release); return new Generation(key);
      };
      var first = executor.submit(() -> cache.read("example.git", () -> 1, builder, Generation::value));
      await(building);
      var second = executor.submit(() -> cache.read("example.git", () -> 1, builder, Generation::value));
      release.countDown();
      assertEquals(1, (int) first.get(10, TimeUnit.SECONDS));
      assertEquals(1, (int) second.get(10, TimeUnit.SECONDS));
      assertEquals(1, builds.get());
      cache.invalidateCanonical("example");
      cache.read("example.git", () -> 1, builder, Generation::value);
      assertEquals(1, builds.get());
      cache.invalidateCanonical("example.git");
      cache.read("example.git", () -> 1, builder, Generation::value);
      assertEquals(2, builds.get());
      cache.invalidateAll();
      cache.read("example.git", () -> 1, builder, Generation::value);
      assertEquals(3, builds.get());
    } finally { release.countDown(); }
  }

  @Test public void evictionDoesNotCloseAGenerationInsideAReader() throws Exception {
    var reading = new CountDownLatch(1); var release = new CountDownLatch(1);
    var first = new Generation(1); var second = new Generation(2);
    try (var cache = new AnalysisGenerationCache<Integer, Generation>(1, 2);
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var result = executor.submit(() -> cache.read("first", () -> 1, key -> first, generation -> {
        reading.countDown(); await(release); return generation.value();
      }));
      await(reading);
      assertEquals(2, (int) cache.read("second", () -> 2, key -> second, Generation::value));
      assertEquals(0, first.closed.get());
      assertEquals(1, second.closed.get()); // All retained capacity is pinned by the first reader.
      release.countDown(); assertEquals(1, (int) result.get(10, TimeUnit.SECONDS));
    } finally { release.countDown(); }
    assertEquals(1, first.closed.get());
  }

  @Test public void snapshotIsReadAgainAfterWaitingForTheGlobalBuildPermit() throws Exception {
    var building = new CountDownLatch(1); var release = new CountDownLatch(1);
    var secondSnapshot = new CountDownLatch(1); var current = new AtomicInteger(1);
    try (var cache = new AnalysisGenerationCache<Integer, Generation>(2, 1);
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> cache.read("first", () -> 0, key -> {
        building.countDown(); await(release); return new Generation(key);
      }, Generation::value));
      await(building);
      var second = executor.submit(() -> cache.read("second", () -> {
        int value = current.get(); secondSnapshot.countDown(); return value;
      }, Generation::new, Generation::value));
      await(secondSnapshot); current.set(2); release.countDown();
      assertEquals(0, (int) first.get(10, TimeUnit.SECONDS));
      assertEquals(2, (int) second.get(10, TimeUnit.SECONDS));
    } finally { release.countDown(); }
  }

  @Test public void repeatedInvalidationIsBoundedAndDoesNotPoisonThePreviousGeneration() throws Exception {
    var created = new ArrayList<Generation>();
    try (var cache = new AnalysisGenerationCache<Integer, Generation>(1, 1)) {
      var builder = builder(created);
      cache.read("repo", () -> 1, builder, Generation::value);
      assertThrows(IOException.class, () -> cache.read("repo", () -> 2, key -> {
        var result = builder.build(key); cache.invalidateCanonical("repo"); return result;
      }, Generation::value));
      assertEquals(4, created.size());
      assertEquals(0, created.getFirst().closed.get());
      assertTrue(created.subList(1, 4).stream().allMatch(g -> g.closed.get() == 1));
      assertEquals(2, (int) cache.read("repo", () -> 2, builder, Generation::value));
    }
  }

  @Test public void aCacheHitAfterPermitWaitingDoesNotOccupyBuildCapacityDuringItsRead() throws Exception {
    var building = new CountDownLatch(1); var releaseBuild = new CountDownLatch(1);
    var waiting = new CountDownLatch(1); var reading = new CountDownLatch(1); var releaseRead = new CountDownLatch(1);
    var current = new AtomicInteger(2);
    try (var cache = new AnalysisGenerationCache<Integer, Generation>(3, 1);
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      cache.read("cached", () -> 1, Generation::new, Generation::value);
      var blocker = executor.submit(() -> cache.read("blocker", () -> 0, key -> {
        building.countDown(); await(releaseBuild); return new Generation(key);
      }, Generation::value));
      await(building);
      var cached = executor.submit(() -> cache.read("cached", () -> {
        int value = current.get(); waiting.countDown(); return value;
      }, Generation::new, generation -> { reading.countDown(); await(releaseRead); return generation.value(); }));
      await(waiting); current.set(1); releaseBuild.countDown(); await(reading);
      var unrelated = executor.submit(() -> cache.read("unrelated", () -> 3, Generation::new, Generation::value));
      try { assertEquals(3, (int) unrelated.get(2, TimeUnit.SECONDS)); }
      finally { releaseRead.countDown(); }
      assertEquals(1, (int) cached.get(10, TimeUnit.SECONDS)); assertEquals(0, (int) blocker.get(10, TimeUnit.SECONDS));
    } finally { releaseBuild.countDown(); releaseRead.countDown(); }
  }

  @Test public void interruptedWaitPreservesTheFlagAndReleasesItsPin() throws Exception {
    var reading = new CountDownLatch(1); var release = new CountDownLatch(1);
    try (var cache = new AnalysisGenerationCache<Integer, Generation>(1, 1);
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> cache.read("repo", () -> 1, Generation::new, generation -> {
        reading.countDown(); await(release); return generation.value();
      }));
      await(reading);
      var entered = new CountDownLatch(1); var preserved = new AtomicBoolean();
      Thread waiting = Thread.ofVirtual().start(() -> {
        entered.countDown();
        try { cache.read("repo", () -> 1, Generation::new, Generation::value); }
        catch (InterruptedIOException expected) { preserved.set(Thread.currentThread().isInterrupted()); }
        catch (IOException unexpected) { throw new AssertionError(unexpected); }
      });
      await(entered); waiting.interrupt(); waiting.join(10_000);
      assertFalse(waiting.isAlive()); assertTrue(preserved.get());
      release.countDown(); assertEquals(1, (int) first.get(10, TimeUnit.SECONDS));
    } finally { release.countDown(); }
  }

  @Test public void closeWaitsForReadersAndRejectsNewRequestsAndReentrancy() throws Exception {
    var cache = new AnalysisGenerationCache<Integer, Generation>(1, 1);
    var reading = new CountDownLatch(1); var release = new CountDownLatch(1); var generation = new Generation(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> cache.read("repo", () -> 1, key -> generation, value -> {
        assertThrows(IllegalStateException.class, cache::close);
        reading.countDown(); await(release); return value.value();
      }));
      await(reading);
      var closing = executor.submit(() -> { cache.close(); return true; });
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (!cache.isClosing() && System.nanoTime() < deadline) Thread.onSpinWait();
      assertTrue(cache.isClosing()); assertFalse(closing.isDone()); assertEquals(0, generation.closed.get());
      assertThrows(IllegalStateException.class, () -> cache.read("other", () -> 2, Generation::new, Generation::value));
      release.countDown(); assertEquals(1, (int) first.get(10, TimeUnit.SECONDS)); assertTrue(closing.get(10, TimeUnit.SECONDS));
      assertEquals(1, generation.closed.get()); cache.close(); assertEquals(1, generation.closed.get());
    } finally { release.countDown(); cache.close(); }
  }

  @Test public void closeAlsoWaitsForAnEvictedGenerationStillBeingDisposed() throws Exception {
    var disposing = new CountDownLatch(1); var release = new CountDownLatch(1);
    var first = new Generation(1) {
      @Override public void close() throws IOException {
        disposing.countDown();
        try { await(release); } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        super.close();
      }
    };
    var cache = new AnalysisGenerationCache<Integer, Generation>(1, 1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      cache.read("first", () -> 1, key -> first, Generation::value);
      var second = executor.submit(() -> cache.read("second", () -> 2, Generation::new, Generation::value));
      await(disposing);
      var closing = executor.submit(() -> { cache.close(); return true; });
      assertThrows(TimeoutException.class, () -> closing.get(100, TimeUnit.MILLISECONDS));
      release.countDown(); assertEquals(2, (int) second.get(10, TimeUnit.SECONDS));
      assertTrue(closing.get(10, TimeUnit.SECONDS)); assertEquals(1, first.closed.get());
    } finally { release.countDown(); cache.close(); }
  }

  @Test public void disposalCallbacksCannotRecursivelyCloseTheCache() throws Exception {
    var cache = new AnalysisGenerationCache<Integer, Generation>(1, 1);
    var generation = new Generation(1) {
      @Override public void close() throws IOException { assertThrows(IllegalStateException.class, cache::close); super.close(); }
    };
    cache.read("repo", () -> 1, key -> generation, Generation::value);
    var failure = new AtomicReference<Throwable>();
    Thread closing = Thread.ofVirtual().start(() -> { try { cache.close(); } catch (Throwable error) { failure.set(error); } });
    closing.join(2_000);
    assertFalse("A disposal callback waited for its own close", closing.isAlive());
    assertNull(failure.get()); assertEquals(1, generation.closed.get());
  }

  @Test public void readerFailureKeepsCleanupFailureSuppressedAndReleasesItsPin() throws Exception {
    var cleanup = new IOException("eviction close");
    var old = new Generation(1) { @Override public void close() throws IOException { super.close(); throw cleanup; } };
    var replacement = new Generation(2);
    try (var cache = new AnalysisGenerationCache<Integer, Generation>(1, 1)) {
      cache.read("first", () -> 1, key -> old, Generation::value);
      var primary = new IOException("reader failure");
      assertSame(primary, assertThrows(IOException.class, () -> cache.read("second", () -> 2, key -> replacement,
          generation -> { throw primary; })));
      assertArrayEquals(new Throwable[] {cleanup}, primary.getSuppressed());
      assertEquals(2, (int) cache.read("second", () -> 2, key -> { throw new AssertionError("Must reuse"); }, Generation::value));
    }
    assertEquals(1, old.closed.get()); assertEquals(1, replacement.closed.get());
  }

  @Test public void shutdownAttemptsAllGenerationsEvenAfterCloseFailures() throws Exception {
    var cache = new AnalysisGenerationCache<Integer, Generation>(3, 1);
    var first = new Generation(1) { @Override public void close() throws IOException { super.close(); throw new IOException("first"); } };
    var second = new Generation(2) { @Override public void close() throws IOException { super.close(); throw new IllegalStateException("second"); } };
    var third = new Generation(3);
    cache.read("first", () -> 1, key -> first, Generation::value);
    cache.read("second", () -> 2, key -> second, Generation::value);
    cache.read("third", () -> 3, key -> third, Generation::value);
    var failure = assertThrows(IOException.class, cache::close);
    assertEquals("first", failure.getMessage()); assertEquals(1, failure.getSuppressed().length);
    assertEquals(List.of(1, 1, 1), List.of(first.closed.get(), second.closed.get(), third.closed.get()));
    cache.close();
  }

  @Test public void shutdownClosesLaterGenerationsWhenEarlierOnesThrowTheSameFailure() throws Exception {
    var cache = new AnalysisGenerationCache<Integer, Generation>(3, 1);
    var shared = new IOException("shared disposal failure");
    var first = new Generation(1) { @Override public void close() throws IOException { super.close(); throw shared; } };
    var second = new Generation(2) { @Override public void close() throws IOException { super.close(); throw shared; } };
    var third = new Generation(3);
    cache.read("first", () -> 1, key -> first, Generation::value);
    cache.read("second", () -> 2, key -> second, Generation::value);
    cache.read("third", () -> 3, key -> third, Generation::value);
    assertSame(shared, assertThrows(IOException.class, cache::close));
    assertEquals(0, shared.getSuppressed().length);
    assertEquals(List.of(1, 1, 1), List.of(first.closed.get(), second.closed.get(), third.closed.get()));
    cache.close();
  }

  @Test public void identicalReaderAndEvictionFailuresPreserveTheOriginalException() throws Exception {
    var shared = new IOException("shared read and disposal failure");
    var old = new Generation(1) { @Override public void close() throws IOException { super.close(); throw shared; } };
    var replacement = new Generation(2);
    try (var cache = new AnalysisGenerationCache<Integer, Generation>(1, 1)) {
      cache.read("first", () -> 1, key -> old, Generation::value);
      assertSame(shared, assertThrows(IOException.class, () -> cache.read("second", () -> 2, key -> replacement,
          generation -> { throw shared; })));
      assertEquals(0, shared.getSuppressed().length);
      assertEquals(2, (int) cache.read("second", () -> 2, key -> { throw new AssertionError("Must reuse"); }, Generation::value));
    }
    assertEquals(1, old.closed.get()); assertEquals(1, replacement.closed.get());
  }

  @Test public void interruptionDuringBuildClosesTheUnpublishedCandidate() throws Exception {
    var candidate = new Generation(2);
    try (var cache = new AnalysisGenerationCache<Integer, Generation>(1, 1)) {
      try {
        assertThrows(InterruptedIOException.class, () -> cache.read("repo", () -> 2, key -> {
          Thread.currentThread().interrupt(); return candidate;
        }, Generation::value));
        assertTrue(Thread.currentThread().isInterrupted()); assertEquals(1, candidate.closed.get());
      } finally { Thread.interrupted(); }
      assertEquals(3, (int) cache.read("repo", () -> 3, Generation::new, Generation::value));
    }
  }

  private static AnalysisGenerationCache.Builder<Integer, Generation> builder(List<Generation> created) {
    return key -> { var generation = new Generation(key); created.add(generation); return generation; };
  }
  private static void await(CountDownLatch latch) throws IOException {
    try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IOException("Timed out waiting for fixture"); }
    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new InterruptedIOException(); }
  }
  private static class Generation implements Closeable {
    final int id;
    final AtomicInteger closed = new AtomicInteger();
    Generation(int id) { this.id = id; }
    int value() { assertEquals("Generation was closed during a read", 0, closed.get()); return id; }
    @Override public void close() throws IOException { closed.incrementAndGet(); }
  }
}
