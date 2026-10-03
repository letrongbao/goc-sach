package vn.edu.utex.bookstore.payment;

import java.time.Instant;

public interface PaymentGateway extends AutoCloseable {
  boolean enabled();

  Link create(long orderCode, long amount, String description, Instant expiresAt);

  Snapshot query(long orderCode);

  Snapshot cancel(long orderCode, String reason);

  Receipt verify(String rawJson);

  record Link(long orderCode, long amount, String paymentLinkId, String checkoutUrl, String status) {}

  record Snapshot(long orderCode, String paymentLinkId, long amount, long amountPaid,
      String status, String checkoutUrl) {}

  record Receipt(long orderCode, String paymentLinkId, long amount, String reference,
      String currency, boolean success) {}

  @Override
  default void close() {}
}
