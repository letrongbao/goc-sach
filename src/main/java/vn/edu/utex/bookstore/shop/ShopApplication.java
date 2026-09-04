package vn.edu.utex.bookstore.shop;

import java.time.Instant;

public class ShopApplication {
  public Long id, shopId, reviewedBy;
  public long applicantId;
  public String name, address, status, reviewNote;
  public Instant createdAt, reviewedAt;
}
