/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.history;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/** Per-commit file versions, distinct from deduplicated source/blob search documents. */
public interface SandboxFileHistory {
  List<FileVersion> versions(String repository, String exactPath, int offset, int limit) throws IOException;

  record FileVersion(String commitObjectId, String filePath, String blobObjectId,
      String fileType, Instant commitTime) {}
}
