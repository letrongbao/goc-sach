package vn.edu.utex.bookstore.order;

import java.math.BigDecimal;
import java.time.Instant;

public class Order {
  public Long id;
  public long userId, shopId, checkoutGroupId;
  public String idempotencyKey, status, recipientName, phone, addressSnapshot;
  public BigDecimal subtotal, shippingFee, total;
  public Instant createdAt, updatedAt;
}
