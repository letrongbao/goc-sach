package vn.edu.utex.bookstore.payment;

import java.time.Instant;
import java.util.List;

public interface PaymentRepository {
  PaymentAttempt paymentForOrder(long orderId, boolean lock);
  PaymentAttempt paymentByCode(long orderCode);
  PaymentAttempt savePayment(PaymentAttempt attempt);
  PaymentEvent paymentEvent(String reference);
  PaymentEvent savePaymentEvent(PaymentEvent event);
  List<PaymentEvent> paymentEvents(long attemptId);
  List<PaymentAttempt> paymentsNeedingReview(int limit);
  List<Long> dueOnlineOrders(Instant now, int limit);
  default List<Long> openPaymentOrders(int limit) { return openPaymentOrders(limit, 0); }
  List<Long> openPaymentOrders(int limit, long afterOrderId);
}
