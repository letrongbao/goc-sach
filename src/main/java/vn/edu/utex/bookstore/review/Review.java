package vn.edu.utex.bookstore.review;

import java.time.Instant;

public class Review {
  public String mediaUrl="",mediaType="";
  public Long id;
  public long userId, productId, orderId;
  public int rating;
  public String body, status;
  public Instant createdAt, updatedAt;
}
