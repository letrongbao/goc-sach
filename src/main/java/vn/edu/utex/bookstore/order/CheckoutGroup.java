package vn.edu.utex.bookstore.order;

import java.math.BigDecimal;
import java.time.Instant;

public class CheckoutGroup {
  public Long id;
  public long userId;
  public String idempotencyKey, addressSnapshot, status;
  public BigDecimal shippingFee, total;
  public Instant createdAt;
}
