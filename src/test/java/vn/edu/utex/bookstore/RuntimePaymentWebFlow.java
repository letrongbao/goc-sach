package vn.edu.utex.bookstore;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import vn.edu.utex.bookstore.config.Settings;

/** Explicit real-provider acceptance via the local application's web endpoints; never transfers money. */
public final class RuntimePaymentWebFlow {
  private static final String BASE = "http://127.0.0.1:8080";
  private final Settings settings;
  private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
      .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(10)).build();
  private record State(String orderStatus, String paymentStatus, String copyStatus, String reservationStatus,
      long orderCode, String checkoutUrl, int attempts) {}
  private static final class AcceptanceFailure extends RuntimeException {
    final String code;
    AcceptanceFailure(String code) { this.code = code; }
  }

  private RuntimePaymentWebFlow(Settings settings) { this.settings = settings; }

  public static void main(String[] arguments) {
    if (arguments.length != 1 || !java.util.Set.of("--accept-unpaid-create-and-cancel", "--show-checkout-url", "--prepare-next-order").contains(arguments[0])) {
      System.out.println("Usage: RuntimePaymentWebFlow --accept-unpaid-create-and-cancel | --show-checkout-url | --prepare-next-order");
      System.exit(2);
      return;
    }
    try {
      var flow = new RuntimePaymentWebFlow(Settings.load());
      if ("--show-checkout-url".equals(arguments[0])) flow.showCheckoutUrl();
      else if ("--prepare-next-order".equals(arguments[0])) flow.prepareNextOrder();
      else flow.run();
    } catch (AcceptanceFailure failure) {
      System.err.println("WEB_ACCEPTANCE_FAILED code=" + failure.code);
      System.exit(1);
    } catch (Exception failure) {
      // No credentials, response bodies, SQL values or exception stack traces in diagnostics.
      System.err.println("WEB_ACCEPTANCE_FAILED code=HTTP_OR_DATABASE");
      System.exit(1);
    }
  }

  private void run() throws Exception {
    Properties fixture = fixture();
    long orderId = Long.parseLong(value(fixture, "record.order-id"));
    long userId = Long.parseLong(value(fixture, "record.user-id"));
    long copyId = Long.parseLong(value(fixture, "record.copy-id"));
    State initial = state(orderId, userId, copyId);
    require("PENDING".equals(initial.orderStatus()) && "UNPAID".equals(initial.paymentStatus()), "ORDER_NOT_FRESH_UNPAID");
    login(fixture);
    String route = "/orders/" + orderId + "/payment";
    var paymentPage = page(route);
    require(paymentPage.body().contains("Chưa thanh toán") && !paymentPage.body().contains("${"), "PAYMENT_RENDER");
    expect(post(route + "/start", Map.of()), 403, "START_CSRF");
    var start = post(route + "/start", Map.of("_csrf", csrf(paymentPage.body())));
    expect(start, 302, "START");
    String checkout = start.headers().firstValue("location").orElseThrow(() -> new AcceptanceFailure("NO_PROVIDER_REDIRECT"));
    requireProviderUrl(checkout);
    State started = state(orderId, userId, copyId);
    require("PENDING".equals(started.paymentStatus()) && started.attempts() == 1
        && checkout.equals(started.checkoutUrl()), "START_STATE");

    expect(post(route + "/refresh", Map.of("_csrf", csrf(page(route).body()))), 302, "REFRESH");
    State confirmedPending = state(orderId, userId, copyId);
    require("PENDING".equals(confirmedPending.paymentStatus()) && "RESERVED".equals(confirmedPending.copyStatus()), "PROVIDER_NOT_PENDING");
    require(page(route).body().contains("Chờ thanh toán"), "PENDING_RENDER");
    var repeated = post(route + "/start", Map.of("_csrf", csrf(page(route).body())));
    expect(repeated, 302, "REPEAT_START");
    State reused = state(orderId, userId, copyId);
    require(checkout.equals(repeated.headers().firstValue("location").orElse(""))
        && reused.orderCode() == started.orderCode() && reused.attempts() == 1, "LINK_NOT_REUSED");

    // Cancel only while the confirmed provider state remains unpaid; paid/refund cases are never acted on here.
    expect(post(route + "/cancel", Map.of("_csrf", csrf(page(route).body()))), 302, "CANCEL_UNPAID");
    State cancelled = state(orderId, userId, copyId);
    require("CANCELLED".equals(cancelled.orderStatus()) && "CANCELLED".equals(cancelled.paymentStatus())
        && "AVAILABLE".equals(cancelled.copyStatus()) && "RELEASED".equals(cancelled.reservationStatus()), "CANCEL_STATE");
    require(page(route).body().contains("Đã hủy thanh toán"), "CANCEL_RENDER");
    System.out.printf("WEB_ACCEPTANCE order_id=%d user_id=%d copy_id=%d order_code=%d start_http=302 refresh_http=302 link_reused=1 cancel_http=302 cancelled=1 copy_available=1 payment_attempts=%d%n",
        orderId, userId, copyId, started.orderCode(), cancelled.attempts());
  }

  private void showCheckoutUrl() throws Exception {
    Properties fixture = fixture();
    State recorded = state(Long.parseLong(value(fixture, "record.order-id")), Long.parseLong(value(fixture, "record.user-id")),
        Long.parseLong(value(fixture, "record.copy-id")));
    requireProviderUrl(recorded.checkoutUrl());
    System.out.println("PUBLIC_CHECKOUT_URL=" + recorded.checkoutUrl());
  }

  private void prepareNextOrder() throws Exception {
    Properties fixture = fixture();
    long userId = Long.parseLong(value(fixture, "record.user-id"));
    long copyId = Long.parseLong(value(fixture, "record.copy-id"));
    String priorHandoff = fixture.getProperty("handoff.order-id");
    if (priorHandoff != null) {
      long existingId = Long.parseLong(priorHandoff);
      State existing = state(existingId, userId, copyId);
      require("PENDING".equals(existing.orderStatus()) && "UNPAID".equals(existing.paymentStatus()) && existing.attempts() == 0,
          "HANDOFF_ORDER_ALREADY_CHANGED");
      System.out.printf("WEB_HANDOFF order_id=%d user_id=%d copy_id=%d scenarios_created=0 payment_attempts=0%n", existingId, userId, copyId);
      return;
    }
    State first = state(Long.parseLong(value(fixture, "record.order-id")), userId, copyId);
    require("CANCELLED".equals(first.orderStatus()) && "AVAILABLE".equals(first.copyStatus()), "FIRST_ORDER_NOT_CANCELLED");
    login(fixture);
    String productPage = page("/product/detail?id=" + value(fixture, "record.product-id")).body();
    expect(post("/cart/items/add", Map.of("_csrf", csrf(productPage), "copyId", Long.toString(copyId))), 302, "HANDOFF_CART");
    String checkoutPage = page("/checkout").body();
    var placed = post("/checkout", Map.of("_csrf", csrf(checkoutPage), "addressId", value(fixture, "record.address-id"),
        "paymentMethod", "PAYOS", "idempotencyKey", hidden(checkoutPage, "idempotencyKey"),
        "quoteFingerprint", hidden(checkoutPage, "quoteFingerprint")));
    expect(placed, 302, "HANDOFF_CHECKOUT");
    String target = placed.headers().firstValue("location").orElse("");
    var orderRoute = Pattern.compile("/orders/(\\d+)/payment$").matcher(target);
    require(orderRoute.find(), "HANDOFF_REDIRECT");
    long orderId = Long.parseLong(orderRoute.group(1));
    State fresh = state(orderId, userId, copyId);
    require("PENDING".equals(fresh.orderStatus()) && "UNPAID".equals(fresh.paymentStatus()) && fresh.attempts() == 0,
        "HANDOFF_ORDER_NOT_FRESH");
    fixture.setProperty("handoff.order-id", Long.toString(orderId));
    fixture.setProperty("handoff.checkout-key", hidden(checkoutPage, "idempotencyKey"));
    RuntimePaymentScenario.writePrivate(Path.of("config/payment-acceptance.properties").toAbsolutePath().normalize(), fixture, false);
    System.out.printf("WEB_HANDOFF order_id=%d user_id=%d copy_id=%d scenarios_created=1 payment_attempts=0%n", orderId, userId, copyId);
  }

  private static Properties fixture() throws Exception {
    Properties fixture = new Properties();
    Path manifest = Path.of("config/payment-acceptance.properties").toAbsolutePath().normalize();
    if (Files.isSymbolicLink(manifest)) throw new AcceptanceFailure("UNSAFE_MANIFEST");
    try (var reader = Files.newBufferedReader(manifest)) { fixture.load(reader); }
    require("PAYOS_ACCEPTANCE_V1".equals(fixture.getProperty("setup.kind")), "WRONG_SCENARIO");
    return fixture;
  }

  private void login(Properties fixture) throws Exception {
    String loginPage = page("/auth/login").body();
    expect(post("/auth/login", Map.of("_csrf", csrf(loginPage), "login", value(fixture, "login.username"),
        "password", value(fixture, "login.password"), "mode", "session")), 302, "LOGIN");
  }

  private static void requireProviderUrl(String checkout) {
    URI destination = URI.create(checkout);
    require("https".equals(destination.getScheme()) && "pay.payos.vn".equalsIgnoreCase(destination.getHost())
        && destination.getUserInfo() == null && destination.getFragment() == null, "UNTRUSTED_PROVIDER_URL");
  }

  private State state(long orderId, long userId, long copyId) throws Exception {
    String url = settings.require("db.url");
    URI address = URI.create(url.substring("jdbc:".length()));
    require("postgresql".equals(address.getScheme()) && address.getPort() == 5434
        && java.util.Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(address.getHost())
        && "/goc_sach".equals(address.getPath()) && address.getUserInfo() == null
        && address.getQuery() == null && address.getFragment() == null, "DATABASE_NOT_LOCAL_GOC_SACH");
    try (var connection = DriverManager.getConnection(url, settings.require("db.user"), settings.require("db.password"))) {
      connection.setReadOnly(true);
      try (var query = connection.prepareStatement("""
          select o.status, o.payment_status, c.status, r.status,
                 coalesce(p.order_code,0), coalesce(p.checkout_url,''),
                 (select count(*) from payment_attempts pa where pa.order_id=o.id)
            from orders o join order_items i on i.order_id=o.id
            join book_copies c on c.id=i.copy_id
            join inventory_reservations r on r.order_id=o.id and r.copy_id=c.id
            left join payment_attempts p on p.order_id=o.id
           where o.id=? and o.user_id=? and c.id=? and o.payment_method='PAYOS' and o.total=31000
          """)) {
        query.setLong(1, orderId); query.setLong(2, userId); query.setLong(3, copyId);
        try (var rows = query.executeQuery()) {
          require(rows.next(), "MISSING_ACCEPTANCE_ORDER");
          State result = new State(rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4),
              rows.getLong(5), rows.getString(6), rows.getInt(7));
          require(!rows.next(), "AMBIGUOUS_ACCEPTANCE_ORDER");
          return result;
        }
      }
    }
  }

  private HttpResponse<String> page(String route) throws Exception {
    var response = http.send(HttpRequest.newBuilder(URI.create(BASE + route)).timeout(Duration.ofSeconds(30)).GET().build(),
        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    expect(response, 200, "PAGE");
    return response;
  }
  private HttpResponse<String> post(String route, Map<String, String> fields) throws Exception {
    String form = fields.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
    return http.send(HttpRequest.newBuilder(URI.create(BASE + route)).timeout(Duration.ofSeconds(60))
        .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),
        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }
  private static String csrf(String body) {
    return hidden(body, "_csrf");
  }
  private static String hidden(String body, String field) {
    var token = Pattern.compile("name=\"" + Pattern.quote(field) + "\" value=\"([^\"]+)\"").matcher(body);
    require(token.find(), "MISSING_FORM_FIELD");
    return token.group(1);
  }
  private static String value(Properties values, String key) {
    String result = values.getProperty(key);
    require(result != null && !result.isBlank(), "MANIFEST_INCOMPLETE");
    return result;
  }
  private static void expect(HttpResponse<?> response, int status, String step) {
    require(response.statusCode() == status, step + "_HTTP_" + response.statusCode());
  }
  private static void require(boolean condition, String code) { if (!condition) throw new AcceptanceFailure(code); }
}
