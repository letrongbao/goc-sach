package vn.edu.utex.bookstore.operations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.auth.User;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.order.OrderService;
import vn.edu.utex.bookstore.persistence.Store;
import vn.edu.utex.bookstore.shop.ShopAccess;
import vn.edu.utex.bookstore.shop.ShopAuditEvent;

/** Administrative changes are audited; refunds record an external settlement, never move funds. */
public final class OperationsService {
  private final Store store;
  private final Clock clock;
  public OperationsService(Store store, Clock clock) { this.store=store; this.clock=clock; }
  public record Account(long id,String username,String email,String role,boolean blocked) {}
  public record Revenue(long orders,BigDecimal sales,BigDecimal shipping,BigDecimal discounts,BigDecimal commission,BigDecimal net) {}
  public record Moderation(long id,long productId,String body,int rating,String status) {}

  public List<Carrier> carriers() {return store.tx(d->d.carriers().stream().filter(c->c.active).toList());}
  public List<Carrier> allCarriers(Identity actor) {return store.tx(d->{admin(d,actor);return d.carriers();});}
  public Carrier saveCarrier(Identity actor,Long id,String name,String fee,boolean active) {
    String label=text(name,2,200); BigDecimal amount=money(fee,false);
    return store.tx(d->{admin(d,actor); Carrier c=id==null?new Carrier():d.carrier(id,true);
      if(c==null)throw Problem.missing(); c.name=label;c.fee=amount;c.active=active;
      d.saveCarrier(c);audit(d,actor,null,"CARRIER","Cập nhật nhà vận chuyển #"+c.id);return c;});
  }
  public List<Voucher> vouchers(Identity actor,long shopId) {return store.tx(d->{ShopAccess.require(d,actor,shopId,false);return d.vouchers(shopId);});}
  public Voucher createVoucher(Identity actor,long shopId,String code,String amount,String minimum,int uses,Instant expiry) {
    String normalized=code==null?"":code.strip().toUpperCase(java.util.Locale.ROOT);
    if(!normalized.matches("[A-Z0-9_-]{4,30}") || uses<1 || uses>100000 || expiry==null || !expiry.isAfter(clock.instant()))
      throw Problem.invalid("Mã cần 4–30 chữ/số, lượt dùng 1–100.000 và ngày hết hạn trong tương lai.");
    BigDecimal discount=money(amount,true), threshold=money(minimum,false);
    return store.tx(d->{ShopAccess.require(d,actor,shopId,true);
      if(d.voucher(normalized,false)!=null)throw new Problem(409,"Mã giảm giá đã tồn tại.");
      Voucher v=new Voucher();v.shopId=shopId;v.code=normalized;v.amount=discount;v.minimumSubtotal=threshold;
      v.maximumUses=uses;v.used=0;v.active=true;v.expiresAt=expiry;d.saveVoucher(v);
      audit(d,actor,shopId,"VOUCHER_CREATE","Tạo mã "+normalized);return v;});
  }
  public void disableVoucher(Identity actor,String code) {store.tx(d->{var v=d.voucher(code,true);if(v==null)throw Problem.missing();
    ShopAccess.require(d,actor,v.shopId,false);v.active=false;d.saveVoucher(v);audit(d,actor,v.shopId,"VOUCHER_DISABLE",code);return null;});}
  public void commission(Identity actor,long shopId,String rawRate) {
    BigDecimal rate;
    try {rate=new BigDecimal(rawRate).setScale(4,RoundingMode.UNNECESSARY);}catch(RuntimeException e){throw Problem.invalid("Tỷ lệ chiết khấu không hợp lệ.");}
    if(rate.signum()<0||rate.compareTo(new BigDecimal("0.5"))>0)throw Problem.invalid("Chiết khấu từ 0 đến 0,5 (50%).");
    store.tx(d->{admin(d,actor);var shop=d.shop(shopId,true);if(shop==null)throw Problem.missing();shop.commissionRate=rate;d.saveShop(shop);
      audit(d,actor,shopId,"COMMISSION","Tỷ lệ đơn mới: "+rate);return null;});
  }
  public Revenue revenue(Identity actor,long shopId) {return store.tx(d->{ShopAccess.require(d,actor,shopId,false);
    long count=0;BigDecimal sales=BigDecimal.ZERO,shipping=BigDecimal.ZERO,discounts=BigDecimal.ZERO,commission=BigDecimal.ZERO;
    for(var o:d.ordersForShop(shopId)) {
      var r=d.returnForOrder(o.id,false);
      if(!"COMPLETED".equals(o.status)||!"PAID".equals(o.paymentStatus)||(r!=null&& !"REJECTED".equals(r.status)))continue;
      count++;sales=sales.add(o.subtotal);shipping=shipping.add(o.shippingFee);discounts=discounts.add(o.discount);
      commission=commission.add(o.subtotal.subtract(o.discount).multiply(o.commissionRate).setScale(0,RoundingMode.HALF_UP));
    }
    return new Revenue(count,sales,shipping,discounts,commission,sales.subtract(discounts).subtract(commission));});}
  public List<Account> accounts(Identity actor) {return store.tx(d->{admin(d,actor);return d.userAccounts().stream()
    .map(u->new Account(u.id,u.username,u.email,u.role,u.blocked)).toList();});}
  public void block(Identity actor,long userId,boolean blocked) {store.tx(d->{admin(d,actor);var u=d.lockUser(userId);if(u==null)throw Problem.missing();
    if("ADMIN".equals(u.role))throw Problem.invalid("Không khóa tài khoản quản trị tại màn hình này.");
    if(u.blocked!=blocked){u.blocked=blocked;u.authVersion++;d.saveUser(u);audit(d,actor,null,"USER_ACCESS","User #"+userId+" blocked="+blocked);}return null;});}
  public List<Moderation> moderation(Identity actor) {return store.tx(d->{admin(d,actor);return d.moderationReviews().stream()
    .map(r->new Moderation(r.id,r.productId,r.body,r.rating,r.status)).toList();});}
  public void moderate(Identity actor,long id,boolean visible,String note) {String reason=text(note,5,300);store.tx(d->{admin(d,actor);
    var r=d.reviewById(id,true);if(r==null)throw Problem.missing();r.status=visible?"PUBLISHED":"HIDDEN";r.updatedAt=clock.instant();d.saveReview(r);
    audit(d,actor,null,"REVIEW_MODERATE","Review #"+id+": "+r.status+"; "+reason);return null;});}
  public ReturnRequest requestReturn(long userId,long orderId,String reason) {String detail=text(reason,10,500);return store.tx(d->{
    var u=d.user(userId);if(u==null||!u.active||u.blocked)throw new Problem(403,"Tài khoản không thể gửi yêu cầu.");
    var o=OrderService.lockedOrder(d,orderId);if(o==null||o.userId!=userId)throw Problem.missing();
    var existing=d.returnForOrder(orderId,true);if(existing!=null)return existing;
    if(!"COMPLETED".equals(o.status)||!"PAID".equals(o.paymentStatus)||o.updatedAt.plusSeconds(7*86400).isBefore(clock.instant()))
      throw new Problem(409,"Chỉ nhận yêu cầu trong 7 ngày sau khi đơn đã giao và thanh toán.");
    ReturnRequest r=new ReturnRequest();r.orderId=orderId;r.status="REQUESTED";r.reason=detail;r.resolution="";r.refundReference="";
    r.createdAt=clock.instant();r.updatedAt=r.createdAt;d.saveReturn(r);history(d,orderId,"Đã gửi yêu cầu trả hàng: "+detail);return r;});}
  public ReturnRequest buyerReturn(long userId,long orderId) {return store.tx(d->{var o=d.order(orderId,false);if(o==null||o.userId!=userId)throw Problem.missing();return d.returnForOrder(orderId,false);});}
  public List<ReturnRequest> shopReturns(Identity actor,long shopId) {return store.tx(d->{ShopAccess.require(d,actor,shopId,false);return d.ordersForShop(shopId).stream()
    .map(o->d.returnForOrder(o.id,false)).filter(java.util.Objects::nonNull).toList();});}
  public void resolveReturn(Identity actor,long orderId,String action,String note,String reference) {
    if(!Set.of("APPROVED","REJECTED","REFUNDED").contains(action))throw Problem.invalid("Thao tác trả hàng không hợp lệ.");
    String detail=text(note,5,300);String ref="REFUNDED".equals(action)?text(reference,5,200):"";
    store.tx(d->{var o=OrderService.lockedOrder(d,orderId);if(o==null)throw Problem.missing();ShopAccess.require(d,actor,o.shopId,false);
      var r=d.returnForOrder(orderId,true);if(r==null)throw Problem.missing();
      if(r.status.equals(action))return null;
      if(("REFUNDED".equals(action)&& !"APPROVED".equals(r.status))||(!"REFUNDED".equals(action)&&!"REQUESTED".equals(r.status)))
        throw new Problem(409,"Yêu cầu đã được xử lý. Vui lòng tải lại trang.");
      if("REFUNDED".equals(action)) {
        // Returned copies stay unavailable until the shop inspects and reclassifies them.
        for(var item:d.orderItems(orderId)){var copy=d.copy(item.copyId,true);if(copy==null||!"SOLD".equals(copy.status))throw new Problem(409,"Kho không nhất quán.");}
        for(var item:d.orderItems(orderId)){var copy=d.copy(item.copyId,false);copy.status="UNAVAILABLE";copy.updatedAt=clock.instant();d.saveCopy(copy);}
      }
      r.status=action;r.resolution=detail;r.refundReference=ref;r.updatedAt=clock.instant();d.saveReturn(r);
      history(d,orderId,"Trả hàng "+action+": "+detail);audit(d,actor,o.shopId,"RETURN_"+action,"Đơn #"+orderId+"; "+detail+"; "+ref);return null;});
  }
  private void history(Store.Data d,long orderId,String note){var h=new vn.edu.utex.bookstore.order.OrderStatusHistory();h.orderId=orderId;h.status="COMPLETED";h.note=note.length()>500?note.substring(0,500):note;h.createdAt=clock.instant();d.saveOrderStatus(h);}
  private void audit(Store.Data d,Identity a,Long shopId,String action,String note){var e=new ShopAuditEvent();e.actorId=a.id();e.shopId=shopId;e.action=action;e.note=note;e.createdAt=clock.instant();d.recordShopEvent(e);}
  public static User admin(Store.Data d,Identity a){var u=a==null?null:d.user(a.id());if(u==null||!u.active||u.blocked||!"ADMIN".equals(u.role)||u.authVersion!=a.authVersion())throw new Problem(403,"Chỉ quản trị viên được thực hiện thao tác này.");return u;}
  private static String text(String s,int min,int max){String v=s==null?"":s.strip();if(v.length()<min||v.length()>max||v.codePoints().anyMatch(Character::isISOControl))throw Problem.invalid("Nội dung cần từ "+min+" đến "+max+" ký tự hợp lệ.");return v;}
  private static BigDecimal money(String raw,boolean positive){try{var n=new BigDecimal(raw).setScale(0,RoundingMode.UNNECESSARY);if(n.signum()<0||(positive&&n.signum()==0)||n.compareTo(new BigDecimal("100000000"))>0)throw new ArithmeticException();return n;}catch(RuntimeException e){throw Problem.invalid("Số tiền phải là số nguyên đồng hợp lệ, tối đa 100 triệu.");}}
}
