package vn.edu.utex.bookstore.product;

import java.math.BigDecimal;
import java.util.*;

public interface CatalogRepository {
  record Stock(long productId, long available, BigDecimal minimumPrice) {}
  record Offer(long id, long shopId, String shopName, String code, String condition, String defects, BigDecimal price) {}
  List<Product> catalogProducts(String query, int offset, int limit);
  long catalogCount(String query);
  Map<Long, Stock> catalogStock(List<Long> productIds);
  List<Offer> catalogOffers(long productId, int offset, int limit);
}
