package vn.edu.utex.bookstore.product;

import java.util.*;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.persistence.Store;

/** Public catalog uses only available physical copies in active shops, never legacy stock. */
public final class CatalogService {
  public record Page(List<ProductView> items, int number, int pages, long total, String query) {}
  public record Detail(ProductView product, List<CatalogRepository.Offer> offers, int number, int pages) {}
  private final Store store;
  public CatalogService(Store store) { this.store = store; }

  public List<ProductView> latest() { return store.tx(d -> views(d, d.catalogProducts("", 0, 20))); }

  public Page page(String rawPage, String rawQuery) {
    int number = number(rawPage);
    String query = rawQuery == null ? "" : rawQuery.strip();
    if (query.length() > 100) throw Problem.invalid("Từ khóa tối đa 100 ký tự.");
    return store.tx(d -> {
      long total = d.catalogCount(query);
      int pages = pages(total, 20);
      if (number > pages) throw Problem.missing();
      return new Page(views(d, d.catalogProducts(query, Math.multiplyExact(number - 1, 20), 20)), number, pages, total, query);
    });
  }

  public Detail detail(long id, String rawPage) {
    int number = number(rawPage);
    return store.tx(d -> {
      Product p = d.product(id);
      if (p == null || !p.category.active) throw Problem.missing();
      ProductView view = views(d, List.of(p)).getFirst();
      int pages = pages(view.stock(), 20);
      if (number > pages) throw Problem.missing();
      return new Detail(view, d.catalogOffers(id, Math.multiplyExact(number - 1, 20), 20), number, pages);
    });
  }

  private List<ProductView> views(Store.Data d, List<Product> products) {
    if (products.isEmpty()) return List.of();
    var stock = d.catalogStock(products.stream().map(p -> p.id).toList());
    return products.stream().map(p -> {
      var current = stock.get(p.id);
      return new ProductView(p.id, p.category.id, p.category.name, p.title, p.author, p.description,
          current == null ? null : current.minimumPrice(), current == null ? 0 : Math.toIntExact(current.available()), p.image);
    }).toList();
  }
  private static int number(String raw) {
    try {
      int number = raw == null || raw.isBlank() ? 1 : Integer.parseInt(raw);
      if (number < 1) throw new NumberFormatException();
      return number;
    } catch (NumberFormatException e) { throw Problem.invalid("Số trang phải là số nguyên dương."); }
  }
  private static int pages(long count, int size) { return Math.toIntExact(Math.max(1, (count + size - 1) / size)); }
}
