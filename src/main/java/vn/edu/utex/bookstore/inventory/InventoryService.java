package vn.edu.utex.bookstore.inventory;

import java.math.*;
import java.time.*;
import java.util.*;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.persistence.Store;
import vn.edu.utex.bookstore.shop.ShopAccess;

public final class InventoryService {
  public record CopyView(long id, long productId, String title, String code, String condition,
      String defects, BigDecimal price, String status, int version) {}
  public record Page(List<CopyView> items, int number, int pages, long total) {}
  public record MovementView(String action, String oldStatus, String newStatus, String oldCondition,
      String newCondition, BigDecimal oldPrice, BigDecimal newPrice, String reason, Instant createdAt) {
    public String getDisplayTime() {
      return java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"))
          .withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(createdAt);
    }
  }
  private final Store store;
  private final Clock clock;
  private static final Set<String> CONDITIONS = Set.of("UNCLASSIFIED", "LIKE_NEW", "GOOD", "ACCEPTABLE");

  public InventoryService(Store store, Clock clock) { this.store = store; this.clock = clock; }

  public Page page(Identity actor, long shopId, String rawPage) {
    int number;
    try { number = rawPage == null || rawPage.isBlank() ? 1 : Integer.parseInt(rawPage); }
    catch (NumberFormatException e) { throw Problem.invalid("Số trang không hợp lệ."); }
    if (number < 1) throw Problem.invalid("Số trang phải lớn hơn 0.");
    final int page = number;
    return store.tx(d -> {
      ShopAccess.require(d, actor, shopId, false);
      long total = d.copyCount(shopId);
      int pages = (int) Math.max(1, (total + 19) / 20);
      if (page > pages) throw Problem.missing();
      return new Page(d.copies(shopId, Math.multiplyExact(page - 1, 20), 20).stream()
          .map(c -> view(d, c)).toList(), page, pages, total);
    });
  }

  public CopyView get(Identity actor, long shopId, long copyId) {
    return store.tx(d -> {
      ShopAccess.require(d, actor, shopId, false);
      return view(d, requiredCopy(d, shopId, copyId, false));
    });
  }

  public List<MovementView> history(Identity actor, long shopId, long copyId) {
    return store.tx(d -> {
      ShopAccess.require(d, actor, shopId, false);
      requiredCopy(d, shopId, copyId, false);
      return d.movements(copyId).stream().map(m -> new MovementView(m.action, m.oldStatus, m.newStatus,
          m.oldCondition, m.newCondition, m.oldPrice, m.newPrice, m.reason, m.createdAt)).toList();
    });
  }

  public long receive(Identity actor, long shopId, long productId, String condition, String defects,
      String rawPrice, String reason) {
    String validCondition = condition(condition), detail = defects(defects), note = reason(reason);
    BigDecimal price = price(rawPrice);
    return store.tx(d -> {
      ShopAccess.require(d, actor, shopId, true);
      if (d.product(productId) == null) throw Problem.invalid("Đầu sách không tồn tại.");
      BookCopy copy = new BookCopy();
      copy.shopId = shopId; copy.productId = productId;
      copy.code = "GS-" + UUID.randomUUID();
      copy.condition = validCondition; copy.defects = detail; copy.price = price;
      copy.status = "UNAVAILABLE"; copy.createdAt = clock.instant(); copy.updatedAt = copy.createdAt;
      d.saveCopy(copy);
      movement(d, actor, copy, "RECEIVED", null, null, null, note);
      return copy.id;
    });
  }

  public void update(Identity actor, long shopId, long copyId, int expectedVersion, String condition,
      String defects, String rawPrice, String status, String reason) {
    String validCondition = condition(condition), detail = defects(defects), note = reason(reason);
    BigDecimal price = price(rawPrice);
    if (!("AVAILABLE".equals(status) || "UNAVAILABLE".equals(status)))
      throw Problem.invalid("Chỉ được mở bán hoặc ngừng bán tại đây.");
    if ("AVAILABLE".equals(status) && "UNCLASSIFIED".equals(validCondition))
      throw Problem.invalid("Phân loại tình trạng sách trước khi mở bán.");
    store.tx(d -> {
      ShopAccess.require(d, actor, shopId, true);
      BookCopy copy = requiredCopy(d, shopId, copyId, true);
      if (copy.version != expectedVersion) throw new Problem(409, "Bản sách đã thay đổi. Tải lại trang rồi thử lại.");
      if (!("AVAILABLE".equals(copy.status) || "UNAVAILABLE".equals(copy.status)))
        throw new Problem(409, "Sách đang được giữ hoặc đã bán, không thể sửa tại kho.");
      String oldStatus = copy.status, oldCondition = copy.condition;
      BigDecimal oldPrice = copy.price;
      if (oldStatus.equals(status) && oldCondition.equals(validCondition) && oldPrice.compareTo(price) == 0
          && Objects.equals(copy.defects, detail)) return null;
      copy.condition = validCondition; copy.defects = detail; copy.price = price; copy.status = status;
      copy.updatedAt = clock.instant();
      d.saveCopy(copy);
      movement(d, actor, copy, "ADJUSTED", oldStatus, oldCondition, oldPrice, note);
      return null;
    });
  }

  private BookCopy requiredCopy(Store.Data d, long shopId, long id, boolean lock) {
    BookCopy copy = d.copy(id, lock);
    if (copy == null || copy.shopId != shopId) throw Problem.missing();
    return copy;
  }
  private CopyView view(Store.Data d, BookCopy c) {
    return new CopyView(c.id, c.productId, d.product(c.productId).title, c.code, c.condition,
        c.defects, c.price, c.status, c.version);
  }
  private void movement(Store.Data d, Identity actor, BookCopy c, String action,
      String oldStatus, String oldCondition, BigDecimal oldPrice, String reason) {
    InventoryMovement m = new InventoryMovement();
    m.copyId = c.id; m.actorId = actor.id(); m.action = action;
    m.oldStatus = oldStatus; m.newStatus = c.status; m.oldCondition = oldCondition; m.newCondition = c.condition;
    m.oldPrice = oldPrice; m.newPrice = c.price; m.reason = reason; m.createdAt = clock.instant();
    d.recordMovement(m);
  }
  private static String condition(String value) {
    if (value == null || !CONDITIONS.contains(value)) throw Problem.invalid("Tình trạng sách không hợp lệ.");
    return value;
  }
  private static String defects(String raw) {
    String value = raw == null ? "" : raw.strip();
    if (value.length() > 2000) throw Problem.invalid("Mô tả tình trạng tối đa 2.000 ký tự.");
    return value;
  }
  private static String reason(String raw) {
    String value = raw == null ? "" : raw.strip();
    if (value.length() < 5 || value.length() > 500) throw Problem.invalid("Lý do cần 5–500 ký tự.");
    return value;
  }
  private static BigDecimal price(String raw) {
    try {
      if (raw == null || !raw.matches("[0-9]{1,10}(\\.[0-9]{1,2})?")) throw new NumberFormatException();
      BigDecimal value = new BigDecimal(raw).setScale(2, RoundingMode.UNNECESSARY);
      if (value.signum() < 0 || value.precision() > 12) throw new NumberFormatException();
      return value;
    } catch (IllegalArgumentException | ArithmeticException e) {
      throw Problem.invalid("Giá phải không âm, tối đa 2 số lẻ và không vượt 9.999.999.999,99.");
    }
  }
}
