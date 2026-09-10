package vn.edu.utex.bookstore.security;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import vn.edu.utex.bookstore.TempDirectorySupport;
import vn.edu.utex.bookstore.MemoryStore;
import vn.edu.utex.bookstore.config.*;

class JwtConfigurationTest {
  @TempDir(cleanup = CleanupMode.NEVER) Path directory;
  Properties values;
  final JwtConfiguration configuration = new JwtConfiguration();
  @BeforeEach void setup() {
    values = new Properties();
    values.setProperty("otp.secret", "test-only-secret-not-for-production-0123456789");
  }
  @AfterEach void cleanup() { TempDirectorySupport.deleteEventually(directory); }
  App app() { return new App(new Settings(values), new MemoryStore(), Clock.systemUTC(), (to, subject, body) -> {}); }
  void configure(int bits) throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(bits);
    var pair = generator.generateKeyPair();
    Path privateKey = directory.resolve("private.pem"), publicKey = directory.resolve("public.pem");
    Files.writeString(privateKey, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
    Files.writeString(publicKey, pem("PUBLIC KEY", pair.getPublic().getEncoded()));
    values.setProperty("jwt.private-key-file", privateKey.toString());
    values.setProperty("jwt.public-key-file", publicKey.toString());
    values.setProperty("jwt.issuer", "https://goc-sach.test");
    values.setProperty("jwt.audience", "goc-sach-api");
  }
  String pem(String label, byte[] encoded) {
    return "-----BEGIN " + label + "-----\n"
        + Base64.getMimeEncoder(64, new byte[] {10}).encodeToString(encoded)
        + "\n-----END " + label + "-----\n";
  }
  @Test void absentKeysStillInstallARejectingDecoder() {
    App app = app();
    var material = configuration.jwtMaterial(app);
    var tokens = configuration.jwtTokenService(material, app, Clock.systemUTC());
    assertFalse(tokens.enabled());
    assertThrows(org.springframework.security.oauth2.jwt.BadJwtException.class,
        () -> configuration.jwtDecoder(material, tokens, Clock.systemUTC()).decode("forged"));
  }
  @Test void partialConfigurationFailsInsteadOfSilentlyDisablingSecurity() {
    values.setProperty("jwt.issuer", "https://goc-sach.test");
    assertThrows(IllegalStateException.class, () -> configuration.jwtMaterial(app()));
  }
  @Test void multilinePemIsReadable() throws Exception {
    configure(2048);
    assertNotNull(configuration.jwtMaterial(app()).encoder());
  }
  @Test void mismatchedKeysAreRejected() throws Exception {
    configure(2048);
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    Files.writeString(directory.resolve("public.pem"), pem("PUBLIC KEY", generator.generateKeyPair().getPublic().getEncoded()));
    assertThrows(IllegalStateException.class, () -> configuration.jwtMaterial(app()));
  }
  @Test void weakKeysAreRejected() throws Exception {
    configure(1024);
    assertThrows(IllegalStateException.class, () -> configuration.jwtMaterial(app()));
  }
  @Test void wrongPemLabelAndMissingFileAreRejected() throws Exception {
    configure(2048);
    Files.writeString(directory.resolve("private.pem"), "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----");
    assertThrows(IllegalStateException.class, () -> configuration.jwtMaterial(app()));
    values.setProperty("jwt.private-key-file", directory.resolve("missing.pem").toString());
    assertThrows(IllegalStateException.class, () -> configuration.jwtMaterial(app()));
  }
  @Test void excessiveTokenLifetimeIsRejected() throws Exception {
    configure(2048);
    values.setProperty("jwt.lifetime-seconds", "3601");
    assertThrows(IllegalStateException.class, () -> configuration.jwtMaterial(app()));
  }
}
