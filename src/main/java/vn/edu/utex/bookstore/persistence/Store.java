package vn.edu.utex.bookstore.persistence;

import java.util.function.Function;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.category.*;
import vn.edu.utex.bookstore.buyer.BuyerRepository;
import vn.edu.utex.bookstore.order.OrderRepository;
import vn.edu.utex.bookstore.review.ReviewRepository;

public interface Store extends AutoCloseable {
  interface Data
      extends UserRepository,
          TokenRepository,
          CategoryRepository,
          OtpRepository,
          vn.edu.utex.bookstore.product.ProductRepository,
          vn.edu.utex.bookstore.product.CatalogRepository,
          vn.edu.utex.bookstore.shop.ShopRepository,
          vn.edu.utex.bookstore.inventory.InventoryRepository,
          BuyerRepository,
          OrderRepository,
          vn.edu.utex.bookstore.payment.PaymentRepository,
          ReviewRepository {}

  <T> T tx(Function<Data, T> action);

  @Override
  default void close() {}
}
