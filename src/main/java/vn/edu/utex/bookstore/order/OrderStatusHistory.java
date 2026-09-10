package vn.edu.utex.bookstore.order;

import java.time.Instant;

public class OrderStatusHistory {
  public Long id;
  public long orderId;
  public String status, note;
  public Instant createdAt;
}
