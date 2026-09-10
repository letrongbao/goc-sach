package vn.edu.utex.bookstore.order;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import vn.edu.utex.bookstore.buyer.Address;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.inventory.BookCopy;
import vn.edu.utex.bookstore.persistence.Store;

/** Checkout reserves each physical copy once and creates one child order per shop. */
public final class OrderService {
  private static final BigDecimal SHIPPING_FEE_PER_SHOP = new BigDecimal("30000.00");
  public record CheckoutResult(List<Order> orders) {}
  private final Store store;
  private final Clock clock;
  public OrderService(Store store, Clock clock) { this.store=store; this.clock=clock; }

  public CheckoutResult checkout(long userId, long addressId, String rawKey) {
    String key = rawKey == null ? "" : rawKey.trim();
    if (!key.matches("[A-Za-z0-9._-]{16,80}")) throw Problem.invalid("Idempotency key không hợp lệ.");
    return store.tx(d -> {
      Order previous=d.orderByKey(userId,key); if(previous!=null)return new CheckoutResult(d.ordersFor(userId).stream().filter(o -> key.equals(o.idempotencyKey)).toList());
      Address address=d.address(addressId); if(address==null||address.userId!=userId)throw Problem.missing();
      var cart=d.cart(userId); if(cart==null)throw new Problem(409,"Giỏ hàng đang trống.");
      var lines=new ArrayList<>(d.cartItems(cart.id)); if(lines.isEmpty())throw new Problem(409,"Giỏ hàng đang trống.");
      lines.sort(Comparator.comparingLong(line -> line.copyId));
      Map<Long,List<BookCopy>> grouped=new LinkedHashMap<>();
      for(var line:lines){ BookCopy copy=d.copy(line.copyId,true); if(!sellable(d,copy))throw new Problem(409,"Một bản sách trong giỏ không còn sẵn bán."); grouped.computeIfAbsent(copy.shopId,k->new ArrayList<>()).add(copy); }
      Instant now=clock.instant(); List<Order> orders=new ArrayList<>();
      BigDecimal groupShipping = SHIPPING_FEE_PER_SHOP.multiply(BigDecimal.valueOf(grouped.size()));
      BigDecimal groupTotal = grouped.values().stream().flatMap(Collection::stream).map(c -> c.price).reduce(BigDecimal.ZERO, BigDecimal::add).add(groupShipping);
      CheckoutGroup group=new CheckoutGroup(); group.userId=userId; group.idempotencyKey=key; group.addressSnapshot=snapshot(address); group.shippingFee=groupShipping; group.total=groupTotal; group.status="PENDING"; group.createdAt=now; d.saveCheckoutGroup(group);
      for(var entry:grouped.entrySet()){
        BigDecimal subtotal=entry.getValue().stream().map(c->c.price).reduce(BigDecimal.ZERO,BigDecimal::add);
        Order order=new Order(); order.userId=userId;order.shopId=entry.getKey();order.checkoutGroupId=group.id;order.idempotencyKey=key;order.status="PENDING";order.recipientName=address.recipientName;order.phone=address.phone;order.addressSnapshot=snapshot(address);order.subtotal=subtotal;order.shippingFee=SHIPPING_FEE_PER_SHOP;order.total=subtotal.add(order.shippingFee);order.createdAt=now;order.updatedAt=now; d.saveOrder(order);
        for(BookCopy copy:entry.getValue()){
          copy.status="RESERVED";copy.updatedAt=now;d.saveCopy(copy);
          OrderItem item=new OrderItem();item.orderId=order.id;item.copyId=copy.id;item.productId=copy.productId;item.unitPrice=copy.price;d.saveOrderItem(item);
          InventoryReservation reservation=new InventoryReservation();reservation.orderId=order.id;reservation.copyId=copy.id;reservation.status="ACTIVE";reservation.createdAt=now;reservation.expiresAt=now.plus(Duration.ofMinutes(15));d.saveReservation(reservation);
          }
        OrderStatusHistory history=new OrderStatusHistory();history.orderId=order.id;history.status="PENDING";history.note="Tạo đơn từ giỏ hàng";history.createdAt=now;d.saveOrderStatus(history);orders.add(order);
      }
      for(var line:lines)d.deleteCartItem(cart.id,line.copyId); cart.updatedAt=now;d.saveCart(cart);
      return new CheckoutResult(List.copyOf(orders));
    });
  }

  public List<Order> orders(long userId){return store.tx(d->d.ordersFor(userId));}
  public void cancel(long userId, long orderId) {
    store.tx(d -> {
      Order order = d.order(orderId, true);
      if (order == null || order.userId != userId) throw Problem.missing();
      if (!"PENDING".equals(order.status)) throw new Problem(409, "Đơn này không còn có thể hủy.");
      Instant now = clock.instant();
      Set<Long> reservedCopyIds = d.reservationsForOrder(order.id).stream()
          .filter(reservation -> "ACTIVE".equals(reservation.status))
          .map(reservation -> reservation.copyId).collect(java.util.stream.Collectors.toSet());
      for (OrderItem item : d.orderItems(order.id)) {
        BookCopy copy = d.copy(item.copyId, true);
        if (reservedCopyIds.contains(item.copyId) && copy != null && "RESERVED".equals(copy.status)) { copy.status = "AVAILABLE"; copy.updatedAt = now; d.saveCopy(copy); }
      }
      for (InventoryReservation reservation : d.reservationsForOrder(order.id)) {
        if ("ACTIVE".equals(reservation.status)) { reservation.status = "RELEASED"; d.saveReservation(reservation); }
      }
      order.status = "CANCELLED"; order.updatedAt = now; d.saveOrder(order);
      OrderStatusHistory history = new OrderStatusHistory(); history.orderId = order.id; history.status = "CANCELLED"; history.note = "Khách hủy đơn"; history.createdAt = now; d.saveOrderStatus(history);
      return null;
    });
  }
  private static String snapshot(Address address) {
    return java.util.stream.Stream.of(address.line1, address.ward, address.district, address.city)
        .filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining(", "));
  }
  private static boolean sellable(Store.Data d,BookCopy c){return c!=null&&"AVAILABLE".equals(c.status)&&!"UNCLASSIFIED".equals(c.condition)&&d.shop(c.shopId,false)!=null&&"ACTIVE".equals(d.shop(c.shopId,false).status)&&d.product(c.productId)!=null&&d.product(c.productId).category.active;}
}
