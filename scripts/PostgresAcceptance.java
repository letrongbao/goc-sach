import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.auth.Passwords;
import vn.edu.utex.bookstore.auth.User;
import vn.edu.utex.bookstore.buyer.BuyerService;
import vn.edu.utex.bookstore.category.Category;
import vn.edu.utex.bookstore.config.Settings;
import vn.edu.utex.bookstore.inventory.BookCopy;
import vn.edu.utex.bookstore.order.OrderService;
import vn.edu.utex.bookstore.payment.PaymentGateway;
import vn.edu.utex.bookstore.payment.PaymentService;
import vn.edu.utex.bookstore.persistence.JpaStore;
import vn.edu.utex.bookstore.product.Product;
import vn.edu.utex.bookstore.shop.Shop;
import vn.edu.utex.bookstore.shop.ShopMembership;

/** Explicit local acceptance CLI: no DDL and all fixture writes roll back in goc_sach_test. */
public final class PostgresAcceptance {
  private static final Clock CLOCK = Clock.systemUTC();
  private record Fixture(User buyer, User seller, long product, List<Long> copies) {}

  public static void main(String[] args) {
    try {
      if (args.length != 1 || !"--rollback".equals(args[0])) throw new IllegalArgumentException();
      Settings settings = Settings.load();
      String url = settings.require("db.url");
      if (!url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):5434/goc_sach")) throw new IllegalArgumentException();
      try (var ignored = factory(settings, url)) {
        System.out.println("JPA VALIDATE PASS: goc_sach, all persistence.xml mappings; no DDL.");
      }
      try (var factory = factory(settings, url + "_test")) {
        System.out.println("JPA VALIDATE PASS: goc_sach_test, all persistence.xml mappings; no DDL.");
        runRollbackAcceptance(factory);
      }
      System.out.println("ACCEPTANCE PASS: PostgreSQL persistence and rollback; payment provider is fake, not a live payment test.");
    } catch (Throwable failure) {
      System.err.println("ACCEPTANCE FAILED: " + failure.getClass().getSimpleName() + "; credentials not displayed.");
      for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
        if (cause instanceof java.sql.SQLException sql) System.err.println("SQLState=" + sql.getSQLState());
      }
      System.exit(1);
    }
  }

  private static EntityManagerFactory factory(Settings settings, String url) {
    return Persistence.createEntityManagerFactory("bookstore", Map.of(
        "jakarta.persistence.jdbc.url", url,
        "jakarta.persistence.jdbc.user", settings.require("db.user"),
        "jakarta.persistence.jdbc.password", settings.require("db.password"),
        "hibernate.hbm2ddl.auto", "validate", "hibernate.show_sql", "false"));
  }

  private static void runRollbackAcceptance(EntityManagerFactory factory) {
    var manager = new JpaTransactionManager(factory);
    var store = new JpaStore(factory, manager);
    var transactions = new TransactionTemplate(manager);
    String prefix = "accept-" + UUID.randomUUID().toString().substring(0, 12);
    transactions.execute(status -> {
      status.setRollbackOnly();
      Fixture fixture = seed(store, prefix);
      var buyer = new BuyerService(store, CLOCK);
      var orders = new OrderService(store, CLOCK);
      Identity seller = Identity.of(fixture.seller());
      long user = fixture.buyer().id;
      var address = buyer.saveAddress(user, null, "Nhà", "Người kiểm thử", "0900000000",
          "1 Đường Sách", "", "", "TP HCM", true);
      buyer.saveProfile(user, "Người kiểm thử", "0900000000");
      buyer.addFavorite(user, fixture.product());
      buyer.addFavorite(user, fixture.product());
      buyer.recordViewed(user, fixture.product());
      require(buyer.favorites(user).size() == 1, "favorite idempotency");
      require(buyer.recentlyViewed(user, 10).size() == 1, "recently viewed");
      buyer.addToCart(user, fixture.copies().get(0));
      buyer.addToCart(user, fixture.copies().get(1));
      var quote = orders.quote(user);
      require(quote.ready() && quote.shops().size() == 2, "multi-shop quote");
      require(quote.shippingFee().compareTo(new BigDecimal("60000.00")) == 0, "shipping total");
      String codKey = prefix + "-cod";
      var cod = orders.checkout(user, address.id, codKey, quote.fingerprint());
      var repeated = orders.checkout(user, address.id, codKey, quote.fingerprint());
      require(cod.orders().size() == 2 && repeated.orders().getFirst().id.equals(cod.orders().getFirst().id), "checkout idempotency");
      require(buyer.cart(user).isEmpty(), "checked-out cart cleared");
      orders.cancel(user, cod.orders().getFirst().id);
      long completed = cod.orders().getLast().id;
      orders.advance(seller, completed, "CONFIRMED");
      orders.advance(seller, completed, "SHIPPING");
      orders.advance(seller, completed, "COMPLETED");
      require(store.tx(data -> "AVAILABLE".equals(data.copy(fixture.copies().get(0), false).status)), "COD cancel releases copy");
      require(store.tx(data -> "SOLD".equals(data.copy(fixture.copies().get(1), false).status)), "COD completion sells copy");
      System.out.println("POSTGRES PASS: profile/address/favorite/recent/cart, two-shop COD, duplicate checkout, cancel and fulfillment.");

      var gateway = new FakeGateway();
      var payments = new PaymentService(store, orders, gateway, CLOCK);
      buyer.addToCart(user, fixture.copies().get(2));
      var online = payments.checkout(user, address.id, prefix + "-online", orders.quote(user).fingerprint(), "PAYOS").orders().getFirst();
      require(!payments.start(user, online.id).isBlank(), "payment link persistence");
      var attempt = store.tx(data -> data.paymentForOrder(online.id, false));
      gateway.receipt = new PaymentGateway.Receipt(attempt.orderCode, attempt.paymentLinkId, attempt.amount,
          prefix + "-bank", "VND", true);
      payments.webhook("fixture only");
      payments.webhook("fixture only");
      require("PAID".equals(payments.view(user, online.id).status()), "paid settlement");
      require(store.tx(data -> data.paymentEvents(attempt.id).size()) == 1, "duplicate payment event");
      require(store.tx(data -> data.reservationsForOrder(online.id).getFirst().expiresAt == null), "paid reservation does not expire");
      orders.advance(seller, online.id, "CONFIRMED");
      orders.advance(seller, online.id, "SHIPPING");
      orders.advance(seller, online.id, "COMPLETED");
      require(store.tx(data -> "SOLD".equals(data.copy(fixture.copies().get(2), false).status)), "online completion sells copy");
      buyer.addToCart(user, fixture.copies().get(3));
      var cancelled = payments.checkout(user, address.id, prefix + "-cancel", orders.quote(user).fingerprint(), "PAYOS").orders().getFirst();
      payments.start(user, cancelled.id);
      payments.cancel(user, cancelled.id);
      require("CANCELLED".equals(orders.detail(user, cancelled.id).order().status()), "provider-confirmed cancellation");
      require(store.tx(data -> "AVAILABLE".equals(data.copy(fixture.copies().get(3), false).status)), "online cancel releases copy");
      require(store.tx(data -> data.openPaymentOrders(25, 0).isEmpty()), "actual open-payment JPQL executes");
      store.tx(data -> data.dueOnlineOrders(CLOCK.instant(), 25));
      System.out.println("POSTGRES PASS: PayOS attempt/event mappings, duplicate settlement, paid fulfillment, terminal cancel, maintenance JPQL (fake provider).");
      return null;
    });
    try (var entityManager = factory.createEntityManager()) {
      long users = entityManager.createQuery("select count(u) from User u where u.username like :prefix", Long.class)
          .setParameter("prefix", prefix + "%").getSingleResult();
      long categories = entityManager.createQuery("select count(c) from Category c where c.name=:name", Long.class)
          .setParameter("name", prefix).getSingleResult();
      require(users == 0 && categories == 0, "fixture transaction rolled back");
    }
    System.out.println("POSTGRES PASS: independent connection sees zero fixture rows after rollback.");
  }

  private static Fixture seed(JpaStore store, String prefix) {
    return store.tx(data -> {
      var password = new Passwords().hash(UUID.randomUUID().toString());
      User buyer = user(prefix + "-buyer", password), seller = user(prefix + "-seller", password);
      data.saveUser(buyer); data.saveUser(seller);
      Category category = new Category(); category.name = prefix; category.image = ""; category.active = true;
      data.saveCategory(category);
      Product product = new Product(); product.category = category; product.title = "Sách kiểm thử";
      product.author = "Tác giả kiểm thử"; product.description = ""; product.image = "";
      product.createdAt = CLOCK.instant(); product.updatedAt = CLOCK.instant(); data.saveProduct(product);
      List<Long> copies = new ArrayList<>();
      for (int index = 0; index < 2; index++) {
        Shop shop = new Shop(); shop.ownerId = seller.id; shop.name = prefix + "-shop-" + index;
        shop.address = "1 Đường Sách"; shop.status = "ACTIVE"; shop.createdAt = CLOCK.instant(); data.saveShop(shop);
        ShopMembership member = new ShopMembership(); member.shopId = shop.id; member.userId = seller.id;
        member.role = "SELLER"; member.active = true; member.updatedAt = CLOCK.instant(); data.saveMembership(member);
        BookCopy copy = copy(prefix + "-" + index, shop.id, product.id); data.saveCopy(copy); copies.add(copy.id);
        if (index == 0) {
          BookCopy online = copy(prefix + "-online", shop.id, product.id); data.saveCopy(online);
          BookCopy cancelled = copy(prefix + "-cancel", shop.id, product.id); data.saveCopy(cancelled);
          copies.add(online.id); copies.add(cancelled.id);
        }
      }
      // First two copies must belong to different shops; the final two exercise online payment.
      return new Fixture(buyer, seller, product.id, List.of(copies.get(0), copies.get(3), copies.get(1), copies.get(2)));
    });
  }

  private static User user(String username, String hash) {
    User user = new User(); user.username = username; user.email = username + "@example.test";
    user.passwordHash = hash; user.role = "USER"; user.active = true; user.createdAt = CLOCK.instant(); return user;
  }
  private static BookCopy copy(String code, long shop, long product) {
    BookCopy copy = new BookCopy(); copy.shopId = shop; copy.productId = product; copy.code = code;
    copy.condition = "GOOD"; copy.defects = ""; copy.status = "AVAILABLE";
    copy.price = new BigDecimal("50000.00"); copy.createdAt = CLOCK.instant(); copy.updatedAt = CLOCK.instant(); return copy;
  }
  private static void require(boolean condition, String scenario) {
    if (!condition) throw new AssertionError(scenario);
  }

  private static final class FakeGateway implements PaymentGateway {
    final Map<Long, Snapshot> snapshots = new HashMap<>();
    Receipt receipt;
    public boolean enabled() { return true; }
    public Link create(long code, long amount, String description, Instant expiry) {
      String link = UUID.randomUUID().toString().replace("-", "");
      String url = "https://pay.payos.vn/web/" + link;
      snapshots.put(code, new Snapshot(code, link, amount, 0, "PENDING", url));
      return new Link(code, amount, link, url, "PENDING");
    }
    public Snapshot query(long code) { return snapshots.get(code); }
    public Snapshot cancel(long code, String reason) {
      var old = snapshots.get(code);
      var cancelled = new Snapshot(code, old.paymentLinkId(), old.amount(), 0, "CANCELLED", old.checkoutUrl());
      snapshots.put(code, cancelled); return cancelled;
    }
    public Receipt verify(String ignored) { return receipt; }
  }
}
