package vn.edu.utex.bookstore.inventory;

import java.math.BigDecimal;
import java.time.Instant;

public class BookCopy {
  public Long id;
  public long shopId, productId;
  public String code, condition, defects, status;
  public BigDecimal price;
  public Instant createdAt, updatedAt;
  public int version;
}
