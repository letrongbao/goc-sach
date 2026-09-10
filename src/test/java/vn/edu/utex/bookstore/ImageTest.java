package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import org.junit.jupiter.api.Test;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.image.InMemoryImageStorage;

class ImageTest {
  @Test
  void storesValidPngInDisposableTestStorage() throws Exception {
    var output = new ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png", output);
    String reference = new InMemoryImageStorage().store(
        new ByteArrayInputStream(output.toByteArray()), output.size());
    assertTrue(reference.startsWith("data:image/png;base64,"));
  }

  @Test
  void rejectsTruncatedPngHeader() {
    byte[] bytes = new byte[24];
    System.arraycopy(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}, 0, bytes, 0, 8);
    assertThrows(Problem.class,
        () -> new InMemoryImageStorage().store(new ByteArrayInputStream(bytes), bytes.length));
  }

  @Test
  void rejectsUntrustedUrls() {
    var storage = new InMemoryImageStorage();
    for (String url : new String[] {
      "javascript:alert(1)", "//example.org/a.png", "file:///etc/passwd",
      "http://example.org/a.png", "https://user:pass@res.cloudinary.com/image"
    }) assertThrows(Problem.class, () -> storage.validateReference(url));
  }

  @Test
  void permitsCloudinaryHttpsImagesOnly() {
    String url = "https://res.cloudinary.com/dsvkscwti/image/upload/WEPR/a.png";
    assertEquals(url, new InMemoryImageStorage().validateReference(url));
  }

  @Test
  void rejectsHtmlDisguisedAsImage() {
    byte[] b = "<html>not an image</html>".getBytes();
    assertThrows(Problem.class,
        () -> new InMemoryImageStorage().store(new ByteArrayInputStream(b), b.length));
  }

  @Test
  void rejectsOversizedUpload() {
    assertThrows(Problem.class,
        () -> new InMemoryImageStorage().store(InputStream.nullInputStream(), 5 * 1024 * 1024 + 1L));
  }
}
