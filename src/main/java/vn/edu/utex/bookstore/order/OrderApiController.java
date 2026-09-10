package vn.edu.utex.bookstore.order;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public final class OrderApiController {
  private final OrderService orders;
  public OrderApiController(OrderService orders) { this.orders = orders; }

  @PostMapping("/checkout")
  public ResponseEntity<?> checkout(JwtAuthenticationToken auth, @RequestBody CheckoutRequest request) {
    var result = orders.checkout(userId(auth), request.addressId(), request.idempotencyKey());
    return ResponseEntity.ok(Map.of("orders", result.orders()));
  }

  @GetMapping("/orders")
  public ResponseEntity<?> list(JwtAuthenticationToken auth) { return ResponseEntity.ok(orders.orders(userId(auth))); }

  @PostMapping("/orders/{id}/cancel")
  public ResponseEntity<?> cancel(JwtAuthenticationToken auth, @PathVariable long id) {
    orders.cancel(userId(auth), id);
    return ResponseEntity.noContent().build();
  }

  public record CheckoutRequest(long addressId, String idempotencyKey) {}
  private static long userId(JwtAuthenticationToken auth) {
    Object value=auth.getToken().getClaim("user_id");
    if (!(value instanceof Number n)||n.longValue()<=0) throw new IllegalStateException("JWT user_id không hợp lệ.");
    return n.longValue();
  }
}
