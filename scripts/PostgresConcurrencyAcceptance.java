import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import vn.edu.utex.bookstore.auth.Passwords;
import vn.edu.utex.bookstore.auth.User;
import vn.edu.utex.bookstore.buyer.BuyerService;
import vn.edu.utex.bookstore.category.Category;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.config.Settings;
import vn.edu.utex.bookstore.inventory.BookCopy;
import vn.edu.utex.bookstore.order.OrderService;
import vn.edu.utex.bookstore.persistence.JpaStore;
import vn.edu.utex.bookstore.product.Product;
import vn.edu.utex.bookstore.shop.Shop;

/** Explicit operator acceptance only. Temporary committed fixtures live solely in goc_sach_test. */
public final class PostgresConcurrencyAcceptance {
  private static final Clock CLOCK = Clock.systemUTC();
  private record Fixture(List<Long> buyers, List<Long> addresses, long copy) {}
  private record Outcome(long buyer, long order, String key, int error) {}

  public static void main(String[] args) {
    try {
      Settings settings = Settings.load();
      if (args.length == 2 && "--cleanup".equals(args[0])) cleanup(settings, marker(args[1]));
      else if (args.length == 6 && "--restart-read".equals(args[0]))
        restartRead(settings, marker(args[1]), Long.parseLong(args[2]), Long.parseLong(args[3]),
            Long.parseLong(args[4]), args[5]);
      else if (args.length == 1 && "--contend".equals(args[0])) contend(settings);
      else throw new IllegalArgumentException();
    } catch (Throwable failure) {
      System.err.println("CONTENTION FAILED: " + failure.getClass().getSimpleName() + "; credentials not displayed.");
      for (Throwable cause = failure; cause != null; cause = cause.getCause())
        if (cause instanceof java.sql.SQLException sql) System.err.println("SQLState=" + sql.getSQLState());
      System.exit(1);
    }
  }

  private static String databaseUrl(Settings settings) {
    String url = settings.require("db.url");
    if (!url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):5434/goc_sach")) throw new IllegalArgumentException();
    return url + "_test";
  }
  private static String marker(String value) {
    if (!value.matches("race-[a-f0-9]{20}")) throw new IllegalArgumentException();
    return value;
  }
  private static EntityManagerFactory factory(Settings settings) {
    return Persistence.createEntityManagerFactory("bookstore", Map.of(
        "jakarta.persistence.jdbc.url", databaseUrl(settings),
        "jakarta.persistence.jdbc.user", settings.require("db.user"),
        "jakarta.persistence.jdbc.password", settings.require("db.password"),
        "hibernate.hbm2ddl.auto", "validate", "hibernate.show_sql", "false"));
  }

  private static void contend(Settings settings) throws Exception {
    String prefix = "race-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    System.out.println("TEMPORARY FIXTURE: " + prefix + "; database=goc_sach_test only.");
    try {
      Outcome winner;
      Fixture fixture;
      try (var factory = factory(settings); var store = new JpaStore(factory)) {
        fixture = seed(store, prefix);
        var buyer = new BuyerService(store, CLOCK);
        var orders = new OrderService(store, CLOCK);
        for (long user : fixture.buyers()) buyer.addToCart(user, fixture.copy());
        List<String> fingerprints = fixture.buyers().stream().map(user -> orders.quote(user).fingerprint()).toList();
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        List<Outcome> outcomes;
        try (var workers = Executors.newFixedThreadPool(2)) {
          var first = workers.submit(() -> checkout(orders, fixture, prefix, fingerprints, 0, ready, go));
          var second = workers.submit(() -> checkout(orders, fixture, prefix, fingerprints, 1, ready, go));
          require(ready.await(20, TimeUnit.SECONDS), "workers ready");
          go.countDown();
          outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally { go.countDown(); }
        require(outcomes.stream().filter(result -> result.error() == 0).count() == 1, "one checkout success");
        require(outcomes.stream().filter(result -> result.error() == 409).count() == 1, "one conflict rejection");
        winner = outcomes.stream().filter(result -> result.error() == 0).findFirst().orElseThrow();
        long address = fixture.addresses().get(fixture.buyers().indexOf(winner.buyer()));
        var repeated = orders.checkout(winner.buyer(), address, winner.key());
        require(repeated.orders().size() == 1 && repeated.orders().getFirst().id == winner.order(), "winner retry reuses order");
        verifyConsistency(store, fixture, winner);
        System.out.println("POSTGRES CONTENTION PASS: two different buyers, one success + one HTTP409; one order, one active reservation; retry idempotent.");
      }
      // Close the entire JPA factory, then validate and read through a fresh JVM.
      restartProcess(prefix, winner, fixture.copy());
    } finally { cleanup(settings, prefix); }
  }

  private static Outcome checkout(OrderService orders, Fixture fixture, String prefix, List<String> fingerprints,
      int index, CountDownLatch ready, CountDownLatch go) throws InterruptedException {
    long user = fixture.buyers().get(index);
    String key = prefix + "-checkout-" + index;
    ready.countDown();
    require(go.await(20, TimeUnit.SECONDS), "checkout start gate");
    try {
      long order = orders.checkout(user, fixture.addresses().get(index), key, fingerprints.get(index)).orders().getFirst().id;
      return new Outcome(user, order, key, 0);
    } catch (Problem problem) { return new Outcome(user, 0, key, problem.status); }
  }

  private static void verifyConsistency(JpaStore store, Fixture fixture, Outcome winner) {
    store.tx(data -> {
      require(fixture.buyers().stream().mapToInt(user -> data.ordersFor(user).size()).sum() == 1, "only one order created");
      require(data.orderItems(winner.order()).size() == 1, "one order item");
      var reservations = data.reservationsForOrder(winner.order());
      require(reservations.size() == 1 && reservations.getFirst().copyId == fixture.copy()
          && "ACTIVE".equals(reservations.getFirst().status), "one matching active reservation");
      require("RESERVED".equals(data.copy(fixture.copy(), false).status), "copy held by winner");
      require(data.orderStatuses(winner.order()).size() == 1, "no duplicate order history");
      for (long user : fixture.buyers()) {
        var cart = data.cart(user);
        require(data.cartItems(cart.id).size() == (user == winner.buyer() ? 0 : 1), "loser cart retained and winner cart cleared");
      }
      return null;
    });
  }

  private static Fixture seed(JpaStore store, String prefix) {
    return store.tx(data -> {
      String hash = new Passwords().hash(UUID.randomUUID().toString());
      User first = user(prefix + "-buyer-a", hash), second = user(prefix + "-buyer-b", hash), seller = user(prefix + "-seller", hash);
      data.saveUser(first); data.saveUser(second); data.saveUser(seller);
      Category category = new Category(); category.name = prefix; category.image = ""; category.active = true; data.saveCategory(category);
      Product product = new Product(); product.category = category; product.title = prefix; product.author = "Người kiểm thử";
      product.description = ""; product.image = ""; product.createdAt = CLOCK.instant(); product.updatedAt = CLOCK.instant(); data.saveProduct(product);
      Shop shop = new Shop(); shop.ownerId = seller.id; shop.name = prefix; shop.address = "1 Đường Sách";
      shop.status = "ACTIVE"; shop.createdAt = CLOCK.instant(); data.saveShop(shop);
      BookCopy copy = new BookCopy(); copy.shopId = shop.id; copy.productId = product.id; copy.code = prefix + "-copy";
      copy.condition = "GOOD"; copy.defects = ""; copy.price = new BigDecimal("50000.00"); copy.status = "AVAILABLE";
      copy.createdAt = CLOCK.instant(); copy.updatedAt = CLOCK.instant(); data.saveCopy(copy);
      var buyer = new BuyerService(store, CLOCK);
      long addressA = buyer.saveAddress(first.id, null, "Nhà", "Người kiểm thử A", "0900000000", "1 Đường Sách", "", "", "TP HCM", true).id;
      long addressB = buyer.saveAddress(second.id, null, "Nhà", "Người kiểm thử B", "0900000000", "2 Đường Sách", "", "", "TP HCM", true).id;
      return new Fixture(List.of(first.id, second.id), List.of(addressA, addressB), copy.id);
    });
  }
  private static User user(String username, String hash) {
    User user = new User(); user.username = username; user.email = username + "@example.test";
    user.passwordHash = hash; user.role = "USER"; user.active = true; user.createdAt = Instant.now(); return user;
  }

  private static void restartProcess(String prefix, Outcome winner, long copy) throws Exception {
    String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
    String config = System.getProperty("bookstore.config", System.getenv("BOOKSTORE_CONFIG"));
    var process = new ProcessBuilder(javaExecutable, "-XX:+PerfDisableSharedMem", "-Dfile.encoding=UTF-8", "-Dbookstore.config=" + config,
        "-cp", System.getProperty("java.class.path"), "scripts/PostgresConcurrencyAcceptance.java", "--restart-read", prefix,
        Long.toString(winner.buyer()), Long.toString(winner.order()), Long.toString(copy), winner.key()).redirectErrorStream(true).start();
    var output = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
      try { return new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
      catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    });
    if (!process.waitFor(45, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new AssertionError("new JVM read timed out");
    }
    boolean readPassed = false;
    for (String line : output.get(5, TimeUnit.SECONDS).split("\\R")) {
      if (line.startsWith("POSTGRES RESTART PASS:")) { System.out.println(line); readPassed = true; }
      if (line.startsWith("CONTENTION FAILED:") || line.startsWith("SQLState=")) System.out.println(line);
    }
    require(process.exitValue() == 0 && readPassed, "new JVM persistence check");
  }

  private static void restartRead(Settings settings, String prefix, long user, long order, long copy, String key) {
    try (var factory = factory(settings); var store = new JpaStore(factory)) {
      store.tx(data -> {
        var owner = data.user(user);
        require(owner != null && List.of(prefix + "-buyer-a", prefix + "-buyer-b").contains(owner.username), "fixture ownership");
        var persisted = data.order(order, false);
        require(persisted != null && persisted.userId == user && "PENDING".equals(persisted.status)
            && key.equals(persisted.idempotencyKey), "committed order persists");
        var book = data.copy(copy, false);
        require(book != null && (prefix + "-copy").equals(book.code) && "RESERVED".equals(book.status), "inventory persists");
        var held = data.reservationsForOrder(order);
        require(held.size() == 1 && held.getFirst().copyId == copy && "ACTIVE".equals(held.getFirst().status), "reservation persists");
        return null;
      });
      System.out.println("POSTGRES RESTART PASS: fresh Java process + JPA validate sees committed order, reserved copy and matching reservation.");
    }
  }

  private static void cleanup(Settings settings, String prefix) throws Exception {
    marker(prefix);
    String users = "SELECT id FROM users WHERE username IN (?,?,?)";
    List<String> statements = List.of(
        "DELETE FROM payment_events WHERE attempt_id IN (SELECT id FROM payment_attempts WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (" + users + ")))",
        "DELETE FROM payment_attempts WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (" + users + "))",
        "DELETE FROM orders WHERE user_id IN (" + users + ")",
        "DELETE FROM checkout_groups WHERE user_id IN (" + users + ")",
        "DELETE FROM carts WHERE user_id IN (" + users + ")",
        "DELETE FROM addresses WHERE user_id IN (" + users + ")",
        "DELETE FROM book_copies WHERE code=?",
        "DELETE FROM shops WHERE name=? AND owner_id IN (SELECT id FROM users WHERE username=?)",
        "DELETE FROM products WHERE title=? AND category_id IN (SELECT id FROM categories WHERE name=?)",
        "DELETE FROM categories WHERE name=?",
        "DELETE FROM users WHERE username IN (?,?,?)");
    DriverManager.setLoginTimeout(5);
    try (var connection = DriverManager.getConnection(databaseUrl(settings), settings.require("db.user"), settings.require("db.password"))) {
      connection.setAutoCommit(false);
      try {
        for (String sql : statements) {
          try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(10);
            if (sql.contains(users) || sql.equals("DELETE FROM users WHERE username IN (?,?,?)")) {
              statement.setString(1, prefix + "-buyer-a"); statement.setString(2, prefix + "-buyer-b"); statement.setString(3, prefix + "-seller");
            } else if (sql.startsWith("DELETE FROM book_copies")) statement.setString(1, prefix + "-copy");
            else {
              statement.setString(1, prefix);
              if (sql.startsWith("DELETE FROM shops")) statement.setString(2, prefix + "-seller");
              if (sql.startsWith("DELETE FROM products")) statement.setString(2, prefix);
            }
            statement.executeUpdate();
          }
        }
        connection.commit();
        try (var statement = connection.prepareStatement("SELECT count(*) FROM users WHERE username IN (?,?,?)")) {
          statement.setString(1, prefix + "-buyer-a"); statement.setString(2, prefix + "-buyer-b"); statement.setString(3, prefix + "-seller");
          try (var rows = statement.executeQuery()) { rows.next(); require(rows.getLong(1) == 0, "fixture cleanup"); }
        }
        System.out.println("TEMPORARY FIXTURE CLEANED: " + prefix + "; exact marker only, no unrelated rows deleted.");
      } catch (Throwable failure) {
        connection.rollback();
        System.err.println("FIXTURE CLEANUP RETRY: run --cleanup " + prefix + " on this explicit helper.");
        throw failure;
      }
    }
  }
  private static void require(boolean condition, String scenario) { if (!condition) throw new AssertionError(scenario); }
}
