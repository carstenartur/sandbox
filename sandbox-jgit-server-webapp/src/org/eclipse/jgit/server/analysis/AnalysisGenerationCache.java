/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Owns complete, closeable analysis generations. Callbacks cannot retain a generation,
 * reenter this cache or close it. Snapshot values must be immutable and implement value equality.
 * Names are already canonical; invalidation must never normalize them a second time.
 *
 * <p>The entry limit bounds idle retention, not corpus bytes. Active entries may temporarily exceed
 * it. Builds and reads run outside the map monitor; readers pin a slot until DTO mapping finishes.
 */
final class AnalysisGenerationCache<S, G extends Closeable> implements Closeable {
  @FunctionalInterface interface Snapshot<S> { S read() throws IOException; }
  @FunctionalInterface interface Builder<S, G> { G build(S snapshot) throws IOException; }
  @FunctionalInterface interface Reader<G, T> { T read(G generation) throws IOException; }

  private static final int MAX_ATTEMPTS = 3;
  private final Object monitor = new Object();
  private final LinkedHashMap<String, Slot> slots = new LinkedHashMap<>(16, .75f, true);
  private final Semaphore builders;
  private final int retainedEntries;
  private final ThreadLocal<Boolean> inside = ThreadLocal.withInitial(() -> false);
  private long refreshEpoch;
  private int activeUsers;
  private boolean closing;
  private boolean closed;

  AnalysisGenerationCache(int retainedEntries, int concurrentBuilds) {
    if (retainedEntries < 1 || concurrentBuilds < 1) throw new IllegalArgumentException("Cache limits must be positive");
    this.retainedEntries = retainedEntries;
    builders = new Semaphore(concurrentBuilds, true);
  }

  <T> T read(String canonicalName, Snapshot<S> snapshots, Builder<S, G> builder, Reader<G, T> reader) throws IOException {
    Objects.requireNonNull(canonicalName); Objects.requireNonNull(snapshots);
    Objects.requireNonNull(builder); Objects.requireNonNull(reader);
    if (inside.get()) throw new IllegalStateException("Analysis callbacks cannot reenter their cache");
    Slot slot;
    synchronized (monitor) {
      if (closing) throw new IllegalStateException("Analysis cache is closing");
      slot = slots.computeIfAbsent(canonicalName, ignored -> new Slot());
      slot.users++; activeUsers++;
    }
    inside.set(true);
    boolean locked = false;
    Throwable primary = null;
    try {
      try { slot.lock.lockInterruptibly(); locked = true; }
      catch (InterruptedException interrupted) { throw interrupted(interrupted); }
      return readLocked(slot, snapshots, builder, reader);
    } catch (IOException | RuntimeException | Error failure) {
      primary = failure; throw failure;
    } finally {
      if (locked) slot.lock.unlock();
      try { release(slot); }
      catch (IOException cleanup) {
        if (primary == null) throw cleanup;
        if (primary != cleanup) primary.addSuppressed(cleanup);
      }
      finally { inside.remove(); }
    }
  }

  private <T> T readLocked(Slot slot, Snapshot<S> snapshots, Builder<S, G> builder, Reader<G, T> reader) throws IOException {
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      checkInterrupted();
      Epoch epoch = epoch(slot);
      S snapshot = Objects.requireNonNull(snapshots.read(), "snapshot");
      if (matches(slot, snapshot, epoch)) return reader.read(slot.generation);
      try { builders.acquire(); }
      catch (InterruptedException interrupted) { throw interrupted(interrupted); }
      G candidate = null;
      G previous = null;
      boolean published = false;
      boolean reused = false;
      Throwable buildFailure = null;
      try {
        // A source can change while this request waits for a permit. Reload it after that wait.
        epoch = epoch(slot);
        snapshot = Objects.requireNonNull(snapshots.read(), "snapshot");
        reused = matches(slot, snapshot, epoch);
        if (!reused) {
          candidate = Objects.requireNonNull(builder.build(snapshot), "generation");
          checkInterrupted();
          synchronized (monitor) {
            if (epoch.equals(epochLocked(slot))) {
              previous = slot.generation;
              slot.generation = candidate; slot.snapshot = snapshot; slot.generationEpoch = epoch;
              published = true;
            }
          }
        }
      } catch (IOException | RuntimeException | Error failure) {
        buildFailure = failure; throw failure;
      } finally {
        builders.release();
        if (candidate != null && !published) {
          try { candidate.close(); }
          catch (IOException | RuntimeException cleanup) {
            if (buildFailure == null) throw cleanup;
            if (buildFailure != cleanup) buildFailure.addSuppressed(cleanup);
          }
        }
      }
      if (reused) return reader.read(slot.generation);
      if (published) {
        if (previous != null) previous.close();
        return reader.read(slot.generation);
      }
    }
    throw new IOException("Analysis was repeatedly invalidated while building; retry the request");
  }

  private boolean matches(Slot slot, S snapshot, Epoch epoch) {
    if (slot.generation == null || !epoch.equals(slot.generationEpoch) || !snapshot.equals(slot.snapshot)) return false;
    synchronized (monitor) {
      return epoch.equals(epochLocked(slot));
    }
  }

  /** Invalidates exactly this already-canonical identity without waiting for its active readers. */
  void invalidateCanonical(String canonicalName) {
    Objects.requireNonNull(canonicalName);
    synchronized (monitor) {
      Slot slot = slots.get(canonicalName);
      if (slot != null) slot.epoch++;
    }
  }

  /** Refresh local dependency/configuration inputs even when Git history itself is unchanged. */
  void invalidateAll() { synchronized (monitor) { refreshEpoch++; } }

  boolean isClosing() { synchronized (monitor) { return closing; } }

  private Epoch epoch(Slot slot) { synchronized (monitor) { return epochLocked(slot); } }
  private Epoch epochLocked(Slot slot) { return new Epoch(refreshEpoch, slot.epoch); }

  private void release(Slot slot) throws IOException {
    var retired = new ArrayList<G>();
    synchronized (monitor) {
      slot.users--;
      var iterator = slots.entrySet().iterator();
      while (slots.size() > retainedEntries && iterator.hasNext()) {
        var entry = iterator.next();
        if (entry.getValue().users == 0) {
          if (entry.getValue().generation != null) retired.add(entry.getValue().generation);
          iterator.remove();
        }
      }
    }
    try { closeAll(retired); }
    finally { synchronized (monitor) { activeUsers--; monitor.notifyAll(); } }
  }

  @Override public void close() throws IOException {
    if (inside.get()) throw new IllegalStateException("Analysis callbacks cannot close their cache");
    var retired = new ArrayList<G>();
    boolean interrupted = false;
    synchronized (monitor) {
      if (closing) {
        while (!closed) {
          try { monitor.wait(); } catch (InterruptedException ignored) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
        return;
      }
      closing = true;
      while (activeUsers != 0) {
        try { monitor.wait(); } catch (InterruptedException ignored) { interrupted = true; }
      }
      slots.values().forEach(slot -> { if (slot.generation != null) retired.add(slot.generation); });
      slots.clear();
    }
    inside.set(true);
    try { closeAll(retired); }
    finally {
      synchronized (monitor) { closed = true; monitor.notifyAll(); }
      inside.remove();
      if (interrupted) Thread.currentThread().interrupt();
    }
  }

  private static void closeAll(List<? extends Closeable> generations) throws IOException {
    IOException first = null;
    for (var generation : generations) {
      try { generation.close(); }
      catch (IOException | RuntimeException failure) {
        var exception = failure instanceof IOException io ? io : new IOException("Cannot close analysis generation", failure);
        if (first == null) first = exception;
        else if (first != exception) first.addSuppressed(exception);
      }
    }
    if (first != null) throw first;
  }

  private static void checkInterrupted() throws InterruptedIOException {
    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Analysis read interrupted");
  }
  private static InterruptedIOException interrupted(InterruptedException cause) {
    Thread.currentThread().interrupt();
    var failure = new InterruptedIOException("Analysis wait interrupted"); failure.initCause(cause); return failure;
  }
  private record Epoch(long refresh, long repository) {}
  private final class Slot {
    final ReentrantLock lock = new ReentrantLock(true);
    int users;
    long epoch;
    G generation;
    S snapshot;
    Epoch generationEpoch;
  }
}
