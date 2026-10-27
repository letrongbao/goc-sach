package vn.edu.utex.bookstore.inventory;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.edu.utex.bookstore.config.App;
import vn.edu.utex.bookstore.security.ApiActor;

@RestController
@RequestMapping("/api/seller/shops/{shopId}/copies")
public final class InventoryApiController {
  private final App app;
  public InventoryApiController(App app) { this.app=app; }
  @GetMapping public Object list(JwtAuthenticationToken a,@PathVariable long shopId,@RequestParam(required=false) String page) { return app.inventory.page(ApiActor.require(app,a),shopId,page); }
  @GetMapping("/{id}") public Object get(JwtAuthenticationToken a,@PathVariable long shopId,@PathVariable long id) { return app.inventory.get(ApiActor.require(app,a),shopId,id); }
  @GetMapping("/{id}/history") public Object history(JwtAuthenticationToken a,@PathVariable long shopId,@PathVariable long id) { return app.inventory.history(ApiActor.require(app,a),shopId,id); }
  @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object create(JwtAuthenticationToken a,@PathVariable long shopId,@RequestBody CopyRequest r) { return Map.of("id",app.inventory.receive(ApiActor.require(app,a),shopId,r.productId(),r.condition(),r.defects(),r.price(),r.reason())); }
  @PutMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void update(JwtAuthenticationToken a,@PathVariable long shopId,@PathVariable long id,@RequestBody CopyRequest r) { if(r.version()==null)throw vn.edu.utex.bookstore.common.Problem.invalid("Thiếu version."); app.inventory.update(ApiActor.require(app,a),shopId,id,r.version(),r.condition(),r.defects(),r.price(),r.status(),r.reason()); }
  public record CopyRequest(long productId,Integer version,String condition,String defects,String price,String status,String reason) {}
}
