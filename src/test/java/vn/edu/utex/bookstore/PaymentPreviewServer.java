package vn.edu.utex.bookstore;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.util.HtmlUtils;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.buyer.BuyerService;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.config.App;
import vn.edu.utex.bookstore.config.Settings;
import vn.edu.utex.bookstore.order.OrderService;
import vn.edu.utex.bookstore.payment.PaymentGateway;
import vn.edu.utex.bookstore.payment.PaymentService;

/** Disposable local payment preview. This class and all simulated provider routes are test-source only. */
public final class PaymentPreviewServer {
  private static final int PORT = 18081;
  private static final String BASE = "http://127.0.0.1:" + PORT;

  public static void main(String[] args) throws IOException {
    var properties = new Properties();
    properties.setProperty("cookie.secure", "false");
    properties.setProperty("otp.secret", "preview-only-not-production-secret-0123456789");
    MemoryStore store = DemoFixtures.store();
    DemoFixtures.catalogStock(store);
    Clock clock = Clock.systemUTC();
    App app = new App(new Settings(properties), store, clock,
        (to, subject, body) -> { throw new IllegalStateException("Preview does not deliver email"); });
    BuyerService buyer = new BuyerService(store, clock);
    OrderService orders = new OrderService(store, clock);
    FakePaymentGateway gateway = new FakePaymentGateway();
    PaymentService payments = new PaymentService(store, orders, gateway, clock);
    seed(app, store, buyer, orders, payments, gateway);

    var context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(GocSachApplication.class)
        .properties("server.port=" + PORT, "server.address=127.0.0.1", "logging.level.root=WARN")
        .initializers(ctx -> {
          var registry = (GenericApplicationContext) ctx;
          registry.registerBean("previewApp", App.class, () -> app);
          registry.registerBean("previewBuyer", BuyerService.class, () -> buyer);
          registry.registerBean("previewOrders", OrderService.class, () -> orders);
          registry.registerBean("previewGateway", PaymentGateway.class, () -> gateway);
          registry.registerBean("previewPayments", PaymentService.class, () -> payments);
          registry.registerBean("previewHostedPayment", ServletRegistrationBean.class, () ->
              new ServletRegistrationBean<>(new HostedPaymentPreview(store, gateway, payments), "/__preview/payments/*"));
        }).run("--server.port=" + PORT, "--server.address=127.0.0.1");
    context.getServletContext().setAttribute("preview", true);
    System.out.println("PAYMENT PREVIEW ONLY: " + BASE + " — memory data, fake payment provider, no database/email.");
    System.out.println("Fixture login: reader / demo-password-123. Reader owns the first shop; admin owns the second.");
    System.out.println("Payment pages: /orders/1/payment (unpaid), /orders/2/payment (paid), /orders/3/payment (cancelled).");
    System.out.println("Two-shop cart: /checkout. Seller screen: /seller/orders. Restart resets preview data.");
    System.out.println("Press Enter to stop.");
    try { System.in.read(); } finally { context.close(); }
  }

  private static void seed(App app, MemoryStore store, BuyerService buyer, OrderService orders,
      PaymentService payments, FakePaymentGateway gateway) {
    Identity reader = Identity.of(store.findLogin("reader"));
    Identity admin = Identity.of(store.findLogin("admin"));
    buyer.saveProfile(reader.id(), "Lê Trọng Bảo", "0900000000");
    var address = buyer.saveAddress(reader.id(), null, "Nhà", "Lê Trọng Bảo", "0900000000",
        "12 Đường Sách", "Phường Linh Chiểu", "Thủ Đức", "TP. Hồ Chí Minh", true);
    buyer.saveAddress(reader.id(), null, "Văn phòng", "Lê Trọng Bảo", "0900000000",
        "218 Nguyễn Văn Trỗi", "Phường 8", "Phú Nhuận", "TP. Hồ Chí Minh", false);
    List<Long> copies = new ArrayList<>(store.copies.keySet());
    seedOrder(buyer, orders, reader.id(), address.id, List.of(copies.get(0)), "payment-preview-unpaid", "PAYOS");
    long paidId = seedOrder(buyer, orders, reader.id(), address.id, List.of(copies.get(1), copies.get(2)), "payment-preview-paid", "PAYOS");
    payments.start(reader.id(), paidId);
    var paidAttempt = store.paymentForOrder(paidId, false);
    gateway.markPaid(paidAttempt.orderCode);
    payments.webhook("preview-paid:" + paidAttempt.orderCode);
    long cancelledId = seedOrder(buyer, orders, reader.id(), address.id, List.of(copies.get(3)), "payment-preview-cancelled", "PAYOS");
    payments.start(reader.id(), cancelledId);
    payments.cancel(reader.id(), cancelledId);
    long codId = seedOrder(buyer, orders, reader.id(), address.id, List.of(copies.get(4)), "payment-preview-cod", "COD");
    orders.advance(reader, codId, "CONFIRMED");

    long shopId = app.shops.approve(admin, app.shops.apply(admin, "Trang Sách Bình Yên",
        "218 Nguyễn Văn Trỗi, TP. Hồ Chí Minh"));
    long secondCopy = app.inventory.receive(admin, shopId, 8L, "LIKE_NEW", "Bìa và trang sách còn rất đẹp.",
        "125000", "Nhập sách minh họa");
    app.inventory.update(admin, shopId, secondCopy, 0, "LIKE_NEW", "Bìa và trang sách còn rất đẹp.",
        "125000", "AVAILABLE", "Mở bán minh họa");
    buyer.addToCart(reader.id(), copies.get(5));
    buyer.addToCart(reader.id(), secondCopy);
    buyer.addFavorite(reader.id(), 7L);
    buyer.recordViewed(reader.id(), 8L);
  }

  private static long seedOrder(BuyerService buyer, OrderService orders, long readerId, long addressId,
      List<Long> copyIds, String key, String method) {
    for (long id : copyIds) buyer.addToCart(readerId, id);
    return orders.checkout(readerId, addressId, key, orders.quote(readerId).fingerprint(), method).orders().getFirst().id;
  }

  private static final class FakePaymentGateway implements PaymentGateway {
    private final Map<Long, Snapshot> states = new ConcurrentHashMap<>();
    public boolean enabled() { return true; }
    public Link create(long code, long amount, String description, Instant expiresAt) {
      String id = "preview-payment-" + code;
      String url = BASE + "/__preview/payments/" + code;
      states.put(code, new Snapshot(code, id, amount, 0, "PENDING", url));
      return new Link(code, amount, id, url, "PENDING");
    }
    public Snapshot query(long code) {
      return states.getOrDefault(code, new Snapshot(code, "", 0, 0, "NOT_FOUND", ""));
    }
    public Snapshot cancel(long code, String reason) {
      Snapshot old = query(code);
      if ("PAID".equals(old.status())) return old;
      Snapshot cancelled = new Snapshot(code, old.paymentLinkId(), old.amount(), old.amountPaid(), "CANCELLED", "");
      states.put(code, cancelled);
      return cancelled;
    }
    public Receipt verify(String raw) {
      if (raw == null || !raw.startsWith("preview-paid:")) throw Problem.invalid("Chữ ký thanh toán thử không hợp lệ.");
      final long code;
      try { code = Long.parseLong(raw.substring("preview-paid:".length())); }
      catch (NumberFormatException invalid) { throw Problem.invalid("Mã thanh toán thử không hợp lệ."); }
      Snapshot state = query(code);
      if (!"PAID".equals(state.status())) throw Problem.invalid("Thanh toán thử chưa được xác nhận.");
      return new Receipt(code, state.paymentLinkId(), state.amount(), "preview-receipt-" + code, "VND", true);
    }
    void markPaid(long code) {
      Snapshot old = query(code);
      states.put(code, new Snapshot(code, old.paymentLinkId(), old.amount(), old.amount(), "PAID", old.checkoutUrl()));
    }
  }

  private static final class HostedPaymentPreview extends HttpServlet {
    private final MemoryStore store;
    private final FakePaymentGateway gateway;
    private final PaymentService payments;
    HostedPaymentPreview(MemoryStore store, FakePaymentGateway gateway, PaymentService payments) {
      this.store = store; this.gateway = gateway; this.payments = payments;
    }
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
      Long code = ownedCode(request, response);
      if (code == null) return;
      var snapshot = gateway.query(code);
      var attempt = store.paymentByCode(code);
      var order = store.orders.get(attempt.orderId);
      String csrf = HtmlUtils.htmlEscape(String.valueOf(request.getAttribute("csrf")));
      String shop = HtmlUtils.htmlEscape(store.shops.get(order.shopId).name);
      String amount = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(new BigDecimal(snapshot.amount()));
      response.setContentType("text/html;charset=UTF-8");
      response.setHeader("Cache-Control", "no-store");
      response.getWriter().write("""
          <!doctype html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
          <title>Thanh toán thử · Góc Sách</title><link rel="stylesheet" href="/assets/bootstrap.min.css"><link rel="stylesheet" href="/assets/app.css"></head>
          <body><main><section class="container section narrow"><div class="panel p-4"><p class="eyebrow">BẢN TRẢI NGHIỆM</p><h1>Trang thanh toán thử</h1>
          <p class="alert alert-warning">Trang này chỉ dùng thử giao diện. Không chuyển tiền thật.</p>
          <p>Cửa hàng: <strong>%s</strong></p><div class="checkout-line total"><span>Số tiền minh họa</span><strong>%s ₫</strong></div>
          <form method="post"><input type="hidden" name="_csrf" value="%s"><div class="form-actions">
          <button class="btn btn-forest" name="simulate" value="paid" type="submit">Xác nhận thanh toán thử</button>
          <button class="btn btn-outline-danger" name="simulate" value="cancelled" type="submit">Hủy thanh toán thử</button></div></form>
          <a class="text-link d-block mt-4" href="/orders/%d/payment">Quay lại đơn hàng</a></div></section></main></body></html>
          """.formatted(shop, amount, csrf, order.id));
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
      Long code = ownedCode(request, response);
      if (code == null) return;
      Identity identity = (Identity) request.getAttribute("identity");
      var attempt = store.paymentByCode(code);
      if ("paid".equals(request.getParameter("simulate"))) {
        gateway.markPaid(code);
        payments.webhook("preview-paid:" + code);
      } else if ("cancelled".equals(request.getParameter("simulate"))) {
        gateway.cancel(code, "Hủy trong bản trải nghiệm");
        payments.refresh(identity.id(), attempt.orderId);
      }
      response.sendRedirect("/payments/payos/return?orderCode=" + code);
    }
    private Long ownedCode(HttpServletRequest request, HttpServletResponse response) throws IOException {
      Identity identity = (Identity) request.getAttribute("identity");
      if (identity == null) { response.sendRedirect("/auth/login"); return null; }
      String path = request.getPathInfo();
      final long code;
      try { code = Long.parseLong(path == null ? "" : path.substring(1)); }
      catch (NumberFormatException invalid) { response.sendError(404); return null; }
      var attempt = store.paymentByCode(code);
      if (attempt == null || store.orders.get(attempt.orderId).userId != identity.id()) {
        response.sendError(404); return null;
      }
      return code;
    }
  }
}
