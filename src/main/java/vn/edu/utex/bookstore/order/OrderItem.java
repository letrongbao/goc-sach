package vn.edu.utex.bookstore.order;

import java.math.BigDecimal;

public class OrderItem {
  public Long id;
  public long orderId, copyId, productId;
  public BigDecimal unitPrice;
}
