package vn.edu.utex.bookstore.buyer;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.common.FormProblem;
import vn.edu.utex.bookstore.inventory.BookCopy;
import vn.edu.utex.bookstore.persistence.Store;

/** Account-scoped buyer features. Browser/API layers never receive another user's rows. */
public final class BuyerService {
  public record CartLine(long copyId, long productId, long shopId, String title, String shopName,
      String shopSlug, String author, String image, String condition, BigDecimal price, boolean sellable) {}
  public record CartGroup(long shopId, String shopName, String shopSlug, List<CartLine> lines,
      BigDecimal subtotal, boolean ready) {}
  public record BookListItem(long productId, long categoryId, String categoryName, String title,
      String author, String image, BigDecimal price, int stock) {}
  private static final vn.edu.utex.bookstore.product.CatalogRepository.Filter ALL_OFFERS =
      new vn.edu.utex.bookstore.product.CatalogRepository.Filter("", 0, BigDecimal.ZERO,
          new BigDecimal("9999999999.99"), "", 0);
  private final Store store;
  private final Clock clock;

  public BuyerService(Store store, Clock clock) { this.store = store; this.clock = clock; }

  public CustomerProfile profile(long userId) {
    return store.tx(d -> {
      CustomerProfile p = d.profile(userId);
      if (p != null) return p;
      p = new CustomerProfile(); p.userId = userId; p.updatedAt = clock.instant();
      return d.saveProfile(p);
    });
  }

  public CustomerProfile saveProfile(long userId, String displayName, String phone) {
    String name = text(displayName, 120, "Tên hiển thị");
    String mobile = phone == null ? "" : phone.trim();
    if (mobile.length() > 30) throw Problem.invalid("Số điện thoại tối đa 30 ký tự.");
    return store.tx(d -> {
      CustomerProfile p = d.profile(userId);
      if (p == null) { p = new CustomerProfile(); p.userId = userId; }
      p.displayName = name; p.phone = mobile; p.updatedAt = clock.instant();
      return d.saveProfile(p);
    });
  }

  public List<Address> addresses(long userId) { return store.tx(d -> d.addresses(userId)); }

  public Address address(long userId, long id) {
    return store.tx(d -> {
      Address address = d.address(id);
      if (address == null || address.userId != userId) throw Problem.missing();
      return address;
    });
  }

  public Address saveAddress(long userId, Long id, String label, String recipient, String phone,
      String line1, String ward, String district, String city, boolean makeDefault) {
    String tag = fieldText(label, 60, "label", "Tên gợi nhớ"),
        person = fieldText(recipient, 120, "recipientName", "Tên người nhận"),
        mobile = fieldText(phone, 30, "phone", "Số điện thoại"),
        street = fieldText(line1, 250, "line1", "Địa chỉ"),
        area = fieldText(city, 100, "city", "Tỉnh/thành phố");
    String wardValue = ward == null ? "" : ward.trim(), districtValue = district == null ? "" : district.trim();
    if (wardValue.length() > 100)
      throw new FormProblem("ward", "Phường/xã tối đa 100 ký tự.");
    if (districtValue.length() > 100)
      throw new FormProblem("district", "Quận/huyện tối đa 100 ký tự.");
    return store.tx(d -> {
      Address a = id == null ? new Address() : d.address(id);
      if (a == null || (id != null && a.userId != userId)) throw Problem.missing();
      if (id == null) a.createdAt = clock.instant();
      a.userId = userId; a.label = tag; a.recipientName = person; a.phone = mobile; a.line1 = street;
      a.ward = wardValue; a.district = districtValue; a.city = area; a.updatedAt = clock.instant();
      if (makeDefault || d.addresses(userId).isEmpty()) { d.clearDefaultAddresses(userId); a.defaultAddress = true; }
      return d.saveAddress(a);
    });
  }

  public void deleteAddress(long userId, long id) {
    store.tx(d -> {
      Address address = d.address(id);
      if (address == null || address.userId != userId) throw Problem.missing();
      boolean wasDefault = address.defaultAddress;
      d.deleteAddress(id);
      if (wasDefault) {
        List<Address> remaining = d.addresses(userId);
        if (!remaining.isEmpty()) {
          Address replacement = remaining.getFirst();
          replacement.defaultAddress = true;
          replacement.updatedAt = clock.instant();
          d.saveAddress(replacement);
        }
      }
      return null;
    });
  }

  public List<Favorite> favorites(long userId) { return store.tx(d -> d.favorites(userId)); }
  public boolean isFavorite(long userId, long productId) {
    return store.tx(d -> d.favorite(userId, productId) != null);
  }
  public void addFavorite(long userId, long productId) {
    store.tx(d -> { if (!visible(d, productId)) throw Problem.missing(); if (d.favorite(userId, productId) == null) { Favorite f = new Favorite(); f.userId=userId; f.productId=productId; f.createdAt=clock.instant(); d.saveFavorite(f); } return null; });
  }
  public void removeFavorite(long userId, long productId) { store.tx(d -> { d.deleteFavorite(userId, productId); return null; }); }

  public void recordViewed(long userId, long productId) {
    store.tx(d -> { if (!visible(d, productId)) throw Problem.missing(); RecentlyViewed v=d.viewed(userId, productId); if(v==null){v=new RecentlyViewed();v.userId=userId;v.productId=productId;} v.viewedAt=clock.instant(); d.saveViewed(v); return null; });
  }
  public List<RecentlyViewed> recentlyViewed(long userId, int limit) { int n = Math.max(1, Math.min(limit, 50)); return store.tx(d -> d.recentlyViewed(userId, n)); }
  public List<BookListItem> favoriteBooks(long userId) {
    return store.tx(d -> books(d, d.favorites(userId).stream().map(f -> f.productId).toList()));
  }
  public List<BookListItem> recentlyViewedBooks(long userId, int limit) {
    int n = Math.max(1, Math.min(limit, 50));
    return store.tx(d -> books(d, d.recentlyViewed(userId, n).stream().map(v -> v.productId).toList()));
  }

  public void addToCart(long userId, long copyId) {
    store.tx(d -> {
      if (d.lockUser(userId) == null) throw Problem.missing();
      BookCopy copy = d.copy(copyId, true);
      if (!sellable(d, copy)) throw new Problem(409, "Bản sách không còn sẵn bán.");
      Cart cart = d.cart(userId); if (cart == null) { cart=new Cart(); cart.userId=userId; }
      cart.updatedAt=clock.instant(); cart=d.saveCart(cart);
      if (d.cartItem(cart.id, copyId) == null) { CartItem item=new CartItem(); item.cartId=cart.id; item.copyId=copyId; item.unitPrice=copy.price; item.addedAt=clock.instant(); d.saveCartItem(item); }
      return null;
    });
  }
  public void removeFromCart(long userId, long copyId) { store.tx(d -> { if (d.lockUser(userId) == null) throw Problem.missing(); Cart c=d.cart(userId); if(c!=null){d.deleteCartItem(c.id,copyId);c.updatedAt=clock.instant();d.saveCart(c);} return null; }); }
  public List<CartLine> cart(long userId) {
    return store.tx(d -> {
      Cart cart = d.cart(userId);
      if (cart == null) return List.of();
      return d.cartItems(cart.id).stream().map(item -> {
        BookCopy copy = d.copy(item.copyId, false);
        if (copy == null) return null;
        var product = d.product(copy.productId);
        var shop = d.shop(copy.shopId, false);
        return new CartLine(copy.id, copy.productId, copy.shopId,
            product == null ? "Sách không còn tồn tại" : product.title,
            shop == null ? "Cửa hàng không còn tồn tại" : shop.name,
            shop == null ? "" : vn.edu.utex.bookstore.product.CatalogService.shopSlug(shop.id, shop.name),
            product == null ? "" : product.author, product == null ? "" : product.image,
            copy.condition, copy.price, sellable(d, copy));
      }).filter(java.util.Objects::nonNull).toList();
    });
  }

  public List<CartGroup> groupCart(List<CartLine> lines) {
    Map<Long, List<CartLine>> grouped = new LinkedHashMap<>();
    for (CartLine line : lines) grouped.computeIfAbsent(line.shopId(), ignored -> new ArrayList<>()).add(line);
    return grouped.values().stream().map(group -> new CartGroup(group.getFirst().shopId(),
        group.getFirst().shopName(), group.getFirst().shopSlug(), List.copyOf(group),
        group.stream().map(CartLine::price).reduce(BigDecimal.ZERO, BigDecimal::add),
        group.stream().allMatch(CartLine::sellable))).toList();
  }

  private static List<BookListItem> books(Store.Data d, List<Long> ids) {
    List<vn.edu.utex.bookstore.product.Product> products = new ArrayList<>();
    for (long id : new LinkedHashSet<>(ids)) {
      var product = d.product(id);
      if (product != null && product.category != null && product.category.active) products.add(product);
    }
    if (products.isEmpty()) return List.of();
    var stock = d.catalogStock(products.stream().map(product -> product.id).toList(), ALL_OFFERS);
    return products.stream().map(product -> {
      var current = stock.get(product.id);
      return new BookListItem(product.id, product.category.id, product.category.name, product.title,
          product.author, product.image, current == null ? null : current.minimumPrice(),
          current == null ? 0 : Math.toIntExact(current.available()));
    }).toList();
  }

  private static boolean visible(Store.Data d, long productId) {
    var product = d.product(productId);
    return product != null && product.category != null && product.category.active;
  }
  private static boolean sellable(Store.Data d, BookCopy c) { return c != null && "AVAILABLE".equals(c.status) && !"UNCLASSIFIED".equals(c.condition) && d.shop(c.shopId, false) != null && "ACTIVE".equals(d.shop(c.shopId, false).status) && d.product(c.productId) != null && d.product(c.productId).category.active; }
  private static String text(String raw, int max, String label) { if(raw==null||raw.trim().isBlank()||raw.trim().length()>max) throw Problem.invalid(label+" cần từ 1 đến "+max+" ký tự."); return raw.trim(); }
  private static String fieldText(String raw, int max, String field, String label) {
    if (raw == null || raw.trim().isBlank() || raw.trim().length() > max)
      throw new FormProblem(field, label + " cần từ 1 đến " + max + " ký tự.");
    return raw.trim();
  }
}
