package vn.edu.utex.bookstore.shop;

import java.time.Instant;

public class ShopMembership {
  public Long id;
  public long shopId, userId;
  public String role;
  public boolean active;
  public Instant updatedAt;
}
