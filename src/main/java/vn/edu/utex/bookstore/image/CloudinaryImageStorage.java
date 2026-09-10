package vn.edu.utex.bookstore.image;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.io.*;
import java.net.URI;
import java.util.Map;
import vn.edu.utex.bookstore.common.Problem;

public final class CloudinaryImageStorage implements ImageStorage {
  @FunctionalInterface
  interface UploadClient {
    Map<?, ?> upload(byte[] data, Map<String, Object> options) throws IOException;
  }

  private final UploadClient client;
  private final String assetFolder;

  public CloudinaryImageStorage(String cloudName, String apiKey, String apiSecret, String assetFolder) {
    if (cloudName == null || cloudName.isBlank() || apiKey == null || apiKey.isBlank()
        || apiSecret == null || apiSecret.isBlank()) {
      throw new IllegalArgumentException("Thiếu cấu hình Cloudinary.");
    }
    Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
        "cloud_name", cloudName.trim(), "api_key", apiKey.trim(), "api_secret", apiSecret.trim(), "secure", true));
    this.client = (data, options) -> cloudinary.uploader().upload(data, options);
    this.assetFolder = requireFolder(assetFolder);
  }

  CloudinaryImageStorage(UploadClient client, String assetFolder) {
    this.client = client;
    this.assetFolder = requireFolder(assetFolder);
  }

  @Override
  public String store(InputStream input, long size) throws IOException {
    return storeAsset(input, size).secureUrl();
  }

  @Override
  public StoredImage storeAsset(InputStream input, long size) throws IOException {
    byte[] data = ImageValidation.read(input, size);
    Map<?, ?> result = client.upload(data, ObjectUtils.asMap(
        "resource_type", "image",
        "asset_folder", assetFolder,
        "use_asset_folder_as_public_id_prefix", true,
        "unique_filename", true,
        "overwrite", false,
        "tags", "goc-sach"));
    Object rawUrl = result.get("secure_url");
    if (!(rawUrl instanceof String url) || !isCloudinaryHttps(url)) {
      throw new IOException("Cloudinary không trả về URL ảnh HTTPS hợp lệ.");
    }
    return new StoredImage(string(result.get("public_id")), string(result.get("resource_type"), "image"), url,
        integer(result.get("width")), integer(result.get("height")));
  }

  @Override
  public String validateReference(String value) {
    return ImageValidation.reference(value);
  }

  private static String requireFolder(String value) {
    String folder = value == null ? "" : value.trim();
    if (!folder.matches("[A-Za-z0-9 _./-]{1,255}") || folder.contains(".."))
      throw new IllegalArgumentException("Tên thư mục Cloudinary không hợp lệ.");
    return folder;
  }

  private static boolean isCloudinaryHttps(String value) {
    try {
      URI uri = URI.create(value);
      String host = uri.getHost();
      return "https".equalsIgnoreCase(uri.getScheme())
          && host != null
          && (host.equalsIgnoreCase("res.cloudinary.com") || host.toLowerCase().endsWith(".res.cloudinary.com"));
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }
  private static String string(Object value) { return value instanceof String s ? s : null; }
  private static String string(Object value, String fallback) { String s=string(value); return s==null?fallback:s; }
  private static Integer integer(Object value) { return value instanceof Number n ? n.intValue() : null; }
}
