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

class CatalogTest {
  MemoryStore store;
  CatalogService catalog;
  ShopService shops;
  InventoryService inventory;
  Identity admin, owner;
  long shop;
  @BeforeEach void setup() {
    store = DemoFixtures.store(); catalog = new CatalogService(store);
    shops = new ShopService(store, Clock.systemUTC()); inventory = new InventoryService(store, Clock.systemUTC());
    admin = Identity.of(store.findLogin("admin")); owner = Identity.of(store.findLogin("reader"));
    shop = shops.approve(admin, shops.apply(owner, "Cửa hàng thử", "Địa chỉ minh họa"));
  }
  long offer(long product, String price) {
    long copy = inventory.receive(owner, shop, product, "GOOD", "Đủ trang", price, "Nhập kho để test");
    inventory.update(owner, shop, copy, 0, "GOOD", "Đủ trang", price, "AVAILABLE", "Đã kiểm tra sách");
    return copy;
  }
  @Test void productWithoutCopiesNeverAppearsPublicly() {
    assertTrue(catalog.latest().isEmpty()); assertEquals(0, catalog.page("1", "").total());
    var detail = catalog.detail(1, null);
    assertEquals(0, detail.product().stock()); assertNull(detail.product().price()); assertTrue(detail.offers().isEmpty());
  }
  @Test void multipleCopiesProduceOneTitleAndMinimumActualPrice() {
    offer(1, "40000"); offer(1, "60000");
    assertEquals(1, catalog.page("1", "").total());
    var detail = catalog.detail(1, null);
    assertEquals(2, detail.product().stock()); assertEquals(new BigDecimal("40000.00"), detail.product().price());
    assertEquals(new BigDecimal("40000.00"), detail.offers().getFirst().price());
  }
  @Test void priceAndCountAreAggregatedAcrossActiveShopsOnly() {
    offer(1, "50000");
    long otherShop = shops.approve(admin, shops.apply(admin, "Shop thứ hai", "Địa chỉ thử thứ hai"));
    long copy = inventory.receive(admin, otherShop, 1, "GOOD", "", "30000", "Nhập thử shop hai");
    inventory.update(admin, otherShop, copy, 0, "GOOD", "", "30000", "AVAILABLE", "Đã kiểm tra xong");
    assertEquals(2, catalog.detail(1, null).product().stock());
    assertEquals(new BigDecimal("30000.00"), catalog.detail(1, null).product().price());
    assertEquals(1, catalog.page("1", "").total());
    shops.setSuspended(admin, otherShop, true, "Tạm khóa shop hai");
    assertEquals(1, catalog.detail(1, null).product().stock());
    assertEquals(new BigDecimal("50000.00"), catalog.detail(1, null).product().price());
  }
  @Test void inactiveShopDisappearsAndReopeningRestoresAvailableCopies() {
    offer(1, "40000"); shops.setSuspended(admin, shop, true, "Kiểm tra shop");
    assertTrue(catalog.latest().isEmpty()); assertEquals(0, catalog.detail(1, null).product().stock());
    shops.setSuspended(admin, shop, false, "Kiểm tra xong"); assertEquals(1, catalog.detail(1, null).product().stock());
  }
  @Test void unavailableReservedSoldAndUnclassifiedAreExcluded() {
    long id = offer(1, "40000");
    for (String state : List.of("UNAVAILABLE", "RESERVED", "SOLD")) {
      store.copy(id, false).status = state; assertTrue(catalog.latest().isEmpty()); assertTrue(catalog.detail(1, null).offers().isEmpty());
    }
    store.copy(id, false).status = "AVAILABLE"; store.copy(id, false).condition = "UNCLASSIFIED";
    assertTrue(catalog.latest().isEmpty());
  }
  @Test void hiddenCategoryIsNotPublicButStillEditableByAdmin() {
    offer(1, "40000"); store.product(1).category.active = false;
    assertTrue(catalog.latest().isEmpty()); assertEquals(404, assertThrows(Problem.class, () -> catalog.detail(1, null)).status);
    assertNotNull(new ProductService(store, Clock.systemUTC()).get(1));
  }
  @Test void keywordMatchesTitleOrAuthorLiterallyAndIgnoresCase() {
    offer(1, "40000"); offer(3, "50000");
    assertEquals(1, catalog.page("1", "  LẬP TRÌNH  ").total());
    assertEquals(2, catalog.page("1", "tủ sách góc").total());
    for (String q : List.of("%' OR 1=1 --", "_", "<script>")) assertEquals(0, catalog.page("1", q).total());
  }
  @Test void latestIsTopTwentyAndCatalogPagesAreStableTwentyOne() {
    var products = new ProductService(store, Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC));
    for (int i = 14; i <= 21; i++) products.saveDetails(null, 1, "Sách " + i, "Tác giả", "", "");
    for (long id = 1; id <= 21; id++) offer(id, "40000");
    assertEquals(20, catalog.latest().size()); assertEquals(21, catalog.latest().getFirst().id());
    assertEquals(20, catalog.page("1", "").items().size()); assertEquals(1, catalog.page("2", "").items().size());
    assertEquals(1, catalog.page("2", "").items().getFirst().id());
  }
  @Test void offerPagesAreStableTwentyOneAndShowOnlyPublicFields() {
    for (int i = 0; i < 21; i++) offer(1, "40000");
    var first = catalog.detail(1, "1"); var second = catalog.detail(1, "2");
    assertEquals(20, first.offers().size()); assertEquals(1, second.offers().size());
    assertEquals(2, first.pages()); assertTrue(first.offers().getFirst().id() < second.offers().getFirst().id());
    assertEquals("Cửa hàng thử", first.offers().getFirst().shopName());
  }
  @Test void invalidPaginationQueryAndMissingIdsAreHandled() {
    for (String raw : List.of("0", "-1", "bad", "2147483648")) {
      assertEquals(400, assertThrows(Problem.class, () -> catalog.page(raw, "")).status);
      assertEquals(400, assertThrows(Problem.class, () -> catalog.detail(1, raw)).status);
    }
    assertEquals(404, assertThrows(Problem.class, () -> catalog.page("2", "")).status);
    assertEquals(404, assertThrows(Problem.class, () -> catalog.detail(999, "1")).status);
    assertEquals(400, assertThrows(Problem.class, () -> catalog.page("1", "x".repeat(101))).status);
  }
  @Test void metadataEditCannotChangeCopyPricing() {
    new ProductService(store, Clock.systemUTC()).saveDetails(1L, 1, "Tên mới", "Tác giả", "Mô tả", "");
    offer(1, "40000");
    assertEquals(new BigDecimal("40000.00"), catalog.detail(1, null).product().price());
  }

  @Test void filtersAndPublicShopUseOnlyMatchingSellableCopies() {
    offer(1, "40000");
    long good = offer(3, "80000");
    store.copy(good, false).condition = "LIKE_NEW";
    assertEquals(1, catalog.page("1", "", "1", "30000", "50000", "GOOD", Long.toString(shop)).total());
    assertEquals(1, catalog.page("1", "", "3", "70000", "90000", "LIKE_NEW", Long.toString(shop)).total());
    assertEquals(0, catalog.page("1", "", null, null, "39999", null, null).total());
    var publicShop = catalog.shop("cua-hang-thu-" + shop, "1");
    assertEquals("Cửa hàng thử", publicShop.shop().name());
    assertEquals(2, publicShop.catalog().total());
    shops.setSuspended(admin, shop, true, "Tạm khóa để kiểm tra");
    assertEquals(404, assertThrows(Problem.class,
        () -> catalog.shop("cua-hang-thu-" + shop, "1")).status);
  }

  @Test void priceSortingUsesOnlyCopiesMatchingTheCurrentFilters() {
    offer(1, "10000");
    long firstMatchingCopy = offer(1, "90000");
    store.copy(firstMatchingCopy, false).condition = "LIKE_NEW";
    long secondMatchingCopy = offer(2, "50000");
    store.copy(secondMatchingCopy, false).condition = "LIKE_NEW";
    offer(3, "30000");
    var page = catalog.page("1", "", null, "20000", "100000", "LIKE_NEW", Long.toString(shop), "price_asc");
    assertEquals(List.of(2L, 1L), page.items().stream().map(ProductView::id).toList());
    assertEquals(new BigDecimal("50000.00"), page.items().getFirst().price());
    assertEquals(new BigDecimal("90000.00"), page.items().getLast().price());
    assertEquals("price_asc", page.filters().sort());
    assertEquals(2, page.total());
    assertEquals(List.of(1L, 2L), catalog.page("1", "", null, "20000", "100000", "LIKE_NEW", Long.toString(shop), "price_desc")
        .items().stream().map(ProductView::id).toList());
  }

  @Test void favoritesRankingAndShelfFollowRealFavoritesAndVisibility() {
    offer(1, "40000"); offer(2, "30000"); offer(3, "50000");
    var buyer = new vn.edu.utex.bookstore.buyer.BuyerService(store, Clock.systemUTC());
    buyer.addFavorite(owner.id(), 1); buyer.addFavorite(admin.id(), 1); buyer.addFavorite(owner.id(), 2);
    assertEquals(List.of(1L, 2L, 3L), catalog.page("1", "", null, null, null, null, null, "favorites")
        .items().stream().map(ProductView::id).toList());
    assertEquals(List.of(1L, 2L), catalog.shelf("favorites", 5).stream().map(ProductView::id).toList());
    buyer.removeFavorite(owner.id(), 1); buyer.removeFavorite(admin.id(), 1);
    assertEquals(List.of(2L), catalog.shelf("favorites", 5).stream().map(ProductView::id).toList());
    store.product(2).category.active = false;
    assertTrue(catalog.shelf("favorites", 5).isEmpty());
    assertEquals(List.of(3L, 1L), catalog.page("1", "", null, null, null, null, null, "favorites")
        .items().stream().map(ProductView::id).toList());
  }

  @Test void equalPriceAndFavoriteRanksHaveStablePageBoundaries() {
    var products = new ProductService(store, Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC));
    for (int i = 14; i <= 21; i++) products.saveDetails(null, 1, "Sách " + i, "Tác giả", "", "");
    for (long id = 1; id <= 21; id++) offer(id, "40000");
    for (String sort : List.of("newest", "price_asc", "price_desc", "favorites")) {
      var first = catalog.page("1", "", null, null, null, null, null, sort);
      var second = catalog.page("2", "", null, null, null, null, null, sort);
      assertEquals(21, first.total()); assertEquals(20, first.items().size()); assertEquals(2, first.pages());
      assertEquals(21, first.items().getFirst().id()); assertEquals(2, first.items().getLast().id());
      assertEquals(1, second.items().getFirst().id());
    }
  }

  @Test void invalidSortingCannotBecomeQuerySyntaxAndDefaultStaysNewest() {
    for (String sort : List.of("cheapest", "newest desc", "favorites; drop table products", "NEWEST"))
      assertEquals(400, assertThrows(Problem.class,
          () -> catalog.page("1", "", null, null, null, null, null, sort)).status);
    assertEquals("newest", catalog.page("1", "").filters().sort());
    assertTrue(catalog.shelf("favorites", 5).isEmpty());
  }
}
