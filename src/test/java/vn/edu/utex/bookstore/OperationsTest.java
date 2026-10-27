package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.buyer.BuyerService;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.operations.OperationsService;
import vn.edu.utex.bookstore.order.OrderService;

class OperationsTest {
  final Clock clock=Clock.systemUTC();
  final MemoryStore store=DemoFixtures.store();
  final Identity reader=Identity.of(store.findLogin("reader")), admin=Identity.of(store.findLogin("admin"));
  final OperationsService ops=new OperationsService(store,clock);
  final OrderService orders=new OrderService(store,clock);
  final BuyerService buyer=new BuyerService(store,clock);
  final long address,copy,shop;
  OperationsTest(){DemoFixtures.catalogStock(store);copy=store.copies.keySet().iterator().next();shop=store.copies.get(copy).shopId;
    address=buyer.saveAddress(reader.id(),null,"Nhà","Bạn đọc","0900000000","12 Đường Sách","","","TP HCM",true).id;buyer.addToCart(reader.id(),copy);}
  @Test void discountCarrierAndCommissionAreSnapshottedAndRetryDoesNotConsumeTwice(){
    var c=ops.saveCarrier(admin,null,"Giao tiết kiệm","15000",true);
    var v=ops.createVoucher(reader,shop,"SACH10","10000","0",1,clock.instant().plusSeconds(3600));
    ops.commission(admin,shop,"0.10");var q=orders.quote(reader.id(),c.id,v.code);
    assertEquals(0,q.total().compareTo(q.subtotal().add(new BigDecimal("5000"))));
    var o=orders.checkout(reader.id(),address,"checkout-voucher-0001",q.fingerprint(),"COD",c.id,v.code).orders().getFirst();
    assertEquals(1,v.used);orders.checkout(reader.id(),address,"checkout-voucher-0001",q.fingerprint(),"COD",c.id,v.code);assertEquals(1,v.used);
    ops.saveCarrier(admin,c.id,"Giao tiết kiệm","99000",false);ops.commission(admin,shop,"0.20");
    assertEquals(0,o.shippingFee.compareTo(new BigDecimal("15000")));assertEquals(0,o.commissionRate.compareTo(new BigDecimal("0.10")));
    assertEquals(0,o.discount.compareTo(new BigDecimal("10000")));
    var displayed=orders.orders(reader.id()).getFirst();
    assertEquals("SACH10",displayed.voucherCode());assertEquals("Giao tiết kiệm",displayed.carrierName());
    assertEquals(0,displayed.total().compareTo(displayed.subtotal().add(displayed.shippingFee()).subtract(displayed.discount())));
    complete(o.id);var revenue=ops.revenue(reader,shop);assertEquals(1,revenue.orders());
    assertEquals(0,revenue.net().compareTo(o.subtotal.subtract(o.discount).subtract(revenue.commission())));
  }
  @Test void changedFeeOrVoucherAndWrongShopCannotSilentlyChangeCheckout(){
    var c=ops.saveCarrier(admin,null,"Giao nhanh","20000",true);var q=orders.quote(reader.id(),c.id,"");
    ops.saveCarrier(admin,c.id,"Giao nhanh","30000",true);
    assertEquals(409,assertThrows(Problem.class,()->orders.checkout(reader.id(),address,"checkout-stale-0001",q.fingerprint(),"COD",c.id,"")).status);
    var v=ops.createVoucher(reader,shop,"EXPIRE","10000","0",1,clock.instant().plusSeconds(1));v.expiresAt=clock.instant().minusSeconds(1);
    assertThrows(Problem.class,()->orders.quote(reader.id(),0,"EXPIRE"));
    v.expiresAt=clock.instant().plusSeconds(10);v.shopId=999;
    assertThrows(Problem.class,()->orders.quote(reader.id(),0,"EXPIRE"));
    v.shopId=shop;v.used=1;assertThrows(Problem.class,()->orders.quote(reader.id(),0,"EXPIRE"));assertTrue(store.orders.isEmpty());
  }
  @Test void databaseDecimalScaleDoesNotInvalidateAnUnchangedQuote(){
    var c=ops.saveCarrier(admin,null,"Giao demo","20000",true);
    var v=ops.createVoucher(reader,shop,"SCALE10","10000","0",2,clock.instant().plusSeconds(3600));
    var q=orders.quote(reader.id(),c.id,v.code);
    c.fee=c.fee.setScale(2);v.amount=v.amount.setScale(2);store.copies.get(copy).price=store.copies.get(copy).price.setScale(2);
    assertEquals(q.fingerprint(),orders.quote(reader.id(),c.id,v.code).fingerprint());
    assertEquals(1,orders.checkout(reader.id(),address,"checkout-scale-0001",q.fingerprint(),"COD",c.id,v.code).orders().size());
  }
  @Test void returnRequiresDeliveredPaidOwnedOrderAndSettlementIsIdempotent(){
    var o=orders.checkout(reader.id(),address,"checkout-return-0001").orders().getFirst();
    assertThrows(Problem.class,()->ops.requestReturn(reader.id(),o.id,"Sách không đúng mô tả"));complete(o.id);
    assertThrows(Problem.class,()->ops.requestReturn(admin.id(),o.id,"Sách không đúng mô tả"));
    var r=ops.requestReturn(reader.id(),o.id,"Sách không đúng mô tả");assertEquals(r.id,ops.requestReturn(reader.id(),o.id,"Yêu cầu gửi lặp lại").id);
    assertEquals(0,ops.revenue(reader,shop).orders());
    assertThrows(Problem.class,()->ops.resolveReturn(reader,o.id,"REFUNDED","Đã kiểm tra sách","DEMO-REF-001"));
    ops.resolveReturn(reader,o.id,"APPROVED","Đồng ý nhận lại sách","");
    ops.resolveReturn(reader,o.id,"REFUNDED","Đã nhận sách và đối soát","DEMO-REF-001");
    ops.resolveReturn(reader,o.id,"REFUNDED","Lặp xác nhận đối soát","DEMO-REF-001");
    assertEquals("UNAVAILABLE",store.copies.get(copy).status);assertEquals("REFUNDED",r.status);
    assertEquals(0,ops.revenue(reader,shop).orders());
  }
  @Test void expiredReturnAndCrossShopOperationsAreDenied(){
    var o=orders.checkout(reader.id(),address,"checkout-late-return").orders().getFirst();complete(o.id);o.updatedAt=clock.instant().minusSeconds(8*86400);
    assertThrows(Problem.class,()->ops.requestReturn(reader.id(),o.id,"Yêu cầu sau tám ngày"));
    var outsider=new User();outsider.username="outside";outsider.email="outside@example.test";outsider.role="USER";outsider.active=true;store.saveUser(outsider);
    var actor=Identity.of(outsider);assertThrows(Problem.class,()->ops.revenue(actor,shop));assertThrows(Problem.class,()->ops.vouchers(actor,shop));
    assertThrows(Problem.class,()->ops.createVoucher(actor,shop,"OTHER","1000","0",10,clock.instant().plusSeconds(5000)));
  }
  @Test void blockRevokesExistingSessionsAndProtectsAdmin(){
    assertThrows(Problem.class,()->ops.block(reader,admin.id(),true));assertThrows(Problem.class,()->ops.block(admin,admin.id(),true));
    ops.block(admin,reader.id(),true);assertTrue(store.user(reader.id()).blocked);assertNotEquals(reader.authVersion(),store.user(reader.id()).authVersion);
    assertThrows(Problem.class,()->ops.vouchers(reader,shop));
    ops.block(admin,reader.id(),false);assertThrows(Problem.class,()->ops.vouchers(reader,shop));
  }
  @Test void moderationRemovesPublicRatingWithoutDeletingEvidence(){
    var o=orders.checkout(reader.id(),address,"checkout-review-0001").orders().getFirst();complete(o.id);
    var reviews=new vn.edu.utex.bookstore.review.ReviewService(store,clock);
    long product=store.copies.get(copy).productId;
    var r=reviews.add(reader.id(),product,5,"Sách đúng tình trạng được mô tả, đóng gói cẩn thận và đủ trang để đọc.");
    assertThrows(Problem.class,()->ops.moderate(reader,r.id,false,"Kiểm tra nội dung"));
    ops.moderate(admin,r.id,false,"Ẩn để kiểm tra nội dung");assertEquals(0,reviews.summary(null,product).count());
    ops.moderate(admin,r.id,true,"Nội dung đáp ứng quy định");assertEquals(1,reviews.summary(null,product).count());
    assertEquals(2,store.shopEvents.stream().filter(e->"REVIEW_MODERATE".equals(e.action)).count());
  }
  @Test void statusFilterCannotExposeAnotherBuyersOrders(){var o=orders.checkout(reader.id(),address,"checkout-filter-0001").orders().getFirst();
    assertEquals(1,orders.filteredOrders(reader.id(),"PENDING").size());assertTrue(orders.filteredOrders(admin.id(),"PENDING").isEmpty());
    orders.cancel(reader.id(),o.id);assertTrue(orders.filteredOrders(reader.id(),"PENDING").isEmpty());assertEquals(1,orders.filteredOrders(reader.id(),"CANCELLED").size());
    assertThrows(Problem.class,()->orders.filteredOrders(reader.id(),"NOT_A_STATUS"));}
  private void complete(long id){orders.advance(reader,id,"CONFIRMED");orders.advance(reader,id,"SHIPPING");orders.advance(reader,id,"COMPLETED");}
}
