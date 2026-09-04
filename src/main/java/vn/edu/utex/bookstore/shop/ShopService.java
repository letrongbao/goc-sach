package vn.edu.utex.bookstore.shop;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.persistence.Store;

public final class ShopService {
  public record ShopView(long id, String name, String address, String status, String role, boolean canManageMembers) {}
  public record ApplicationView(long id, String name, String address, String status, String reviewNote, Long shopId) {}
  public record MemberView(long userId, String username, String role, boolean active, boolean owner) {}
  private final Store store;
  private final Clock clock;

  public ShopService(Store store, Clock clock) { this.store = store; this.clock = clock; }

  public List<ShopView> shops(Identity actor) {
    return store.tx(d -> {
      User user = actor(d, actor);
      return d.shopsFor(user.id, admin(user)).stream().map(shop -> view(d, user, shop)).toList();
    });
  }

  public ShopView get(Identity actor, long shopId) {
    return store.tx(d -> {
      User user = actor(d, actor);
      Shop shop = ShopAccess.require(d, actor, shopId, false);
      return view(d, user, shop);
    });
  }

  public List<ApplicationView> applications(Identity actor, boolean all) {
    return store.tx(d -> {
      User user = actor(d, actor);
      if (all) requireAdmin(user);
      return d.applicationsFor(user.id, all).stream().map(this::applicationView).toList();
    });
  }

  public long apply(Identity actor, String name, String address) {
    String validName = text(name, 3, 150, "Tên cửa hàng cần 3–150 ký tự.");
    String validAddress = text(address, 10, 300, "Địa chỉ cửa hàng cần 10–300 ký tự.");
    return store.tx(d -> {
      User user = actor(d, actor);
      User locked = d.lockUser(user.id);
      if (locked == null || locked.blocked || !locked.active || locked.authVersion != actor.authVersion()) throw forbidden();
      if (d.pendingApplication(user.id) != null) throw new Problem(409, "Bạn đã có một yêu cầu đang chờ duyệt.");
      ShopApplication application = new ShopApplication();
      application.applicantId = user.id;
      application.name = validName;
      application.address = validAddress;
      application.status = "PENDING";
      application.reviewNote = "";
      application.createdAt = clock.instant();
      d.saveApplication(application);
      event(d, user.id, null, application.id, "APPLICATION_SUBMITTED", validName);
      return application.id;
    });
  }

  public long approve(Identity actor, long applicationId) {
    return store.tx(d -> {
      User reviewer = actor(d, actor);
      requireAdmin(reviewer);
      ShopApplication application = requiredApplication(d, applicationId);
      if ("APPROVED".equals(application.status)) return application.shopId;
      if (!"PENDING".equals(application.status)) throw new Problem(409, "Yêu cầu này không còn chờ duyệt.");
      User owner = d.lockUser(application.applicantId);
      if (owner == null || !owner.active || owner.blocked) throw new Problem(409, "Chủ cửa hàng chưa thể hoạt động.");
      Shop shop = new Shop();
      shop.ownerId = owner.id;
      shop.name = application.name;
      shop.address = application.address;
      shop.status = "ACTIVE";
      shop.createdAt = clock.instant();
      d.saveShop(shop);
      ShopMembership member = new ShopMembership();
      member.shopId = shop.id;
      member.userId = owner.id;
      member.role = "SELLER";
      member.active = true;
      member.updatedAt = clock.instant();
      d.saveMembership(member);
      application.status = "APPROVED";
      application.shopId = shop.id;
      application.reviewedBy = reviewer.id;
      application.reviewedAt = clock.instant();
      application.reviewNote = "";
      d.saveApplication(application);
      event(d, reviewer.id, shop.id, application.id, "APPLICATION_APPROVED", "");
      return shop.id;
    });
  }

  public void reject(Identity actor, long applicationId, String reason) {
    String note = text(reason, 5, 500, "Ghi lý do từ chối từ 5–500 ký tự.");
    store.tx(d -> {
      User reviewer = actor(d, actor);
      requireAdmin(reviewer);
      ShopApplication application = requiredApplication(d, applicationId);
      if (!"PENDING".equals(application.status)) throw new Problem(409, "Yêu cầu này không còn chờ duyệt.");
      application.status = "REJECTED";
      application.reviewNote = note;
      application.reviewedBy = reviewer.id;
      application.reviewedAt = clock.instant();
      d.saveApplication(application);
      event(d, reviewer.id, null, application.id, "APPLICATION_REJECTED", note);
      return null;
    });
  }

  public void setSuspended(Identity actor, long shopId, boolean suspended, String reason) {
    String note = text(reason, 5, 500, "Ghi lý do thay đổi từ 5–500 ký tự.");
    store.tx(d -> {
      User user = actor(d, actor);
      requireAdmin(user);
      Shop shop = requiredShop(d, shopId, true);
      String status = suspended ? "SUSPENDED" : "ACTIVE";
      if (!status.equals(shop.status)) {
        shop.status = status;
        d.saveShop(shop);
        event(d, user.id, shop.id, null, suspended ? "SHOP_SUSPENDED" : "SHOP_REOPENED", note);
      }
      return null;
    });
  }

  public List<MemberView> members(Identity actor, long shopId) {
    return store.tx(d -> {
      User user = actor(d, actor);
      Shop shop = requiredShop(d, shopId, false);
      requireManager(d, user, shop);
      return d.members(shop.id).stream().map(member -> new MemberView(member.userId,
          d.user(member.userId).username, member.role, member.active, member.userId == shop.ownerId)).toList();
    });
  }

  public void setMember(Identity actor, long shopId, String login, String role, boolean active) {
    if (!Set.of("SELLER", "MANAGER").contains(role == null ? "" : role))
      throw Problem.invalid("Vai trò cửa hàng không hợp lệ.");
    store.tx(d -> {
      User user = actor(d, actor);
      Shop shop = requiredShop(d, shopId, true);
      requireManager(d, user, shop);
      if (!"ACTIVE".equals(shop.status)) throw new Problem(409, "Cửa hàng đang tạm khóa.");
      User target = d.findLogin(AuthService.normalize(login));
      if (target == null || !target.active || target.blocked) throw Problem.invalid("Không tìm thấy tài khoản đang hoạt động.");
      ShopMembership member = d.membership(shop.id, target.id);
      if (target.id == shop.ownerId) throw new Problem(409, "Không thay đổi quyền chủ cửa hàng bằng thao tác này.");
      if (!admin(user) && (!"SELLER".equals(role) || (member != null && !"SELLER".equals(member.role))))
        throw forbidden();
      if (member == null) {
        member = new ShopMembership();
        member.shopId = shop.id;
        member.userId = target.id;
      }
      member.role = role;
      member.active = active;
      member.updatedAt = clock.instant();
      d.saveMembership(member);
      event(d, user.id, shop.id, null, active ? "MEMBER_GRANTED" : "MEMBER_REVOKED", target.id + ":" + role);
      return null;
    });
  }

  private User actor(Store.Data d, Identity identity) {
    if (identity == null) throw new Problem(401, "Vui lòng đăng nhập.");
    User user = d.user(identity.id());
    if (user == null || !user.active || user.blocked || user.authVersion != identity.authVersion()) throw forbidden();
    return user;
  }
  private boolean admin(User user) { return "ADMIN".equals(user.role); }
  private void requireAdmin(User user) { if (!admin(user)) throw forbidden(); }
  private void requireMember(Store.Data d, User user, Shop shop) {
    if (admin(user)) return;
    ShopMembership member = d.membership(shop.id, user.id);
    if (member == null || !member.active) throw forbidden();
  }
  private void requireManager(Store.Data d, User user, Shop shop) {
    requireMember(d, user, shop);
    if (!admin(user) && !"MANAGER".equals(d.membership(shop.id, user.id).role)) throw forbidden();
  }
  private Shop requiredShop(Store.Data d, long id, boolean lock) {
    Shop shop = d.shop(id, lock);
    if (shop == null) throw Problem.missing();
    return shop;
  }
  private ShopApplication requiredApplication(Store.Data d, long id) {
    ShopApplication application = d.application(id, true);
    if (application == null) throw Problem.missing();
    return application;
  }
  private ShopView view(Store.Data d, User user, Shop shop) {
    String role = admin(user) ? "ADMIN" : d.membership(shop.id, user.id).role;
    return new ShopView(shop.id, shop.name, shop.address, shop.status, role,
        "ADMIN".equals(role) || "MANAGER".equals(role));
  }
  private ApplicationView applicationView(ShopApplication a) {
    return new ApplicationView(a.id, a.name, a.address, a.status, a.reviewNote, a.shopId);
  }
  private void event(Store.Data d, long actorId, Long shopId, Long applicationId, String action, String note) {
    ShopAuditEvent event = new ShopAuditEvent();
    event.actorId = actorId;
    event.shopId = shopId;
    event.applicationId = applicationId;
    event.action = action;
    event.note = note;
    event.createdAt = clock.instant();
    d.recordShopEvent(event);
  }
  private static String text(String raw, int min, int max, String message) {
    String value = raw == null ? "" : raw.strip();
    if (value.length() < min || value.length() > max || value.codePoints().anyMatch(Character::isISOControl))
      throw Problem.invalid(message);
    return value;
  }
  private Problem forbidden() { return new Problem(403, "Bạn không có quyền thao tác với cửa hàng này."); }
}
