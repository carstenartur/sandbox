/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Git paths that can share the released analyzer's temporary tree on supported server hosts. */
final class PortableSourcePaths {
  private PortableSourcePaths() {}

  static Set<String> excluded(Collection<String> paths) {
    var excluded = new HashSet<String>();
    var aliases = new HashMap<String, String>();
    for (String path : paths) {
      if (path.isEmpty() || !relative(path)) { excluded.add(path); continue; }
      String previous = aliases.putIfAbsent(path.toLowerCase(Locale.ROOT), path);
      if (previous != null) { excluded.add(previous); excluded.add(path); }
    }
    for (String path : paths) {
      String lower = path.toLowerCase(Locale.ROOT);
      for (int slash = lower.indexOf('/'); slash >= 0; slash = lower.indexOf('/', slash + 1)) {
        String file = aliases.get(lower.substring(0, slash));
        if (file != null) { excluded.add(file); excluded.add(path); }
      }
    }
    return Set.copyOf(excluded);
  }

  static boolean relative(String text) {
    if (text.contains("\\") || text.chars().anyMatch(c -> c < 32 || "<>:\"|?*".indexOf(c) >= 0)) return false;
    try {
      Path path = Path.of(text);
      if (path.isAbsolute() || path.startsWith("..")
          || !path.normalize().toString().replace('\\', '/').equals(text)) return false;
      // Reserve the released analyzer's temporary-directory suffix and leave room below legacy host limits.
      if (Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().toString().length()
          + 64 + text.length() > 240) return false;
      for (String part : text.split("/")) {
        if (part.length() > 255 || part.getBytes(StandardCharsets.UTF_8).length > 255
            || part.endsWith(".") || part.endsWith(" ")
            || part.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) return false;
      }
      return true;
    } catch (InvalidPathException invalid) { return false; }
  }
}
