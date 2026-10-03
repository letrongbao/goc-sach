package vn.edu.utex.bookstore;

import java.util.*;
import java.util.function.Function;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.category.*;
import vn.edu.utex.bookstore.persistence.Store;
import vn.edu.utex.bookstore.shop.*;
import vn.edu.utex.bookstore.inventory.*;
import vn.edu.utex.bookstore.product.CatalogRepository;
import vn.edu.utex.bookstore.buyer.*;
import vn.edu.utex.bookstore.order.*;
import vn.edu.utex.bookstore.review.*;

public class MemoryStore implements Store, Store.Data {
  private boolean sellable(BookCopy c) {
    return "AVAILABLE".equals(c.status) && !"UNCLASSIFIED".equals(c.condition)
        && shops.containsKey(c.shopId) && "ACTIVE".equals(shops.get(c.shopId).status)
        && products.containsKey(c.productId) && products.get(c.productId).category.active;
  }
  public List<vn.edu.utex.bookstore.product.Product> catalogProducts(CatalogRepository.Filter filter, int offset, int limit) {
    String q = filter.query().toLowerCase(Locale.ROOT);
    var stock = catalogStock(new ArrayList<>(products.keySet()), filter);
    Comparator<vn.edu.utex.bookstore.product.Product> newest = Comparator
        .comparing((vn.edu.utex.bookstore.product.Product p) -> p.createdAt).reversed()
        .thenComparing(p -> p.id, Comparator.reverseOrder());
    Comparator<vn.edu.utex.bookstore.product.Product> ordering = switch (filter.sort()) {
      case NEWEST -> newest;
      case PRICE_ASC -> Comparator.comparing((vn.edu.utex.bookstore.product.Product p) -> stock.get(p.id).minimumPrice()).thenComparing(newest);
      case PRICE_DESC -> Comparator.comparing((vn.edu.utex.bookstore.product.Product p) -> stock.get(p.id).minimumPrice()).reversed().thenComparing(newest);
      case FAVORITES -> Comparator.comparingLong((vn.edu.utex.bookstore.product.Product p) -> favoriteCount(p.id)).reversed().thenComparing(newest);
    };
    return products(0, Integer.MAX_VALUE).stream().filter(p -> p.category.active
        && (filter.categoryId() == 0 || p.category.id == filter.categoryId())
        && (p.title.toLowerCase(Locale.ROOT).contains(q) || p.author.toLowerCase(Locale.ROOT).contains(q))
        && stock.containsKey(p.id) && (!filter.favoritesOnly() || favoriteCount(p.id) > 0))
        .sorted(ordering).skip(offset).limit(limit).toList();
  }
  private long favoriteCount(long productId) {
    return favoriteRows.values().stream().filter(favorite -> favorite.productId == productId).count();
  }
  public long catalogCount(CatalogRepository.Filter filter) { return catalogProducts(filter, 0, Integer.MAX_VALUE).size(); }
  public Map<Long, CatalogRepository.Stock> catalogStock(List<Long> ids, CatalogRepository.Filter filter) {
    Map<Long, CatalogRepository.Stock> stock = new HashMap<>();
    for (BookCopy c : copies.values()) if (ids.contains(c.productId) && matches(c, filter)) {
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
  public List<CatalogRepository.ShopOption> catalogShops() {
    return shops.values().stream().filter(s -> "ACTIVE".equals(s.status)
        && copies.values().stream().anyMatch(c -> c.shopId == s.id && sellable(c)))
        .sorted(Comparator.comparing((Shop s) -> s.name).thenComparing(s -> s.id))
        .map(s -> new CatalogRepository.ShopOption(s.id, s.name, s.address)).toList();
  }
  public CatalogRepository.ShopOption catalogShop(long shopId) {
    Shop shop = shops.get(shopId);
    return shop == null || !"ACTIVE".equals(shop.status) ? null
        : new CatalogRepository.ShopOption(shop.id, shop.name, shop.address);
  }
  private boolean matches(BookCopy copy, CatalogRepository.Filter filter) {
    return sellable(copy) && (filter.shopId() == 0 || copy.shopId == filter.shopId())
        && (filter.condition().isEmpty() || filter.condition().equals(copy.condition))
        && copy.price.compareTo(filter.minimumPrice()) >= 0
        && copy.price.compareTo(filter.maximumPrice()) <= 0;
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

  public final Map<Long, CustomerProfile> profiles = new LinkedHashMap<>();
  public final Map<Long, Address> addresses = new LinkedHashMap<>();
  public final Map<Long, Favorite> favoriteRows = new LinkedHashMap<>();
  public final Map<Long, RecentlyViewed> viewedRows = new LinkedHashMap<>();
  public final Map<Long, Cart> carts = new LinkedHashMap<>();
  public final Map<Long, CartItem> cartItems = new LinkedHashMap<>();
  private long nextAddress=1, nextFavorite=1, nextViewed=1, nextCart=1, nextCartItem=1;
  public CustomerProfile profile(long userId){return profiles.get(userId);}
  public CustomerProfile saveProfile(CustomerProfile p){profiles.put(p.userId,p);return p;}
  public List<Address> addresses(long userId){return addresses.values().stream().filter(a->a.userId==userId).sorted(Comparator.comparing((Address a)->a.defaultAddress).reversed().thenComparing(a->a.id)).toList();}
  public Address address(long id){return addresses.get(id);}
  public Address saveAddress(Address a){if(a.id==null)a.id=nextAddress++;addresses.put(a.id,a);return a;}
  public void clearDefaultAddresses(long userId){addresses.values().stream().filter(a->a.userId==userId).forEach(a->a.defaultAddress=false);}
  public void deleteAddress(long id){addresses.remove(id);}
  public Favorite favorite(long userId,long productId){return favoriteRows.values().stream().filter(f->f.userId==userId&&f.productId==productId).findFirst().orElse(null);}
  public List<Favorite> favorites(long userId){return favoriteRows.values().stream().filter(f->f.userId==userId).sorted(Comparator.comparing((Favorite f)->f.createdAt).reversed().thenComparing(f->f.id,Comparator.reverseOrder())).toList();}
  public Favorite saveFavorite(Favorite f){if(f.id==null)f.id=nextFavorite++;favoriteRows.put(f.id,f);return f;}
  public void deleteFavorite(long userId,long productId){favoriteRows.values().removeIf(f->f.userId==userId&&f.productId==productId);}
  public RecentlyViewed viewed(long userId,long productId){return viewedRows.values().stream().filter(v->v.userId==userId&&v.productId==productId).findFirst().orElse(null);}
  public List<RecentlyViewed> recentlyViewed(long userId,int limit){return viewedRows.values().stream().filter(v->v.userId==userId).sorted(Comparator.comparing((RecentlyViewed v)->v.viewedAt).reversed().thenComparing(v->v.id,Comparator.reverseOrder())).limit(limit).toList();}
  public RecentlyViewed saveViewed(RecentlyViewed v){if(v.id==null)v.id=nextViewed++;viewedRows.put(v.id,v);return v;}
  public Cart cart(long userId){return carts.values().stream().filter(c->c.userId==userId).findFirst().orElse(null);}
  public Cart saveCart(Cart c){if(c.id==null)c.id=nextCart++;carts.put(c.id,c);return c;}
  public List<CartItem> cartItems(long cartId){return cartItems.values().stream().filter(i->i.cartId==cartId).sorted(Comparator.comparing(i->i.id)).toList();}
  public CartItem cartItem(long cartId,long copyId){return cartItems.values().stream().filter(i->i.cartId==cartId&&i.copyId==copyId).findFirst().orElse(null);}
  public CartItem saveCartItem(CartItem i){if(i.id==null)i.id=nextCartItem++;cartItems.put(i.id,i);return i;}
  public void deleteCartItem(long cartId,long copyId){cartItems.values().removeIf(i->i.cartId==cartId&&i.copyId==copyId);}
  public final Map<Long,Order> orders = new LinkedHashMap<>();
  public final Map<Long,OrderItem> orderItemRows = new LinkedHashMap<>();
  public final Map<Long,OrderStatusHistory> orderStatusRows = new LinkedHashMap<>();
  public final Map<Long,InventoryReservation> reservationRows = new LinkedHashMap<>();
  private long nextOrder=1,nextOrderItem=1,nextOrderStatus=1,nextReservation=1;
  public final Map<Long,CheckoutGroup> checkoutGroups = new LinkedHashMap<>(); private long nextCheckoutGroup=1;
  public CheckoutGroup checkoutGroup(long id,boolean lock){return checkoutGroups.get(id);}
  public CheckoutGroup saveCheckoutGroup(CheckoutGroup g){if(g.id==null)g.id=nextCheckoutGroup++;checkoutGroups.put(g.id,g);return g;}
  public Order orderByKey(long userId,String key){return ordersByKey(userId,key).stream().findFirst().orElse(null);}
  public List<Order> ordersByKey(long userId,String key){return orders.values().stream().filter(o->o.userId==userId&&key.equals(o.idempotencyKey)).sorted(Comparator.comparing(o->o.id)).toList();}
  public Order order(long id, boolean lock){return orders.get(id);}
  public Order saveOrder(Order o){if(o.id==null)o.id=nextOrder++;orders.put(o.id,o);return o;}
  public OrderItem saveOrderItem(OrderItem i){if(i.id==null)i.id=nextOrderItem++;orderItemRows.put(i.id,i);return i;}
  public OrderStatusHistory saveOrderStatus(OrderStatusHistory h){if(h.id==null)h.id=nextOrderStatus++;orderStatusRows.put(h.id,h);return h;}
  public InventoryReservation saveReservation(InventoryReservation r){if(r.id==null)r.id=nextReservation++;reservationRows.put(r.id,r);return r;}
  public List<InventoryReservation> reservationsForOrder(long orderId){return reservationRows.values().stream().filter(r->r.orderId==orderId).sorted(Comparator.comparing(r->r.id)).toList();}
  public List<Order> ordersFor(long userId){return orders.values().stream().filter(o->o.userId==userId).sorted(Comparator.comparing((Order o)->o.createdAt).reversed().thenComparing(o->o.id,Comparator.reverseOrder())).toList();}
  public List<Order> ordersForShop(long shopId){return orders.values().stream().filter(o->o.shopId==shopId).sorted(Comparator.comparing((Order o)->o.createdAt).reversed().thenComparing(o->o.id,Comparator.reverseOrder())).toList();}
  public List<Order> ordersForGroup(long groupId){return orders.values().stream().filter(o->o.checkoutGroupId==groupId).sorted(Comparator.comparing(o->o.id)).toList();}
  public List<OrderItem> orderItems(long orderId){return orderItemRows.values().stream().filter(i->i.orderId==orderId).sorted(Comparator.comparingLong((OrderItem i)->i.copyId).thenComparing(i->i.id)).toList();}
  public List<OrderStatusHistory> orderStatuses(long orderId){return orderStatusRows.values().stream().filter(h->h.orderId==orderId).sorted(Comparator.comparing((OrderStatusHistory h)->h.createdAt).thenComparing(h->h.id)).toList();}
  public final Map<Long,vn.edu.utex.bookstore.payment.PaymentAttempt> payments = new LinkedHashMap<>();
  public final Map<Long,vn.edu.utex.bookstore.payment.PaymentEvent> paymentEvents = new LinkedHashMap<>();
  private long nextPayment=1, nextPaymentEvent=1;
  public vn.edu.utex.bookstore.payment.PaymentAttempt paymentForOrder(long orderId, boolean lock) { return payments.values().stream().filter(p -> p.orderId==orderId).findFirst().orElse(null); }
  public vn.edu.utex.bookstore.payment.PaymentAttempt paymentByCode(long code) { return payments.values().stream().filter(p -> p.orderCode==code).findFirst().orElse(null); }
  public vn.edu.utex.bookstore.payment.PaymentAttempt savePayment(vn.edu.utex.bookstore.payment.PaymentAttempt p) { if(p.id==null)p.id=nextPayment++; payments.put(p.id,p); return p; }
  public vn.edu.utex.bookstore.payment.PaymentEvent paymentEvent(String reference) { return paymentEvents.values().stream().filter(e -> e.reference.equals(reference)).findFirst().orElse(null); }
  public vn.edu.utex.bookstore.payment.PaymentEvent savePaymentEvent(vn.edu.utex.bookstore.payment.PaymentEvent e) { if(e.id==null)e.id=nextPaymentEvent++; paymentEvents.put(e.id,e);return e; }
  public List<Long> dueOnlineOrders(java.time.Instant now,int limit) { return orders.values().stream().filter(o -> "PAYOS".equals(o.paymentMethod) && "PENDING".equals(o.status) && java.util.Set.of("UNPAID","PENDING").contains(o.paymentStatus) && o.paymentExpiresAt!=null && !o.paymentExpiresAt.isAfter(now)).sorted(Comparator.comparing((Order o)->o.paymentExpiresAt).thenComparing(o->o.id)).limit(limit).map(o->o.id).toList(); }
  public List<Long> openPaymentOrders(int limit, long afterOrderId) {
    return orders.values().stream().filter(o -> o.id > afterOrderId && "PAYOS".equals(o.paymentMethod) && "PENDING".equals(o.status))
        .filter(o -> {
          var payment = paymentForOrder(o.id, false);
          return java.util.Set.of("UNPAID", "PENDING").contains(o.paymentStatus) && payment != null
              && java.util.Set.of("CREATING", "PENDING", "PROCESSING").contains(payment.status)
              || java.util.Set.of("CANCELLED", "EXPIRED").contains(o.paymentStatus)
              && (payment == null || java.util.Set.of("CANCELLED", "EXPIRED").contains(payment.status));
        }).sorted(Comparator.comparing(o -> o.id)).limit(limit).map(o -> o.id).toList();
  }
  public final Map<Long,Review> reviews = new LinkedHashMap<>(); private long nextReview=1;
  public List<vn.edu.utex.bookstore.payment.PaymentEvent> paymentEvents(long attemptId) { return paymentEvents.values().stream().filter(e->e.attemptId==attemptId).toList(); }
  public List<vn.edu.utex.bookstore.payment.PaymentAttempt> paymentsNeedingReview(int limit) { return payments.values().stream().filter(p->"REVIEW_REQUIRED".equals(p.status)).limit(limit).toList(); }
  public boolean deliveredPurchase(long userId,long productId){return orders.values().stream().filter(o->o.userId==userId&&"COMPLETED".equals(o.status)).flatMap(o->orderItemRows.values().stream().filter(i->i.orderId==o.id)).anyMatch(i->i.productId==productId);}
  public long completedOrderFor(long userId,long productId){return orders.values().stream().filter(o->o.userId==userId&&"COMPLETED".equals(o.status)).filter(o->orderItemRows.values().stream().anyMatch(i->i.orderId==o.id&&i.productId==productId)).sorted(Comparator.comparing((Order o)->o.createdAt).reversed().thenComparing(o->o.id,Comparator.reverseOrder())).map(o->o.id).findFirst().orElse(0L);}
  public Review review(long userId,long productId){return reviews.values().stream().filter(r->r.userId==userId&&r.productId==productId).findFirst().orElse(null);}
  public Review saveReview(Review r){if(r.id==null)r.id=nextReview++;reviews.put(r.id,r);return r;}
  public List<Review> reviews(long productId){return reviews.values().stream().filter(r->r.productId==productId&&"PUBLISHED".equals(r.status)).sorted(Comparator.comparing((Review r)->r.createdAt).reversed().thenComparing(r->r.id,Comparator.reverseOrder())).toList();}
  public List<Review> reviews(long productId,int offset,int limit){return reviews(productId).stream().skip(offset).limit(limit).toList();}
  public ReviewRepository.ReviewStats reviewStats(long productId){var published=reviews(productId);return new ReviewRepository.ReviewStats(published.size(),published.stream().mapToInt(r->r.rating).average().orElse(0));}
}
