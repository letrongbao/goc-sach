package vn.edu.utex.bookstore.shop;

import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.persistence.Store;

/** Shared live authorization for shop and inventory operations, inside the caller's transaction. */
public final class ShopAccess {
  private ShopAccess() {}

  public static Shop require(Store.Data data, Identity identity, long shopId, boolean write) {
    if (identity == null) throw new Problem(401, "Vui lòng đăng nhập.");
    Shop shop = data.shop(shopId, write);
    if (shop == null) throw Problem.missing();
    User user = data.user(identity.id());
    if (user == null || !user.active || user.blocked || user.authVersion != identity.authVersion())
      throw denied();
    if (!"ADMIN".equals(user.role)) {
      ShopMembership member = data.membership(shopId, user.id);
      if (member == null || !member.active || !("SELLER".equals(member.role) || "MANAGER".equals(member.role)))
        throw denied();
    }
    if (write && !"ACTIVE".equals(shop.status)) throw new Problem(409, "Cửa hàng đang tạm khóa.");
    return shop;
  }

  private static Problem denied() { return new Problem(403, "Bạn không có quyền thao tác với cửa hàng này."); }
}
