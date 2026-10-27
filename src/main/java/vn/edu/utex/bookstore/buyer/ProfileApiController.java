package vn.edu.utex.bookstore.buyer;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.edu.utex.bookstore.config.App;
import vn.edu.utex.bookstore.security.ApiActor;

@RestController
@RequestMapping("/api/account")
public final class ProfileApiController {
  private final App app; private final BuyerService buyer;
  public ProfileApiController(App app,BuyerService buyer) { this.app=app;this.buyer=buyer; }
  @GetMapping("/profile") public Object profile(JwtAuthenticationToken auth) { return buyer.profile(ApiActor.require(app,auth).id()); }
  @PutMapping("/profile") public Object profile(JwtAuthenticationToken auth,@RequestBody ProfileRequest r) { return buyer.saveProfile(ApiActor.require(app,auth).id(),r.displayName(),r.phone()); }
  @GetMapping("/addresses") public Object addresses(JwtAuthenticationToken auth) { return buyer.addresses(ApiActor.require(app,auth).id()); }
  @GetMapping("/addresses/{id}") public Object address(JwtAuthenticationToken auth,@PathVariable long id) { return buyer.address(ApiActor.require(app,auth).id(),id); }
  @PostMapping("/addresses") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object create(JwtAuthenticationToken auth,@RequestBody AddressRequest r) { return save(auth,null,r); }
  @PutMapping("/addresses/{id}") public Object update(JwtAuthenticationToken auth,@PathVariable long id,@RequestBody AddressRequest r) { return save(auth,id,r); }
  @DeleteMapping("/addresses/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(JwtAuthenticationToken auth,@PathVariable long id) { buyer.deleteAddress(ApiActor.require(app,auth).id(),id); }
  private Object save(JwtAuthenticationToken auth,Long id,AddressRequest r) { return buyer.saveAddress(ApiActor.require(app,auth).id(),id,r.label(),r.recipientName(),r.phone(),r.line1(),r.ward(),r.district(),r.city(),r.defaultAddress()); }
  public record ProfileRequest(String displayName,String phone) {}
  public record AddressRequest(String label,String recipientName,String phone,String line1,String ward,String district,String city,boolean defaultAddress) {}
}
