package vn.edu.utex.bookstore.payment;

import java.time.Instant;

/** Sanitized receipt only: never persist the bank account or full webhook body. */
public class PaymentEvent {
  public Long id;
  public long attemptId, amount;
  public String reference, outcome;
  public String source = "WEBHOOK";
  public Instant createdAt;
}
