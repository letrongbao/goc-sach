package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.shop.*;

class ShopTest {
  MemoryStore store;
  ShopService service;
  Identity admin, ownerA, ownerB, manager, seller;
  @BeforeEach void setup() {
    store = new MemoryStore();
    service = new ShopService(store, Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC));
    admin = user("admin", "ADMIN"); ownerA = user("owner-a", "USER"); ownerB = user("owner-b", "USER");
    manager = user("manager", "USER"); seller = user("seller", "USER");
  }
  Identity user(String username, String role) {
    User user = new User();
    user.username = username; user.email = username + "@example.test"; user.role = role;
    user.active = true; user.createdAt = Instant.now();
    store.saveUser(user); return Identity.of(user);
  }
  long apply(Identity actor) { return service.apply(actor, "Góc Sách " + actor.username(), "123 Đường Sách, Thành phố Hồ Chí Minh"); }
  long shop(Identity actor) { return service.approve(admin, apply(actor)); }
  void status(int expected, org.junit.jupiter.api.function.Executable action) {
    assertEquals(expected, assertThrows(Problem.class, action).status);
  }

  @Test void applicationWaitsForApprovalAndCannotBeDuplicated() {
    long id = apply(ownerA);
    assertEquals("PENDING", service.applications(ownerA, false).getFirst().status());
    assertTrue(service.shops(ownerA).isEmpty());
    assertTrue(store.shops.isEmpty());
    status(409, () -> apply(ownerA));
    assertEquals(id, store.pendingApplication(ownerA.id()).id);
    assertEquals(1, store.shopEvents.size());
  }
  @Test void approvalIsIdempotentAndDoesNotGrantGlobalRole() {
    long id = apply(ownerA), shopId = service.approve(admin, id);
    assertEquals(shopId, service.approve(admin, id));
    assertEquals(1, store.shops.size());
    assertEquals(1, store.memberships.size());
    assertEquals(2, store.shopEvents.size());
    assertEquals("USER", store.user(ownerA.id()).role);
    assertEquals("SELLER", service.get(ownerA, shopId).role());
    assertNull(store.pendingApplication(ownerA.id()));
  }
  @Test void adminRoleFromCallerCannotBeForged() {
    long id = apply(ownerA);
    Identity forged = new Identity(ownerA.id(), ownerA.username(), "ADMIN", ownerA.authVersion());
    status(403, () -> service.approve(forged, id));
    status(403, () -> service.applications(forged, true));
    assertTrue(store.shops.isEmpty());
  }
  @Test void shopDataCannotBeReadAcrossOwners() {
    long a = shop(ownerA), b = shop(ownerB);
    assertEquals(List.of(a), service.shops(ownerA).stream().map(ShopService.ShopView::id).toList());
    status(403, () -> service.get(ownerA, b));
    status(403, () -> service.get(ownerB, a));
    status(403, () -> service.members(ownerA, a));
    assertEquals(2, service.shops(admin).size());
  }
  @Test void managerCanManageSellersOnlyInAssignedShop() {
    long a = shop(ownerA), b = shop(ownerB);
    service.setMember(admin, a, manager.username(), "MANAGER", true);
    service.setMember(manager, a, seller.username(), "SELLER", true);
    assertEquals("SELLER", service.get(seller, a).role());
    status(403, () -> service.setMember(manager, b, seller.username(), "SELLER", true));
    status(403, () -> service.setMember(manager, a, seller.username(), "MANAGER", true));
    status(403, () -> service.setMember(manager, a, manager.username(), "SELLER", false));
    service.setMember(manager, a, seller.username(), "SELLER", false);
    status(403, () -> service.get(seller, a));
    assertTrue(service.shops(seller).isEmpty());
  }
  @Test void ownerMembershipCannotBeRemovedAndRolesAreValidated() {
    long a = shop(ownerA);
    status(409, () -> service.setMember(admin, a, ownerA.username(), "SELLER", false));
    status(400, () -> service.setMember(admin, a, seller.username(), "ADMIN", true));
    status(400, () -> service.setMember(admin, a, "missing", "SELLER", true));
  }
  @Test void suspendedShopRejectsChangesUntilAdminReopensIt() {
    long a = shop(ownerA);
    service.setMember(admin, a, manager.username(), "MANAGER", true);
    status(403, () -> service.setSuspended(manager, a, true, "Không đủ quyền"));
    service.setSuspended(admin, a, true, "Tạm kiểm tra thông tin");
    assertEquals("SUSPENDED", service.get(ownerA, a).status());
    status(409, () -> service.setMember(manager, a, seller.username(), "SELLER", true));
    service.setSuspended(admin, a, false, "Đã kiểm tra thông tin");
    service.setMember(manager, a, seller.username(), "SELLER", true);
    assertEquals("ACTIVE", service.get(seller, a).status());
  }
  @Test void rejectionKeepsHistoryAndAllowsANewApplication() {
    long rejected = apply(ownerA);
    service.reject(admin, rejected, "Cần bổ sung địa chỉ kinh doanh");
    status(409, () -> service.approve(admin, rejected));
    long next = apply(ownerA);
    assertNotEquals(rejected, next);
    assertEquals(2, service.applications(ownerA, false).size());
    assertEquals("REJECTED", store.application(rejected, false).status);
    assertEquals("Cần bổ sung địa chỉ kinh doanh", store.application(rejected, false).reviewNote);
  }
  @Test void blockedOrExpiredIdentityCannotOperate() {
    long id = apply(ownerA);
    store.user(ownerA.id()).blocked = true;
    status(409, () -> service.approve(admin, id));
    status(403, () -> service.shops(ownerA));
    store.user(ownerA.id()).blocked = false;
    store.user(ownerA.id()).authVersion++;
    status(403, () -> apply(ownerA));
    status(401, () -> service.shops(null));
  }
  @Test void invalidFieldsAndMissingIdsAreHandled() {
    status(400, () -> service.apply(ownerA, "a", "valid business address"));
    status(400, () -> service.apply(ownerA, "Shop", "short"));
    status(400, () -> service.apply(ownerA, "Shop\nInjected", "valid business address"));
    status(404, () -> service.get(admin, 999));
    status(404, () -> service.approve(admin, 999));
    long id = apply(ownerA);
    status(400, () -> service.reject(admin, id, ""));
    assertEquals("PENDING", store.application(id, false).status);
  }
  @Test void parallelApprovalsCreateOnlyOneShopInSerializedTestStore() throws Exception {
    long id = apply(ownerA);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> service.approve(admin, id));
      var second = executor.submit(() -> service.approve(admin, id));
      assertEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
      assertEquals(1, store.shops.size());
      assertEquals(1, store.memberships.size());
    }
  }
}
