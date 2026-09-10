package vn.edu.utex.bookstore.order;

import java.time.Instant;

public class InventoryReservation {
  public Long id;
  public long orderId, copyId;
  public String status;
  public Instant expiresAt, createdAt;
}
