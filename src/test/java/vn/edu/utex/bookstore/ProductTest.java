package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import vn.edu.utex.bookstore.category.*;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.product.*;

class ProductTest {
  MemoryStore store;
  ProductService products;
  Category category;

  @BeforeEach
  void setup() {
    store = new MemoryStore();
    category = new Category();
    category.name = "Công nghệ";
    category.image = "";
    category.active = true;
    store.saveCategory(category);
    products =
        new ProductService(
            store, Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC));
  }

  void add(int n) {
    for (int i = 1; i <= n; i++)
      products.save(null, category.id, "Sách " + i, "Góc", "Nội dung demo", "100000", "10", "");
  }

  @Test
  void latest10AndPagination661() {
    add(13);
    assertEquals(10, products.latest().size());
    assertEquals(13, products.latest().getFirst().id());
    assertEquals(6, products.page("1").items().size());
    assertEquals(6, products.page("2").items().size());
    assertEquals(1, products.page("3").items().size());
    assertEquals(13, products.page("1").total());
    assertEquals(3, products.page("1").pages());
    assertEquals(1, products.page("3").items().getFirst().id());
    assertThrows(Problem.class, () -> products.page("4"));
  }

  @Test
  void detailEditDeleteAndCategoryRestriction() {
    add(1);
    assertEquals(category.id.longValue(), products.get(1).categoryId());
    assertThrows(Problem.class, () -> new CategoryService(store).delete(category.id));
    products.save(1L, category.id, "Đã sửa", "Tác giả", "Mô tả", "120000.50", "0", "");
    assertEquals("Đã sửa", products.get(1).title());
    assertEquals(0, products.get(1).stock());
    products.delete(1);
    assertThrows(Problem.class, () -> products.get(1));
    new CategoryService(store).delete(category.id);
  }

  @Test
  void emptyCatalog() {
    assertTrue(products.latest().isEmpty());
    assertEquals(0, products.page(null).total());
    assertEquals(1, products.page(null).pages());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "x", "2147483648"})
  void badPages(String raw) {
    assertThrows(Problem.class, () -> products.page(raw));
  }

  @ParameterizedTest
  @CsvSource({"-1,1", "1,-1", "1.001,1", "10000000000,1", "x,2", "5,x"})
  void invalidNumbers(String price, String stock) {
    assertThrows(
        Problem.class,
        () -> products.save(null, category.id, "Book", "Author", "", price, stock, ""));
  }

  @Test
  void invalidFields() {
    assertThrows(Problem.class, () -> products.save(null, 999, "Book", "Author", "", "1", "1", ""));
    assertThrows(
        Problem.class, () -> products.save(null, category.id, "", "Author", "", "1", "1", ""));
    assertThrows(
        Problem.class,
        () -> products.save(null, category.id, "Book", "Author", "", null, null, ""));
  }
}
