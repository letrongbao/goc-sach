package vn.edu.utex.bookstore.order;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public final class OrderApiController {
  private final OrderService orders;
  private final vn.edu.utex.bookstore.payment.PaymentService payments;
  public OrderApiController(OrderService orders, vn.edu.utex.bookstore.payment.PaymentService payments) { this.orders = orders; this.payments = payments; }

  @GetMapping("/checkout/quote")
  public ResponseEntity<?> quote(JwtAuthenticationToken auth) {
    return ResponseEntity.ok(orders.quote(userId(auth)));
  }

  @PostMapping("/checkout")
  public ResponseEntity<?> checkout(JwtAuthenticationToken auth, @RequestBody CheckoutRequest request) {
    long userId = userId(auth);
    String key = request.idempotencyKey() == null ? "" : request.idempotencyKey().trim();
    payments.checkout(userId, request.addressId(), key, request.quoteFingerprint(), request.paymentMethod());
    return ResponseEntity.ok(orders.ordersByKey(userId, key));
  }

  @GetMapping("/orders")
  public ResponseEntity<?> list(JwtAuthenticationToken auth) { return ResponseEntity.ok(orders.orders(userId(auth))); }

  @GetMapping("/orders/{id}")
  public ResponseEntity<?> detail(JwtAuthenticationToken auth, @PathVariable long id) {
    return ResponseEntity.ok(orders.detail(userId(auth), id));
  }

  @PostMapping("/orders/{id}/cancel")
  public ResponseEntity<?> cancel(JwtAuthenticationToken auth, @PathVariable long id) {
    payments.cancel(userId(auth), id);
    return ResponseEntity.noContent().build();
  }

  public record CheckoutRequest(long addressId, String idempotencyKey, String quoteFingerprint, String paymentMethod) {}
  private static long userId(JwtAuthenticationToken auth) {
    Object value=auth.getToken().getClaim("user_id");
    if (!(value instanceof Number n)||n.longValue()<=0)
      throw new vn.edu.utex.bookstore.common.Problem(401, "Phiên đăng nhập không hợp lệ.");
    return n.longValue();
  }
}
