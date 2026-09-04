package vn.edu.utex.bookstore.product;

import java.math.*;
import java.time.*;
import java.util.*;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.persistence.Store;

public final class ProductService {
  private final Store store;
  private final Clock clock;

  public ProductService(Store store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  public record Page(List<ProductView> items, int number, int pages, long total) {
    public List<ProductView> getItems() {
      return items;
    }

    public int getNumber() {
      return number;
    }

    public int getPages() {
      return pages;
    }

    public long getTotal() {
      return total;
    }
  }

  public List<ProductView> latest() {
    return store.tx(d -> d.products(0, 10).stream().map(ProductView::from).toList());
  }

  public Page page(String raw) {
    int number;
    try {
      number = raw == null || raw.isBlank() ? 1 : Integer.parseInt(raw);
      if (number < 1) throw Problem.invalid("Số trang phải là số nguyên dương.");
    } catch (NumberFormatException e) {
      throw Problem.invalid("Số trang không hợp lệ.");
    }
    return store.tx(
        d -> {
          long total = d.productCount();
          int pages = (int) Math.max(1, (total + 5) / 6);
          if (number > pages) throw Problem.missing();
          return new Page(
              d.products(Math.multiplyExact(number - 1, 6), 6).stream()
                  .map(ProductView::from)
                  .toList(),
              number,
              pages,
              total);
        });
  }

  public ProductView get(long id) {
    return store.tx(
        d -> {
          Product p = d.product(id);
          if (p == null) throw Problem.missing();
          return ProductView.from(p);
        });
  }

  public void save(
      Long id,
      long categoryId,
      String title,
      String author,
      String description,
      String rawPrice,
      String rawStock,
      String image) {
    String name = text(title, 200, "Tên sách"),
        writer = text(author, 150, "Tác giả"),
        detail = description == null ? "" : description.trim();
    if (detail.length() > 10000) throw Problem.invalid("Mô tả tối đa 10.000 ký tự.");
    BigDecimal price;
    int stock;
    try {
      price =
          new BigDecimal(rawPrice == null ? "" : rawPrice).setScale(2, RoundingMode.UNNECESSARY);
      stock = Integer.parseInt(rawStock);
      if (price.signum() < 0 || price.precision() > 12 || stock < 0)
        throw new NumberFormatException();
    } catch (IllegalArgumentException | ArithmeticException e) {
      throw Problem.invalid(
          "Giá/tồn kho không hợp lệ. Giá không âm, tối đa 2 số lẻ; tồn kho là số nguyên không âm.");
    }
    store.tx(
        d -> {
          var category = d.category(categoryId);
          if (category == null) throw Problem.invalid("Danh mục không tồn tại.");
          Product p = id == null ? new Product() : d.product(id);
          if (p == null) throw Problem.missing();
          if (id == null) p.createdAt = clock.instant();
          p.updatedAt = clock.instant();
          p.category = category;
          p.title = name;
          p.author = writer;
          p.description = detail;
          p.price = price;
          p.stock = stock;
          p.image = image;
          d.saveProduct(p);
          return null;
        });
  }

  /** Admin edits bibliographic data only; old columns await a reviewed import. */
  public void saveDetails(Long id, long categoryId, String title, String author, String description, String image) {
    store.tx(d -> {
      Product previous = id == null ? null : d.product(id);
      if (id != null && previous == null) throw Problem.missing();
      save(id, categoryId, title, author, description,
          previous == null ? "0" : previous.price.toPlainString(), previous == null ? "0" : Integer.toString(previous.stock), image);
      return null;
    });
  }

  public void delete(long id) {
    store.tx(
        d -> {
          if (d.product(id) == null) throw Problem.missing();
          if (d.copyCountForProduct(id) > 0)
            throw new Problem(409, "Đầu sách đã có bản sách trong kho. Hãy ngừng bán từng bản, không xóa đầu sách.");
          d.deleteProduct(id);
          return null;
        });
  }

  private static String text(String raw, int max, String label) {
    if (raw == null || raw.trim().isBlank() || raw.trim().length() > max)
      throw Problem.invalid(label + " cần từ 1 đến " + max + " ký tự.");
    return raw.trim();
  }
}
