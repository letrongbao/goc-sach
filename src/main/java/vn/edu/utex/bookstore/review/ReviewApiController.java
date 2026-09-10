package vn.edu.utex.bookstore.review;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
public final class ReviewApiController {
  private final ReviewService reviews;
  public ReviewApiController(ReviewService reviews){this.reviews=reviews;}
  @GetMapping public ResponseEntity<?> list(@PathVariable long productId){return ResponseEntity.ok(reviews.list(productId));}
  @PostMapping public ResponseEntity<?> add(JwtAuthenticationToken auth,@PathVariable long productId,@RequestBody ReviewRequest request){return ResponseEntity.status(201).body(reviews.add(userId(auth),productId,request.rating(),request.body()));}
  public record ReviewRequest(int rating,String body){}
  private static long userId(JwtAuthenticationToken auth){Object v=auth.getToken().getClaim("user_id");if(!(v instanceof Number n)||n.longValue()<=0)throw new IllegalStateException("JWT user_id không hợp lệ.");return n.longValue();}
}
