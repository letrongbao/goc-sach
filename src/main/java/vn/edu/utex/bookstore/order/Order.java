package vn.edu.utex.bookstore.order;

import java.math.BigDecimal;
import java.time.Instant;

public class Order {
  public Long id;
  public long userId, shopId, checkoutGroupId;
  public String idempotencyKey, status, shopNameSnapshot, recipientName, phone, addressSnapshot;
  public BigDecimal subtotal, shippingFee, total;
  public Instant createdAt, updatedAt;
  public String paymentMethod = "COD", paymentStatus = "UNPAID";
  public Instant paymentExpiresAt;
  public String carrierName = "Giao hàng tiêu chuẩn", voucherCode = "";
  public BigDecimal discount = BigDecimal.ZERO, commissionRate = new BigDecimal("0.05");
}
