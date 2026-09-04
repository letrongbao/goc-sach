package vn.edu.utex.bookstore.shop;

import java.time.Instant;

public class Shop {
  public Long id;
  public long ownerId;
  public String name, address, status;
  public Instant createdAt;
  public int version;
}
