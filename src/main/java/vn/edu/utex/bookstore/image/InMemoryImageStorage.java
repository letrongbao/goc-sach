package vn.edu.utex.bookstore.image;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Disposable storage used only by the preview/test wiring; production uses Cloudinary. */
public final class InMemoryImageStorage implements ImageStorage {
  private final Map<String, byte[]> images = new ConcurrentHashMap<>();

  @Override
  public String store(InputStream input, long size) throws IOException {
    byte[] data = ImageValidation.read(input, size);
    String key = UUID.randomUUID().toString();
    images.put(key, data);
    return "data:image/" + ImageValidation.extension(data) + ";base64," +
        java.util.Base64.getEncoder().encodeToString(data);
  }

  @Override
  public String validateReference(String value) {
    return ImageValidation.reference(value);
  }
}
