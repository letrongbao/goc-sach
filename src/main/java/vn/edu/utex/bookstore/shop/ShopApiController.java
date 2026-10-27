package vn.edu.utex.bookstore.shop;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.edu.utex.bookstore.config.App;
import vn.edu.utex.bookstore.security.ApiActor;

@RestController
@RequestMapping("/api")
public final class ShopApiController {
  private final App app;
  public ShopApiController(App app) { this.app=app; }
  @GetMapping("/account/shop-applications") public Object applications(JwtAuthenticationToken a) { return app.shops.applications(ApiActor.require(app,a),false); }
  @PostMapping("/account/shop-applications") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object apply(JwtAuthenticationToken a,@RequestBody ApplicationRequest r) { return Map.of("id",app.shops.apply(ApiActor.require(app,a),r.name(),r.address())); }
  @GetMapping("/seller/shops") public Object shops(JwtAuthenticationToken a) { return app.shops.shops(ApiActor.require(app,a)); }
  @GetMapping("/seller/shops/{id}") public Object shop(JwtAuthenticationToken a,@PathVariable long id) { return app.shops.get(ApiActor.require(app,a),id); }
  @GetMapping("/seller/shops/{id}/members") public Object members(JwtAuthenticationToken a,@PathVariable long id) { return app.shops.members(ApiActor.require(app,a),id); }
  @PutMapping("/seller/shops/{id}/members") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void member(JwtAuthenticationToken a,@PathVariable long id,@RequestBody MemberRequest r) { app.shops.setMember(ApiActor.require(app,a),id,r.login(),r.role(),r.active()); }
  @GetMapping("/admin/shop-applications") public Object all(JwtAuthenticationToken a) { return app.shops.applications(ApiActor.admin(app,a),true); }
  @PostMapping("/admin/shop-applications/{id}/approve") public Object approve(JwtAuthenticationToken a,@PathVariable long id) { return Map.of("shopId",app.shops.approve(ApiActor.admin(app,a),id)); }
  @PostMapping("/admin/shop-applications/{id}/reject") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void reject(JwtAuthenticationToken a,@PathVariable long id,@RequestBody ReasonRequest r) { app.shops.reject(ApiActor.admin(app,a),id,r.reason()); }
  @PutMapping("/admin/shops/{id}/suspension") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void suspend(JwtAuthenticationToken a,@PathVariable long id,@RequestBody SuspensionRequest r) { app.shops.setSuspended(ApiActor.admin(app,a),id,r.suspended(),r.reason()); }
  public record ApplicationRequest(String name,String address) {}
  public record MemberRequest(String login,String role,boolean active) {}
  public record ReasonRequest(String reason) {}
  public record SuspensionRequest(boolean suspended,String reason) {}
}
