package vn.edu.utex.bookstore;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Persistence;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.auth.Passwords;
import vn.edu.utex.bookstore.auth.User;
import vn.edu.utex.bookstore.buyer.BuyerService;
import vn.edu.utex.bookstore.category.Category;
import vn.edu.utex.bookstore.config.Settings;
import vn.edu.utex.bookstore.inventory.InventoryService;
import vn.edu.utex.bookstore.order.OrderService;
import vn.edu.utex.bookstore.payment.PayOsSettings;
import vn.edu.utex.bookstore.persistence.JpaStore;
import vn.edu.utex.bookstore.persistence.Store;
import vn.edu.utex.bookstore.product.Product;
import vn.edu.utex.bookstore.shop.Shop;
import vn.edu.utex.bookstore.shop.ShopAuditEvent;
import vn.edu.utex.bookstore.shop.ShopMembership;

/** Explicit acceptance setup only. Never included in the production JAR or called by verify. */
public final class RuntimePaymentScenario {
  private static final String KIND = "PAYOS_ACCEPTANCE_V1";
  private static final BigDecimal ORDER_TOTAL = new BigDecimal("31000.00");
  private static final String NOTE = "ACCEPTANCE_SETUP: Dữ liệu riêng để nghiệm thu thanh toán PayOS; không phải hàng kinh doanh.";
  private record Records(long userId, long shopId, long categoryId, long productId,
      long copyId, long addressId, long orderId, int created) {}
  private static final class SetupFailure extends RuntimeException {
    final String code;
    SetupFailure(String code) { this.code = code; }
  }

  private RuntimePaymentScenario() {}

  public static void main(String[] arguments) {
    if (arguments.length != 1 || !"--setup".equals(arguments[0])) {
      System.out.println("Usage: RuntimePaymentScenario --setup (explicit local acceptance setup only)");
      System.exit(2);
      return;
    }
    if (LoggerFactory.getILoggerFactory() instanceof LoggerContext logs) logs.getLogger("ROOT").setLevel(Level.OFF);
    try {
      setup();
    } catch (SetupFailure failure) {
      System.err.println("SETUP_FAILED code=" + failure.code);
      System.exit(1);
    } catch (Exception failure) {
      // Neither credentials, SQL values nor exception stacks belong in terminal output.
      System.err.println("SETUP_FAILED code=VALIDATION_OR_DATABASE");
      System.exit(1);
    }
  }

  private static void setup() throws Exception {
    Settings settings = Settings.load();
    requireLocalDatabase(settings);
    if (!Boolean.parseBoolean(settings.get("payos.enabled", "false"))) throw new SetupFailure("PAYOS_DISABLED");
    PayOsSettings.from(settings); // Local validation only; no gateway, provider request or transfer.
    Path root = Path.of("").toRealPath();
    if (!"goc-sach".equals(root.getFileName().toString()) || !Files.isRegularFile(root.resolve("pom.xml")))
      throw new SetupFailure("WRONG_WORKSPACE");
    Path directory = root.resolve("config");
    Files.createDirectories(directory);
    if (!directory.toRealPath().equals(directory)) throw new SetupFailure("UNSAFE_OUTPUT_PATH");
    Path manifest = directory.resolve("payment-acceptance.properties");
    Path lockPath = directory.resolve("payment-acceptance.lock.properties");
    if (Files.isSymbolicLink(manifest) || Files.isSymbolicLink(lockPath)) throw new SetupFailure("UNSAFE_OUTPUT_PATH");
    try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        var ignoredLock = channel.lock()) {
      privateFile(lockPath);
      Properties state = readOrCreate(manifest);
      var factory = Persistence.createEntityManagerFactory("bookstore", Map.of(
          "jakarta.persistence.jdbc.url", settings.require("db.url"),
          "jakarta.persistence.jdbc.user", settings.require("db.user"),
          "jakarta.persistence.jdbc.password", settings.require("db.password"),
          "hibernate.hbm2ddl.auto", "validate", "hibernate.show_sql", "false",
          "hibernate.hikari.maximumPoolSize", "2"));
      try (JpaStore store = new JpaStore(factory)) {
        EntityManager shared = SharedEntityManagerCreator.createSharedEntityManager(factory);
        Records records = seed(store, shared, state, Clock.systemUTC());
        state.setProperty("record.user-id", Long.toString(records.userId()));
        state.setProperty("record.shop-id", Long.toString(records.shopId()));
        state.setProperty("record.category-id", Long.toString(records.categoryId()));
        state.setProperty("record.product-id", Long.toString(records.productId()));
        state.setProperty("record.copy-id", Long.toString(records.copyId()));
        state.setProperty("record.address-id", Long.toString(records.addressId()));
        state.setProperty("record.order-id", Long.toString(records.orderId()));
        writePrivate(manifest, state, false);
        System.out.printf("SETUP user_id=%d shop_id=%d category_id=%d product_id=%d copy_id=%d address_id=%d order_id=%d scenarios_created=%d%n",
            records.userId(), records.shopId(), records.categoryId(), records.productId(), records.copyId(),
            records.addressId(), records.orderId(), records.created());
      }
    }
  }

  private static Records seed(Store store, EntityManager shared, Properties state, Clock clock) {
    String marker = required(state, "setup.session");
    String username = required(state, "login.username");
    String password = required(state, "login.password");
    String key = "payos_acceptance_" + marker;
    Passwords passwords = new Passwords();
    BuyerService buyer = new BuyerService(store, clock);
    OrderService orders = new OrderService(store, clock);
    InventoryService inventory = new InventoryService(store, clock);
    return store.tx(data -> {
      User existing = data.findLogin(username);
      if (existing != null) {
        if (!"USER".equals(existing.role) || !existing.active || existing.blocked
            || !passwords.matches(password, existing.passwordHash)) throw new SetupFailure("EXISTING_ACCOUNT_MISMATCH");
        var previous = data.ordersByKey(existing.id, key);
        if (previous.size() != 1) throw new SetupFailure("EXISTING_SCENARIO_MISMATCH");
        var order = previous.getFirst();
        var items = data.orderItems(order.id);
        if (!"PAYOS".equals(order.paymentMethod) || order.total.compareTo(ORDER_TOTAL) != 0 || items.size() != 1)
          throw new SetupFailure("EXISTING_SCENARIO_MISMATCH");
        var product = data.product(items.getFirst().productId);
        if (!product.title.contains(marker.substring(0, 12))) throw new SetupFailure("EXISTING_SCENARIO_MISMATCH");
        return new Records(existing.id, order.shopId, product.category.id, product.id, items.getFirst().copyId,
            data.addresses(existing.id).getFirst().id, order.id, 0);
      }

      User user = new User();
      user.username = username;
      user.email = username + "@example.invalid";
      user.passwordHash = passwords.hash(password);
      user.role = "USER";
      user.active = true;
      user.blocked = false;
      user.createdAt = clock.instant();
      data.saveUser(user);
      String suffix = marker.substring(0, 12);
      Shop shop = new Shop();
      shop.ownerId = user.id;
      shop.name = "Cửa hàng nghiệm thu PayOS " + suffix;
      shop.address = "Địa chỉ kiểm thử; không giao hàng thật";
      shop.status = "ACTIVE";
      shop.createdAt = clock.instant();
      data.saveShop(shop);
      ShopMembership membership = new ShopMembership();
      membership.shopId = shop.id;
      membership.userId = user.id;
      membership.role = "SELLER";
      membership.active = true;
      membership.updatedAt = clock.instant();
      data.saveMembership(membership);
      ShopAuditEvent audit = new ShopAuditEvent();
      audit.shopId = shop.id;
      audit.actorId = user.id;
      audit.action = "ACCEPTANCE_SETUP";
      audit.note = NOTE + " Cửa hàng ACTIVE được tạo offline bởi công cụ setup riêng, không giả lập phê duyệt của quản trị viên.";
      audit.createdAt = clock.instant();
      data.recordShopEvent(audit);
      Category category = new Category();
      category.name = "Nghiệm thu PayOS " + suffix;
      category.image = "";
      category.active = true;
      data.saveCategory(category);
      Product product = new Product();
      product.category = category;
      product.title = "Sách kiểm thử PayOS " + suffix;
      product.author = "Dữ liệu nghiệm thu Góc Sách";
      product.description = NOTE;
      product.image = "";
      product.language = "Tiếng Việt";
      product.createdAt = clock.instant();
      product.updatedAt = clock.instant();
      data.saveProduct(product);
      shared.flush();

      Identity actor = Identity.of(user);
      long copyId = inventory.receive(actor, shop.id, product.id, "GOOD", NOTE, "1000", NOTE);
      shared.flush();
      inventory.update(actor, shop.id, copyId, 0, "GOOD", NOTE, "1000", "AVAILABLE", NOTE);
      buyer.saveProfile(user.id, "Người nghiệm thu PayOS", "0000000000");
      var address = buyer.saveAddress(user.id, null, "Địa chỉ nghiệm thu", "Người nghiệm thu PayOS", "0000000000",
          "Địa chỉ kiểm thử; không giao hàng thật", "Phường kiểm thử", "Quận kiểm thử", "Thành phố kiểm thử", true);
      buyer.addToCart(user.id, copyId);
      shared.flush();
      var order = orders.checkout(user.id, address.id, key, orders.quote(user.id).fingerprint(), "PAYOS").orders().getFirst();
      shared.flush();
      if (order.total.compareTo(ORDER_TOTAL) != 0 || !"UNPAID".equals(order.paymentStatus)
          || data.paymentForOrder(order.id, false) != null) throw new SetupFailure("UNEXPECTED_ORDER_STATE");
      return new Records(user.id, shop.id, category.id, product.id, copyId, address.id, order.id, 1);
    });
  }

  private static Properties readOrCreate(Path manifest) throws IOException {
    if (Files.exists(manifest)) {
      Properties state = new Properties();
      try (var reader = Files.newBufferedReader(manifest)) { state.load(reader); }
      if (!KIND.equals(state.getProperty("setup.kind")) || !required(state, "setup.session").matches("[a-f0-9]{32}")
          || !required(state, "login.username").equals("payos_acc_" + state.getProperty("setup.session")))
        throw new SetupFailure("UNRELATED_MANIFEST");
      validatePassword(state);
      privateFile(manifest);
      return state;
    }
    Properties state = new Properties();
    String marker = UUID.randomUUID().toString().replace("-", "");
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    state.setProperty("setup.kind", KIND);
    state.setProperty("setup.session", marker);
    state.setProperty("login.username", "payos_acc_" + marker);
    state.setProperty("login.password", Base64.getUrlEncoder().withoutPadding().encodeToString(random));
    java.util.Arrays.fill(random, (byte) 0);
    writePrivate(manifest, state, true);
    return state;
  }

  static void writePrivate(Path manifest, Properties state, boolean fresh) throws IOException {
    if (fresh) {
      try (FileChannel created = FileChannel.open(manifest, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
        privateFile(manifest);
        try (var writer = java.nio.channels.Channels.newWriter(created, java.nio.charset.StandardCharsets.UTF_8)) {
          state.store(writer, "Private acceptance credentials and record IDs. Do not commit or paste in chat.");
        }
      }
      return;
    }
    if (Files.isSymbolicLink(manifest)) throw new SetupFailure("UNSAFE_OUTPUT_PATH");
    Properties current = new Properties();
    try (var reader = Files.newBufferedReader(manifest)) { current.load(reader); }
    if (!KIND.equals(current.getProperty("setup.kind")) || !required(state, "setup.session").equals(current.getProperty("setup.session")))
      throw new SetupFailure("UNRELATED_MANIFEST");
    Path temporary = Files.createTempFile(manifest.getParent(), ".payment-acceptance-", ".properties");
    try {
      privateFile(temporary);
      try (var writer = Files.newBufferedWriter(temporary)) {
        state.store(writer, "Private acceptance credentials and record IDs. Do not commit or paste in chat.");
      }
      Files.move(temporary, manifest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } finally { Files.deleteIfExists(temporary); }
  }

  private static void privateFile(Path file) throws IOException {
    AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
    if (acl != null) {
      AclEntry owner = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(file))
          .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();
      acl.setAcl(List.of(owner));
    } else Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
  }

  private static void requireLocalDatabase(Settings settings) throws Exception {
    URI address;
    try {
      String url = settings.require("db.url");
      if (!url.startsWith("jdbc:")) throw new SetupFailure("INVALID_DATABASE_URL");
      address = URI.create(url.substring("jdbc:".length()));
    }
    catch (IllegalArgumentException malformed) { throw new SetupFailure("INVALID_DATABASE_URL"); }
    if (!"postgresql".equals(address.getScheme()) || !Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(address.getHost())
        || address.getPort() != 5434 || address.getUserInfo() != null || address.getQuery() != null || address.getFragment() != null
        || !"/goc_sach".equals(address.getPath())) throw new SetupFailure("DATABASE_NOT_LOCAL_GOC_SACH");
    DriverManager.setLoginTimeout(5);
    try (var connection = DriverManager.getConnection(settings.require("db.url"), settings.require("db.user"), settings.require("db.password"));
        var query = connection.prepareStatement("select current_database()"); var result = query.executeQuery()) {
      if (!result.next() || !"goc_sach".equals(result.getString(1))) throw new SetupFailure("WRONG_DATABASE");
    }
  }

  private static String required(Properties state, String key) {
    String value = state.getProperty(key);
    if (value == null || value.isBlank()) throw new SetupFailure("MANIFEST_INCOMPLETE");
    return value;
  }

  private static void validatePassword(Properties state) {
    String password = required(state, "login.password");
    if (!password.matches("[A-Za-z0-9_-]{43}")) throw new SetupFailure("MANIFEST_PASSWORD_INVALID");
    byte[] decoded;
    try { decoded = Base64.getUrlDecoder().decode(password); }
    catch (IllegalArgumentException invalid) { throw new SetupFailure("MANIFEST_PASSWORD_INVALID"); }
    try {
      if (decoded.length != 32 || !password.equals(Base64.getUrlEncoder().withoutPadding().encodeToString(decoded)))
        throw new SetupFailure("MANIFEST_PASSWORD_INVALID");
    } finally { java.util.Arrays.fill(decoded, (byte) 0); }
  }
}
