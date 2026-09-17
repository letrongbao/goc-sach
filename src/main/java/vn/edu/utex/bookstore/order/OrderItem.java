package vn.edu.utex.bookstore.order;

import java.math.BigDecimal;

public class OrderItem {
  public Long id;
  public long orderId, copyId, productId;
  public String titleSnapshot, coverUrlSnapshot, conditionSnapshot, copyCodeSnapshot;
  public BigDecimal unitPrice;
}
