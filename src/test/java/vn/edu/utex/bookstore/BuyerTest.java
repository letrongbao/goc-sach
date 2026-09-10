package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.buyer.BuyerService;

class BuyerTest {
  @Test
  void profileAddressesFavoritesRecentlyViewedAndCartAreAccountScoped() {
    MemoryStore store = DemoFixtures.store();
    DemoFixtures.catalogStock(store);
    Identity reader = Identity.of(store.findLogin("reader"));
    Identity admin = Identity.of(store.findLogin("admin"));
    BuyerService buyer = new BuyerService(store, Clock.systemUTC());

    assertEquals("", buyer.profile(reader.id()).displayName);
    buyer.saveProfile(reader.id(), "Bạn đọc", "0900000000");
    var address = buyer.saveAddress(reader.id(), null, "Nhà", "Bạn đọc", "0900000000",
        "1 Đường Sách", "Phường 1", "Quận 1", "TP HCM", true);
    assertTrue(address.defaultAddress);
    assertThrows(vn.edu.utex.bookstore.common.Problem.class,
        () -> buyer.deleteAddress(admin.id(), address.id));
    buyer.addFavorite(reader.id(), 1);
    buyer.addFavorite(reader.id(), 1);
    assertEquals(1, buyer.favorites(reader.id()).size());
    buyer.recordViewed(reader.id(), 1);
    buyer.recordViewed(reader.id(), 2);
    assertEquals(2, buyer.recentlyViewed(reader.id(), 10).size());
    long copy = store.copies.values().iterator().next().id;
    buyer.addToCart(reader.id(), copy);
    buyer.addToCart(reader.id(), copy);
    assertEquals(1, buyer.cart(reader.id()).size());
    assertTrue(buyer.cart(admin.id()).isEmpty());
    buyer.removeFromCart(reader.id(), copy);
    assertTrue(buyer.cart(reader.id()).isEmpty());
  }

  @Test
  void cartRejectsUnavailableCopyAndAddressDefaultsMoveAtomically() {
    MemoryStore store = DemoFixtures.store();
    DemoFixtures.catalogStock(store);
    Identity reader = Identity.of(store.findLogin("reader"));
    BuyerService buyer = new BuyerService(store, Clock.systemUTC());
    var first = buyer.saveAddress(reader.id(), null, "Nhà", "Bạn đọc", "0900000000", "1 Đường Sách", "", "", "TP HCM", false);
    var second = buyer.saveAddress(reader.id(), null, "Cơ quan", "Bạn đọc", "0900000000", "2 Đường Sách", "", "", "TP HCM", true);
    assertFalse(first.defaultAddress);
    assertTrue(second.defaultAddress);
    buyer.deleteAddress(reader.id(), second.id);
    assertTrue(buyer.addresses(reader.id()).getFirst().defaultAddress);
    var copy = store.copies.values().iterator().next();
    copy.status = "SOLD";
    assertThrows(vn.edu.utex.bookstore.common.Problem.class, () -> buyer.addToCart(reader.id(), copy.id));
  }

  @Test
  void cartViewsContainHumanReadableMetadataAndAvailability() {
    MemoryStore store = DemoFixtures.store();
    DemoFixtures.catalogStock(store);
    Identity reader = Identity.of(store.findLogin("reader"));
    BuyerService buyer = new BuyerService(store, Clock.systemUTC());
    var copy = store.copies.values().iterator().next();
    buyer.addToCart(reader.id(), copy.id);
    var line = buyer.cart(reader.id()).getFirst();
    assertFalse(line.title().isBlank());
    assertFalse(line.shopName().isBlank());
    assertTrue(line.sellable());
    copy.status = "SOLD";
    assertFalse(buyer.cart(reader.id()).getFirst().sellable());
  }
}
