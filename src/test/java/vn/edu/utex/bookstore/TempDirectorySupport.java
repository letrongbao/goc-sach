package vn.edu.utex.bookstore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Windows may briefly retain handles to key files after a Spring context closes. */
public final class TempDirectorySupport {
  private TempDirectorySupport() {}

  public static void deleteEventually(Path directory) {
    if (directory == null) return;
    for (int attempt = 0; attempt < 20; attempt++) {
      try {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
          for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
        return;
      } catch (IOException failure) {
        try { Thread.sleep(50); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
      }
    }
  }
}
