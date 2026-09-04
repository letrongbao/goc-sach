package vn.edu.utex.bookstore.shop;

import java.time.Instant;

public class ShopAuditEvent {
  public Long id, shopId, applicationId;
  public long actorId;
  public String action, note;
  public Instant createdAt;
}
