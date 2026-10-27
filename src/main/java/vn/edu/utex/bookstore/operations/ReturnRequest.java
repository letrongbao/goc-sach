package vn.edu.utex.bookstore.operations;

public class ReturnRequest {
  public Long id;
  public long orderId;
  public String status;
  public String reason;
  public String resolution;
  public String refundReference;
  public java.time.Instant createdAt;
  public java.time.Instant updatedAt;
}
