package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.buyer.BuyerService;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.order.Order;
import vn.edu.utex.bookstore.order.OrderService;
import vn.edu.utex.bookstore.payment.*;

/** Business tests use a fake provider; they do not establish PostgreSQL locking or real bank settlement. */
class PaymentServiceTest {
  @Test
  void successDuplicateWebhookAndQueryPreserveOneSettlementHistory() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    var attempt = f.start(order);
    assertEquals(attempt.checkoutUrl, f.payments.start(f.reader.id(), order.id));
    assertEquals(1, f.gateway.createdCodes.size());
    f.receive(attempt, attempt.amount, "bank-01");
    f.payments.webhook("same signed receipt");
    assertEquals("PAID", order.paymentStatus);
    assertEquals(1, f.store.paymentEvents.size());
    assertTrue(f.store.reservationsForOrder(order.id).stream().allMatch(row -> row.expiresAt == null));
    assertEquals(2, f.store.orderStatuses(order.id).size());
    f.gateway.put(attempt, "PAID", attempt.amount);
    f.payments.refresh(f.reader.id(), order.id);
    assertEquals("PAID", order.paymentStatus);
    assertEquals(2, f.store.orderStatuses(order.id).size());
    assertFalse(f.payments.view(f.reader.id(), order.id).canStart());
  }

  @Test
  void forgedReceiptCannotChangePaymentOrInventory() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    f.start(order);
    f.gateway.verifyFailure = Problem.invalid("Chữ ký không hợp lệ.");
    assertProblem(400, () -> f.payments.webhook("forged"));
    assertEquals("PENDING", order.paymentStatus);
    assertTrue(f.store.paymentEvents.isEmpty());
    f.assertHeld(order);
  }

  @Test
  void queryBeforeFirstWebhookIsOnePaymentAndLaterDistinctTransferNeedsReview() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    var attempt = f.start(order);
    f.gateway.put(attempt, "PAID", attempt.amount);
    f.payments.refresh(f.reader.id(), order.id);
    assertEquals("PAID", order.paymentStatus);
    f.receive(attempt, attempt.amount, "bank-query-first");
    assertEquals("PAID", order.paymentStatus);
    assertEquals(2, f.store.orderStatuses(order.id).size());
    f.receive(attempt, attempt.amount, "bank-extra-transfer");
    assertEquals("REVIEW_REQUIRED", order.paymentStatus);
  }

  @Test
  void mismatchedProviderSnapshotCannotReleaseReservedStock() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    var attempt = f.start(order);
    f.gateway.states.put(attempt.orderCode, new PaymentGateway.Snapshot(attempt.orderCode,
        attempt.paymentLinkId, attempt.amount + 1, 0, "CANCELLED", attempt.checkoutUrl));
    assertProblem(502, () -> f.payments.cancel(f.reader.id(), order.id));
    assertEquals("PENDING", order.status);
    f.assertHeld(order);
    f.gateway.states.put(attempt.orderCode, new PaymentGateway.Snapshot(attempt.orderCode,
        "wrong-link", attempt.amount, 0, "EXPIRED", attempt.checkoutUrl));
    assertProblem(502, () -> f.payments.refresh(f.reader.id(), order.id));
    assertEquals("PENDING", order.status);
    f.assertHeld(order);
  }

  @Test
  void unsupportedProviderStatesRequireReviewAndRetainReservations() {
    for (String status : List.of("FAILED", "UNDERPAID", "NEW_PROVIDER_STATE")) {
      var f = new Fixture();
      var order = f.checkout("PAYOS");
      var attempt = f.start(order);
      f.gateway.put(attempt, status, 0);
      f.payments.refresh(f.reader.id(), order.id);
      assertEquals("REVIEW_REQUIRED", order.paymentStatus, status);
      assertEquals("REVIEW_REQUIRED", attempt.status, status);
      assertEquals("PENDING", order.status, status);
      assertEquals(2, f.store.orderStatuses(order.id).size(), status);
      f.assertHeld(order);
      assertProblem(409, () -> f.payments.start(f.reader.id(), order.id));
      assertProblem(409, () -> f.payments.cancel(f.reader.id(), order.id));
      assertProblem(409, () -> f.orders.advance(f.reader, order.id, "CONFIRMED"));
      f.gateway.put(attempt, "CANCELLED", 0);
      f.payments.refresh(f.reader.id(), order.id);
      assertEquals("REVIEW_REQUIRED", order.paymentStatus, status);
      assertEquals(2, f.store.orderStatuses(order.id).size(), status);
      f.assertHeld(order);
    }
  }

  @Test
  void adminReviewRequiresCurrentAdminRoleAndAuthenticationVersion() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    var attempt = f.start(order);
    f.gateway.put(attempt, "FAILED", 0);
    f.payments.refresh(f.reader.id(), order.id);
    assertProblem(403, () -> f.payments.reviews(f.reader));
    var admin = f.store.findLogin("admin");
    Identity identity = Identity.of(admin);
    assertEquals(order.id.longValue(), f.payments.reviews(identity).getFirst().orderId());
    admin.authVersion++;
    assertProblem(403, () -> f.payments.reviews(identity));
    admin.blocked = true;
    assertProblem(403, () -> f.payments.reviews(Identity.of(admin)));
  }

  @Test
  void onlinePaymentCancelEndpointRejectsCodBeforeMutation() {
    var f = new Fixture();
    var order = f.checkout("COD");
    var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("unit-token")
        .header("alg", "RS256").claim("user_id", f.reader.id()).build();
    var auth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt);
    var controller = new PaymentApiController(f.payments);
    assertProblem(400, () -> controller.cancel(order.id, auth));
    assertEquals("PENDING", order.status);
    assertEquals("UNPAID", order.paymentStatus);
    f.assertHeld(order);
    assertEquals(0, f.gateway.queryCalls);
    assertEquals(0, f.gateway.cancelCalls);
  }

  @Test
  void wrongLinkOrCurrencyIsRejectedAndWrongAmountRequiresReview() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    var attempt = f.start(order);
    f.gateway.receipt = new PaymentGateway.Receipt(attempt.orderCode, "another-link", attempt.amount,
        "bank-wrong-link", "VND", true);
    assertProblem(400, () -> f.payments.webhook("signed wrong link"));
    f.gateway.receipt = new PaymentGateway.Receipt(attempt.orderCode, attempt.paymentLinkId, attempt.amount,
        "bank-wrong-currency", "USD", true);
    assertProblem(400, () -> f.payments.webhook("signed wrong currency"));
    assertTrue(f.store.paymentEvents.isEmpty());
    f.receive(attempt, attempt.amount - 1, "bank-partial");
    assertEquals("REVIEW_REQUIRED", order.paymentStatus);
    f.assertHeld(order);
    f.receive(attempt, attempt.amount, "bank-later");
    assertEquals("REVIEW_REQUIRED", order.paymentStatus);
  }

  @Test
  void wrongUserCannotViewStartRefreshOrCancelPayment() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    long outsider = f.store.findLogin("admin").id;
    assertProblem(404, () -> f.payments.view(outsider, order.id));
    assertProblem(404, () -> f.payments.start(outsider, order.id));
    assertProblem(404, () -> f.payments.refresh(outsider, order.id));
    assertProblem(404, () -> f.payments.cancel(outsider, order.id));
    assertTrue(f.gateway.createdCodes.isEmpty());
    assertEquals(0, f.gateway.queryCalls);
    f.assertHeld(order);
  }

  @Test
  void terminalCancellationReleasesStockOnceButProcessingDoesNot() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    f.start(order);
    f.gateway.cancelStatus = "PROCESSING";
    assertProblem(409, () -> f.payments.cancel(f.reader.id(), order.id));
    assertEquals("PENDING", order.status);
    f.assertHeld(order);
    f.gateway.cancelStatus = "CANCELLED";
    f.payments.cancel(f.reader.id(), order.id);
    f.payments.cancel(f.reader.id(), order.id);
    assertEquals("CANCELLED", order.status);
    assertEquals("CANCELLED", order.paymentStatus);
    assertEquals("AVAILABLE", f.store.copies.get(f.copyId).status);
    assertTrue(f.store.reservationsForOrder(order.id).stream().allMatch(row -> "RELEASED".equals(row.status)));
    assertEquals(2, f.store.orderStatuses(order.id).size());
  }

  @Test
  void unstartedOnlineOrderCanBeCancelledWithoutCallingProvider() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    f.payments.cancel(f.reader.id(), order.id);
    assertEquals("CANCELLED", order.status);
    assertEquals("AVAILABLE", f.store.copies.get(f.copyId).status);
    assertEquals(0, f.gateway.queryCalls);
    assertEquals(0, f.gateway.cancelCalls);
  }

  @Test
  void sellerCancellationKeepsSellerReasonAfterProviderConfirmation() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    f.start(order);
    String reason = "Bản sách bị hư hỏng khi kiểm tra trước khi giao";
    f.payments.sellerCancel(f.reader, order.id, reason);
    assertEquals("CANCELLED", order.status);
    assertEquals("AVAILABLE", f.store.copies.get(f.copyId).status);
    assertTrue(f.store.orderStatuses(order.id).getLast().note.contains(reason));
    assertTrue(f.store.orderStatuses(order.id).getLast().note.startsWith("Cửa hàng hủy:"));
  }

  @Test
  void expiredLocalDeadlineRemainsExpiredWhenProviderReportsCancelled() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    f.start(order);
    f.clock.advance(901);
    // A cancellation requested because the local deadline passed is reported CANCELLED by the provider.
    f.gateway.cancelStatus = "CANCELLED";
    f.payments.maintain();
    assertEquals("CANCELLED", order.status);
    assertEquals("EXPIRED", order.paymentStatus);
    assertEquals("AVAILABLE", f.store.copies.get(f.copyId).status);
    assertTrue(f.store.reservationsForOrder(order.id).stream().allMatch(row -> "RELEASED".equals(row.status)));
  }

  @Test
  void maintenanceRecoversCommittedProviderCancellationWithoutAnotherRemoteCall() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    var attempt = f.start(order);
    // Persisted state after provider cancellation committed but the fulfillment transaction failed.
    attempt.status = "CANCELLED";
    order.paymentStatus = "CANCELLED";
    f.gateway.available = false;
    f.payments.maintain();
    assertEquals("CANCELLED", order.status);
    assertEquals("AVAILABLE", f.store.copies.get(f.copyId).status);
    assertEquals(0, f.gateway.queryCalls);
    assertEquals(0, f.gateway.cancelCalls);
    f.payments.maintain();
    assertEquals(2, f.store.orderStatuses(order.id).size());
  }

  @Test
  void maintenanceRecoversTerminalNoAttemptButDoesNotTrustAmbiguousAttempt() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    order.paymentStatus = "EXPIRED";
    f.gateway.available = false;
    f.payments.maintain();
    assertEquals("CANCELLED", order.status);
    assertEquals("EXPIRED", order.paymentStatus);
    assertEquals("AVAILABLE", f.store.copies.get(f.copyId).status);
    assertEquals(0, f.gateway.queryCalls);

    var ambiguous = new Fixture();
    var pending = ambiguous.checkout("PAYOS");
    ambiguous.start(pending);
    pending.paymentStatus = "CANCELLED";
    ambiguous.gateway.available = false;
    ambiguous.payments.maintain();
    assertEquals("PENDING", pending.status);
    ambiguous.assertHeld(pending);
  }

  @Test
  void oldestUnavailableAttemptsCannotStarveOrdersBeyondFirstMaintenanceBatch() {
    var f = new Fixture();
    long shopId = f.store.copies.get(f.copyId).shopId;
    var inventory = new vn.edu.utex.bookstore.inventory.InventoryService(f.store, f.clock);
    var createdOrders = new ArrayList<Order>();
    for (int index = 0; index < 31; index++) {
      long copy = inventory.receive(f.reader, shopId, 1, "GOOD", "", "99000", "Nhập sách kiểm thử bảo trì");
      inventory.update(f.reader, shopId, copy, 0, "GOOD", "", "99000", "AVAILABLE", "Mở bán sách kiểm thử bảo trì");
      f.buyer.addToCart(f.reader.id(), copy);
      var quote = f.orders.quote(f.reader.id());
      var order = f.payments.checkout(f.reader.id(), f.addressId,
          String.format(Locale.ROOT, "checkout-fair-batch-%04d", index), quote.fingerprint(), "PAYOS").orders().getFirst();
      createdOrders.add(order);
      var attempt = f.start(order);
      if (index < 25) f.gateway.failingQueryCodes.add(attempt.orderCode);
      else f.gateway.put(attempt, "PAID", attempt.amount);
    }
    f.payments.maintain();
    f.payments.maintain();
    assertEquals(new HashSet<>(f.gateway.createdCodes), new HashSet<>(f.gateway.queriedCodes));
    for (int index = 0; index < 31; index++)
      assertEquals(index < 25 ? "PENDING" : "PAID", createdOrders.get(index).paymentStatus);
    assertEquals("PENDING", createdOrders.getFirst().status);
    f.assertHeld(createdOrders.getFirst());
  }

  @Test
  void expiryReleasesOnlyAfterProviderTerminalConfirmationAndLeavesCodHeld() {
    var f = new Fixture();
    var online = f.checkout("PAYOS");
    f.start(online);
    long otherCopy = f.store.copies.values().stream().filter(copy -> copy.id != f.copyId).findFirst().orElseThrow().id;
    f.buyer.addToCart(f.reader.id(), otherCopy);
    var quote = f.orders.quote(f.reader.id());
    var cod = f.payments.checkout(f.reader.id(), f.addressId, "checkout-cod-no-expiry-01", quote.fingerprint(), "COD").orders().getFirst();
    f.clock.advance(901);
    f.gateway.queryFailure = new Problem(503, "Gateway unavailable");
    f.payments.maintain();
    f.assertHeld(online);
    assertEquals("PENDING", online.status);
    f.gateway.queryFailure = null;
    f.gateway.cancelStatus = "EXPIRED";
    f.payments.maintain();
    assertEquals("CANCELLED", online.status);
    assertEquals("EXPIRED", online.paymentStatus);
    assertEquals("AVAILABLE", f.store.copies.get(f.copyId).status);
    assertEquals("PENDING", cod.status);
    assertEquals("RESERVED", f.store.copies.get(otherCopy).status);
    assertNull(cod.paymentExpiresAt);
    assertTrue(f.store.reservationsForOrder(cod.id).stream().allMatch(row -> row.expiresAt == null));
  }

  @Test
  void cancelAndQueryOutagesRetainInventoryInsteadOfClaimingCancellation() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    f.start(order);
    f.gateway.queryFailure = new Problem(503, "Query unavailable");
    assertProblem(503, () -> f.payments.cancel(f.reader.id(), order.id));
    f.assertHeld(order);
    f.gateway.queryFailure = null;
    f.gateway.cancelFailure = new Problem(503, "Cancellation unavailable");
    assertProblem(503, () -> f.payments.cancel(f.reader.id(), order.id));
    f.assertHeld(order);
    assertEquals("PENDING", order.status);
    assertEquals("PENDING", order.paymentStatus);
  }

  @Test
  void timeoutAfterRemoteCreationRecoversSameCodeWithoutASecondLink() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    f.gateway.failCreateAfterSaving = true;
    assertProblem(503, () -> f.payments.start(f.reader.id(), order.id));
    var attempt = f.store.paymentForOrder(order.id, false);
    long originalCode = attempt.orderCode;
    assertProblem(409, () -> f.payments.start(f.reader.id(), order.id));
    f.clock.advance(31);
    String recovered = f.payments.start(f.reader.id(), order.id);
    assertEquals(originalCode, f.store.paymentForOrder(order.id, false).orderCode);
    assertEquals(1, f.gateway.createdCodes.size());
    assertEquals(FakeGateway.url(originalCode), recovered);
    f.assertHeld(order);
  }

  @Test
  void timeoutBeforeRemoteCreationRetriesOnlyThePersistedCode() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    f.gateway.failCreateBeforeSaving = true;
    assertProblem(503, () -> f.payments.start(f.reader.id(), order.id));
    long originalCode = f.store.paymentForOrder(order.id, false).orderCode;
    f.clock.advance(31);
    f.gateway.failCreateBeforeSaving = false;
    assertEquals(FakeGateway.url(originalCode), f.payments.start(f.reader.id(), order.id));
    assertEquals(List.of(originalCode, originalCode), f.gateway.createdCodes);
    assertEquals(1, f.store.payments.size());
  }

  @Test
  void differentOrdersKeepDistinctProviderCodesAndIndependentPaymentState() {
    var f = new Fixture();
    var first = f.checkout("PAYOS");
    var firstAttempt = f.start(first);
    long secondCopy = f.store.copies.values().stream().filter(copy -> copy.id != f.copyId).findFirst().orElseThrow().id;
    f.buyer.addToCart(f.reader.id(), secondCopy);
    var quote = f.orders.quote(f.reader.id());
    var second = f.payments.checkout(f.reader.id(), f.addressId, "checkout-independent-02", quote.fingerprint(), "PAYOS").orders().getFirst();
    var secondAttempt = f.start(second);
    assertNotEquals(firstAttempt.orderCode, secondAttempt.orderCode);
    assertEquals(2, f.store.payments.size());
    assertEquals(2, new HashSet<>(f.gateway.createdCodes).size());
    f.receive(firstAttempt, firstAttempt.amount, "bank-only-first-order");
    assertEquals("PAID", first.paymentStatus);
    assertEquals("PENDING", second.paymentStatus);
    assertEquals("PENDING", secondAttempt.status);
    assertTrue(f.store.reservationsForOrder(first.id).stream().allMatch(row -> row.expiresAt == null));
    assertTrue(f.store.reservationsForOrder(second.id).stream().allMatch(row -> row.expiresAt != null));
  }

  @Test
  void createResponseArrivingAfterWebhookCannotDowngradePaidOrder() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    f.gateway.afterCreate = () -> {
      var attempt = f.store.paymentForOrder(order.id, false);
      f.receive(attempt, attempt.amount, "bank-before-response");
    };
    assertProblem(409, () -> f.payments.start(f.reader.id(), order.id));
    assertEquals("PAID", order.paymentStatus);
    assertEquals("PAID", f.store.paymentForOrder(order.id, false).status);
    assertEquals(1, f.store.paymentEvents.size());
    assertFalse(f.payments.view(f.reader.id(), order.id).canStart());
    f.assertHeld(order);
  }

  @Test
  void sellerCannotProcessUnpaidOnlineOrderAndPaidOrderCannotBeCancelled() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    assertProblem(409, () -> f.orders.advance(f.reader, order.id, "CONFIRMED"));
    var attempt = f.start(order);
    f.receive(attempt, attempt.amount, "bank-paid-01");
    assertProblem(409, () -> f.payments.cancel(f.reader.id(), order.id));
    assertProblem(409, () -> f.payments.sellerCancel(f.reader, order.id, "Cần hủy để kiểm tra sách"));
    assertProblem(409, () -> f.orders.cancel(f.reader.id(), order.id));
    f.orders.advance(f.reader, order.id, "CONFIRMED");
    f.orders.advance(f.reader, order.id, "SHIPPING");
    f.orders.advance(f.reader, order.id, "COMPLETED");
    assertEquals("SOLD", f.store.copies.get(f.copyId).status);
    assertEquals("PAID", order.paymentStatus);
  }

  @Test
  void lateReceiptForCancelledOrderRequiresReviewAndCannotTouchNewReservation() {
    var f = new Fixture();
    var old = f.checkout("PAYOS");
    var attempt = f.start(old);
    f.payments.cancel(f.reader.id(), old.id);
    f.buyer.addToCart(f.reader.id(), f.copyId);
    var quote = f.orders.quote(f.reader.id());
    var replacement = f.payments.checkout(f.reader.id(), f.addressId, "checkout-replacement-01", quote.fingerprint(), "COD").orders().getFirst();
    f.receive(attempt, attempt.amount, "bank-late-01");
    assertEquals("CANCELLED", old.status);
    assertEquals("REVIEW_REQUIRED", old.paymentStatus);
    assertEquals("PENDING", replacement.status);
    assertEquals("UNPAID", replacement.paymentStatus);
    f.assertHeld(replacement);
    assertTrue(f.store.reservationsForOrder(old.id).stream().allMatch(row -> "RELEASED".equals(row.status)));
  }

  @Test
  void missingReservationsCannotProduceAReadyToShipPaidOrder() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    var attempt = f.start(order);
    f.store.reservationRows.values().removeIf(row -> row.orderId == order.id);
    f.receive(attempt, attempt.amount, "bank-no-inventory");
    assertEquals("REVIEW_REQUIRED", order.paymentStatus);
    assertProblem(409, () -> f.orders.advance(f.reader, order.id, "CONFIRMED"));
  }

  @Test
  void distinctSecondBankTransferIsNotSilentlyTreatedAsDuplicate() {
    var f = new Fixture();
    var order = f.checkout("PAYOS");
    var attempt = f.start(order);
    f.receive(attempt, attempt.amount, "bank-first");
    f.receive(attempt, attempt.amount, "bank-second");
    assertEquals("REVIEW_REQUIRED", order.paymentStatus);
    assertEquals(2, f.store.paymentEvents.size());
    f.assertHeld(order);
  }

  @Test
  void codCancellationWorksAndFractionalOnlineTotalIsRejectedBeforeOrdering() {
    var f = new Fixture();
    var cod = f.checkout("COD");
    f.payments.cancel(f.reader.id(), cod.id);
    assertEquals("CANCELLED", cod.status);
    assertTrue(f.store.payments.isEmpty());
    assertTrue(f.gateway.createdCodes.isEmpty());
    f.store.copies.get(f.copyId).price = new BigDecimal("75000.50");
    f.buyer.addToCart(f.reader.id(), f.copyId);
    var quote = f.orders.quote(f.reader.id());
    assertProblem(400, () -> f.payments.checkout(f.reader.id(), f.addressId, "checkout-fractional-01", quote.fingerprint(), "PAYOS"));
    assertEquals(1, f.store.orders.size());
    assertEquals("AVAILABLE", f.store.copies.get(f.copyId).status);
  }

  @Test
  void unavailableOnlineGatewayStillAllowsCodWithoutRemoteCalls() {
    var f = new Fixture();
    f.gateway.available = false;
    f.buyer.addToCart(f.reader.id(), f.copyId);
    var quote = f.orders.quote(f.reader.id());
    assertProblem(503, () -> f.payments.checkout(f.reader.id(), f.addressId, "checkout-disabled-01", quote.fingerprint(), "PAYOS"));
    assertTrue(f.store.orders.isEmpty());
    var cod = f.checkout("COD");
    f.payments.cancel(f.reader.id(), cod.id);
    assertEquals("CANCELLED", cod.status);
    assertTrue(f.gateway.createdCodes.isEmpty());
    assertEquals(0, f.gateway.queryCalls);
  }

  private static void assertProblem(int status, org.junit.jupiter.api.function.Executable action) {
    assertEquals(status, assertThrows(Problem.class, action).status);
  }

  private static final class Fixture {
    final MemoryStore store = DemoFixtures.store();
    final MutableClock clock = new MutableClock();
    final Identity reader;
    final BuyerService buyer;
    final OrderService orders;
    final FakeGateway gateway = new FakeGateway();
    final PaymentService payments;
    final long copyId, addressId;
    Fixture() {
      DemoFixtures.catalogStock(store);
      reader = Identity.of(store.findLogin("reader"));
      buyer = new BuyerService(store, clock);
      orders = new OrderService(store, clock);
      payments = new PaymentService(store, orders, gateway, clock);
      copyId = store.copies.values().stream().mapToLong(copy -> copy.id).min().orElseThrow();
      addressId = buyer.saveAddress(reader.id(), null, "Nhà", "Bạn đọc", "0900000000",
          "1 Đường Sách", "", "", "TP HCM", true).id;
    }
    Order checkout(String method) {
      buyer.addToCart(reader.id(), copyId);
      var quote = orders.quote(reader.id());
      return payments.checkout(reader.id(), addressId, "checkout-payment-test-01", quote.fingerprint(), method).orders().getFirst();
    }
    PaymentAttempt start(Order order) {
      payments.start(reader.id(), order.id);
      return store.paymentForOrder(order.id, false);
    }
    void receive(PaymentAttempt attempt, long amount, String reference) {
      gateway.receipt = new PaymentGateway.Receipt(attempt.orderCode, FakeGateway.linkId(attempt.orderCode),
          amount, reference, "VND", true);
      payments.webhook("verified fake receipt");
    }
    void assertHeld(Order order) {
      var reservations = store.reservationsForOrder(order.id);
      assertFalse(reservations.isEmpty());
      assertTrue(reservations.stream().allMatch(row -> "ACTIVE".equals(row.status)
          && "RESERVED".equals(store.copies.get(row.copyId).status)));
    }
  }

  private static final class MutableClock extends Clock {
    Instant now = Instant.parse("2026-10-02T03:00:00Z");
    void advance(long seconds) { now = now.plusSeconds(seconds); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
  }

  private static final class FakeGateway implements PaymentGateway {
    final Map<Long, Snapshot> states = new HashMap<>();
    final List<Long> createdCodes = new ArrayList<>();
    final List<Long> queriedCodes = new ArrayList<>();
    final Set<Long> failingQueryCodes = new HashSet<>();
    Receipt receipt;
    Problem verifyFailure, queryFailure, cancelFailure;
    boolean failCreateAfterSaving, failCreateBeforeSaving, available = true;
    Runnable afterCreate;
    String cancelStatus = "CANCELLED";
    int queryCalls, cancelCalls;
    @Override public boolean enabled() { return available; }
    @Override public Link create(long code, long amount, String description, Instant expiresAt) {
      createdCodes.add(code);
      if (failCreateBeforeSaving) throw new Problem(503, "Create timed out");
      states.put(code, new Snapshot(code, linkId(code), amount, 0, "PENDING", url(code)));
      if (afterCreate != null) afterCreate.run();
      if (failCreateAfterSaving) throw new Problem(503, "Create timed out");
      return new Link(code, amount, linkId(code), url(code), "PENDING");
    }
    @Override public Snapshot query(long code) {
      queryCalls++;
      queriedCodes.add(code);
      if (queryFailure != null) throw queryFailure;
      if (failingQueryCodes.contains(code)) throw new Problem(503, "Selected query unavailable");
      return states.getOrDefault(code, new Snapshot(code, "", 0, 0, "NOT_FOUND", ""));
    }
    @Override public Snapshot cancel(long code, String reason) {
      cancelCalls++;
      if (cancelFailure != null) throw cancelFailure;
      Snapshot old = states.get(code);
      var next = new Snapshot(code, old.paymentLinkId(), old.amount(), old.amountPaid(), cancelStatus, old.checkoutUrl());
      states.put(code, next);
      return next;
    }
    @Override public Receipt verify(String raw) {
      if (verifyFailure != null) throw verifyFailure;
      return Objects.requireNonNull(receipt);
    }
    void put(PaymentAttempt attempt, String status, long paid) {
      states.put(attempt.orderCode, new Snapshot(attempt.orderCode, linkId(attempt.orderCode), attempt.amount,
          paid, status, url(attempt.orderCode)));
    }
    static String linkId(long code) { return "link-" + code; }
    static String url(long code) { return "https://pay.payos.vn/web/" + linkId(code); }
  }
}
