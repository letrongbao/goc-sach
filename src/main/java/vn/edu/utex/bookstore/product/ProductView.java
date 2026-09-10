package vn.edu.utex.bookstore.product;

import java.math.BigDecimal;

public record ProductView(
    long id,
    long categoryId,
    String categoryName,
    String title,
    String author,
    String description,
    BigDecimal price,
    int stock,
    String image,
    String isbn,
    String publisher,
    Integer publishedYear,
    String language) {
  public static ProductView from(Product p) {
    return from(p, null, 0);
  }

  public static ProductView from(Product p, BigDecimal price, int stock) {
    return new ProductView(
        p.id,
        p.category.id,
        p.category.name,
        p.title,
        p.author,
        p.description,
        price,
        stock,
        p.image,
        p.isbn,
        p.publisher,
        p.publishedYear,
        p.language);
  }

  public long getId() {
    return id;
  }

  public long getCategoryId() {
    return categoryId;
  }

  public String getCategoryName() {
    return categoryName;
  }

  public String getTitle() {
    return title;
  }

  public String getAuthor() {
    return author;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public int getStock() {
    return stock;
  }

  public String getImage() {
    return image;
  }
  public String getIsbn() { return isbn; }
  public String getPublisher() { return publisher; }
  public Integer getPublishedYear() { return publishedYear; }
  public String getLanguage() { return language; }
}
