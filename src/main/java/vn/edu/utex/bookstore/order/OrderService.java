package vn.edu.utex.bookstore.order;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.auth.User;
import vn.edu.utex.bookstore.buyer.Address;
import vn.edu.utex.bookstore.buyer.CartItem;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.inventory.BookCopy;
import vn.edu.utex.bookstore.persistence.Store;
import vn.edu.utex.bookstore.product.Product;
import vn.edu.utex.bookstore.shop.Shop;
import vn.edu.utex.bookstore.shop.ShopAccess;

/** Creates orders and owns every allowed buyer/seller fulfillment transition. */
public final class OrderService {
  public static final BigDecimal SHIPPING_FEE_PER_SHOP = new BigDecimal("30000.00");

  public record CheckoutResult(List<Order> orders) {}
  public record QuoteLine(long copyId, long productId, String title, String coverUrl,
      String condition, String conditionLabel, String copyCode, BigDecimal price, boolean sellable) {}
  public record QuoteShop(long shopId, String shopName, List<QuoteLine> lines, BigDecimal subtotal,
      BigDecimal shippingFee, BigDecimal total) {}
  public record CheckoutQuote(List<QuoteShop> shops, BigDecimal subtotal, BigDecimal shippingFee,
      BigDecimal total, String fingerprint, boolean ready, List<String> issues) {}
  public record OrderItemView(long copyId, long productId, String title, String coverUrl,
      String condition, String conditionLabel, String copyCode, BigDecimal price) {}
  public record StatusView(String status, String label, String note, Instant createdAt) {
    public String displayTime() { return DISPLAY_TIME.format(createdAt); }
  }
  public record OrderSummary(long id, long shopId, String shopName, String status, String statusLabel,
      String recipientName, String phone, String address, BigDecimal subtotal, BigDecimal shippingFee,
      BigDecimal total, Instant createdAt, Instant updatedAt, boolean buyerCanCancel,
      boolean sellerCanCancel, String nextStatus, String nextStatusLabel,
      String paymentMethod, String paymentMethodLabel, String paymentStatus, String paymentStatusLabel,
      boolean paymentCanStart, Instant paymentExpiresAt) {
    public String displayTime() { return DISPLAY_TIME.format(createdAt); }
  }
  public record OrderDetail(OrderSummary order, List<OrderItemView> items, List<StatusView> history) {}
  public record ShopOption(long id, String name) {}

  private static final Map<String, String> NEXT_STATUS = Map.of(
      "PENDING", "CONFIRMED", "CONFIRMED", "SHIPPING", "SHIPPING", "COMPLETED");
  private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm",
      Locale.forLanguageTag("vi-VN")).withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
  private final Store store;
  private final Clock clock;

  public OrderService(Store store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  public CheckoutQuote quote(long userId) {
    return store.tx(data -> quote(data, userId, false));
  }

  /** Compatibility entry point for older callers. New UI/API callers should submit the quote fingerprint. */
  public CheckoutResult checkout(long userId, long addressId, String rawKey) {
    return checkout(userId, addressId, rawKey, null, false, "COD");
  }

  public CheckoutResult checkout(long userId, long addressId, String rawKey, String expectedFingerprint) {
    return checkout(userId, addressId, rawKey, expectedFingerprint, true, "COD");
  }

  public CheckoutResult checkout(long userId, long addressId, String rawKey, String expectedFingerprint, String method) {
    return checkout(userId, addressId, rawKey, expectedFingerprint, true, method);
  }

  private CheckoutResult checkout(long userId, long addressId, String rawKey,
      String expectedFingerprint, boolean requireFingerprint, String method) {
    if (!Set.of("COD", "PAYOS").contains(method)) throw Problem.invalid("Phương thức thanh toán không hợp lệ.");
    String key = rawKey == null ? "" : rawKey.trim();
    if (!key.matches("[A-Za-z0-9._-]{16,80}"))
      throw Problem.invalid("Phiên đặt hàng đã hết hạn. Vui lòng tải lại trang.");
    return store.tx(data -> {
      User user = data.lockUser(userId);
      if (user == null || !user.active || user.blocked) throw Problem.missing();
      List<Order> previous = data.ordersByKey(userId, key);
      if (!previous.isEmpty()) {
        if (previous.stream().anyMatch(order -> !method.equals(order.paymentMethod)))
          throw new Problem(409, "Đơn đã được tạo với phương thức thanh toán khác. Vui lòng xem Đơn hàng của tôi.");
        return new CheckoutResult(List.copyOf(previous));
      }

      Address address = data.address(addressId);
      if (address == null || address.userId != userId) throw Problem.missing();
      CheckoutQuote current = quote(data, userId, true);
      if (!current.ready()) throw new Problem(409, current.issues().getFirst());
      if ("PAYOS".equals(method)) {
        for (QuoteShop shop : current.shops()) {
          try { if (shop.total().longValueExact() <= 0) throw new ArithmeticException(); }
          catch (ArithmeticException invalidAmount) { throw Problem.invalid("Thanh toán chuyển khoản cần tổng tiền là số nguyên đồng và lớn hơn 0."); }
        }
      }
      String submitted = expectedFingerprint == null ? "" : expectedFingerprint.trim();
      if (requireFingerprint && submitted.isEmpty())
        throw Problem.invalid("Thông tin đơn hàng đã hết hạn. Vui lòng tải lại trang.");
      if (!submitted.isEmpty() && !MessageDigest.isEqual(
          current.fingerprint().getBytes(StandardCharsets.US_ASCII), submitted.getBytes(StandardCharsets.US_ASCII)))
        throw new Problem(409, "Giá hoặc tình trạng sách vừa thay đổi. Vui lòng kiểm tra lại đơn hàng.");
      Set<Long> checkedOutCopies = current.shops().stream().flatMap(shop -> shop.lines().stream())
          .map(QuoteLine::copyId).collect(Collectors.toSet());

      Instant now = clock.instant();
      CheckoutGroup group = new CheckoutGroup();
      group.userId = userId;
      group.idempotencyKey = key;
      group.addressSnapshot = snapshot(address);
      group.shippingFee = current.shippingFee();
      group.total = current.total();
      group.status = "PENDING";
      group.createdAt = now;
      data.saveCheckoutGroup(group);

      List<Order> created = new ArrayList<>();
      for (QuoteShop quotedShop : current.shops()) {
        Shop shop = data.shop(quotedShop.shopId(), false);
        if (shop == null || !"ACTIVE".equals(shop.status))
          throw new Problem(409, "Một cửa hàng trong đơn tạm ngừng nhận đơn.");
        Order order = new Order();
        order.userId = userId;
        order.shopId = shop.id;
        order.shopNameSnapshot = shop.name;
        order.checkoutGroupId = group.id;
        order.idempotencyKey = key;
        order.status = "PENDING";
        order.paymentMethod = method;
        order.paymentStatus = "UNPAID";
        order.paymentExpiresAt = "PAYOS".equals(method) ? now.plusSeconds(900) : null;
        order.recipientName = address.recipientName;
        order.phone = address.phone;
        order.addressSnapshot = snapshot(address);
        order.subtotal = quotedShop.subtotal();
        order.shippingFee = quotedShop.shippingFee();
        order.total = quotedShop.total();
        order.createdAt = now;
        order.updatedAt = now;
        data.saveOrder(order);

        for (QuoteLine line : quotedShop.lines()) {
          BookCopy copy = data.copy(line.copyId(), false);
          Product product = data.product(line.productId());
          if (!sellable(data, copy) || product == null)
            throw new Problem(409, "Một cuốn sách vừa không còn sẵn bán. Vui lòng kiểm tra lại giỏ hàng.");
          copy.status = "RESERVED";
          copy.updatedAt = now;
          data.saveCopy(copy);

          OrderItem item = new OrderItem();
          item.orderId = order.id;
          item.copyId = copy.id;
          item.productId = product.id;
          item.titleSnapshot = product.title;
          item.coverUrlSnapshot = Objects.toString(product.image, "");
          item.conditionSnapshot = copy.condition;
          item.copyCodeSnapshot = copy.code;
          item.unitPrice = copy.price;
          data.saveOrderItem(item);

          InventoryReservation reservation = new InventoryReservation();
          reservation.orderId = order.id;
          reservation.copyId = copy.id;
          reservation.status = "ACTIVE";
          reservation.createdAt = now;
          reservation.expiresAt = order.paymentExpiresAt;
          data.saveReservation(reservation);
        }
        recordStatus(data, order.id, "PENDING", "Đã tiếp nhận đơn hàng", now);
        created.add(order);
      }

      var cart = data.cart(userId);
      if (cart != null) {
        for (CartItem line : new ArrayList<>(data.cartItems(cart.id)))
          if (checkedOutCopies.contains(line.copyId)) data.deleteCartItem(cart.id, line.copyId);
        cart.updatedAt = now;
        data.saveCart(cart);
      }
      return new CheckoutResult(List.copyOf(created));
    });
  }

  public List<OrderSummary> orders(long userId) {
    return store.tx(data -> data.ordersFor(userId).stream().map(order -> summary(data, order)).toList());
  }

  public List<OrderSummary> ordersByKey(long userId, String key) {
    String normalized = key == null ? "" : key.trim();
    return store.tx(data -> data.ordersByKey(userId, normalized).stream().map(order -> summary(data, order)).toList());
  }

  public OrderDetail detail(long userId, long orderId) {
    return store.tx(data -> {
      Order order = data.order(orderId, false);
      if (order == null || order.userId != userId) throw Problem.missing();
      return detail(data, order);
    });
  }

  public List<ShopOption> sellerShops(Identity actor) {
    return store.tx(data -> {
      requireActor(data, actor);
      return data.shopsFor(actor.id(), actor.isAdmin()).stream()
          .map(shop -> new ShopOption(shop.id, shop.name)).toList();
    });
  }

  public List<OrderSummary> sellerOrders(Identity actor, Long shopId) {
    return store.tx(data -> {
      requireActor(data, actor);
      List<Shop> shops;
      if (shopId == null) shops = data.shopsFor(actor.id(), actor.isAdmin());
      else shops = List.of(ShopAccess.require(data, actor, shopId, false));
      return shops.stream().flatMap(shop -> data.ordersForShop(shop.id).stream())
          .sorted(Comparator.comparing((Order order) -> order.createdAt).reversed()
              .thenComparing(order -> order.id, Comparator.reverseOrder()))
          .map(order -> summary(data, order)).toList();
    });
  }

  public OrderDetail sellerDetail(Identity actor, long orderId) {
    return store.tx(data -> {
      requireActor(data, actor);
      Order order = data.order(orderId, false);
      if (order == null) throw Problem.missing();
      ShopAccess.require(data, actor, order.shopId, false);
      return detail(data, order);
    });
  }

  public void cancel(long userId, long orderId) {
    store.tx(data -> {
      Order order = lockedOrder(data, orderId);
      if (order == null || order.userId != userId) throw Problem.missing();
      if ("CANCELLED".equals(order.status)) return null;
      if (!"PENDING".equals(order.status))
        throw new Problem(409, "Đơn này đã được cửa hàng xử lý nên không thể hủy.");
      requireSafeCancellation(data, order);
      cancel(data, order, "Khách hàng đã hủy đơn", clock.instant());
      return null;
    });
  }

  public void sellerCancel(Identity actor, long orderId, String rawReason) {
    String reason = requiredText(rawReason, 5, 500, "Vui lòng ghi lý do hủy từ 5 đến 500 ký tự.");
    store.tx(data -> {
      requireActor(data, actor);
      Order order = lockedOrder(data, orderId);
      if (order == null) throw Problem.missing();
      // A suspended shop must still be able to settle orders accepted before suspension.
      ShopAccess.require(data, actor, order.shopId, false);
      if ("CANCELLED".equals(order.status)) return null;
      if (!Set.of("PENDING", "CONFIRMED").contains(order.status))
        throw new Problem(409, "Đơn đang giao hoặc đã kết thúc nên không thể hủy.");
      requireSafeCancellation(data, order);
      cancel(data, order, "Cửa hàng hủy: " + reason, clock.instant());
      return null;
    });
  }

  public void advance(Identity actor, long orderId, String requestedStatus) {
    String target = Objects.toString(requestedStatus, "").strip().toUpperCase(Locale.ROOT);
    store.tx(data -> {
      requireActor(data, actor);
      Order order = lockedOrder(data, orderId);
      if (order == null) throw Problem.missing();
      // Shop status blocks new sales, not the safe completion or cancellation of existing orders.
      ShopAccess.require(data, actor, order.shopId, false);
      if (target.equals(order.status)) return null;
      if ("PAYOS".equals(order.paymentMethod) && !"PAID".equals(order.paymentStatus))
        throw new Problem(409, "Đơn chưa được xác nhận thanh toán nên chưa thể xử lý.");
      String allowed = NEXT_STATUS.get(order.status);
      if (allowed == null || !allowed.equals(target))
        throw new Problem(409, "Trạng thái đơn đã thay đổi. Vui lòng tải lại trang.");
      Instant now = clock.instant();
      if ("COMPLETED".equals(target)) completeInventory(data, order, now);
      if ("COMPLETED".equals(target) && "COD".equals(order.paymentMethod)) order.paymentStatus = "PAID";
      order.status = target;
      order.updatedAt = now;
      data.saveOrder(order);
      recordStatus(data, order.id, target, statusNote(target), now);
      updateGroupStatus(data, order.checkoutGroupId);
      return null;
    });
  }

  public static String statusLabel(String status) {
    return switch (Objects.toString(status, "")) {
      case "PENDING" -> "Chờ xác nhận";
      case "CONFIRMED" -> "Đang chuẩn bị";
      case "SHIPPING" -> "Đang giao";
      case "COMPLETED" -> "Hoàn tất";
      case "CANCELLED" -> "Đã hủy";
      default -> "Đang cập nhật";
    };
  }

  public static String conditionLabel(String condition) {
    return switch (Objects.toString(condition, "")) {
      case "LIKE_NEW" -> "Như mới";
      case "GOOD" -> "Tốt";
      case "ACCEPTABLE" -> "Đọc được";
      default -> "Chưa phân loại";
    };
  }

  private CheckoutQuote quote(Store.Data data, long userId, boolean lockCopies) {
    var cart = data.cart(userId);
    if (cart == null) return emptyQuote();
    List<CartItem> cartItems = new ArrayList<>(data.cartItems(cart.id));
    cartItems.sort(Comparator.comparingLong(item -> item.copyId));
    if (cartItems.isEmpty()) return emptyQuote();

    if (lockCopies) {
      SortedSet<Long> shopIds = new TreeSet<>(), categoryIds = new TreeSet<>();
      for (CartItem cartItem : cartItems) {
        BookCopy copy = data.copy(cartItem.copyId, false);
        if (copy == null) continue;
        shopIds.add(copy.shopId);
        Product product = data.product(copy.productId);
        if (product != null && product.category != null) categoryIds.add(product.category.id);
      }
      for (long shopId : shopIds) data.shop(shopId, true);
      for (long categoryId : categoryIds) data.category(categoryId, true);
    }

    Map<Long, MutableQuoteShop> grouped = new LinkedHashMap<>();
    List<String> issues = new ArrayList<>();
    StringBuilder signature = new StringBuilder();
    for (CartItem cartItem : cartItems) {
      BookCopy copy = data.copy(cartItem.copyId, lockCopies);
      Product product = copy == null ? null : data.product(copy.productId);
      Shop shop = copy == null ? null : data.shop(copy.shopId, false);
      boolean available = sellable(data, copy);
      if (!available) issues.add("Một cuốn sách trong giỏ không còn sẵn bán. Hãy bỏ cuốn đó rồi thử lại.");
      long shopId = copy == null ? 0 : copy.shopId;
      String shopName = shop == null ? "Cửa hàng không còn hoạt động" : shop.name;
      BigDecimal price = copy == null || copy.price == null ? BigDecimal.ZERO : copy.price;
      QuoteLine line = new QuoteLine(cartItem.copyId, copy == null ? 0 : copy.productId,
          product == null ? "Sách không còn tồn tại" : product.title,
          product == null ? "" : Objects.toString(product.image, ""),
          copy == null ? "" : copy.condition, conditionLabel(copy == null ? "" : copy.condition),
          copy == null ? "" : copy.code, price, available);
      MutableQuoteShop group = grouped.computeIfAbsent(shopId, ignored -> new MutableQuoteShop(shopId, shopName));
      group.lines.add(line);
      group.subtotal = group.subtotal.add(price);
      signature.append(cartItem.copyId).append(':').append(line.productId()).append(':').append(shopId)
          .append(':').append(price.toPlainString()).append(':').append(copy == null ? "missing" : copy.status)
          .append(':').append(copy == null ? -1 : copy.version).append(';');
    }
    if (issues.size() > 1)
      issues = new ArrayList<>(List.of("Một số cuốn sách trong giỏ không còn sẵn bán. Hãy kiểm tra lại giỏ hàng."));
    BigDecimal subtotal = grouped.values().stream().map(group -> group.subtotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal shipping = SHIPPING_FEE_PER_SHOP.multiply(BigDecimal.valueOf(grouped.size()));
    List<QuoteShop> shops = grouped.values().stream().map(group -> new QuoteShop(group.shopId,
        group.shopName, List.copyOf(group.lines), group.subtotal, SHIPPING_FEE_PER_SHOP,
        group.subtotal.add(SHIPPING_FEE_PER_SHOP))).toList();
    signature.append("shipping:").append(shipping.toPlainString());
    return new CheckoutQuote(shops, subtotal, shipping, subtotal.add(shipping), digest(signature.toString()),
        issues.isEmpty(), List.copyOf(issues));
  }

  private CheckoutQuote emptyQuote() {
    return new CheckoutQuote(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        digest("empty"), false, List.of("Giỏ hàng đang trống."));
  }

  private OrderDetail detail(Store.Data data, Order order) {
    List<OrderItemView> items = data.orderItems(order.id).stream().map(item -> itemView(data, item)).toList();
    List<StatusView> history = data.orderStatuses(order.id).stream()
        .map(item -> new StatusView(item.status, statusLabel(item.status), item.note, item.createdAt)).toList();
    return new OrderDetail(summary(data, order), items, history);
  }

  private OrderSummary summary(Store.Data data, Order order) {
    Shop shop = data.shop(order.shopId, false);
    String shopName = notBlank(order.shopNameSnapshot)
        ? order.shopNameSnapshot : shop == null ? "Cửa hàng" : shop.name;
    String next = NEXT_STATUS.getOrDefault(order.status, "");
    boolean online = "PAYOS".equals(order.paymentMethod);
    boolean settled = Set.of("PAID", "REVIEW_REQUIRED").contains(order.paymentStatus);
    if (online && !"PAID".equals(order.paymentStatus)) next = "";
    return new OrderSummary(order.id, order.shopId, shopName, order.status, statusLabel(order.status),
        order.recipientName, order.phone, order.addressSnapshot, order.subtotal, order.shippingFee,
        order.total, order.createdAt, order.updatedAt, "PENDING".equals(order.status) && (!online || !settled),
        Set.of("PENDING", "CONFIRMED").contains(order.status) && (!online || !settled), next,
        next.isEmpty() ? "" : statusLabel(next), order.paymentMethod,
        online ? "Chuyển khoản qua PayOS" : "Thanh toán khi nhận hàng", order.paymentStatus,
        vn.edu.utex.bookstore.payment.PaymentService.statusLabel(order.paymentStatus),
        online && "PENDING".equals(order.status) && Set.of("UNPAID", "PENDING").contains(order.paymentStatus), order.paymentExpiresAt);
  }

  private OrderItemView itemView(Store.Data data, OrderItem item) {
    Product product = data.product(item.productId);
    BookCopy copy = data.copy(item.copyId, false);
    String title = notBlank(item.titleSnapshot) ? item.titleSnapshot : product == null ? "Sách" : product.title;
    String cover = item.coverUrlSnapshot == null ? product == null ? "" : Objects.toString(product.image, "") : item.coverUrlSnapshot;
    String condition = notBlank(item.conditionSnapshot) ? item.conditionSnapshot : copy == null ? "" : copy.condition;
    String code = notBlank(item.copyCodeSnapshot) ? item.copyCodeSnapshot : copy == null ? "" : copy.code;
    return new OrderItemView(item.copyId, item.productId, title, cover, condition,
        conditionLabel(condition), code, item.unitPrice);
  }

  private void cancel(Store.Data data, Order order, String note, Instant now) {
    releaseInventory(data, order, now);
    order.status = "CANCELLED";
    if (!"EXPIRED".equals(order.paymentStatus)) order.paymentStatus = "CANCELLED";
    order.updatedAt = now;
    data.saveOrder(order);
    recordStatus(data, order.id, "CANCELLED", note, now);
    updateGroupStatus(data, order.checkoutGroupId);
  }

  private void releaseInventory(Store.Data data, Order order, Instant now) {
    Map<Long, InventoryReservation> active = data.reservationsForOrder(order.id).stream()
        .filter(reservation -> "ACTIVE".equals(reservation.status))
        .collect(Collectors.toMap(reservation -> reservation.copyId, reservation -> reservation));
    List<OrderItem> items = new ArrayList<>(data.orderItems(order.id));
    items.sort(Comparator.comparingLong(item -> item.copyId));
    for (OrderItem item : items) {
      InventoryReservation reservation = active.get(item.copyId);
      BookCopy copy = data.copy(item.copyId, true);
      if (reservation == null || copy == null || !"RESERVED".equals(copy.status))
        throw new Problem(409, "Kho sách của đơn không còn nhất quán. Vui lòng kiểm tra lại.");
      copy.status = "AVAILABLE";
      copy.updatedAt = now;
      data.saveCopy(copy);
      reservation.status = "RELEASED";
      data.saveReservation(reservation);
    }
  }

  private void completeInventory(Store.Data data, Order order, Instant now) {
    Map<Long, InventoryReservation> active = data.reservationsForOrder(order.id).stream()
        .filter(reservation -> "ACTIVE".equals(reservation.status))
        .collect(Collectors.toMap(reservation -> reservation.copyId, reservation -> reservation));
    List<OrderItem> items = new ArrayList<>(data.orderItems(order.id));
    items.sort(Comparator.comparingLong(item -> item.copyId));
    for (OrderItem item : items) {
      InventoryReservation reservation = active.get(item.copyId);
      BookCopy copy = data.copy(item.copyId, true);
      if (reservation == null || copy == null || !"RESERVED".equals(copy.status))
        throw new Problem(409, "Kho sách của đơn không còn nhất quán. Vui lòng kiểm tra lại.");
      copy.status = "SOLD";
      copy.updatedAt = now;
      data.saveCopy(copy);
      reservation.status = "COMMITTED";
      data.saveReservation(reservation);
    }
  }

  private void updateGroupStatus(Store.Data data, long groupId) {
    CheckoutGroup group = data.checkoutGroup(groupId, true);
    if (group == null) return;
    List<Order> children = data.ordersForGroup(groupId);
    if (children.stream().allMatch(order -> "CANCELLED".equals(order.status))) group.status = "CANCELLED";
    else if (children.stream().allMatch(order -> Set.of("COMPLETED", "CANCELLED").contains(order.status))) group.status = "COMPLETED";
    else if (children.stream().noneMatch(order -> "PENDING".equals(order.status))) group.status = "CONFIRMED";
    else group.status = "PENDING";
    data.saveCheckoutGroup(group);
  }

  private static void recordStatus(Store.Data data, long orderId, String status, String note, Instant now) {
    OrderStatusHistory history = new OrderStatusHistory();
    history.orderId = orderId;
    history.status = status;
    history.note = note;
    history.createdAt = now;
    data.saveOrderStatus(history);
  }

  private static String statusNote(String status) {
    return switch (status) {
      case "CONFIRMED" -> "Cửa hàng đã xác nhận và đang chuẩn bị sách";
      case "SHIPPING" -> "Đơn hàng đã được giao cho đơn vị vận chuyển";
      case "COMPLETED" -> "Đơn hàng đã giao thành công";
      default -> "Đã cập nhật đơn hàng";
    };
  }

  private static void requireActor(Store.Data data, Identity identity) {
    if (identity == null) throw new Problem(401, "Vui lòng đăng nhập.");
    User user = data.user(identity.id());
    if (user == null || !user.active || user.blocked || user.authVersion != identity.authVersion())
      throw new Problem(403, "Bạn không có quyền xem đơn hàng này.");
  }

  /** All changes to sibling orders take the checkout-group lock before the order lock. */
  public static Order lockedOrder(Store.Data data, long id) {
    Order observed = data.order(id, false);
    if (observed == null) return null;
    data.checkoutGroup(observed.checkoutGroupId, true);
    return data.order(id, true);
  }

  private static void requireSafeCancellation(Store.Data data, Order order) {
    if (!"PAYOS".equals(order.paymentMethod)) return;
    if (Set.of("PAID", "REVIEW_REQUIRED").contains(order.paymentStatus))
      throw new Problem(409, "Đơn đã nhận tiền hoặc cần kiểm tra thanh toán. Vui lòng liên hệ cửa hàng để xử lý hoàn tiền.");
    if (data.paymentForOrder(order.id, false) != null && !Set.of("CANCELLED", "EXPIRED").contains(order.paymentStatus))
      throw new Problem(409, "Vui lòng hủy qua trang thanh toán để kiểm tra kết quả chuyển khoản trước.");
  }

  private static String snapshot(Address address) {
    return java.util.stream.Stream.of(address.line1, address.ward, address.district, address.city)
        .filter(OrderService::notBlank).collect(Collectors.joining(", "));
  }

  private static boolean sellable(Store.Data data, BookCopy copy) {
    if (copy == null || !"AVAILABLE".equals(copy.status) || "UNCLASSIFIED".equals(copy.condition)) return false;
    Shop shop = data.shop(copy.shopId, false);
    Product product = data.product(copy.productId);
    return shop != null && "ACTIVE".equals(shop.status) && product != null
        && product.category != null && product.category.active;
  }

  private static String digest(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("Không thể tạo dấu kiểm tra đơn hàng.", impossible);
    }
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private static String requiredText(String raw, int min, int max, String message) {
    String value = raw == null ? "" : raw.strip();
    if (value.length() < min || value.length() > max || value.codePoints().anyMatch(Character::isISOControl))
      throw Problem.invalid(message);
    return value;
  }

  private static final class MutableQuoteShop {
    final long shopId;
    final String shopName;
    final List<QuoteLine> lines = new ArrayList<>();
    BigDecimal subtotal = BigDecimal.ZERO;
    MutableQuoteShop(long shopId, String shopName) {
      this.shopId = shopId;
      this.shopName = shopName;
    }
  }
}
