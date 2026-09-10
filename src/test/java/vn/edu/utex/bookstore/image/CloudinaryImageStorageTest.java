package vn.edu.utex.bookstore.image;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.utex.bookstore.common.Problem;

class CloudinaryImageStorageTest {
  @Test
  void uploadsValidatedImageToConfiguredAssetFolder() throws Exception {
    Map<String, Object> captured = new HashMap<>();
    var storage = new CloudinaryImageStorage((data, options) -> {
      captured.putAll(options);
      return Map.of("secure_url", "https://res.cloudinary.com/demo/image/upload/WEPR/book.png");
    }, "WEPR");

    byte[] png = png();
    String url = storage.store(new ByteArrayInputStream(png), png.length);

    assertEquals("https://res.cloudinary.com/demo/image/upload/WEPR/book.png", url);
    assertEquals("WEPR", captured.get("asset_folder"));
    assertEquals(true, captured.get("use_asset_folder_as_public_id_prefix"));
    assertEquals(false, captured.get("overwrite"));
    assertEquals("goc-sach", captured.get("tags"));
  }

  @Test
  void exposesAssetMetadataForMediaTable() throws Exception {
    var storage = new CloudinaryImageStorage((data, options) ->
        Map.of("secure_url", "https://res.cloudinary.com/demo/image/upload/WEPR/book.png",
            "public_id", "WEPR/book", "resource_type", "image", "width", 120, "height", 180), "WEPR");
    byte[] png = png();
    var asset = storage.storeAsset(new ByteArrayInputStream(png), png.length);
    assertEquals("WEPR/book", asset.publicId());
    assertEquals("image", asset.resourceType());
    assertEquals(120, asset.width());
    assertEquals(180, asset.height());
  }

  @Test
  void rejectsInvalidImageBeforeCallingCloudinary() {
    var called = new boolean[] {false};
    var storage = new CloudinaryImageStorage((data, options) -> {
      called[0] = true;
      return Map.of();
    }, "WEPR");
    byte[] html = "<html>not an image</html>".getBytes();

    assertThrows(Problem.class,
        () -> storage.store(new ByteArrayInputStream(html), html.length));
    assertFalse(called[0]);
  }

  @Test
  void rejectsUntrustedUploadUrl() throws Exception {
    var storage = new CloudinaryImageStorage((data, options) ->
        Map.of("secure_url", "https://example.org/fake.png"), "WEPR");
    byte[] png = png();

    assertThrows(IOException.class,
        () -> storage.store(new ByteArrayInputStream(png), png.length));
  }

  @Test
  void rejectsTraversalInAssetFolder() {
    assertThrows(IllegalArgumentException.class,
        () -> new CloudinaryImageStorage((data, options) -> Map.of(), "../WEPR"));
  }

  private static byte[] png() throws IOException {
    var output = new ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png", output);
    return output.toByteArray();
  }
}
