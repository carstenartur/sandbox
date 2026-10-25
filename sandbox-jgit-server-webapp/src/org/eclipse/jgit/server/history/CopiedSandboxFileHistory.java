/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.history;

import java.util.List;
import org.hibernate.SessionFactory;
import org.eclipse.jgit.storage.hibernate.service.GitDatabaseQueryService;

/** Temporary boundary for the copied startup path's stored file versions. */
public final class CopiedSandboxFileHistory implements SandboxFileHistory {
  private final GitDatabaseQueryService query;

  public CopiedSandboxFileHistory(SessionFactory factory) {
    query = new GitDatabaseQueryService(factory);
  }

  @Override
  public List<FileVersion> versions(String repository, String path, int offset, int limit) {
    return query.getFileHistory(repository, path, offset, limit).stream().map(file ->
        new FileVersion(file.getCommitObjectId(), file.getFilePath(), file.getBlobObjectId(),
            file.getFileType(), file.getCommitTime())).toList();
  }
}
