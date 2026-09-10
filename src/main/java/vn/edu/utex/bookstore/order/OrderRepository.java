package vn.edu.utex.bookstore.order;

import java.util.List;

public interface OrderRepository {
  CheckoutGroup saveCheckoutGroup(CheckoutGroup group);
  Order orderByKey(long userId, String idempotencyKey);
  Order order(long id, boolean lock);
  Order saveOrder(Order order);
  OrderItem saveOrderItem(OrderItem item);
  OrderStatusHistory saveOrderStatus(OrderStatusHistory history);
  InventoryReservation saveReservation(InventoryReservation reservation);
  List<InventoryReservation> reservationsForOrder(long orderId);
  List<Order> ordersFor(long userId);
  List<OrderItem> orderItems(long orderId);
}
