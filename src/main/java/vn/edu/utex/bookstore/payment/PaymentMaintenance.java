package vn.edu.utex.bookstore.payment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class PaymentMaintenance {
  private final PaymentService payments;
  public PaymentMaintenance(PaymentService payments) { this.payments = payments; }
  @Scheduled(fixedDelayString = "${payos.reconcile-delay-ms:60000}", initialDelay = 15000)
  public void check() { payments.maintain(); }
}
