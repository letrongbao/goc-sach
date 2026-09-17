package vn.edu.utex.bookstore.buyer;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

/** Small JWT API for account-scoped cart, favorites and recently viewed data. */
@RestController
@RequestMapping("/api")
public final class BuyerApiController {
  private final BuyerService buyer;
  public BuyerApiController(BuyerService buyer) { this.buyer = buyer; }

  @GetMapping("/cart")
  public ResponseEntity<?> cart(JwtAuthenticationToken auth) { return ResponseEntity.ok(buyer.cart(userId(auth))); }

  @PostMapping("/cart/items")
  public ResponseEntity<?> addCart(JwtAuthenticationToken auth, @RequestBody CartRequest body) {
    buyer.addToCart(userId(auth), body.copyId());
    return ResponseEntity.ok(Map.of("status", "added"));
  }

  @DeleteMapping("/cart/items/{copyId}")
  public ResponseEntity<?> removeCart(JwtAuthenticationToken auth, @PathVariable long copyId) {
    buyer.removeFromCart(userId(auth), copyId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/favorites")
  public ResponseEntity<?> favorites(JwtAuthenticationToken auth) { return ResponseEntity.ok(buyer.favoriteBooks(userId(auth))); }

  @PutMapping("/favorites/{productId}")
  public ResponseEntity<?> addFavorite(JwtAuthenticationToken auth, @PathVariable long productId) {
    buyer.addFavorite(userId(auth), productId); return ResponseEntity.ok(Map.of("status", "added"));
  }

  @DeleteMapping("/favorites/{productId}")
  public ResponseEntity<?> removeFavorite(JwtAuthenticationToken auth, @PathVariable long productId) {
    buyer.removeFavorite(userId(auth), productId); return ResponseEntity.noContent().build();
  }

  @PostMapping("/recent-views/{productId}")
  public ResponseEntity<?> viewed(JwtAuthenticationToken auth, @PathVariable long productId) {
    buyer.recordViewed(userId(auth), productId); return ResponseEntity.noContent().build();
  }

  @GetMapping("/recent-views")
  public ResponseEntity<?> recent(JwtAuthenticationToken auth, @RequestParam(defaultValue="20") int limit) {
    return ResponseEntity.ok(buyer.recentlyViewedBooks(userId(auth), limit));
  }

  public record CartRequest(long copyId) {}
  private static long userId(JwtAuthenticationToken auth) {
    Object value = auth.getToken().getClaim("user_id");
    if (!(value instanceof Number number) || number.longValue() <= 0)
      throw new vn.edu.utex.bookstore.common.Problem(401, "Phiên đăng nhập không hợp lệ.");
    return number.longValue();
  }
}
