package vn.edu.utex.bookstore.persistence;

/** Shared definitions let offline tests validate the actual maintenance queries. */
public final class PaymentQueries {
  private PaymentQueries() {}

  public static final String DUE_ORDERS = "select o.id from Order o where o.paymentMethod='PAYOS' "
      + "and o.status='PENDING' and o.paymentStatus in ('UNPAID','PENDING') "
      + "and o.paymentExpiresAt<=:now order by o.paymentExpiresAt,o.id";

  public static final String OPEN_ORDERS = "select o.id from Order o where o.paymentMethod='PAYOS' "
      + "and o.status='PENDING' and o.id>:after "
      + "and ((o.paymentStatus in ('UNPAID','PENDING') and exists "
      + "(select p.id from PaymentAttempt p where p.orderId=o.id and p.status in ('CREATING','PENDING','PROCESSING'))) "
      + "or (o.paymentStatus in ('CANCELLED','EXPIRED') and (not exists "
      + "(select p.id from PaymentAttempt p where p.orderId=o.id) or exists "
      + "(select p.id from PaymentAttempt p where p.orderId=o.id and p.status in ('CANCELLED','EXPIRED'))))) "
      + "order by o.id";
}
