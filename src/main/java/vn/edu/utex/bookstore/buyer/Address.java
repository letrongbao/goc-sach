package vn.edu.utex.bookstore.buyer;

import java.time.Instant;

public class Address {
  public Long id;
  public long userId;
  public String label, recipientName, phone, line1, ward, district, city;
  public boolean defaultAddress;
  public Instant createdAt, updatedAt;
}
