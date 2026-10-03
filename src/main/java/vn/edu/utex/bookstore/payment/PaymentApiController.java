package vn.edu.utex.bookstore.payment;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.Map;
import vn.edu.utex.bookstore.common.Problem;

@RestController
@RequestMapping("/api/orders/{id}/payment")
public final class PaymentApiController {
  private final PaymentService payments;
  public PaymentApiController(PaymentService payments) { this.payments = payments; }
  @GetMapping public PaymentService.PaymentView view(@PathVariable long id, JwtAuthenticationToken auth) { return payments.view(userId(auth), id); }
  @PostMapping("/start") public Map<String,String> start(@PathVariable long id, JwtAuthenticationToken auth) { return Map.of("checkoutUrl", payments.start(userId(auth), id)); }
  @PostMapping("/refresh") public PaymentService.PaymentView refresh(@PathVariable long id, JwtAuthenticationToken auth) { payments.refresh(userId(auth), id); return payments.view(userId(auth), id); }
  @PostMapping("/cancel") public PaymentService.PaymentView cancel(@PathVariable long id, JwtAuthenticationToken auth) {
    long userId = userId(auth);
    payments.view(userId, id); // Reject COD before changing its order through this online-only endpoint.
    payments.cancel(userId, id);
    return payments.view(userId, id);
  }
  private static long userId(JwtAuthenticationToken auth) {
    Object claim = auth.getToken().getClaim("user_id");
    if (!(claim instanceof Number n) || n.longValue() <= 0) throw new Problem(401,"Phiên đăng nhập không hợp lệ."); return n.longValue();
  }
}
