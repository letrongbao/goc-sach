package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.config.App;
import vn.edu.utex.bookstore.config.Settings;
import vn.edu.utex.bookstore.payment.PaymentGateway;

class PaymentWebTest {
  private static final String SIGNED_RECEIPT = "{\"testSignature\":\"valid\"}";
  private static final String CHECKOUT_URL = "https://pay.payos.vn/web-test-payment";
  private ServletWebServerApplicationContext context;
  private HttpClient client;
  private String base;
  private MemoryStore store;
  private FakeGateway gateway;
  private long copyId;

  @BeforeEach
  void start() {
    var properties = new Properties();
    properties.setProperty("cookie.secure", "false");
    properties.setProperty("otp.secret", "test-only-secret-not-for-production-0123456789");
    store = DemoFixtures.store();
    DemoFixtures.catalogStock(store);
    copyId = store.copies.values().iterator().next().id;
    var app = new App(new Settings(properties), store, Clock.systemUTC(), new OtpTest.Mail());
    gateway = new FakeGateway();
    context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(GocSachApplication.class)
        .properties("server.port=0", "server.address=127.0.0.1",
            "server.servlet.context-path=/bookstore", "logging.level.root=ERROR")
        .initializers(ctx -> {
          var registry = (GenericApplicationContext) ctx;
          registry.registerBean("fixtureApp", App.class, () -> app);
          registry.registerBean("fixturePaymentGateway", PaymentGateway.class, () -> gateway);
        }).run("--server.port=0");
    base = "http://127.0.0.1:" + context.getWebServer().getPort() + "/bookstore";
    client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
  }

  @AfterEach
  void stop() {
    if (context != null) context.close();
  }

  @Test
  void checkoutAndSignedReceiptUnlockSellerProcessingWithoutTrustingReturnParameters() throws Exception {
    long orderId = checkout();
    String route = "/orders/" + orderId + "/payment";
    var page = get(route);
    assertPaymentPage(page, "Chưa thanh toán");
    assertTrue(page.body().contains("Sách được giữ đến"));
    assertTrue(page.body().contains("/bookstore/orders/" + orderId + "/payment/start"));
    assertEquals("no-store", page.headers().firstValue("cache-control").orElseThrow());
    assertTrue(page.headers().firstValue("content-security-policy").orElseThrow().contains("script-src 'self'"));

    assertEquals(403, post(route + "/start", "").statusCode());
    assertEquals(0, gateway.creates);
    assertEquals(409, post("/seller/orders/" + orderId + "/status",
        form(Map.of("_csrf", csrf("/seller/orders/" + orderId), "status", "CONFIRMED"))).statusCode());
    assertEquals("PENDING", store.orders.get(orderId).status);
    assertFalse(get("/seller/orders/" + orderId).body().contains("name=\"status\""));

    var start = post(route + "/start", form(Map.of("_csrf", csrf(route))));
    assertEquals(302, start.statusCode(), start.body());
    assertEquals(CHECKOUT_URL, start.headers().firstValue("location").orElseThrow());
    assertEquals(1, gateway.creates);
    var attempt = store.paymentForOrder(orderId, false);
    assertNotNull(attempt);
    assertPaymentPage(get(route), "Chờ thanh toán");
    assertTrue(get(route).body().contains(CHECKOUT_URL));

    var forgedReturn = get("/payments/payos/return?orderCode=" + attempt.orderCode
        + "&status=PAID&code=00&cancel=false&checkoutUrl=https%3A%2F%2Fevil.example");
    assertPaymentPage(forgedReturn, "Chờ thanh toán");
    assertFalse(forgedReturn.body().contains("evil.example"));
    assertEquals("PENDING", store.orders.get(orderId).paymentStatus);
    assertEquals(0, store.paymentEvents.size());

    gateway.receipt = new PaymentGateway.Receipt(attempt.orderCode, attempt.paymentLinkId,
        attempt.amount, "http-receipt-1", "VND", true);
    HttpClient provider = HttpClient.newHttpClient();
    assertEquals(200, webhook(provider, SIGNED_RECEIPT).statusCode());
    int historyCount = store.orderStatuses(orderId).size();
    assertEquals(200, webhook(provider, SIGNED_RECEIPT).statusCode());
    assertEquals(1, store.paymentEvents.size());
    assertEquals(historyCount, store.orderStatuses(orderId).size());
    assertEquals("PAID", store.orders.get(orderId).paymentStatus);
    assertNull(store.reservationsForOrder(orderId).getFirst().expiresAt);
    assertEquals("RESERVED", store.copies.get(copyId).status);

    var paidPage = get(route);
    assertPaymentPage(paidPage, "Đã thanh toán");
    assertFalse(paidPage.body().contains("/payment/start"));
    assertFalse(paidPage.body().contains("/payment/cancel"));
    assertFalse(get("/orders/" + orderId).body().contains("Hủy đơn hàng</button>"));
    assertEquals(409, post(route + "/cancel", form(Map.of("_csrf", csrf(route)))).statusCode());
    assertEquals(302, post("/seller/orders/" + orderId + "/status",
        form(Map.of("_csrf", csrf("/seller/orders/" + orderId), "status", "CONFIRMED"))).statusCode());
    assertEquals("CONFIRMED", store.orders.get(orderId).status);
  }

  @Test
  void paymentOwnershipAndApiBearerProtectionRemainEnforced() throws Exception {
    long orderId = checkout();
    String route = "/orders/" + orderId + "/payment";
    assertEquals(302, post(route + "/start", form(Map.of("_csrf", csrf(route)))).statusCode());
    var attempt = store.paymentForOrder(orderId, false);
    HttpClient anonymous = HttpClient.newHttpClient();
    var guest = send(anonymous, route, "GET", null, null);
    assertEquals(302, guest.statusCode());
    assertTrue(guest.headers().firstValue("location").orElseThrow().endsWith("/auth/login"));
    assertEquals(401, send(anonymous, "/api/orders/" + orderId + "/payment", "GET", null, null).statusCode());
    // A valid browser session is not a replacement for the API's Bearer credential.
    assertEquals(401, get("/api/orders/" + orderId + "/payment").statusCode());
    var forged = client.send(HttpRequest.newBuilder(URI.create(base + "/api/orders/" + orderId + "/payment"))
        .header("Authorization", "Bearer forged").GET().build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(401, forged.statusCode());

    login("admin");
    assertEquals(404, get(route).statusCode());
    assertEquals(404, post(route + "/start", form(Map.of("_csrf", csrf("/auth/login")))).statusCode());
    assertEquals(404, get("/payments/payos/return?orderCode=" + attempt.orderCode + "&status=PAID").statusCode());
    assertEquals(1, gateway.creates);
    assertEquals("PENDING", store.orders.get(orderId).paymentStatus);
  }

  @Test
  void cancelReturnDoesNotCancelOrderButExplicitProtectedCancellationDoes() throws Exception {
    long orderId = checkout();
    String route = "/orders/" + orderId + "/payment";
    assertEquals(302, post(route + "/start", form(Map.of("_csrf", csrf(route)))).statusCode());
    var attempt = store.paymentForOrder(orderId, false);
    assertPaymentPage(get("/payments/payos/cancel?orderCode=" + attempt.orderCode
        + "&cancel=true&status=CANCELLED"), "Chờ thanh toán");
    assertEquals("PENDING", store.orders.get(orderId).status);
    assertEquals(0, gateway.cancels);
    assertEquals(403, post(route + "/cancel", "").statusCode());
    assertEquals(302, post(route + "/cancel", form(Map.of("_csrf", csrf(route)))).statusCode());
    assertEquals(1, gateway.cancels);
    assertEquals("CANCELLED", store.orders.get(orderId).status);
    assertEquals("CANCELLED", store.orders.get(orderId).paymentStatus);
    assertEquals("AVAILABLE", store.copies.get(copyId).status);
    assertPaymentPage(get(route), "Đã hủy thanh toán");
    assertFalse(get(route).body().contains("/payment/start"));
  }

  @Test
  void publicWebhookRejectsInvalidSignatureAndOversizedPayloadWithoutExposingDetails() throws Exception {
    HttpClient anonymous = HttpClient.newHttpClient();
    var invalid = webhook(anonymous, "{\"signature\":\"forged-secret-test-must-not-leak\"}");
    assertEquals(400, invalid.statusCode(), invalid.body());
    assertTrue(invalid.body().contains("\"received\":false"));
    assertFalse(invalid.body().contains("forged-secret-test-must-not-leak"));
    assertFalse(invalid.body().contains("Exception"));
    assertEquals(413, webhook(anonymous, "x".repeat(32_769)).statusCode());
    assertEquals(0, store.paymentEvents.size());
    assertEquals(0, gateway.creates);
  }

  @Test
  void refreshUsesProviderConfirmationAndCannotBeTriggeredWithoutCsrf() throws Exception {
    long orderId = checkout();
    String route = "/orders/" + orderId + "/payment";
    assertEquals(302, post(route + "/start", form(Map.of("_csrf", csrf(route)))).statusCode());
    var attempt = store.paymentForOrder(orderId, false);
    gateway.markPaid(attempt.orderCode);
    assertEquals(403, post(route + "/refresh", "").statusCode());
    assertEquals("PENDING", store.orders.get(orderId).paymentStatus);
    assertEquals(302, post(route + "/refresh", form(Map.of("_csrf", csrf(route)))).statusCode());
    assertEquals("PAID", store.orders.get(orderId).paymentStatus);
    assertPaymentPage(get(route), "Đã thanh toán");
    int eventCount = store.paymentEvents.size();
    assertEquals(302, post(route + "/refresh", form(Map.of("_csrf", csrf(route)))).statusCode());
    assertEquals(eventCount, store.paymentEvents.size());
  }

  @Test
  void unavailableGatewayHidesChoiceAndRejectsForgedOnlineCheckout() throws Exception {
    gateway.available = false;
    login("reader");
    prepareCartAndAddress();
    var page = get("/checkout");
    assertEquals(200, page.statusCode(), page.body());
    assertTrue(page.body().contains("value=\"COD\""));
    assertFalse(page.body().contains("value=\"PAYOS\""));
    var rejected = submitCheckout(page, "PAYOS");
    assertEquals(503, rejected.statusCode(), rejected.body());
    assertTrue(rejected.body().contains("Thanh toán chuyển khoản tạm chưa khả dụng"));
    assertTrue(store.orders.isEmpty());
    assertEquals("AVAILABLE", store.copies.get(copyId).status);
    assertEquals(0, gateway.creates);
  }

  private long checkout() throws Exception {
    login("reader");
    prepareCartAndAddress();
    var page = get("/checkout");
    assertEquals(200, page.statusCode(), page.body());
    assertTrue(page.body().contains("value=\"PAYOS\""));
    assertTrue(page.body().contains("Phương thức thanh toán"));
    assertTrue(page.body().contains("15 phút"));
    var placed = submitCheckout(page, "PAYOS");
    assertEquals(302, placed.statusCode(), placed.body());
    long orderId = store.orders.values().iterator().next().id;
    assertTrue(placed.headers().firstValue("location").orElseThrow().endsWith("/orders/" + orderId + "/payment"));
    assertEquals("PAYOS", store.orders.get(orderId).paymentMethod);
    assertEquals("UNPAID", store.orders.get(orderId).paymentStatus);
    assertEquals("RESERVED", store.copies.get(copyId).status);
    return orderId;
  }

  private void prepareCartAndAddress() throws Exception {
    assertEquals(302, post("/account/address/save", form(Map.of("_csrf", csrf("/account/addresses"),
        "label", "Nhà", "recipientName", "Bạn đọc", "phone", "0900000000", "line1", "1 Đường Sách",
        "city", "TP HCM", "defaultAddress", "on"))).statusCode());
    assertEquals(302, post("/cart/items/add", form(Map.of("_csrf", csrf("/product/detail?id=1"),
        "copyId", Long.toString(copyId)))).statusCode());
  }

  private HttpResponse<String> submitCheckout(HttpResponse<String> page, String method) throws Exception {
    return post("/checkout", form(Map.of("_csrf", hidden(page.body(), "_csrf"),
        "addressId", Long.toString(store.addresses(2).getFirst().id), "paymentMethod", method,
        "idempotencyKey", hidden(page.body(), "idempotencyKey"),
        "quoteFingerprint", hidden(page.body(), "quoteFingerprint"))));
  }

  private void login(String username) throws Exception {
    var response = post("/auth/login", form(Map.of("_csrf", csrf("/auth/login"), "login", username,
        "password", "demo-password-123", "mode", "session")));
    assertEquals(302, response.statusCode(), response.body());
  }

  private void assertPaymentPage(HttpResponse<String> response, String status) {
    assertEquals(200, response.statusCode(), response.body());
    assertTrue(response.body().contains("Thanh toán đơn hàng"));
    assertTrue(response.body().contains(status), response.body());
    assertFalse(response.body().contains("${"));
    var scripts = Pattern.compile("<script\\b[^>]*>.*?</script>", Pattern.DOTALL).matcher(response.body());
    int scriptCount = 0;
    while (scripts.find()) {
      String allowedSource = Pattern.quote(URI.create(base).getPath() + "/assets/experience.js");
      assertTrue(scripts.group().matches("<script src=\"" + allowedSource
          + "\" defer(?:=\"defer\")?></script>"), scripts.group());
      scriptCount++;
    }
    assertEquals(1, scriptCount);
    assertFalse(response.body().contains("style=\""));
    for (String internal : new String[] {"clientId", "apiKey", "checksumKey", "PostgreSQL", "Exception Report"})
      assertFalse(response.body().contains(internal), internal);
  }

  private String csrf(String route) throws Exception {
    var response = get(route);
    assertEquals(200, response.statusCode(), response.body());
    return hidden(response.body(), "_csrf");
  }

  private static String hidden(String html, String name) {
    var matcher = Pattern.compile("name=\"" + name + "\" value=\"([^\"]+)\"").matcher(html);
    assertTrue(matcher.find(), html);
    return matcher.group(1);
  }

  private static String form(Map<String, String> fields) {
    return fields.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
  }

  private HttpResponse<String> get(String route) throws Exception { return send(client, route, "GET", null, null); }
  private HttpResponse<String> post(String route, String body) throws Exception {
    return send(client, route, "POST", body, "application/x-www-form-urlencoded");
  }
  private HttpResponse<String> webhook(HttpClient provider, String body) throws Exception {
    return send(provider, "/payments/payos/webhook", "POST", body, "application/json");
  }
  private HttpResponse<String> send(HttpClient http, String route, String method, String body, String type) throws Exception {
    var request = HttpRequest.newBuilder(URI.create(base + route));
    if (type != null) request.header("Content-Type", type);
    if ("POST".equals(method)) request.POST(HttpRequest.BodyPublishers.ofString(body)); else request.GET();
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static final class FakeGateway implements PaymentGateway {
    boolean available = true;
    int creates;
    int cancels;
    Receipt receipt;
    final Map<Long, Snapshot> states = new LinkedHashMap<>();
    public boolean enabled() { return available; }
    public Link create(long code, long amount, String description, Instant expiresAt) {
      creates++;
      String id = "test-payment-" + code;
      states.put(code, new Snapshot(code, id, amount, 0, "PENDING", CHECKOUT_URL));
      return new Link(code, amount, id, CHECKOUT_URL, "PENDING");
    }
    public Snapshot query(long code) {
      return states.getOrDefault(code, new Snapshot(code, "", 0, 0, "NOT_FOUND", ""));
    }
    public Snapshot cancel(long code, String reason) {
      cancels++;
      var previous = query(code);
      var cancelled = new Snapshot(code, previous.paymentLinkId(), previous.amount(), previous.amountPaid(), "CANCELLED", "");
      states.put(code, cancelled);
      return cancelled;
    }
    public Receipt verify(String raw) {
      if (!SIGNED_RECEIPT.equals(raw) || receipt == null) throw Problem.invalid("Chữ ký thanh toán không hợp lệ.");
      markPaid(receipt.orderCode());
      return receipt;
    }
    void markPaid(long code) {
      var previous = query(code);
      states.put(code, new Snapshot(code, previous.paymentLinkId(), previous.amount(), previous.amount(), "PAID", previous.checkoutUrl()));
    }
  }
}
