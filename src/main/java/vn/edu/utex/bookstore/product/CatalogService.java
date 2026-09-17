package vn.edu.utex.bookstore.product;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.persistence.Store;

/** Public catalog uses only available physical copies in active shops. */
public final class CatalogService {
  private static final int PAGE_SIZE = 20;
  private static final BigDecimal MAXIMUM_PRICE = new BigDecimal("9999999999.99");
  private static final Set<String> CONDITIONS = Set.of("LIKE_NEW", "GOOD", "ACCEPTABLE");
  private static final Pattern SHOP_ID = Pattern.compile("(?:^|.*-)([1-9][0-9]*)$");

  public record Criteria(String query, Long categoryId, BigDecimal minimumPrice,
      BigDecimal maximumPrice, String condition, Long shopId) {
    CatalogRepository.Filter repositoryFilter() {
      return new CatalogRepository.Filter(query, categoryId == null ? 0 : categoryId,
          minimumPrice == null ? BigDecimal.ZERO : minimumPrice,
          maximumPrice == null ? MAXIMUM_PRICE : maximumPrice,
          condition == null ? "" : condition, shopId == null ? 0 : shopId);
    }
  }
  public record Page(List<ProductView> items, int number, int pages, long total, Criteria filters) {
    public String query() { return filters.query(); }
  }
  public record Offer(long id, long shopId, String shopName, String shopSlug, String code,
      String condition, String defects, BigDecimal price) {}
  public record Detail(ProductView product, List<Offer> offers, int number, int pages) {}
  public record PublicShop(long id, String name, String address, String slug) {}
  public record ShopPage(PublicShop shop, Page catalog) {}

  private final Store store;
  public CatalogService(Store store) { this.store = store; }

  public List<ProductView> latest() {
    Criteria criteria = criteria("", null, null, null, null, null);
    return store.tx(data -> views(data,
        data.catalogProducts(criteria.repositoryFilter(), 0, PAGE_SIZE), criteria.repositoryFilter()));
  }

  public Page page(String rawPage, String rawQuery) {
    return page(rawPage, rawQuery, null, null, null, null, null);
  }

  public Page page(String rawPage, String rawQuery, String rawCategory, String rawMinimum,
      String rawMaximum, String rawCondition, String rawShop) {
    return page(number(rawPage), criteria(rawQuery, rawCategory, rawMinimum, rawMaximum, rawCondition, rawShop));
  }

  public Detail detail(long id, String rawPage) {
    int number = number(rawPage);
    CatalogRepository.Filter allOffers = criteria("", null, null, null, null, null).repositoryFilter();
    return store.tx(data -> {
      Product product = data.product(id);
      if (product == null || !product.category.active) throw Problem.missing();
      ProductView view = views(data, List.of(product), allOffers).getFirst();
      int pages = pages(view.stock(), PAGE_SIZE);
      if (number > pages) throw Problem.missing();
      List<Offer> offers = data.catalogOffers(id, Math.multiplyExact(number - 1, PAGE_SIZE), PAGE_SIZE)
          .stream().map(offer -> new Offer(offer.id(), offer.shopId(), offer.shopName(),
              shopSlug(offer.shopId(), offer.shopName()), offer.code(), offer.condition(),
              offer.defects(), offer.price())).toList();
      return new Detail(view, offers, number, pages);
    });
  }

  public List<PublicShop> shops() {
    return store.tx(data -> data.catalogShops().stream().map(this::publicShop).toList());
  }

  public ShopPage shop(String rawSlug, String rawPage) {
    long id = shopId(rawSlug);
    PublicShop shop = store.tx(data -> {
      var option = data.catalogShop(id);
      if (option == null) throw Problem.missing();
      return publicShop(option);
    });
    Criteria criteria = criteria("", null, null, null, null, Long.toString(id));
    return new ShopPage(shop, page(number(rawPage), criteria));
  }

  public static String shopSlug(long id, String name) { return slug(name) + "-" + id; }

  private Page page(int number, Criteria criteria) {
    CatalogRepository.Filter filter = criteria.repositoryFilter();
    return store.tx(data -> {
      long total = data.catalogCount(filter);
      int pages = pages(total, PAGE_SIZE);
      if (number > pages) throw Problem.missing();
      List<Product> products = data.catalogProducts(filter,
          Math.multiplyExact(number - 1, PAGE_SIZE), PAGE_SIZE);
      return new Page(views(data, products, filter), number, pages, total, criteria);
    });
  }

  private Criteria criteria(String rawQuery, String rawCategory, String rawMinimum,
      String rawMaximum, String rawCondition, String rawShop) {
    String query = rawQuery == null ? "" : rawQuery.strip();
    if (query.length() > 100) throw Problem.invalid("Từ khóa tối đa 100 ký tự.");
    Long category = optionalId(rawCategory, "Danh mục không hợp lệ.");
    Long shop = optionalId(rawShop, "Cửa hàng không hợp lệ.");
    BigDecimal minimum = price(rawMinimum, "Giá thấp nhất không hợp lệ.");
    BigDecimal maximum = price(rawMaximum, "Giá cao nhất không hợp lệ.");
    if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0)
      throw Problem.invalid("Giá thấp nhất không được lớn hơn giá cao nhất.");
    String condition = rawCondition == null ? "" : rawCondition.strip();
    if (!condition.isEmpty() && !CONDITIONS.contains(condition))
      throw Problem.invalid("Tình trạng sách không hợp lệ.");
    return new Criteria(query, category, minimum, maximum, condition, shop);
  }

  private List<ProductView> views(Store.Data data, List<Product> products,
      CatalogRepository.Filter filter) {
    if (products.isEmpty()) return List.of();
    var stock = data.catalogStock(products.stream().map(product -> product.id).toList(), filter);
    return products.stream().map(product -> {
      var current = stock.get(product.id);
      return ProductView.from(product, current == null ? null : current.minimumPrice(),
          current == null ? 0 : Math.toIntExact(current.available()));
    }).toList();
  }

  private PublicShop publicShop(CatalogRepository.ShopOption shop) {
    return new PublicShop(shop.id(), shop.name(), shop.address(), shopSlug(shop.id(), shop.name()));
  }

  private static int number(String raw) {
    try {
      int number = raw == null || raw.isBlank() ? 1 : Integer.parseInt(raw);
      if (number < 1) throw new NumberFormatException();
      return number;
    } catch (NumberFormatException exception) {
      throw Problem.invalid("Số trang phải là số nguyên dương.");
    }
  }

  private static Long optionalId(String raw, String message) {
    if (raw == null || raw.isBlank()) return null;
    try {
      long id = Long.parseLong(raw);
      if (id < 1) throw new NumberFormatException();
      return id;
    } catch (NumberFormatException exception) {
      throw Problem.invalid(message);
    }
  }

  private static BigDecimal price(String raw, String message) {
    if (raw == null || raw.isBlank()) return null;
    try {
      BigDecimal value = new BigDecimal(raw.strip());
      if (value.signum() < 0 || value.compareTo(MAXIMUM_PRICE) > 0 || value.scale() > 2)
        throw new NumberFormatException();
      return value;
    } catch (NumberFormatException exception) {
      throw Problem.invalid(message);
    }
  }

  private static long shopId(String slug) {
    var matcher = SHOP_ID.matcher(slug == null ? "" : slug.strip());
    if (!matcher.matches()) throw Problem.missing();
    try { return Long.parseLong(matcher.group(1)); }
    catch (NumberFormatException exception) { throw Problem.missing(); }
  }

  private static String slug(String value) {
    String ascii = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
        .replace("đ", "d").replace("Đ", "D").replaceAll("\\p{M}+", "");
    String result = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-+|-+$)", "");
    return result.isBlank() ? "cua-hang" : result;
  }

  private static int pages(long count, int size) {
    return Math.toIntExact(Math.max(1, (count + size - 1) / size));
  }
}
