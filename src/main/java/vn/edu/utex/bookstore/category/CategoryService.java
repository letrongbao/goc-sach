package vn.edu.utex.bookstore.category;

import java.util.*;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.persistence.Store;

public class CategoryService {
  private final Store store;

  public CategoryService(Store store) {
    this.store = store;
  }

  public List<Category> list(String query) {
    return store.tx(d -> d.categories(query == null ? "" : query.trim()));
  }

  public Category get(long id) {
    return store.tx(
        d -> {
          Category c = d.category(id);
          if (c == null) throw Problem.missing();
          return c;
        });
  }

  public void save(Long id, String name, String image, boolean active) {
    if (name == null || name.trim().isEmpty() || name.trim().length() > 100)
      throw Problem.invalid("Tên danh mục cần từ 1 đến 100 ký tự.");
    store.tx(
        d -> {
          Category c = id == null ? new Category() : d.category(id, true);
          if (c == null) throw Problem.missing();
          c.name = name.trim();
          c.image = image;
          c.active = active;
          d.saveCategory(c);
          return null;
        });
  }

  public void delete(long id) {
    store.tx(
        d -> {
          if (d.category(id, true) == null) throw Problem.missing();
          if (d.productCountInCategory(id) > 0)
            throw new Problem(409, "Danh mục đang có sản phẩm. Chuyển hoặc xóa sản phẩm trước.");
          d.deleteCategory(id);
          return null;
        });
  }
}
