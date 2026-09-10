package vn.edu.utex.bookstore.image;

import java.io.*;
import java.net.URI;
import java.util.Arrays;
import vn.edu.utex.bookstore.common.Problem;

final class ImageValidation {
  static final int MAX_BYTES = 5 * 1024 * 1024;

  private ImageValidation() {}

  static byte[] read(InputStream input, long size) throws IOException {
    if (size <= 0 || size > MAX_BYTES) throw Problem.invalid("Ảnh tối đa 5 MB.");
    byte[] data = input.readNBytes(MAX_BYTES + 1);
    if (data.length > MAX_BYTES) throw Problem.invalid("Ảnh tối đa 5 MB.");
    extension(data);
    try (var imageInput = javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      var readers = javax.imageio.ImageIO.getImageReaders(imageInput);
      if (!readers.hasNext()) throw Problem.invalid("Không đọc được nội dung ảnh.");
      var reader = readers.next();
      try {
        reader.setInput(imageInput);
        if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000L)
          throw Problem.invalid("Ảnh vượt quá 20 megapixel.");
        if (reader.read(0) == null) throw Problem.invalid("Ảnh không hợp lệ.");
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw Problem.invalid("Ảnh bị hỏng hoặc không đúng định dạng.");
    }
    return data;
  }

  static String extension(byte[] b) {
    if (b.length >= 12 && b[0] == (byte) 0xff && b[1] == (byte) 0xd8 && b[2] == (byte) 0xff)
      return "jpg";
    if (b.length >= 24
        && Arrays.equals(Arrays.copyOf(b, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}))
      return "png";
    if (b.length >= 20
        && new String(b, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
        && new String(b, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP"))
      return "webp";
    throw Problem.invalid("Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP hợp lệ.");
  }

  static String reference(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.isEmpty()) return "";
    try {
      URI uri = URI.create(value);
      if (value.length() <= 1000
          && "https".equalsIgnoreCase(uri.getScheme())
          && uri.getHost() != null
          && uri.getUserInfo() == null
          && (uri.getHost().equalsIgnoreCase("res.cloudinary.com")
              || uri.getHost().toLowerCase(java.util.Locale.ROOT).endsWith(".res.cloudinary.com")))
        return value;
    } catch (IllegalArgumentException ignored) {
    }
    throw Problem.invalid("URL ảnh phải dùng HTTP/HTTPS, không chứa thông tin đăng nhập.");
  }
}
