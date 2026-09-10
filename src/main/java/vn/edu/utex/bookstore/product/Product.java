package vn.edu.utex.bookstore.product;

import java.time.Instant;
import vn.edu.utex.bookstore.category.Category;

public class Product {
  public Long id;
  public Category category;
  public String title, author, description, image, isbn, publisher, language;
  public Integer publishedYear;
  public Instant createdAt, updatedAt;

  public Product() {}
}
