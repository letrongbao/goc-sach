package vn.edu.utex.bookstore;

import java.util.*;
import java.util.function.Function;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.category.*;
import vn.edu.utex.bookstore.persistence.Store;
import vn.edu.utex.bookstore.shop.*;
import vn.edu.utex.bookstore.inventory.*;
import vn.edu.utex.bookstore.product.CatalogRepository;

public class MemoryStore implements Store, Store.Data {
  private boolean sellable(BookCopy c) {
    return "AVAILABLE".equals(c.status) && !"UNCLASSIFIED".equals(c.condition)
        && shops.containsKey(c.shopId) && "ACTIVE".equals(shops.get(c.shopId).status)
        && products.containsKey(c.productId) && products.get(c.productId).category.active;
  }
  public List<vn.edu.utex.bookstore.product.Product> catalogProducts(String query, int offset, int limit) {
    String q = query.toLowerCase(Locale.ROOT);
    return products(0, Integer.MAX_VALUE).stream().filter(p -> p.category.active
        && (p.title.toLowerCase(Locale.ROOT).contains(q) || p.author.toLowerCase(Locale.ROOT).contains(q))
        && copies.values().stream().anyMatch(c -> c.productId == p.id && sellable(c)))
        .skip(offset).limit(limit).toList();
  }
  public long catalogCount(String query) { return catalogProducts(query, 0, Integer.MAX_VALUE).size(); }
  public Map<Long, CatalogRepository.Stock> catalogStock(List<Long> ids) {
    Map<Long, CatalogRepository.Stock> stock = new HashMap<>();
    for (BookCopy c : copies.values()) if (ids.contains(c.productId) && sellable(c)) {
      var previous = stock.get(c.productId);
      stock.put(c.productId, new CatalogRepository.Stock(c.productId, previous == null ? 1 : previous.available() + 1,
          previous == null ? c.price : previous.minimumPrice().min(c.price)));
    }
    return stock;
  }
  public List<CatalogRepository.Offer> catalogOffers(long id, int offset, int limit) {
    return copies.values().stream().filter(c -> c.productId == id && sellable(c))
        .sorted(Comparator.comparing((BookCopy c) -> c.price).thenComparing(c -> c.id)).skip(offset).limit(limit)
        .map(c -> new CatalogRepository.Offer(c.id, c.shopId, shops.get(c.shopId).name, c.code, c.condition, c.defects, c.price)).toList();
  }
  public final Map<Long, BookCopy> copies = new LinkedHashMap<>();
  public final List<InventoryMovement> movements = new ArrayList<>();
  private long nextCopy = 1;
  public BookCopy copy(long id, boolean lock) { return copies.get(id); }
  public BookCopy saveCopy(BookCopy copy) {
    if (copy.id == null) copy.id = nextCopy++; else copy.version++;
    copies.put(copy.id, copy); return copy;
  }
  public List<BookCopy> copies(long shopId, int offset, int limit) {
    return copies.values().stream().filter(c -> c.shopId == shopId)
        .sorted(Comparator.comparing((BookCopy c) -> c.createdAt).thenComparing(c -> c.id).reversed())
        .skip(offset).limit(limit).toList();
  }
  public long copyCount(long shopId) { return copies.values().stream().filter(c -> c.shopId == shopId).count(); }
  public long copyCountForProduct(long productId) { return copies.values().stream().filter(c -> c.productId == productId).count(); }
  public void recordMovement(InventoryMovement m) { m.id = (long) movements.size() + 1; movements.add(m); }
  public List<InventoryMovement> movements(long copyId) {
    return movements.stream().filter(m -> m.copyId == copyId)
        .sorted(Comparator.comparing((InventoryMovement m) -> m.createdAt).thenComparing(m -> m.id).reversed()).limit(100).toList();
  }
  public final Map<Long, Shop> shops = new LinkedHashMap<>();
  public final Map<Long, ShopApplication> applications = new LinkedHashMap<>();
  public final Map<Long, ShopMembership> memberships = new LinkedHashMap<>();
  public final List<ShopAuditEvent> shopEvents = new ArrayList<>();
  private long nextShop = 1, nextApplication = 1, nextMembership = 1;

  public Shop shop(long id, boolean lock) { return shops.get(id); }
  public List<Shop> shopsFor(long userId, boolean all) {
    return shops.values().stream().filter(shop -> all || memberships.values().stream()
        .anyMatch(member -> member.shopId == shop.id && member.userId == userId && member.active))
        .sorted(Comparator.comparing((Shop shop) -> shop.id).reversed()).toList();
  }
  public Shop saveShop(Shop shop) {
    if (shop.id == null) shop.id = nextShop++;
    shops.put(shop.id, shop);
    return shop;
  }
  public ShopApplication application(long id, boolean lock) { return applications.get(id); }
  public ShopApplication pendingApplication(long userId) {
    return applications.values().stream().filter(a -> a.applicantId == userId && "PENDING".equals(a.status)).findFirst().orElse(null);
  }
  public List<ShopApplication> applicationsFor(long userId, boolean all) {
    return applications.values().stream().filter(a -> all || a.applicantId == userId)
        .sorted(Comparator.comparing((ShopApplication a) -> a.id).reversed()).toList();
  }
  public ShopApplication saveApplication(ShopApplication application) {
    if (application.id == null) application.id = nextApplication++;
    applications.put(application.id, application);
    return application;
  }
  public ShopMembership membership(long shopId, long userId) {
    return memberships.values().stream().filter(m -> m.shopId == shopId && m.userId == userId).findFirst().orElse(null);
  }
  public List<ShopMembership> members(long shopId) {
    return memberships.values().stream().filter(m -> m.shopId == shopId).toList();
  }
  public void saveMembership(ShopMembership member) {
    if (member.id == null) member.id = nextMembership++;
    memberships.put(member.id, member);
  }
  public void recordShopEvent(ShopAuditEvent event) { shopEvents.add(event); }
  public final Map<Long, User> users = new LinkedHashMap<>();
  public final Map<Long, Category> categories = new LinkedHashMap<>();
  public final Map<String, RememberToken> tokens = new HashMap<>();
  private long nextUser = 1, nextCategory = 1;
  private long nextProduct = 1;
  public final Map<Long, vn.edu.utex.bookstore.product.Product> products = new LinkedHashMap<>();
  public final Map<String, OtpChallenge> challenges = new HashMap<>();

  public synchronized <T> T tx(Function<Store.Data, T> action) {
    return action.apply(this);
  }

  public User user(long id) {
    return users.get(id);
  }

  public User lockUser(long id) {
    return users.get(id);
  }

  public User findLogin(String login) {
    return users.values().stream()
        .filter(u -> u.username.equals(login) || u.email.equals(login))
        .findFirst()
        .orElse(null);
  }

  public User saveUser(User u) {
    if (u.id == null) u.id = nextUser++;
    users.put(u.id, u);
    return u;
  }

  public Category category(long id) {
    return categories.get(id);
  }

  public List<Category> categories(String q) {
    return categories.values().stream()
        .filter(c -> c.name.toLowerCase().contains(q.toLowerCase()))
        .sorted(Comparator.comparing((Category c) -> c.id).reversed())
        .toList();
  }

  public Category saveCategory(Category c) {
    if (c.id == null) c.id = nextCategory++;
    categories.put(c.id, c);
    return c;
  }

  public void deleteCategory(long id) {
    categories.remove(id);
  }

  public RememberToken token(String hash) {
    return tokens.get(hash);
  }

  public void saveToken(RememberToken token) {
    tokens.put(token.tokenHash, token);
  }

  public void deleteToken(String hash) {
    tokens.remove(hash);
  }

  public void deleteUserTokens(long id) {
    tokens.values().removeIf(t -> t.userId == id);
  }

  public OtpChallenge challenge(long userId, String purpose) {
    return challenges.values().stream()
        .filter(c -> c.userId == userId && c.purpose.equals(purpose))
        .findFirst()
        .orElse(null);
  }

  public void saveChallenge(OtpChallenge c) {
    challenges.put(c.id, c);
  }

  public vn.edu.utex.bookstore.product.Product product(long id) {
    return products.get(id);
  }

  public List<vn.edu.utex.bookstore.product.Product> products(int offset, int limit) {
    return products.values().stream()
        .sorted(
            Comparator.comparing((vn.edu.utex.bookstore.product.Product p) -> p.createdAt)
                .thenComparing(p -> p.id)
                .reversed())
        .skip(offset)
        .limit(limit)
        .toList();
  }

  public long productCount() {
    return products.size();
  }

  public long productCountInCategory(long id) {
    return products.values().stream().filter(p -> p.category.id == id).count();
  }

  public vn.edu.utex.bookstore.product.Product saveProduct(
      vn.edu.utex.bookstore.product.Product p) {
    if (p.id == null) p.id = nextProduct++;
    products.put(p.id, p);
    return p;
  }

  public void deleteProduct(long id) {
    products.remove(id);
  }
}
