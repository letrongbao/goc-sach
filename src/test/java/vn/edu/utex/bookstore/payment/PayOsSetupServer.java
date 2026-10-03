package vn.edu.utex.bookstore.payment;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.Headers;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import vn.edu.utex.bookstore.config.Settings;

/** Explicit, short-lived local setup tool. This class is never packaged in the production JAR. */
public final class PayOsSetupServer {
  private static final Path TARGET = Path.of("C:/BaoLT/goc-sach/config/local.properties").toAbsolutePath().normalize();
  private static final int MAX_BODY = 8192;
  private static final Set<String> FIELDS = Set.of("_csrf", "clientId", "apiKey", "checksumKey");
  private final String token = randomToken();
  private final String path = "/setup/" + randomToken();
  private final AtomicBoolean consumed = new AtomicBoolean();
  private final CountDownLatch done = new CountDownLatch(1);
  private final HttpServer server;
  private final ExecutorService workers;
  private final String origin;

  private PayOsSetupServer() throws IOException {
    checkTarget();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
    origin = "http://127.0.0.1:" + server.getAddress().getPort();
    workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16),
        runnable -> { Thread thread = new Thread(runnable, "payos-local-setup"); thread.setDaemon(true); return thread; },
        new ThreadPoolExecutor.AbortPolicy());
    server.setExecutor(workers);
    server.createContext("/", this::handle);
  }

  public static void main(String[] args) {
    if (args.length != 0) {
      System.out.println("This tool accepts no arguments and writes only the fixed private local configuration.");
      return;
    }
    // This helper runs in its own JVM; bound request, header and connection resources before creating HttpServer.
    System.setProperty("jdk.httpserver.maxConnections", "8");
    System.setProperty("sun.net.httpserver.maxIdleConnections", "2");
    System.setProperty("sun.net.httpserver.maxReqHeaders", "32");
    System.setProperty("sun.net.httpserver.maxReqHeaderSize", "8192");
    // JDK 21 ServerImpl.getTimeMillis converts these property values from seconds to milliseconds.
    System.setProperty("sun.net.httpserver.maxReqTime", "15");
    System.setProperty("sun.net.httpserver.maxRspTime", "15");
    PayOsSetupServer setup;
    try {
      setup = new PayOsSetupServer();
    } catch (IOException failure) {
      System.out.println("PAYOS_SETUP: unavailable; private configuration must be an existing regular file without symbolic links.");
      return;
    }
    setup.server.start();
    System.out.println("PAYOS_SETUP_URL=" + setup.origin + setup.path);
    System.out.println("Local one-shot form expires after 10 minutes. No keys are printed or sent to payOS.");
    try {
      setup.done.await(10, TimeUnit.MINUTES);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    } finally {
      setup.server.stop(1);
      setup.workers.shutdownNow();
    }
  }

  private void handle(HttpExchange exchange) throws IOException {
    try {
      if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress()
          || !singleHeader(exchange, "Host", origin.substring("http://".length()))
          || !path.equals(exchange.getRequestURI().getRawPath())
          || exchange.getRequestURI().getRawQuery() != null) {
        reply(exchange, 404, "Không tìm thấy trang.");
        return;
      }
      if ("GET".equals(exchange.getRequestMethod())) {
        if (consumed.get()) reply(exchange, 410, "Trang cấu hình đã được sử dụng. Bạn có thể đóng cửa sổ.");
        else reply(exchange, 200, form());
        return;
      }
      if (!"POST".equals(exchange.getRequestMethod())) {
        reply(exchange, 405, "Thao tác không được hỗ trợ.");
        return;
      }
      if (!singleHeader(exchange, "Origin", origin)) {
        reply(exchange, 403, "Yêu cầu cấu hình không hợp lệ.");
        return;
      }
      String type = exchange.getRequestHeaders().getFirst("Content-Type");
      if (type == null || !"application/x-www-form-urlencoded".equalsIgnoreCase(type.split(";", 2)[0].strip())) {
        reply(exchange, 415, "Vui lòng dùng biểu mẫu cấu hình.");
        return;
      }
      String length = exchange.getRequestHeaders().getFirst("Content-Length");
      if (length != null && (Long.parseLong(length) < 0 || Long.parseLong(length) > MAX_BODY)) {
        reply(exchange, 413, "Biểu mẫu quá lớn.");
        return;
      }
      byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
      if (bytes.length > MAX_BODY) {
        reply(exchange, 413, "Biểu mẫu quá lớn.");
        return;
      }
      Map<String, String> values = decodeForm(bytes);
      if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
          values.get("_csrf").getBytes(StandardCharsets.UTF_8))) {
        reply(exchange, 403, "Yêu cầu cấu hình không hợp lệ.");
        return;
      }
      if (!consumed.compareAndSet(false, true)) {
        reply(exchange, 410, "Trang cấu hình đã được sử dụng.");
        return;
      }
      try {
        save(values);
        reply(exchange, 200, "<h1>Đã lưu cấu hình PayOS</h1><p>Thông tin đã được lưu vào cấu hình riêng của Góc Sách. Bạn có thể đóng cửa sổ này.</p>");
        System.out.println("PAYOS_SETUP: private configuration saved. No credentials displayed.");
      } catch (IOException | IllegalStateException failure) {
        reply(exchange, 409, "<h1>Chưa lưu được cấu hình</h1><p>Cấu hình bị thay đổi hoặc chưa hợp lệ. Khởi động lại công cụ rồi thử lại.</p>");
        System.out.println("PAYOS_SETUP: configuration was not replaced; check the private file locally.");
      } finally {
        done.countDown();
      }
    } catch (IllegalArgumentException | CharacterCodingException failure) {
      reply(exchange, 400, "Biểu mẫu không hợp lệ. Vui lòng kiểm tra ba trường đã điền.");
    } finally {
      exchange.close();
    }
  }

  private void save(Map<String, String> values) throws IOException {
    checkTarget();
    byte[] original = readPrivateConfig();
    Properties config = new Properties();
    try (Reader reader = new InputStreamReader(new ByteArrayInputStream(original), StandardCharsets.UTF_8)) {
      config.load(reader);
    }
    config.setProperty("payos.client-id", values.get("clientId"));
    config.setProperty("payos.api-key", values.get("apiKey"));
    config.setProperty("payos.checksum-key", values.get("checksumKey"));
    config.setProperty("payos.enabled", "true");
    String base = config.getProperty("payos.base-url", "").strip();
    if (base.isEmpty() || base.contains("REPLACE_WITH_") || base.startsWith("REPLACE_"))
      config.setProperty("payos.base-url", "http://localhost:8080");
    PayOsSettings.from(new Settings(config));

    // Same-directory temporary .properties file is ignored by /config/*.properties and atomically replaced.
    Path temporary = Files.createTempFile(TARGET.getParent(), ".payos-setup-", ".properties");
    try {
      preservePermissions(temporary);
      try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8,
          StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
        config.store(writer, "Goc Sach private configuration");
      }
      checkTarget();
      if (!MessageDigest.isEqual(original, readPrivateConfig()))
        throw new IOException("Private configuration changed during setup");
      Files.move(temporary, TARGET, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
      Arrays.fill(original, (byte) 0);
    }
  }

  private static void checkTarget() throws IOException {
    Path component = TARGET.getRoot();
    for (Path part : TARGET) {
      component = component.resolve(part);
      if (Files.isSymbolicLink(component)
          || !component.toRealPath(LinkOption.NOFOLLOW_LINKS).equals(component.toRealPath()))
        throw new IOException("Symbolic link or redirected private configuration");
    }
    if (!Files.isRegularFile(TARGET, LinkOption.NOFOLLOW_LINKS))
      throw new IOException("Private configuration is not a regular file");
  }

  private static byte[] readPrivateConfig() throws IOException {
    try (InputStream input = Files.newInputStream(TARGET, LinkOption.NOFOLLOW_LINKS)) {
      byte[] data = input.readNBytes(1_048_577);
      if (data.length > 1_048_576) throw new IOException("Invalid private configuration size");
      return data;
    }
  }

  private static void preservePermissions(Path temporary) throws IOException {
    AclFileAttributeView source = Files.getFileAttributeView(TARGET, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    AclFileAttributeView destination = Files.getFileAttributeView(temporary, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (source != null && destination != null) destination.setAcl(source.getAcl());
    else if (Files.getFileAttributeView(temporary, PosixFileAttributeView.class) != null)
      Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
  }

  static Map<String, String> decodeForm(byte[] bytes) throws CharacterCodingException {
    String raw = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    Map<String, String> values = new HashMap<>();
    for (String entry : raw.split("&", -1)) {
      String[] pair = entry.split("=", 2);
      if (pair.length != 2) throw new IllegalArgumentException();
      String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
      String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
      if (!FIELDS.contains(name) || values.putIfAbsent(name, value) != null) throw new IllegalArgumentException();
    }
    if (!values.keySet().equals(FIELDS)) throw new IllegalArgumentException();
    for (String name : List.of("clientId", "apiKey", "checksumKey")) {
      String value = values.get(name);
      if (value.isBlank() || value.length() > 512 || value.chars().anyMatch(Character::isWhitespace)
          || value.chars().anyMatch(Character::isISOControl) || value.indexOf('\ufffd') >= 0)
        throw new IllegalArgumentException();
    }
    if (values.get("_csrf").length() > 128) throw new IllegalArgumentException();
    return values;
  }

  private static boolean singleHeader(HttpExchange exchange, String name, String expected) {
    List<String> values = exchange.getRequestHeaders().get(name);
    return values != null && values.size() == 1 && expected.equals(values.getFirst());
  }

  private String form() {
    return """
        <h1>Cấu hình PayOS cho Góc Sách</h1>
        <p>Điền thông tin từ kênh thanh toán của bạn. Các giá trị chỉ được lưu trên máy này.</p>
        <form method="post" autocomplete="off">
          <input type="hidden" name="_csrf" value="%s">
          <label>Client ID<input name="clientId" type="password" autocomplete="off" spellcheck="false" required maxlength="512"></label>
          <label>API Key<input name="apiKey" type="password" autocomplete="off" spellcheck="false" required maxlength="512"></label>
          <label>Checksum Key<input name="checksumKey" type="password" autocomplete="off" spellcheck="false" required maxlength="512"></label>
          <button type="submit">Lưu cấu hình riêng</button>
        </form>
        """.formatted(token);
  }

  private static void reply(HttpExchange exchange, int status, String content) throws IOException {
    prepareResponseHeaders(exchange.getResponseHeaders());
    byte[] output = ("<!doctype html><html lang=\"vi\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        + "<title>Cấu hình Góc Sách</title><style>body{max-width:36rem;margin:3rem auto;padding:1rem;font:17px system-ui;background:#f7f3e7;color:#173d31}"
        + "label{display:block;margin:1rem 0}input{display:block;width:95%;padding:.7rem;margin-top:.4rem}button{padding:.8rem 1.2rem;background:#17634b;color:white;border:0;border-radius:.4rem}</style>"
        + content + "</html>").getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, output.length);
    try (OutputStream response = exchange.getResponseBody()) { response.write(output); }
  }

  static void prepareResponseHeaders(Headers headers) {
    headers.set("Content-Type", "text/html; charset=UTF-8");
    headers.set("Cache-Control", "no-store");
    headers.set("Pragma", "no-cache");
    // Ordinary form POST must retain its same-origin Origin header; external requests still receive no referrer.
    headers.set("Referrer-Policy", "same-origin");
    headers.set("X-Content-Type-Options", "nosniff");
    headers.set("X-Frame-Options", "DENY");
    headers.set("Content-Security-Policy",
        "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
  }

  private static String randomToken() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
