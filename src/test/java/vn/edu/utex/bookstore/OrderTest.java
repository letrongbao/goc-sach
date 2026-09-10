package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.buyer.BuyerService;
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
}
