package vn.edu.utex.bookstore.buyer;

import java.util.List;

public interface BuyerRepository {
  CustomerProfile profile(long userId);
  CustomerProfile saveProfile(CustomerProfile profile);
  List<Address> addresses(long userId);
  Address address(long id);
  Address saveAddress(Address address);
  void clearDefaultAddresses(long userId);
  void deleteAddress(long id);
  Favorite favorite(long userId, long productId);
  List<Favorite> favorites(long userId);
  Favorite saveFavorite(Favorite favorite);
  void deleteFavorite(long userId, long productId);
  RecentlyViewed viewed(long userId, long productId);
  List<RecentlyViewed> recentlyViewed(long userId, int limit);
  RecentlyViewed saveViewed(RecentlyViewed viewed);
  Cart cart(long userId);
  Cart saveCart(Cart cart);
  List<CartItem> cartItems(long cartId);
  CartItem cartItem(long cartId, long copyId);
  CartItem saveCartItem(CartItem item);
  void deleteCartItem(long cartId, long copyId);
}
