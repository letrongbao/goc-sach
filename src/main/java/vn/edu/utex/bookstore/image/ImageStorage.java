package vn.edu.utex.bookstore.image;

import java.io.*;

public interface ImageStorage {
  record StoredImage(String publicId, String resourceType, String secureUrl, Integer width, Integer height) {}
  String store(InputStream input, long size) throws IOException;

  /** Metadata form used by media_assets; legacy callers may keep using store(). */
  default StoredImage storeAsset(InputStream input, long size) throws IOException {
    return new StoredImage(null, "image", store(input, size), null, null);
  }

  String validateReference(String value);
  default StoredImage storeVideo(InputStream input,long size) throws IOException {
    throw vn.edu.utex.bookstore.common.Problem.invalid("Lưu video cần cấu hình Cloudinary.");
  }
}
