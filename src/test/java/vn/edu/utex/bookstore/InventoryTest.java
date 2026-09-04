package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.inventory.*;
import vn.edu.utex.bookstore.product.*;
import vn.edu.utex.bookstore.shop.*;

class InventoryTest {
  MemoryStore store;
  InventoryService inventory;
  ShopService shops;
  Identity admin, owner, other, manager;
  long shop, otherShop, product;
  Clock clock = Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC);

  @BeforeEach void setup() {
    store = new MemoryStore(); inventory = new InventoryService(store, clock); shops = new ShopService(store, clock);
    admin = user("admin", "ADMIN"); owner = user("owner", "USER"); other = user("other", "USER"); manager = user("manager", "USER");
    shop = shops.approve(admin, shops.apply(owner, "Góc Sách A", "123 Đường Sách, HCM"));
    otherShop = shops.approve(admin, shops.apply(other, "Góc Sách B", "456 Đường Sách, HCM"));
    Product p = new Product(); p.title = "Một cuốn sách"; p.createdAt = clock.instant(); p.price = BigDecimal.TEN; p.stock = 7;
    product = store.saveProduct(p).id;
  }
  Identity user(String name, String role) {
    User u = new User(); u.username = name; u.email = name + "@example.test"; u.role = role; u.active = true;
    store.saveUser(u); return Identity.of(u);
  }
  long receive() { return inventory.receive(owner, shop, product, "UNCLASSIFIED", "", "50000", "Nhập sách từ chủ cũ"); }
  void update(long id, int version, String condition, String price, String status) {
    inventory.update(owner, shop, id, version, condition, "Gáy sách hơi cũ", price, status, "Kiểm tra và phân loại sách");
  }
  void status(int expected, org.junit.jupiter.api.function.Executable action) {
    assertEquals(expected, assertThrows(Problem.class, action).status);
  }

  @Test void receivingCreatesOneUnavailableCopyAndAuditWithoutChangingLegacyStock() {
    long id = receive(); var copy = inventory.get(owner, shop, id);
    assertEquals("UNAVAILABLE", copy.status()); assertEquals("UNCLASSIFIED", copy.condition());
    assertTrue(copy.code().startsWith("GS-")); assertEquals(39, copy.code().length());
    assertEquals(1, inventory.history(owner, shop, id).size());
    assertEquals("04/09/2026 07:00", inventory.history(owner, shop, id).getFirst().getDisplayTime());
    assertEquals(7, store.product(product).stock);
    assertEquals(1, inventory.page(owner, shop, "1").total());
  }
  @Test void copiesHaveDistinctImmutableCodesAndNoCrossShopTransfer() {
    long first = receive(), second = receive();
    assertNotEquals(inventory.get(owner, shop, first).code(), inventory.get(owner, shop, second).code());
    String code = inventory.get(owner, shop, first).code();
    update(first, 0, "GOOD", "50000", "AVAILABLE");
    assertEquals(code, inventory.get(owner, shop, first).code());
    status(404, () -> inventory.get(other, otherShop, first));
    status(404, () -> inventory.update(other, otherShop, first, 1, "GOOD", "", "1", "AVAILABLE", "Wrong shop test"));
    assertEquals(shop, store.copy(first, false).shopId);
  }
  @Test void cannotPublishUnclassifiedOrForgeReservedAndSoldStates() {
    long id = receive();
    status(400, () -> update(id, 0, "UNCLASSIFIED", "50000", "AVAILABLE"));
    for (String state : List.of("SOLD", "RESERVED", "invalid")) status(400, () -> update(id, 0, "GOOD", "50000", state));
    assertEquals(1, store.movements.size());
    assertEquals("UNAVAILABLE", store.copy(id, false).status);
  }
  @Test void editsKeepPriceAndStatusHistoryAndRejectStaleVersion() {
    long id = receive(); update(id, 0, "GOOD", "40000", "AVAILABLE");
    var copy = inventory.get(owner, shop, id); assertEquals(1, copy.version());
    var event = inventory.history(owner, shop, id).getFirst();
    assertEquals(new BigDecimal("50000.00"), event.oldPrice()); assertEquals(new BigDecimal("40000.00"), event.newPrice());
    assertEquals("UNAVAILABLE", event.oldStatus()); assertEquals("AVAILABLE", event.newStatus());
    status(409, () -> update(id, 0, "GOOD", "30000", "UNAVAILABLE"));
    assertEquals(2, store.movements.size());
    update(id, 1, "GOOD", "40000", "UNAVAILABLE");
    assertEquals(3, store.movements.size());
  }
  @Test void identicalResubmissionDoesNotAddAuditNoise() {
    long id = receive(); update(id, 0, "GOOD", "40000", "AVAILABLE");
    update(id, 1, "GOOD", "40000", "AVAILABLE");
    assertEquals(2, store.movements.size()); assertEquals(1, store.copy(id, false).version);
  }
  @Test void reservedOrSoldBooksCannotBeEditedManually() {
    long id = receive();
    for (String status : List.of("RESERVED", "SOLD")) {
      store.copy(id, false).status = status;
      status(409, () -> update(id, 0, "GOOD", "40000", "AVAILABLE"));
    }
    assertEquals(1, store.movements.size());
  }
  @Test void revokedMembershipAndSuspendedShopRejectWrites() {
    long id = receive(); shops.setMember(admin, shop, manager.username(), "MANAGER", true);
    assertEquals(id, inventory.get(manager, shop, id).id());
    shops.setMember(admin, shop, manager.username(), "MANAGER", false);
    status(403, () -> inventory.page(manager, shop, "1"));
    shops.setSuspended(admin, shop, true, "Kiểm tra cửa hàng");
    assertEquals(id, inventory.get(owner, shop, id).id());
    status(409, () -> update(id, 0, "GOOD", "50000", "AVAILABLE"));
    status(409, this::receive);
    shops.setSuspended(admin, shop, false, "Đã kiểm tra xong");
    update(id, 0, "GOOD", "50000", "AVAILABLE");
  }
  @Test void forgedAdminBlockedAndStaleIdentitiesCannotReadInventory() {
    Identity forged = new Identity(other.id(), other.username(), "ADMIN", 0);
    status(403, () -> inventory.page(forged, shop, "1"));
    status(401, () -> inventory.page(null, shop, "1"));
    store.user(owner.id()).blocked = true; status(403, () -> inventory.page(owner, shop, "1"));
    store.user(owner.id()).blocked = false; store.user(owner.id()).authVersion++;
    status(403, () -> inventory.page(owner, shop, "1"));
  }
  @Test void invalidMoneyConditionAndReasonNeverWrite() {
    for (String price : List.of("-1", "0.001", "10000000000", "nan", "", "1e999999999", "9".repeat(10000)))
      status(400, () -> inventory.receive(owner, shop, product, "GOOD", "", price, "Valid reason"));
    status(400, () -> inventory.receive(owner, shop, product, "WRONG", "", "10", "Valid reason"));
    status(400, () -> inventory.receive(owner, shop, product, "GOOD", "x".repeat(2001), "10", "Valid reason"));
    status(400, () -> inventory.receive(owner, shop, product, "GOOD", "", "10", "no"));
    status(400, () -> inventory.receive(owner, shop, 99999, "GOOD", "", "10", "Valid reason"));
    assertTrue(store.copies.isEmpty()); assertTrue(store.movements.isEmpty());
  }
  @Test void paginationHasStableTwentyOneSplitAndRejectsInvalidPages() {
    assertEquals(0, inventory.page(owner, shop, null).total());
    for (int i = 0; i < 21; i++) receive();
    var first = inventory.page(owner, shop, "1"); var second = inventory.page(owner, shop, "2");
    assertEquals(20, first.items().size()); assertEquals(1, second.items().size()); assertEquals(2, first.pages());
    assertEquals(21, first.items().getFirst().id()); assertEquals(1, second.items().getFirst().id());
    for (String raw : List.of("0", "-1", "bad", "2147483648")) status(400, () -> inventory.page(owner, shop, raw));
    status(404, () -> inventory.page(owner, shop, "3"));
  }
  @Test void historyAndReadsCannotCrossShopAndMissingCopyIs404() {
    long id = receive(); status(403, () -> inventory.history(other, shop, id));
    status(404, () -> inventory.history(other, otherShop, id));
    status(404, () -> inventory.get(owner, shop, 99999));
    status(404, () -> inventory.page(admin, 99999, "1"));
  }
  @Test void productWithCopiesCannotBeDeletedEvenWhenUnavailable() {
    receive(); ProductService products = new ProductService(store, clock);
    status(409, () -> products.delete(product)); assertNotNull(store.product(product));
  }
}
