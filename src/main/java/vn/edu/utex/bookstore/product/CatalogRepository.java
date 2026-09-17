package vn.edu.utex.bookstore.product;

import java.math.BigDecimal;
import java.util.*;

public interface CatalogRepository {
  record Filter(String query, long categoryId, BigDecimal minimumPrice, BigDecimal maximumPrice,
      String condition, long shopId) {}
  record Stock(long productId, long available, BigDecimal minimumPrice) {}
  record Offer(long id, long shopId, String shopName, String code, String condition, String defects, BigDecimal price) {}
  record ShopOption(long id, String name, String address) {}
  List<Product> catalogProducts(Filter filter, int offset, int limit);
  long catalogCount(Filter filter);
  Map<Long, Stock> catalogStock(List<Long> productIds, Filter filter);
  List<Offer> catalogOffers(long productId, int offset, int limit);
  List<ShopOption> catalogShops();
  ShopOption catalogShop(long shopId);
}
