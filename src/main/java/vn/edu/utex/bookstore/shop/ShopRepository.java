package vn.edu.utex.bookstore.shop;

import java.util.List;

public interface ShopRepository {
  Shop shop(long id, boolean lock);
  List<Shop> shopsFor(long userId, boolean all);
  Shop saveShop(Shop shop);
  ShopApplication application(long id, boolean lock);
  ShopApplication pendingApplication(long userId);
  List<ShopApplication> applicationsFor(long userId, boolean all);
  ShopApplication saveApplication(ShopApplication application);
  ShopMembership membership(long shopId, long userId);
  List<ShopMembership> members(long shopId);
  void saveMembership(ShopMembership membership);
  void recordShopEvent(ShopAuditEvent event);
}
