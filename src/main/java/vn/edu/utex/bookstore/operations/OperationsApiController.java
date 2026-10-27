package vn.edu.utex.bookstore.operations;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.edu.utex.bookstore.config.App;
import vn.edu.utex.bookstore.security.ApiActor;

@RestController
@RequestMapping("/api")
public final class OperationsApiController {
  private final App app;private final OperationsService ops;
  public OperationsApiController(App app,OperationsService ops) {this.app=app;this.ops=ops;}
  @GetMapping("/carriers") public Object carriers() {return ops.carriers();}
  @GetMapping("/seller/shops/{id}/vouchers") public Object vouchers(JwtAuthenticationToken a,@PathVariable long id){return ops.vouchers(ApiActor.require(app,a),id);}
  @PostMapping("/seller/shops/{id}/vouchers") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object voucher(JwtAuthenticationToken a,@PathVariable long id,@RequestBody VoucherRequest r){return ops.createVoucher(ApiActor.require(app,a),id,r.code(),r.amount(),r.minimumSubtotal(),r.maximumUses(),r.expiresAt());}
  @PostMapping("/seller/vouchers/{code}/disable") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void disable(JwtAuthenticationToken a,@PathVariable String code){ops.disableVoucher(ApiActor.require(app,a),code);}
  @GetMapping("/seller/shops/{id}/revenue") public Object revenue(JwtAuthenticationToken a,@PathVariable long id){return ops.revenue(ApiActor.require(app,a),id);}
  @GetMapping("/admin/carriers") public Object allCarriers(JwtAuthenticationToken a){return ops.allCarriers(ApiActor.admin(app,a));}
  @PostMapping("/admin/carriers") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object carrier(JwtAuthenticationToken a,@RequestBody CarrierRequest r){return ops.saveCarrier(ApiActor.admin(app,a),null,r.name(),r.fee(),r.active());}
  @PutMapping("/admin/carriers/{id}") public Object carrier(JwtAuthenticationToken a,@PathVariable long id,@RequestBody CarrierRequest r){return ops.saveCarrier(ApiActor.admin(app,a),id,r.name(),r.fee(),r.active());}
  @PutMapping("/admin/shops/{id}/commission") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void commission(JwtAuthenticationToken a,@PathVariable long id,@RequestBody CommissionRequest r){ops.commission(ApiActor.admin(app,a),id,r.rate());}
  @GetMapping("/admin/users") public Object users(JwtAuthenticationToken a){return ops.accounts(ApiActor.admin(app,a));}
  @PutMapping("/admin/users/{id}/block") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void block(JwtAuthenticationToken a,@PathVariable long id,@RequestBody BlockRequest r){ops.block(ApiActor.admin(app,a),id,r.blocked());}
  @GetMapping("/admin/reviews") public Object reviews(JwtAuthenticationToken a){return ops.moderation(ApiActor.admin(app,a));}
  @PutMapping("/admin/reviews/{id}/visibility") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void moderate(JwtAuthenticationToken a,@PathVariable long id,@RequestBody VisibilityRequest r){ops.moderate(ApiActor.admin(app,a),id,r.visible(),r.note());}
  public record VoucherRequest(String code,String amount,String minimumSubtotal,int maximumUses,java.time.Instant expiresAt) {}
  public record CarrierRequest(String name,String fee,boolean active) {}
  public record CommissionRequest(String rate) {}
  public record BlockRequest(boolean blocked) {}
  public record VisibilityRequest(boolean visible,String note) {}
}
