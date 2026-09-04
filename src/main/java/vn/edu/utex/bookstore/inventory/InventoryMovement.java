package vn.edu.utex.bookstore.inventory;

import java.math.BigDecimal;
import java.time.Instant;

public class InventoryMovement {
  public Long id;
  public long copyId, actorId;
  public String action, oldStatus, newStatus, oldCondition, newCondition, reason;
  public BigDecimal oldPrice, newPrice;
  public Instant createdAt;
}
