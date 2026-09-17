package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.edu.utex.bookstore.config.*;

class JwtWebTest {
  @TempDir(cleanup = CleanupMode.NEVER) Path directory;
  private ServletWebServerApplicationContext context;
  private HttpClient client;
  private CookieManager cookies;
  private String base;
  private MemoryStore store;
  private KeyPair pair;
  private TestClock clock;
  private final ObjectMapper json = new ObjectMapper();

  static class TestClock extends Clock {
    Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return now; }
  }

  @BeforeEach void start() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    pair = generator.generateKeyPair();
    Path privateKey = directory.resolve("private.pem"), publicKey = directory.resolve("public.pem");
    Files.writeString(privateKey, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
    Files.writeString(publicKey, pem("PUBLIC KEY", pair.getPublic().getEncoded()));
    var properties = new Properties();
    properties.setProperty("cookie.secure", "false");
    properties.setProperty("otp.secret", "test-only-secret-not-for-production-0123456789");
    properties.setProperty("jwt.private-key-file", privateKey.toString());
    properties.setProperty("jwt.public-key-file", publicKey.toString());
    properties.setProperty("jwt.issuer", "https://goc-sach.test");
    properties.setProperty("jwt.audience", "goc-sach-api");
    store = DemoFixtures.store();
    clock = new TestClock();
    App app = new App(new Settings(properties), store, clock, (to, subject, body) -> {});
    context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(GocSachApplication.class)
        .properties("server.port=0", "server.address=127.0.0.1", "server.servlet.context-path=/bookstore", "logging.level.root=ERROR")
        .initializers(ctx -> {
          var registry = (GenericApplicationContext) ctx;
          registry.registerBean("fixtureApp", App.class, () -> app);
          registry.registerBean("fixtureClock", Clock.class, () -> clock);
        }).run("--server.port=0");
    base = "http://127.0.0.1:" + context.getWebServer().getPort() + "/bookstore";
    cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    client = HttpClient.newBuilder().cookieHandler(cookies).build();
  }

  private String pem(String label, byte[] bytes) {
    return "-----BEGIN " + label + "-----\r\n"
        + Base64.getMimeEncoder(64, new byte[] {13, 10}).encodeToString(bytes)
        + "\r\n-----END " + label + "-----\r\n";
  }

  @AfterEach void stop() {
    if (context != null) context.close();
    TempDirectorySupport.deleteEventually(directory);
  }
  HttpResponse<String> get(String path) throws Exception {
    return client.send(HttpRequest.newBuilder(URI.create(base + path)).build(), HttpResponse.BodyHandlers.ofString());
  }
  HttpResponse<String> post(String path, String body) throws Exception {
    return client.send(HttpRequest.newBuilder(URI.create(base + path))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
  }
  String csrf() throws Exception {
    var page = get("/auth/login");
    assertEquals(200, page.statusCode(), page.body());
    var matcher = java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
    assertTrue(matcher.find(), page.body());
    return matcher.group(1);
  }
  void login(String mode) throws Exception {
    var response = post("/auth/login", "_csrf=" + csrf() + "&login=reader&password=demo-password-123&mode=" + mode);
    assertEquals(302, response.statusCode(), response.body());
  }
  String token() throws Exception {
    var response = post("/auth/api-token", "_csrf=" + csrf());
    assertEquals(200, response.statusCode(), response.body());
    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
    return json.readTree(response.body()).get("access_token").asText();
  }
  HttpResponse<String> api(String token, String path) throws Exception {
    return client.send(HttpRequest.newBuilder(URI.create(base + path)).header("Authorization", "Bearer " + token)
        .build(), HttpResponse.BodyHandlers.ofString());
  }
  HttpResponse<String> apiPost(String token, String path, String body) throws Exception {
    return client.send(HttpRequest.newBuilder(URI.create(base + path))
        .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
  }
  String resign(String raw, java.util.function.Consumer<Map<String, Object>> edit) {
    var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build();
    Map<String, Object> claims = new HashMap<>(decoder.decode(raw).getClaims());
    edit.accept(claims);
    var jwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate())
        .keyID("goc-sach-rs256").build();
    var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
        JwtClaimsSet.builder().claims(values -> values.putAll(claims)).build())).getTokenValue();
  }

  @Test void csrfAndBearerAreSeparateAndLogoutRevokes() throws Exception {
    assertEquals(200, get("/api/products/1/reviews").statusCode());
    assertEquals(403, post("/auth/api-token", "").statusCode());
    assertEquals(401, post("/auth/api-token", "_csrf=" + csrf()).statusCode());
    login("session");
    assertEquals(403, post("/auth/api-token", "").statusCode());
    assertEquals(401, get("/api/me").statusCode());
    String raw = token();
    var response = api(raw, "/api/me");
    assertEquals(200, response.statusCode(), response.body());
    assertEquals("reader", json.readTree(response.body()).get("username").asText());
    assertTrue(response.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
    assertEquals(403, api(raw, "/api/admin/future").statusCode());
    String payload = new String(Base64.getUrlDecoder().decode(raw.split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);
    for (var cookie : cookies.getCookieStore().getCookies()) assertFalse(payload.contains(cookie.getValue()));
    assertEquals(302, post("/auth/logout", "_csrf=" + csrf()).statusCode());
    assertEquals(401, api(raw, "/api/me").statusCode());
  }

  @Test void signatureIssuerAudienceExpiryAndClaimsAreChecked() throws Exception {
    login("session");
    String raw = token();
    assertEquals(401, api("forged", "/api/me").statusCode());
    String[] parts = raw.split("\\.");
    byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
    signature[0] ^= 1;
    assertEquals(401, api(parts[0] + "." + parts[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature), "/api/me").statusCode());
    assertEquals(401, api(resign(raw, claims -> claims.put("iss", "https://attacker.test")), "/api/me").statusCode());
    assertEquals(401, api(resign(raw, claims -> claims.put("aud", List.of("other-api"))), "/api/me").statusCode());
    assertEquals(401, api(resign(raw, claims -> claims.put("role", "ADMIN")), "/api/me").statusCode());
    assertEquals(401, api(resign(raw, claims -> claims.remove("auth_version")), "/api/me").statusCode());
    assertEquals(401, api(resign(raw, claims -> claims.put("sid", "unknown-grant")), "/api/me").statusCode());
    assertEquals(401, api(resign(raw, claims -> claims.put("nbf", clock.now.plusSeconds(60))), "/api/me").statusCode());
    clock.now = clock.now.plusSeconds(600);
    assertEquals(401, api(raw, "/api/me").statusCode());
  }

  @Test void passwordVersionAccountLockAndRoleChangeInvalidateTokens() throws Exception {
    login("session");
    String raw = token();
    store.users.get(2L).authVersion++;
    assertEquals(401, api(raw, "/api/me").statusCode());
    assertEquals(401, post("/auth/api-token", "_csrf=" + csrf()).statusCode());
    login("session");
    raw = token();
    store.users.get(2L).active = false;
    assertEquals(401, api(raw, "/api/me").statusCode());
    store.users.get(2L).active = true;
    store.users.get(2L).blocked = true;
    assertEquals(401, api(raw, "/api/me").statusCode());
    store.users.get(2L).blocked = false;
    store.users.get(2L).role = "ADMIN";
    assertEquals(401, api(raw, "/api/me").statusCode());
  }

  @Test void rememberLoginRenewalAndReloginRevokePreviousGrants() throws Exception {
    login("cookie");
    String first = token();
    assertEquals(200, api(first, "/api/me").statusCode());
    String second = token();
    assertEquals(401, api(first, "/api/me").statusCode());
    assertEquals(200, api(second, "/api/me").statusCode());
    login("session");
    assertEquals(401, api(second, "/api/me").statusCode());
    token();
    for (int i = 0; i < 7; i++) token();
    assertEquals(429, post("/auth/api-token", "_csrf=" + csrf()).statusCode());
  }

  @Test void businessFailuresKeepTheirJsonHttpStatus() throws Exception {
    login("session");
    String raw = token();
    var missingOrder = api(raw, "/api/orders/999999");
    assertEquals(404, missingOrder.statusCode(), missingOrder.body());
    assertTrue(missingOrder.headers().firstValue("Content-Type").orElse("").contains("application/json"));
    assertEquals("no-store", missingOrder.headers().firstValue("Cache-Control").orElseThrow());
    assertEquals("Không tìm thấy dữ liệu.", json.readTree(missingOrder.body()).get("error").asText());

    var unavailableCopy = apiPost(raw, "/api/cart/items", "{\"copyId\":999999}");
    assertEquals(409, unavailableCopy.statusCode(), unavailableCopy.body());
    assertNotNull(json.readTree(unavailableCopy.body()).get("error"));

    var invalidCheckout = apiPost(raw, "/api/checkout",
        "{\"addressId\":1,\"idempotencyKey\":\"short\",\"quoteFingerprint\":\"missing\"}");
    assertEquals(400, invalidCheckout.statusCode(), invalidCheckout.body());
    assertNotNull(json.readTree(invalidCheckout.body()).get("error"));
  }
}
