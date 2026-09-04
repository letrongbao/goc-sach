package vn.edu.utex.bookstore.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class PemKeys {
  private PemKeys() {}

  static PrivateKey privateKey(Path path) {
    byte[] der = read(path, "PRIVATE KEY");
    try {
      return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (Exception e) {
      throw new IllegalStateException("Khóa JWT riêng không hợp lệ.", e);
    }
  }

  static PublicKey publicKey(Path path) {
    byte[] der = read(path, "PUBLIC KEY");
    try {
      return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    } catch (Exception e) {
      throw new IllegalStateException("Khóa JWT công khai không hợp lệ.", e);
    }
  }

  private static byte[] read(Path path, String label) {
    try {
      String pem = Files.readString(path);
      if (!pem.strip().startsWith("-----BEGIN " + label + "-----")
          || !pem.strip().endsWith("-----END " + label + "-----"))
        throw new IllegalArgumentException("Sai định dạng PEM.");
      String body =
          pem.replace("-----BEGIN " + label + "-----", "")
              .replace("-----END " + label + "-----", "")
              .replaceAll("\\s", "");
      return Base64.getDecoder().decode(body);
    } catch (IOException | IllegalArgumentException e) {
      throw new IllegalStateException("Không đọc được khóa JWT: " + path, e);
    }
  }
}
