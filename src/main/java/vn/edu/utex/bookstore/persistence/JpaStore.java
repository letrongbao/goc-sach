package vn.edu.utex.bookstore.persistence;

import jakarta.persistence.*;
import java.util.*;
import java.util.function.Function;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.category.*;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.product.*;
import vn.edu.utex.bookstore.shop.*;
import vn.edu.utex.bookstore.inventory.*;
import vn.edu.utex.bookstore.buyer.*;
import vn.edu.utex.bookstore.order.*;
import vn.edu.utex.bookstore.review.*;

public class JpaStore implements Store {
  private final EntityManagerFactory factory;
  private final org.springframework.transaction.support.TransactionTemplate transactions;
  private final EntityManager shared;
  private final boolean ownsFactory;

  public JpaStore(EntityManagerFactory factory, org.springframework.transaction.PlatformTransactionManager manager) {
    this(factory, manager, false);
  }

  public JpaStore(EntityManagerFactory factory) {
    this(factory, new org.springframework.orm.jpa.JpaTransactionManager(factory), true);
  }

  private JpaStore(EntityManagerFactory factory, org.springframework.transaction.PlatformTransactionManager manager, boolean ownsFactory) {
    this.factory = factory;
    this.ownsFactory = ownsFactory;
    this.transactions = new org.springframework.transaction.support.TransactionTemplate(manager);
    this.shared = org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory);
  }

  @Override
  public <T> T tx(Function<Data, T> action) {
    try {
      return transactions.execute(status -> action.apply(new JpaData(shared)));
    } catch (RuntimeException e) {
      if (e instanceof Problem) throw e;
      for (Throwable cause = e; cause != null; cause = cause.getCause()) {
        if (cause instanceof java.sql.SQLException sql) {
          if ("23505".equals(sql.getSQLState()))
            throw uniqueViolation(e);
          if ("23503".equals(sql.getSQLState()))
            throw new Problem(409, "Dữ liệu đang được sử dụng, không thể xóa.");
        }
      }
      throw new IllegalStateException("Lỗi truy cập database.", e);
    }
  }

  private static Problem uniqueViolation(Throwable error) {
    String constraint = "";
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
        constraint = Objects.toString(violation.getConstraintName(), "").toLowerCase(Locale.ROOT);
        break;
      }
    }
    if (constraint.contains("users_username"))
      return new Problem(409, "Tên đăng nhập đã tồn tại.");
    if (constraint.contains("users_email"))
      return new Problem(409, "Email đã được sử dụng.");
    if (constraint.contains("categories_name"))
      return new Problem(409, "Tên danh mục đã tồn tại.");
    if (constraint.contains("products_code"))
      return new Problem(409, "Mã sách đã tồn tại.");
    if (constraint.contains("book_copies_code"))
      return new Problem(409, "Mã cuốn sách đã tồn tại.");
    if (constraint.contains("shops_slug"))
      return new Problem(409, "Đường dẫn cửa hàng đã tồn tại.");
    return new Problem(409, "Dữ liệu đã tồn tại hoặc thao tác này đã được xử lý.");
  }

  @Override
  public void close() {
    if (ownsFactory) factory.close();
  }

  private static final class JpaData implements Data {
    private final EntityManager em;

    JpaData(EntityManager em) {
      this.em = em;
    }

    public List<Product> catalogProducts(CatalogRepository.Filter filter, int offset, int limit) {
      return filtered(em.createQuery(CatalogQueries.PRODUCTS, Product.class), filter)
          .setFirstResult(offset).setMaxResults(limit).getResultList();
    }
    public long catalogCount(CatalogRepository.Filter filter) {
      return filtered(em.createQuery(CatalogQueries.COUNT, Long.class), filter).getSingleResult();
    }
    public Map<Long, CatalogRepository.Stock> catalogStock(List<Long> ids, CatalogRepository.Filter filter) {
      if (ids.isEmpty()) return Map.of();
      var rows = offerFiltered(em.createQuery(CatalogQueries.STOCK, Object[].class).setParameter("ids", ids), filter)
          .getResultList();
      Map<Long, CatalogRepository.Stock> result = new HashMap<>();
      for (Object[] row : rows) result.put((Long) row[0], new CatalogRepository.Stock((Long) row[0], (Long) row[1], (java.math.BigDecimal) row[2]));
      return result;
    }
    public List<CatalogRepository.Offer> catalogOffers(long productId, int offset, int limit) {
      return em.createQuery(CatalogQueries.OFFERS, Object[].class).setParameter("product", productId)
          .setFirstResult(offset).setMaxResults(limit).getResultList().stream()
          .map(r -> new CatalogRepository.Offer((Long) r[0], (Long) r[1], (String) r[2], (String) r[3],
              (String) r[4], (String) r[5], (java.math.BigDecimal) r[6])).toList();
    }
    public List<CatalogRepository.ShopOption> catalogShops() {
      return em.createQuery(CatalogQueries.SHOPS, Object[].class).getResultList().stream()
          .map(r -> new CatalogRepository.ShopOption((Long) r[0], (String) r[1], (String) r[2])).toList();
    }
    public CatalogRepository.ShopOption catalogShop(long shopId) {
      return em.createQuery(CatalogQueries.SHOP, Object[].class).setParameter("shop", shopId)
          .getResultStream().findFirst()
          .map(r -> new CatalogRepository.ShopOption((Long) r[0], (String) r[1], (String) r[2])).orElse(null);
    }
    private <T> TypedQuery<T> filtered(TypedQuery<T> query, CatalogRepository.Filter filter) {
      return query.setParameter("q", filter.query().toLowerCase(Locale.ROOT))
          .setParameter("category", filter.categoryId()).setParameter("shop", filter.shopId())
          .setParameter("condition", filter.condition()).setParameter("minimum", filter.minimumPrice())
          .setParameter("maximum", filter.maximumPrice());
    }
    private <T> TypedQuery<T> offerFiltered(TypedQuery<T> query, CatalogRepository.Filter filter) {
      return query.setParameter("shop", filter.shopId()).setParameter("condition", filter.condition())
          .setParameter("minimum", filter.minimumPrice()).setParameter("maximum", filter.maximumPrice());
    }

    public BookCopy copy(long id, boolean lock) {
      return lock ? em.find(BookCopy.class, id, LockModeType.PESSIMISTIC_WRITE) : em.find(BookCopy.class, id);
    }
    public BookCopy saveCopy(BookCopy copy) {
      if (copy.id == null) { em.persist(copy); return copy; }
      return em.merge(copy);
    }
    public List<BookCopy> copies(long shopId, int offset, int limit) {
      return em.createQuery("select c from BookCopy c where c.shopId=:shop order by c.createdAt desc,c.id desc", BookCopy.class)
          .setParameter("shop", shopId).setFirstResult(offset).setMaxResults(limit).getResultList();
    }
    public long copyCount(long shopId) {
      return em.createQuery("select count(c) from BookCopy c where c.shopId=:shop", Long.class)
          .setParameter("shop", shopId).getSingleResult();
    }
    public long copyCountForProduct(long productId) {
      return em.createQuery("select count(c) from BookCopy c where c.productId=:product", Long.class)
          .setParameter("product", productId).getSingleResult();
    }
    public void recordMovement(InventoryMovement movement) { em.persist(movement); }
    public List<InventoryMovement> movements(long copyId) {
      return em.createQuery("select m from InventoryMovement m where m.copyId=:copy order by m.createdAt desc,m.id desc", InventoryMovement.class)
          .setParameter("copy", copyId).setMaxResults(100).getResultList();
    }

    public Shop shop(long id, boolean lock) {
      return lock ? em.find(Shop.class, id, LockModeType.PESSIMISTIC_WRITE) : em.find(Shop.class, id);
    }

    public List<Shop> shopsFor(long userId, boolean all) {
      if (all) return em.createQuery("select s from Shop s order by s.id desc", Shop.class).getResultList();
      return em.createQuery("select s from Shop s where exists (select m.id from ShopMembership m "
          + "where m.shopId=s.id and m.userId=:user and m.active=true) order by s.id desc", Shop.class)
          .setParameter("user", userId).getResultList();
    }

    public Shop saveShop(Shop shop) {
      if (shop.id == null) { em.persist(shop); return shop; }
      return em.merge(shop);
    }

    public ShopApplication application(long id, boolean lock) {
      return lock ? em.find(ShopApplication.class, id, LockModeType.PESSIMISTIC_WRITE) : em.find(ShopApplication.class, id);
    }

    public ShopApplication pendingApplication(long userId) {
      return em.createQuery("select a from ShopApplication a where a.applicantId=:user and a.status='PENDING'", ShopApplication.class)
          .setParameter("user", userId).getResultStream().findFirst().orElse(null);
    }

    public List<ShopApplication> applicationsFor(long userId, boolean all) {
      if (all) return em.createQuery("select a from ShopApplication a order by a.id desc", ShopApplication.class).getResultList();
      return em.createQuery("select a from ShopApplication a where a.applicantId=:user order by a.id desc", ShopApplication.class)
          .setParameter("user", userId).getResultList();
    }

    public ShopApplication saveApplication(ShopApplication application) {
      if (application.id == null) { em.persist(application); return application; }
      return em.merge(application);
    }

    public ShopMembership membership(long shopId, long userId) {
      return em.createQuery("select m from ShopMembership m where m.shopId=:shop and m.userId=:user", ShopMembership.class)
          .setParameter("shop", shopId).setParameter("user", userId).getResultStream().findFirst().orElse(null);
    }

    public List<ShopMembership> members(long shopId) {
      return em.createQuery("select m from ShopMembership m where m.shopId=:shop order by m.id", ShopMembership.class)
          .setParameter("shop", shopId).getResultList();
    }

    public void saveMembership(ShopMembership member) {
      if (member.id == null) em.persist(member); else em.merge(member);
    }

    public void recordShopEvent(ShopAuditEvent event) { em.persist(event); }

    public User user(long id) {
      return em.find(User.class, id);
    }

    public User lockUser(long id) {
      return em.find(User.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    public User findLogin(String login) {
      return em.createQuery(
              "select u from User u where u.username = :login or u.email = :login", User.class)
          .setParameter("login", login)
          .getResultStream()
          .findFirst()
          .orElse(null);
    }

    public User saveUser(User u) {
      if (u.id == null) {
        em.persist(u);
        return u;
      }
      return em.merge(u);
    }

    public Category category(long id) {
      return em.find(Category.class, id);
    }

    public Category category(long id, boolean lock) {
      return lock ? em.find(Category.class, id, LockModeType.PESSIMISTIC_WRITE) : em.find(Category.class, id);
    }

    public List<Category> categories(String query) {
      return em.createQuery(
              "select c from Category c where locate(:q,lower(c.name)) > 0 order by c.id desc",
              Category.class)
          .setParameter("q", query.toLowerCase(Locale.ROOT))
          .getResultList();
    }

    public Category saveCategory(Category c) {
      if (c.id == null) {
        em.persist(c);
        return c;
      }
      return em.merge(c);
    }

    public void deleteCategory(long id) {
      Category c = category(id);
      if (c != null) em.remove(c);
    }

    public RememberToken token(String hash) {
      return em.find(RememberToken.class, hash);
    }

    public void saveToken(RememberToken token) {
      em.persist(token);
    }

    public void deleteToken(String hash) {
      em.createQuery("delete from RememberToken t where t.tokenHash=:hash")
          .setParameter("hash", hash)
          .executeUpdate();
    }

    public void deleteUserTokens(long id) {
      em.createQuery("delete from RememberToken t where t.userId=:id")
          .setParameter("id", id)
          .executeUpdate();
    }

    public OtpChallenge challenge(long userId, String purpose) {
      return em.createQuery(
              "select c from OtpChallenge c where c.userId=:user and c.purpose=:purpose",
              OtpChallenge.class)
          .setParameter("user", userId)
          .setParameter("purpose", purpose)
          .getResultStream()
          .findFirst()
          .orElse(null);
    }

    public void saveChallenge(OtpChallenge c) {
      em.merge(c);
    }

    public Product product(long id) {
      return em.createQuery(
              "select p from Product p join fetch p.category where p.id=:id", Product.class)
          .setParameter("id", id)
          .getResultStream()
          .findFirst()
          .orElse(null);
    }

    public List<Product> products(int offset, int limit) {
      return em.createQuery(
              "select p from Product p join fetch p.category order by p.createdAt desc,p.id desc",
              Product.class)
          .setFirstResult(offset)
          .setMaxResults(limit)
          .getResultList();
    }

    public long productCount() {
      return em.createQuery("select count(p) from Product p", Long.class).getSingleResult();
    }

    public long productCountInCategory(long id) {
      return em.createQuery("select count(p) from Product p where p.category.id=:id", Long.class)
          .setParameter("id", id)
          .getSingleResult();
    }

    public Product saveProduct(Product p) {
      if (p.id == null) {
        em.persist(p);
        return p;
      }
      return em.merge(p);
    }

    public void deleteProduct(long id) {
      Product p = em.find(Product.class, id);
      if (p != null) em.remove(p);
    }

    public CustomerProfile profile(long userId) { return em.find(CustomerProfile.class, userId); }
    public CustomerProfile saveProfile(CustomerProfile p) { return em.merge(p); }
    public List<Address> addresses(long userId) { return em.createQuery("select a from Address a where a.userId=:user order by a.defaultAddress desc,a.id", Address.class).setParameter("user", userId).getResultList(); }
    public Address address(long id) { return em.find(Address.class, id); }
    public Address saveAddress(Address a) { if (a.id == null) { em.persist(a); return a; } return em.merge(a); }
    public void clearDefaultAddresses(long userId) { em.createQuery("update Address a set a.defaultAddress=false where a.userId=:user").setParameter("user",userId).executeUpdate(); }
    public void deleteAddress(long id) { Address a=address(id); if(a!=null) em.remove(a); }
    public Favorite favorite(long userId, long productId) { return em.createQuery("select f from Favorite f where f.userId=:user and f.productId=:product", Favorite.class).setParameter("user",userId).setParameter("product",productId).getResultStream().findFirst().orElse(null); }
    public List<Favorite> favorites(long userId) { return em.createQuery("select f from Favorite f where f.userId=:user order by f.createdAt desc,f.id desc",Favorite.class).setParameter("user",userId).getResultList(); }
    public Favorite saveFavorite(Favorite f) { if(f.id==null){em.persist(f);return f;}return em.merge(f); }
    public void deleteFavorite(long userId,long productId){em.createQuery("delete from Favorite f where f.userId=:user and f.productId=:product").setParameter("user",userId).setParameter("product",productId).executeUpdate();}
    public RecentlyViewed viewed(long userId,long productId){return em.createQuery("select v from RecentlyViewed v where v.userId=:user and v.productId=:product",RecentlyViewed.class).setParameter("user",userId).setParameter("product",productId).getResultStream().findFirst().orElse(null);}
    public List<RecentlyViewed> recentlyViewed(long userId,int limit){return em.createQuery("select v from RecentlyViewed v where v.userId=:user order by v.viewedAt desc,v.id desc",RecentlyViewed.class).setParameter("user",userId).setMaxResults(limit).getResultList();}
    public RecentlyViewed saveViewed(RecentlyViewed v){if(v.id==null){em.persist(v);return v;}return em.merge(v);}
    public Cart cart(long userId){return em.createQuery("select c from Cart c where c.userId=:user",Cart.class).setParameter("user",userId).getResultStream().findFirst().orElse(null);}
    public Cart saveCart(Cart c){if(c.id==null){em.persist(c);return c;}return em.merge(c);}
    public List<CartItem> cartItems(long cartId){return em.createQuery("select i from CartItem i where i.cartId=:cart order by i.id",CartItem.class).setParameter("cart",cartId).getResultList();}
    public CartItem cartItem(long cartId,long copyId){return em.createQuery("select i from CartItem i where i.cartId=:cart and i.copyId=:copy",CartItem.class).setParameter("cart",cartId).setParameter("copy",copyId).getResultStream().findFirst().orElse(null);}
    public CartItem saveCartItem(CartItem i){if(i.id==null){em.persist(i);return i;}return em.merge(i);}
    public void deleteCartItem(long cartId,long copyId){em.createQuery("delete from CartItem i where i.cartId=:cart and i.copyId=:copy").setParameter("cart",cartId).setParameter("copy",copyId).executeUpdate();}
    public CheckoutGroup checkoutGroup(long id,boolean lock){return lock ? em.find(CheckoutGroup.class,id,LockModeType.PESSIMISTIC_WRITE) : em.find(CheckoutGroup.class,id);}
    public Order orderByKey(long userId,String key){return ordersByKey(userId,key).stream().findFirst().orElse(null);}
    public List<Order> ordersByKey(long userId,String key){return em.createQuery("select o from Order o where o.userId=:user and o.idempotencyKey=:key order by o.id",Order.class).setParameter("user",userId).setParameter("key",key).getResultList();}
    public Order order(long id, boolean lock){return lock ? em.find(Order.class,id,LockModeType.PESSIMISTIC_WRITE) : em.find(Order.class,id);}
    public CheckoutGroup saveCheckoutGroup(CheckoutGroup g){if(g.id==null){em.persist(g);return g;}return em.merge(g);}
    public Order saveOrder(Order o){if(o.id==null){em.persist(o);return o;}return em.merge(o);}
    public OrderItem saveOrderItem(OrderItem i){if(i.id==null){em.persist(i);return i;}return em.merge(i);}
    public OrderStatusHistory saveOrderStatus(OrderStatusHistory h){if(h.id==null){em.persist(h);return h;}return em.merge(h);}
    public InventoryReservation saveReservation(InventoryReservation r){if(r.id==null){em.persist(r);return r;}return em.merge(r);}
    public List<InventoryReservation> reservationsForOrder(long orderId){return em.createQuery("select r from InventoryReservation r where r.orderId=:order order by r.copyId,r.id",InventoryReservation.class).setParameter("order",orderId).getResultList();}
    public List<Order> ordersFor(long userId){return em.createQuery("select o from Order o where o.userId=:user order by o.createdAt desc,o.id desc",Order.class).setParameter("user",userId).getResultList();}
    public List<Order> ordersForShop(long shopId){return em.createQuery("select o from Order o where o.shopId=:shop order by o.createdAt desc,o.id desc",Order.class).setParameter("shop",shopId).getResultList();}
    public List<Order> ordersForGroup(long groupId){return em.createQuery("select o from Order o where o.checkoutGroupId=:group order by o.id",Order.class).setParameter("group",groupId).getResultList();}
    public List<OrderItem> orderItems(long orderId){return em.createQuery("select i from OrderItem i where i.orderId=:order order by i.copyId,i.id",OrderItem.class).setParameter("order",orderId).getResultList();}
    public List<OrderStatusHistory> orderStatuses(long orderId){return em.createQuery("select h from OrderStatusHistory h where h.orderId=:order order by h.createdAt,h.id",OrderStatusHistory.class).setParameter("order",orderId).getResultList();}
    public boolean deliveredPurchase(long userId,long productId){return em.createQuery("select count(i) from OrderItem i, Order o where i.orderId=o.id and o.userId=:user and i.productId=:product and o.status='COMPLETED'",Long.class).setParameter("user",userId).setParameter("product",productId).getSingleResult()>0;}
    public long completedOrderFor(long userId,long productId){return em.createQuery("select o.id from OrderItem i, Order o where i.orderId=o.id and o.userId=:user and i.productId=:product and o.status='COMPLETED' order by o.createdAt desc,o.id desc",Long.class).setParameter("user",userId).setParameter("product",productId).setMaxResults(1).getResultStream().findFirst().orElse(0L);}
    public Review review(long userId,long productId){return em.createQuery("select r from Review r where r.userId=:user and r.productId=:product",Review.class).setParameter("user",userId).setParameter("product",productId).getResultStream().findFirst().orElse(null);}
    public Review saveReview(Review r){if(r.id==null){em.persist(r);return r;}return em.merge(r);}
    public List<Review> reviews(long productId){return em.createQuery("select r from Review r where r.productId=:product and r.status='PUBLISHED' order by r.createdAt desc,r.id desc",Review.class).setParameter("product",productId).getResultList();}
  }
}
