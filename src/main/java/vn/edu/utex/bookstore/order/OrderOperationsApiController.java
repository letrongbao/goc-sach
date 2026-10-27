package vn.edu.utex.bookstore.order;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.edu.utex.bookstore.config.App;
import vn.edu.utex.bookstore.security.ApiActor;
import vn.edu.utex.bookstore.operations.OperationsService;
import vn.edu.utex.bookstore.common.Problem;

@RestController
@RequestMapping("/api")
public final class OrderOperationsApiController {
  private final App app;private final OrderService orders;private final OperationsService ops;
  public OrderOperationsApiController(App app,OrderService orders,OperationsService ops){this.app=app;this.orders=orders;this.ops=ops;}
  @GetMapping("/seller/orders") public Object list(JwtAuthenticationToken a,@RequestParam(required=false) Long shopId){return orders.sellerOrders(ApiActor.require(app,a),shopId);}
  @GetMapping("/seller/orders/{id}") public Object detail(JwtAuthenticationToken a,@PathVariable long id){return orders.sellerDetail(ApiActor.require(app,a),id);}
  @PostMapping("/seller/orders/{id}/status") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void advance(JwtAuthenticationToken a,@PathVariable long id,@RequestBody StatusRequest r){orders.advance(ApiActor.require(app,a),id,r.status());}
  @PostMapping("/seller/orders/{id}/cancel") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void cancel(JwtAuthenticationToken a,@PathVariable long id,@RequestBody ReasonRequest r){orders.sellerCancel(ApiActor.require(app,a),id,r.reason());}
  @GetMapping("/orders/{id}/return") public Object returned(JwtAuthenticationToken a,@PathVariable long id){var r=ops.buyerReturn(ApiActor.require(app,a).id(),id);if(r==null)throw Problem.missing();return r;}
  @PostMapping("/orders/{id}/return") public Object request(JwtAuthenticationToken a,@PathVariable long id,@RequestBody ReasonRequest r){return ops.requestReturn(ApiActor.require(app,a).id(),id,r.reason());}
  @GetMapping("/seller/shops/{id}/returns") public Object returns(JwtAuthenticationToken a,@PathVariable long id){return ops.shopReturns(ApiActor.require(app,a),id);}
  @PostMapping("/seller/orders/{id}/return/resolve") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void resolve(JwtAuthenticationToken a,@PathVariable long id,@RequestBody ResolutionRequest r){
    if("REFUNDED".equals(r.status())&&!r.settled())throw Problem.invalid("Cần xác nhận đã nhận sách và hoàn tiền bên ngoài.");
    ops.resolveReturn(ApiActor.require(app,a),id,r.status(),r.note(),r.reference());
  }
  public record StatusRequest(String status) {}
  public record ReasonRequest(String reason) {}
  public record ResolutionRequest(String status,String note,String reference,boolean settled) {}
}
