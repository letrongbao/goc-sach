package vn.edu.utex.bookstore.buyer;

import java.time.Instant;

/** Private profile fields owned by one account. */
public class CustomerProfile {
  public Long userId;
  public String displayName = "";
  public String phone = "";
  public Instant updatedAt;
}
