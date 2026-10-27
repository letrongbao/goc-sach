package vn.edu.utex.bookstore.operations;

public class Voucher {
  public Long id;
  public long shopId;
  public String code;
  public java.math.BigDecimal amount;
  public java.math.BigDecimal minimumSubtotal;
  public int maximumUses;
  public int used;
  public boolean active;
  public java.time.Instant expiresAt;
}
