/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import java.io.Closeable;

/** Optional application capability; status inspection never initializes a model. */
public interface EmbeddingEncoder extends Closeable {
  int DIMENSIONS = 384;
  enum Status { DISABLED, NOT_INITIALIZED, AVAILABLE, UNAVAILABLE, CLOSED }
  /** Returns null when encoding is disabled/unavailable or the input is blank. */
  float[] embed(String text);
  Status status();
}
