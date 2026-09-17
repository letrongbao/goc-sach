package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.auth.User;
import vn.edu.utex.bookstore.buyer.BuyerService;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.order.OrderService;

class OrderTest {
  @Test
  void checkoutSplitsByShopReservesCopiesAndIsIdempotent() {
    MemoryStore store=DemoFixtures.store(); DemoFixtures.catalogStock(store);
    Identity admin=Identity.of(store.findLogin("admin")), reader=Identity.of(store.findLogin("reader"));
    var shops=new vn.edu.utex.bookstore.shop.ShopService(store,Clock.systemUTC());
    long secondShop=shops.approve(admin,shops.apply(admin,"Góc Sách Hai","2 Book Street City"));
    var inventory=new vn.edu.utex.bookstore.inventory.InventoryService(store,Clock.systemUTC());
    long secondCopy=inventory.receive(admin,secondShop,2,"GOOD","","99000","Nhập shop hai");
    inventory.update(admin,secondShop,secondCopy,0,"GOOD","","99000","AVAILABLE","Mở bán");
    BuyerService buyer=new BuyerService(store,Clock.systemUTC());
    var address=buyer.saveAddress(reader.id(),null,"Nhà","Bạn đọc","0900000000","1 Đường Sách","","","TP HCM",true);
    long firstCopy=store.copies.values().stream().filter(c->c.shopId!=secondShop).findFirst().orElseThrow().id;
    buyer.addToCart(reader.id(),firstCopy); buyer.addToCart(reader.id(),secondCopy);
    OrderService order=new OrderService(store,Clock.systemUTC());
    var result=order.checkout(reader.id(),address.id,"checkout-20260909-0001");
    assertEquals(2,result.orders().size()); assertTrue(buyer.cart(reader.id()).isEmpty());
    assertTrue(store.copies.get(firstCopy).status.equals("RESERVED")); assertTrue(store.copies.get(secondCopy).status.equals("RESERVED"));
    assertEquals(2,order.checkout(reader.id(),address.id,"checkout-20260909-0001").orders().size());
    assertEquals(2,store.orders.size());
    assertTrue(store.reservationRows.values().stream().allMatch(row -> row.expiresAt == null));
    assertTrue(store.orderItemRows.values().stream().allMatch(item -> item.titleSnapshot != null
        && item.conditionSnapshot != null && item.copyCodeSnapshot != null));
  }

  @Test
  void pendingOrderCanBeCancelledAndReservationReleased() {
    MemoryStore store=DemoFixtures.store(); DemoFixtures.catalogStock(store);
    Identity reader=Identity.of(store.findLogin("reader"));
    long shop=store.copies.values().stream().findFirst().orElseThrow().shopId;
    var buyer=new BuyerService(store,Clock.systemUTC());
    var address=buyer.saveAddress(reader.id(),null,"Nhà","Bạn đọc","0900000000","1 Đường Sách","","","TP HCM",true);
    long copy=store.copies.values().stream().filter(c->c.shopId==shop).findFirst().orElseThrow().id;
    buyer.addToCart(reader.id(),copy);
    var order=new OrderService(store,Clock.systemUTC());
    var created=order.checkout(reader.id(),address.id,"checkout-cancel-20260909").orders().getFirst();
    order.cancel(reader.id(),created.id);
    assertEquals("CANCELLED",store.orders.get(created.id).status);
    assertEquals("AVAILABLE",store.copies.get(copy).status);
    assertEquals("RELEASED",store.reservationRows.values().stream().findFirst().orElseThrow().status);
  }

  @Test
  void quoteIncludesShippingPerShopAndRejectsAChangedPrice() {
    MemoryStore store=DemoFixtures.store(); DemoFixtures.catalogStock(store);
    Identity reader=Identity.of(store.findLogin("reader"));
    var buyer=new BuyerService(store,Clock.systemUTC());
    var address=buyer.saveAddress(reader.id(),null,"Nhà","Bạn đọc","0900000000","1 Đường Sách","","","TP HCM",true);
    long copy=store.copies.values().stream().findFirst().orElseThrow().id;
    buyer.addToCart(reader.id(),copy);
    var orders=new OrderService(store,Clock.systemUTC());
    var quote=orders.quote(reader.id());
    assertTrue(quote.ready());
    assertEquals(new BigDecimal("30000.00"),quote.shippingFee());
    assertEquals(quote.subtotal().add(quote.shippingFee()),quote.total());
    store.copies.get(copy).price=store.copies.get(copy).price.add(BigDecimal.ONE);
    var problem=assertThrows(vn.edu.utex.bookstore.common.Problem.class,
        ()->orders.checkout(reader.id(),address.id,"checkout-quote-change-01",quote.fingerprint()));
    assertEquals(409,problem.status);
    assertTrue(store.orders.isEmpty());
  }

  @Test
  void sellerFollowsStateMachineAndCompletionSellsTheCopy() {
    MemoryStore store=DemoFixtures.store(); DemoFixtures.catalogStock(store);
    Identity reader=Identity.of(store.findLogin("reader"));
    var buyer=new BuyerService(store,Clock.systemUTC());
    var address=buyer.saveAddress(reader.id(),null,"Nhà","Bạn đọc","0900000000","1 Đường Sách","","","TP HCM",true);
    long copy=store.copies.values().stream().findFirst().orElseThrow().id;
    buyer.addToCart(reader.id(),copy);
    var orders=new OrderService(store,Clock.systemUTC());
    var quote=orders.quote(reader.id());
    var created=orders.checkout(reader.id(),address.id,"checkout-state-20260917",quote.fingerprint()).orders().getFirst();
    assertEquals(409,assertThrows(Problem.class,()->orders.advance(reader,created.id,"SHIPPING")).status);
    assertEquals(400,assertThrows(Problem.class,()->orders.sellerCancel(reader,created.id,"hư")).status);
    orders.advance(reader,created.id,"CONFIRMED");
    orders.advance(reader,created.id,"SHIPPING");
    assertThrows(vn.edu.utex.bookstore.common.Problem.class,()->orders.cancel(reader.id(),created.id));
    orders.advance(reader,created.id,"COMPLETED");
    assertEquals("COMPLETED",store.orders.get(created.id).status);
    assertEquals("SOLD",store.copies.get(copy).status);
    assertEquals("COMMITTED",store.reservationRows.values().stream().findFirst().orElseThrow().status);
    assertEquals(4,orders.detail(reader.id(),created.id).history().size());
    orders.advance(reader,created.id,"COMPLETED");
    assertEquals(4,orders.detail(reader.id(),created.id).history().size());
  }

  @Test
  void sellerCanCancelConfirmedOrderButAnotherShopCannotReadIt() {
    MemoryStore store=DemoFixtures.store(); DemoFixtures.catalogStock(store);
    Identity admin=Identity.of(store.findLogin("admin")), reader=Identity.of(store.findLogin("reader"));
    var buyer=new BuyerService(store,Clock.systemUTC());
    var address=buyer.saveAddress(reader.id(),null,"Nhà","Bạn đọc","0900000000","1 Đường Sách","","","TP HCM",true);
    long copy=store.copies.values().stream().findFirst().orElseThrow().id;
    buyer.addToCart(reader.id(),copy);
    var orders=new OrderService(store,Clock.systemUTC());
    var created=orders.checkout(reader.id(),address.id,"checkout-seller-cancel-1").orders().getFirst();
    orders.advance(reader,created.id,"CONFIRMED");
    orders.sellerCancel(reader,created.id,"Sách bị hư hỏng khi đóng gói");
    assertEquals("CANCELLED",store.orders.get(created.id).status);
    assertEquals("AVAILABLE",store.copies.get(copy).status);

    User outsider=new User(); outsider.username="seller-b"; outsider.email="seller-b@example.test";
    outsider.passwordHash="unused-in-unit-test"; outsider.role="USER"; outsider.active=true; outsider.createdAt=Instant.now();
    store.saveUser(outsider);
    Identity sellerB=Identity.of(outsider);
    var shops=new vn.edu.utex.bookstore.shop.ShopService(store,Clock.systemUTC());
    long otherShop=shops.approve(admin,shops.apply(sellerB,"Cửa hàng khác","10 Đường Sách Thành phố"));
    assertTrue(orders.sellerOrders(sellerB,otherShop).isEmpty());
    assertEquals(403,assertThrows(Problem.class,()->orders.sellerDetail(sellerB,created.id)).status);
    assertEquals(403,assertThrows(Problem.class,()->orders.advance(sellerB,created.id,"CONFIRMED")).status);
    assertEquals(403,assertThrows(Problem.class,
        ()->orders.sellerCancel(sellerB,created.id,"Không thuộc cửa hàng được phân công")).status);
    assertEquals(404,assertThrows(Problem.class,()->orders.detail(outsider.id,created.id)).status);
    assertEquals(404,assertThrows(Problem.class,()->orders.cancel(outsider.id,created.id)).status);
  }

  @Test
  void suspendedShopCanSettleOrdersAcceptedBeforeSuspension() {
    MemoryStore store=DemoFixtures.store(); DemoFixtures.catalogStock(store);
    Identity reader=Identity.of(store.findLogin("reader"));
    var buyer=new BuyerService(store,Clock.systemUTC());
    var address=buyer.saveAddress(reader.id(),null,"Nhà","Bạn đọc","0900000000","1 Đường Sách","","","TP HCM",true);
    var copies=store.copies.values().stream().limit(2).toList();
    var orders=new OrderService(store,Clock.systemUTC());

    buyer.addToCart(reader.id(),copies.get(0).id);
    var completed=orders.checkout(reader.id(),address.id,"checkout-before-suspend-1").orders().getFirst();
    buyer.addToCart(reader.id(),copies.get(1).id);
    var cancelled=orders.checkout(reader.id(),address.id,"checkout-before-suspend-2").orders().getFirst();
    store.shops.get(completed.shopId).status="SUSPENDED";

    orders.advance(reader,completed.id,"CONFIRMED");
    orders.advance(reader,completed.id,"SHIPPING");
    orders.advance(reader,completed.id,"COMPLETED");
    orders.sellerCancel(reader,cancelled.id,"Cửa hàng tạm ngừng nên hoàn lại đơn");

    assertEquals("COMPLETED",store.orders.get(completed.id).status);
    assertEquals("SOLD",store.copies.get(copies.get(0).id).status);
    assertEquals("CANCELLED",store.orders.get(cancelled.id).status);
    assertEquals("AVAILABLE",store.copies.get(copies.get(1).id).status);
  }

  @Test
  void orderDetailKeepsPurchaseSnapshotsWhenCatalogChanges() {
    MemoryStore store=DemoFixtures.store(); DemoFixtures.catalogStock(store);
    Identity reader=Identity.of(store.findLogin("reader"));
    var buyer=new BuyerService(store,Clock.systemUTC());
    var address=buyer.saveAddress(reader.id(),null,"Nhà","Bạn đọc","0900000000","1 Đường Sách","","","TP HCM",true);
    var copy=store.copies.values().stream().findFirst().orElseThrow();
    var product=store.products.get(copy.productId);
    var shop=store.shops.get(copy.shopId);
    String title=product.title, shopName=shop.name, condition=copy.condition, code=copy.code;
    BigDecimal price=copy.price;
    buyer.addToCart(reader.id(),copy.id);
    var orders=new OrderService(store,Clock.systemUTC());
    var quote=orders.quote(reader.id());
    var created=orders.checkout(reader.id(),address.id,"checkout-snapshot-20260917",quote.fingerprint()).orders().getFirst();

    product.title="Tên mới"; product.image="https://example.test/new.jpg";
    shop.name="Tên cửa hàng mới"; copy.condition="LIKE_NEW"; copy.code="NEW-CODE"; copy.price=price.add(BigDecimal.TEN);
    var detail=orders.detail(reader.id(),created.id);
    assertEquals(shopName,detail.order().shopName());
    assertEquals(title,detail.items().getFirst().title());
    assertEquals(condition,detail.items().getFirst().condition());
    assertEquals(code,detail.items().getFirst().copyCode());
    assertEquals(price,detail.items().getFirst().price());
  }
}
