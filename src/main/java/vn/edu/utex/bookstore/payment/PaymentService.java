package vn.edu.utex.bookstore.payment;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.order.Order;
import vn.edu.utex.bookstore.order.OrderService;
import vn.edu.utex.bookstore.order.OrderStatusHistory;
import vn.edu.utex.bookstore.persistence.Store;

/** Remote calls never run inside a database transaction. Order locks serialize local settlement. */
public final class PaymentService {
  private static final java.security.SecureRandom ORDER_CODES = new java.security.SecureRandom();
  public record PaymentView(long orderId, String status, String statusLabel, String checkoutUrl,
      Instant expiresAt, boolean canStart, boolean canCancel) {
    public String displayExpiresAt() {
      return expiresAt == null ? "" : DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
          .withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(expiresAt);
    }
  }
  public record ReviewView(long orderId, long orderCode, long amount, String status, String statusLabel) {}
  private record Attempt(long orderId, long code, long amount, String linkId, String url,
      String status, Instant expiresAt, boolean fresh) {}
  private final Store store;
  private final OrderService orders;
  private final PaymentGateway gateway;
  private final Clock clock;
  private final java.util.concurrent.atomic.AtomicLong maintenanceCursor = new java.util.concurrent.atomic.AtomicLong();

  public PaymentService(Store store, OrderService orders, PaymentGateway gateway, Clock clock) {
    this.store = store; this.orders = orders; this.gateway = gateway; this.clock = clock;
  }
  public boolean enabled() { return gateway.enabled(); }

  public long orderIdForCode(long userId, long code) {
    return store.tx(data -> { var payment = data.paymentByCode(code); if (payment == null) throw Problem.missing();
      owned(data, userId, payment.orderId, false); return payment.orderId; });
  }
  public List<ReviewView> reviews(Identity actor) {
    return store.tx(data -> {
      var user = actor == null ? null : data.user(actor.id());
      if (user == null || !user.active || user.blocked || !"ADMIN".equals(user.role) || user.authVersion != actor.authVersion())
        throw new Problem(403, "Bạn không có quyền xem mục này.");
      return data.paymentsNeedingReview(100).stream().map(p -> new ReviewView(p.orderId, p.orderCode, p.amount, p.status, statusLabel(p.status))).toList();
    });
  }

  public OrderService.CheckoutResult checkout(long userId, long addressId, String key, String fingerprint, String rawMethod) {
    String method = rawMethod == null || rawMethod.isBlank() ? "COD" : rawMethod.strip();
    if ("PAYOS".equals(method)) requireEnabled();
    return orders.checkout(userId, addressId, key, fingerprint, method);
  }

  public OrderService.CheckoutResult checkout(long userId,long addressId,String key,String fingerprint,String rawMethod,long carrierId,String voucherCode) {
    String method=rawMethod==null||rawMethod.isBlank()?"COD":rawMethod.strip();
    if("PAYOS".equals(method))requireEnabled();
    return orders.checkout(userId,addressId,key,fingerprint,method,carrierId,voucherCode);
  }
  public PaymentView view(long userId, long orderId) {
    return store.tx(data -> {
      Order order = owned(data, userId, orderId, false);
      var attempt = data.paymentForOrder(orderId, false);
      boolean pending = "PENDING".equals(order.status) && Set.of("UNPAID", "PENDING").contains(order.paymentStatus);
      boolean inTime = order.paymentExpiresAt != null && order.paymentExpiresAt.isAfter(clock.instant());
      String url = attempt == null || !pending || !inTime ? "" : Objects.toString(attempt.checkoutUrl, "");
      return new PaymentView(order.id, order.paymentStatus, statusLabel(order.paymentStatus), url,
          order.paymentExpiresAt, enabled() && pending && inTime, enabled() && pending);
    });
  }

  public String start(long userId, long orderId) {
    requireEnabled();
    Attempt attempt = store.tx(data -> {
      Order order = owned(data, userId, orderId, true);
      if (!"PENDING".equals(order.status) || !Set.of("UNPAID", "PENDING").contains(order.paymentStatus))
        throw new Problem(409, "Đơn không còn chờ thanh toán.");
      if (order.paymentExpiresAt == null || !order.paymentExpiresAt.isAfter(clock.instant()))
        throw new Problem(410, "Thời gian thanh toán đã hết. Vui lòng kiểm tra đơn trước khi đặt lại.");
      PaymentAttempt payment = data.paymentForOrder(orderId, true);
      boolean fresh = payment == null;
      if (fresh) {
        payment = new PaymentAttempt(); payment.orderId = orderId;
        payment.amount = order.total.longValueExact(); payment.createdAt = clock.instant();
        payment.updatedAt = clock.instant(); payment.expiresAt = order.paymentExpiresAt;
        do {
          payment.orderCode = 1_000_000_000_001L + ORDER_CODES.nextLong(8_000_000_000_000L);
        } while (data.paymentByCode(payment.orderCode) != null);
        data.savePayment(payment);
        order.paymentStatus = "PENDING"; order.updatedAt = clock.instant(); data.saveOrder(order);
      } else if ("CREATING".equals(payment.status) && payment.updatedAt.plusSeconds(30).isAfter(clock.instant())) {
        throw new Problem(409, "Đang chuẩn bị thanh toán. Vui lòng đợi một chút rồi kiểm tra lại.");
      }
      return snapshot(payment, fresh);
    });
    if (!attempt.url().isBlank() && "PENDING".equals(attempt.status())) return attempt.url();
    if (!attempt.fresh()) {
      var state = gateway.query(attempt.code());
      if (!"NOT_FOUND".equals(state.status())) {
        applySnapshot(attempt, state);
        var current = view(userId, orderId);
        if (!current.checkoutUrl().isBlank() && current.canStart()) return current.checkoutUrl();
        throw new Problem(409, "Đã kiểm tra thanh toán. Vui lòng xem trạng thái đơn trước khi tiếp tục.");
      }
    }
    // The persisted code is reused even if a previous request reached the provider and timed out.
    var link = gateway.create(attempt.code(), attempt.amount(), "GS" + String.format(Locale.ROOT, "%07d", attempt.code() % 10_000_000), attempt.expiresAt());
    if (link.orderCode() != attempt.code() || link.amount() != attempt.amount() || link.paymentLinkId() == null || link.paymentLinkId().isBlank())
      throw new Problem(502, "Chưa thể xác nhận thông tin thanh toán. Vui lòng kiểm tra lại.");
    store.tx(data -> {
      Order order = OrderService.lockedOrder(data, orderId);
      PaymentAttempt payment = data.paymentForOrder(orderId, true);
      if (payment == null || payment.orderCode != attempt.code()) throw Problem.missing();
      if (!payment.paymentLinkId.isBlank() && !payment.paymentLinkId.equals(link.paymentLinkId()))
        throw new Problem(409, "Thông tin thanh toán cần được kiểm tra lại.");
      payment.paymentLinkId = link.paymentLinkId(); payment.checkoutUrl = link.checkoutUrl();
      if ("CREATING".equals(payment.status)) payment.status = "PENDING";
      payment.updatedAt = clock.instant(); data.savePayment(payment);
      if ("PENDING".equals(order.status) && Set.of("UNPAID", "PENDING").contains(order.paymentStatus))
        order.paymentStatus = "PENDING";
      data.saveOrder(order);
      return null;
    });
    PaymentView result = view(userId, orderId);
    if (!result.canStart()) throw new Problem(409, "Trạng thái đơn vừa thay đổi. Vui lòng kiểm tra lại.");
    return result.checkoutUrl();
  }

  public void refresh(long userId, long orderId) {
    requireEnabled();
    Attempt attempt = store.tx(data -> {
      owned(data, userId, orderId, false);
      PaymentAttempt payment = data.paymentForOrder(orderId, false);
      return payment == null ? null : snapshot(payment, false);
    });
    if (attempt != null) applySnapshot(attempt, gateway.query(attempt.code()));
    if (isDue(orderId)) expire(orderId);
  }

  public void webhook(String rawJson) {
    requireEnabled();
    PaymentGateway.Receipt receipt = gateway.verify(rawJson);
    if (!receipt.success()) return;
    if (receipt.orderCode() <= 0 || receipt.amount() <= 0 || !"VND".equals(receipt.currency())
        || receipt.reference() == null || receipt.reference().isBlank() || receipt.reference().length() > 200
        || receipt.paymentLinkId() == null || receipt.paymentLinkId().isBlank())
      throw Problem.invalid("Thông tin thanh toán không hợp lệ.");
    var payment = store.tx(data -> data.paymentByCode(receipt.orderCode()));
    // payOS confirms a new webhook using a signed sample which is not a local order.
    if (payment == null) return;
    settle(payment.orderId, receipt.orderCode(), receipt.paymentLinkId(), receipt.amount(), receipt.reference(), false);
  }

  public void cancel(long userId, long orderId) {
    Order order = store.tx(data -> {
      var user = data.user(userId);
      Order found = data.order(orderId, false);
      if (user == null || !user.active || user.blocked || found == null || found.userId != userId) throw Problem.missing();
      return found;
    });
    if (!"PENDING".equals(order.status) && !"CANCELLED".equals(order.status))
      throw new Problem(409, "Đơn đã được xử lý nên không thể hủy.");
    if ("PAYOS".equals(order.paymentMethod)) cancelOnline(orderId, false);
    else orders.cancel(userId, orderId);
  }

  public void sellerCancel(Identity actor, long orderId, String reason) {
    var order = orders.sellerDetail(actor, orderId).order(); // verifies live shop access before any remote request
    if (!Set.of("PENDING", "CONFIRMED", "CANCELLED").contains(order.status()))
      throw new Problem(409, "Đơn đang giao hoặc đã kết thúc nên không thể hủy.");
    String note = reason == null ? "" : reason.strip();
    if (note.length() < 5 || note.length() > 500 || note.codePoints().anyMatch(Character::isISOControl))
      throw Problem.invalid("Vui lòng ghi lý do hủy từ 5 đến 500 ký tự.");
    if ("PAYOS".equals(order.paymentMethod())) confirmRemoteCancellation(orderId);
    orders.sellerCancel(actor, orderId, note);
  }

  public void maintain() {
    boolean providerAvailable = enabled();
    Set<Long> due = providerAvailable
        ? new LinkedHashSet<>(store.tx(data -> data.dueOnlineOrders(clock.instant(), 25)))
        : new LinkedHashSet<>();
    long cursor = maintenanceCursor.get();
    List<Long> open = new ArrayList<>(store.tx(data -> data.openPaymentOrders(25, cursor)));
    if (open.size() < 25)
      open.addAll(store.tx(data -> data.openPaymentOrders(25 - open.size(), 0)));
    if (!open.isEmpty()) maintenanceCursor.set(open.getLast());
    due.addAll(open);
    for (long id : due) {
      try {
        if (recoverTerminalCancellation(id) || !providerAvailable) continue;
        if (isDue(id)) expire(id);
        else {
          Attempt attempt = store.tx(data -> { var p = data.paymentForOrder(id, false); return p == null ? null : snapshot(p, false); });
          if (attempt != null) applySnapshot(attempt, gateway.query(attempt.code()));
        }
      } catch (Problem unavailable) {
        // Keep the reservation on ambiguous provider failure; a later pass safely retries.
        org.slf4j.LoggerFactory.getLogger(PaymentService.class).warn("Payment check postponed for order {} (HTTP {})", id, unavailable.status);
      }
    }
  }

  private boolean recoverTerminalCancellation(long id) {
    return store.tx(data -> {
      Order observed = data.order(id, false);
      if (observed == null || !"PAYOS".equals(observed.paymentMethod) || !"PENDING".equals(observed.status)
          || !Set.of("CANCELLED", "EXPIRED").contains(observed.paymentStatus)) return false;
      Order order = OrderService.lockedOrder(data, id);
      if (order == null || !"PENDING".equals(order.status)
          || !Set.of("CANCELLED", "EXPIRED").contains(order.paymentStatus)) return false;
      PaymentAttempt payment = data.paymentForOrder(id, true);
      if (payment != null && !Set.of("CANCELLED", "EXPIRED").contains(payment.status)) return false;
      // The earlier committed provider confirmation is authoritative; no new remote call is needed.
      orders.cancel(order.userId, id);
      return true;
    });
  }

  private void expire(long orderId) { cancelOnline(orderId, true); }
  private boolean isDue(long id) {
    return store.tx(data -> { Order o = data.order(id, false); return o != null && "PAYOS".equals(o.paymentMethod)
        && "PENDING".equals(o.status) && Set.of("UNPAID", "PENDING").contains(o.paymentStatus)
        && o.paymentExpiresAt != null && !o.paymentExpiresAt.isAfter(clock.instant()); });
  }

  private void cancelOnline(long id, boolean expired) {
    confirmRemoteCancellation(id);
    store.tx(data -> {
      Order order = OrderService.lockedOrder(data, id);
      if (order == null) throw Problem.missing();
      if ("CANCELLED".equals(order.status)) return null;
      if (!"PENDING".equals(order.status) || Set.of("PAID", "REVIEW_REQUIRED").contains(order.paymentStatus))
        throw new Problem(409, "Đơn đã nhận tiền hoặc cần kiểm tra. Chưa thể tự động hủy.");
      order.paymentStatus = expired ? "EXPIRED" : "CANCELLED"; data.saveOrder(order);
      orders.cancel(order.userId, id);
      return null;
    });
  }

  private void confirmRemoteCancellation(long id) {
    requireEnabled();
    Attempt attempt = store.tx(data -> {
      Order order = OrderService.lockedOrder(data, id);
      if (order == null) throw Problem.missing();
      if ("CANCELLED".equals(order.status)) return null;
      if (Set.of("PAID", "REVIEW_REQUIRED").contains(order.paymentStatus))
        throw new Problem(409, "Đơn đã nhận tiền hoặc cần kiểm tra. Vui lòng liên hệ cửa hàng để xử lý hoàn tiền.");
      PaymentAttempt payment = data.paymentForOrder(id, true);
      if (payment == null) {
        // Blocks a concurrent start before the outer transaction performs fulfillment cancellation.
        order.paymentStatus = "CANCELLED"; data.saveOrder(order);
        return null;
      }
      return snapshot(payment, false);
    });
    if (attempt == null) return;
    PaymentGateway.Snapshot state = gateway.query(attempt.code());
    if (Set.of("PENDING", "PROCESSING").contains(state.status())) state = gateway.cancel(attempt.code(), "Khach hang huy don");
    if ("NOT_FOUND".equals(state.status()))
      throw new Problem(503, "Chưa xác nhận được yêu cầu thanh toán. Vui lòng thử kiểm tra lại sau.");
    applySnapshot(attempt, state, false);
    if (!Set.of("CANCELLED", "EXPIRED").contains(state.status()) || state.amountPaid() != 0)
      throw new Problem(409, "Đơn đã nhận tiền hoặc đang được kiểm tra. Chưa thể tự động hủy.");
  }

  private void applySnapshot(Attempt expected, PaymentGateway.Snapshot state) {
    applySnapshot(expected, state, true);
  }

  private void applySnapshot(Attempt expected, PaymentGateway.Snapshot state, boolean cancelFulfillment) {
    if ("NOT_FOUND".equals(state.status())) return;
    if (state.orderCode() != expected.code() || state.amount() != expected.amount()
        || state.paymentLinkId() == null || state.paymentLinkId().isBlank()
        || (!expected.linkId().isBlank() && !expected.linkId().equals(state.paymentLinkId())))
      throw new Problem(502, "Kết quả thanh toán không khớp. Vui lòng liên hệ cửa hàng.");
    if ("PAID".equals(state.status()) || state.amountPaid() > 0) {
      settle(expected.orderId(), expected.code(), state.paymentLinkId(), state.amountPaid(), "query:" + state.paymentLinkId() + ":" + state.amountPaid(), true);
      return;
    }
    store.tx(data -> {
      Order order = OrderService.lockedOrder(data, expected.orderId());
      PaymentAttempt payment = data.paymentForOrder(expected.orderId(), true);
      if (order == null || payment == null) throw Problem.missing();
      if (Set.of("PAID", "REVIEW_REQUIRED").contains(order.paymentStatus)) return null;
      payment.paymentLinkId = state.paymentLinkId();
      if (state.checkoutUrl() != null && !state.checkoutUrl().isBlank()) payment.checkoutUrl = state.checkoutUrl();
      boolean known = Set.of("PENDING", "PROCESSING", "CANCELLED", "EXPIRED").contains(state.status());
      payment.status = known ? state.status() : "REVIEW_REQUIRED";
      payment.updatedAt = clock.instant(); data.savePayment(payment);
      if (!known) {
        order.paymentStatus = "REVIEW_REQUIRED"; order.updatedAt = clock.instant(); data.saveOrder(order);
        OrderStatusHistory history = new OrderStatusHistory(); history.orderId = order.id; history.status = order.status;
        history.note = "Thông tin thanh toán cần cửa hàng kiểm tra";
        history.createdAt = clock.instant(); data.saveOrderStatus(history);
      }
      if (Set.of("CANCELLED", "EXPIRED").contains(state.status())) {
        order.paymentStatus = state.status(); data.saveOrder(order);
        if (cancelFulfillment && "PENDING".equals(order.status)) orders.cancel(order.userId, order.id);
      }
      return null;
    });
  }

  private void settle(long orderId, long code, String linkId, long received, String reference, boolean query) {
    store.tx(data -> {
      Order order = OrderService.lockedOrder(data, orderId);
      PaymentAttempt payment = data.paymentForOrder(orderId, true);
      if (order == null || payment == null || payment.orderCode != code) throw Problem.missing();
      if (!payment.paymentLinkId.isBlank() && !payment.paymentLinkId.equals(linkId)) throw Problem.invalid("Mã thanh toán không khớp.");
      PaymentEvent previous = data.paymentEvent(reference);
      if (previous != null) {
        if (previous.attemptId != payment.id || previous.amount != received) throw Problem.invalid("Giao dịch thanh toán không khớp.");
        return null;
      }
      var reservations = data.reservationsForOrder(orderId);
      var itemCopies = data.orderItems(orderId).stream().map(item -> item.copyId).collect(java.util.stream.Collectors.toSet());
      var reservedCopies = reservations.stream().map(r -> r.copyId).collect(java.util.stream.Collectors.toSet());
      boolean inventoryHeld = !reservations.isEmpty() && reservations.size() == data.orderItems(orderId).size()
          && itemCopies.equals(reservedCopies)
          && reservations.stream().allMatch(r -> "ACTIVE".equals(r.status)
          && data.copy(r.copyId, false) != null && "RESERVED".equals(data.copy(r.copyId, false).status));
      boolean safe = received == payment.amount && "PENDING".equals(order.status) && inventoryHeld;
      boolean additionalTransfer = !query && data.paymentEvents(payment.id).stream().anyMatch(e -> "WEBHOOK".equals(e.source));
      // A later duplicate query cannot downgrade a receipt already applied successfully.
      String outcome = "REVIEW_REQUIRED".equals(order.paymentStatus) || additionalTransfer ? "REVIEW_REQUIRED"
          : "PAID".equals(order.paymentStatus) && received == payment.amount ? "PAID" : safe ? "PAID" : "REVIEW_REQUIRED";
      PaymentEvent event = new PaymentEvent(); event.attemptId = payment.id; event.reference = reference;
      event.amount = received; event.outcome = outcome; event.createdAt = clock.instant();
      event.source = query ? "QUERY" : "WEBHOOK"; data.savePaymentEvent(event);
      payment.paymentLinkId = linkId; payment.status = outcome; payment.updatedAt = clock.instant(); data.savePayment(payment);
      if (!outcome.equals(order.paymentStatus)) {
        order.paymentStatus = outcome; order.updatedAt = clock.instant(); data.saveOrder(order);
        OrderStatusHistory history = new OrderStatusHistory(); history.orderId = order.id; history.status = order.status;
        history.note = "PAID".equals(outcome) ? "Đã xác nhận thanh toán chuyển khoản" : "Đã nhận thông tin chuyển khoản cần cửa hàng kiểm tra";
        history.createdAt = clock.instant(); data.saveOrderStatus(history);
      }
      if ("PAID".equals(outcome)) {
        for (var reservation : data.reservationsForOrder(orderId)) {
          reservation.expiresAt = null; data.saveReservation(reservation);
        }
      }
      return null;
    });
  }

  private Order owned(Store.Data data, long userId, long orderId, boolean lock) {
    var user = data.user(userId);
    if (user == null || !user.active || user.blocked) throw Problem.missing();
    Order order = lock ? OrderService.lockedOrder(data, orderId) : data.order(orderId, false);
    if (order == null || order.userId != userId) throw Problem.missing();
    if (!"PAYOS".equals(order.paymentMethod)) throw Problem.invalid("Đơn này sử dụng thanh toán khi nhận hàng.");
    return order;
  }

  private static Attempt snapshot(PaymentAttempt p, boolean fresh) {
    return new Attempt(p.orderId, p.orderCode, p.amount, p.paymentLinkId, p.checkoutUrl, p.status, p.expiresAt, fresh);
  }
  private void requireEnabled() { if (!enabled()) throw new Problem(503, "Thanh toán chuyển khoản tạm chưa khả dụng. Vui lòng chọn thanh toán khi nhận hàng."); }
  public static String statusLabel(String status) {
    return switch (Objects.toString(status, "")) {
      case "UNPAID" -> "Chưa thanh toán"; case "PENDING" -> "Chờ thanh toán"; case "PAID" -> "Đã thanh toán";
      case "CANCELLED" -> "Đã hủy thanh toán"; case "EXPIRED" -> "Đã hết thời gian thanh toán";
      case "REVIEW_REQUIRED" -> "Cửa hàng đang kiểm tra khoản chuyển"; default -> "Đang kiểm tra thanh toán";
    };
  }
}
