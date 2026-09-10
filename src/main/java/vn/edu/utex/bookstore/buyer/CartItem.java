package vn.edu.utex.bookstore.buyer;

import java.math.BigDecimal;
import java.time.Instant;

/** A cart row points to one physical copy; quantity is intentionally always one. */
public class CartItem {
  public Long id;
  public long cartId, copyId;
  public BigDecimal unitPrice;
  public Instant addedAt;
}
