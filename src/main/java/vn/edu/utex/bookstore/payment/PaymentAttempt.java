package vn.edu.utex.bookstore.payment;

import java.time.Instant;

/** One durable provider code per order; uncertain requests retry this same code. */
public class PaymentAttempt {
  public Long id;
  public long orderId, orderCode, amount;
  public String currency = "VND", status = "CREATING", paymentLinkId = "", checkoutUrl = "";
  public Instant createdAt, updatedAt, expiresAt;
}
