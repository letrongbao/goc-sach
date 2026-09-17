package vn.edu.utex.bookstore.order;

import java.util.List;

public interface OrderRepository {
  CheckoutGroup checkoutGroup(long id, boolean lock);
  CheckoutGroup saveCheckoutGroup(CheckoutGroup group);
  Order orderByKey(long userId, String idempotencyKey);
  List<Order> ordersByKey(long userId, String idempotencyKey);
  Order order(long id, boolean lock);
  Order saveOrder(Order order);
  OrderItem saveOrderItem(OrderItem item);
  OrderStatusHistory saveOrderStatus(OrderStatusHistory history);
  InventoryReservation saveReservation(InventoryReservation reservation);
  List<InventoryReservation> reservationsForOrder(long orderId);
  List<Order> ordersFor(long userId);
  List<Order> ordersForShop(long shopId);
  List<Order> ordersForGroup(long checkoutGroupId);
  List<OrderItem> orderItems(long orderId);
  List<OrderStatusHistory> orderStatuses(long orderId);
}
