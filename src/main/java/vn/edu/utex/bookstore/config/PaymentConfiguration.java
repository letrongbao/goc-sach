package vn.edu.utex.bookstore.config;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.scheduling.annotation.EnableScheduling;
import vn.edu.utex.bookstore.order.OrderService;
import vn.edu.utex.bookstore.payment.*;

@Configuration
@EnableScheduling
public class PaymentConfiguration {
  @Bean(destroyMethod = "close") @ConditionalOnMissingBean(PaymentGateway.class)
  PaymentGateway paymentGateway(App app) { return new PayOsGateway(PayOsSettings.from(app.settings)); }
  @Bean @ConditionalOnMissingBean(PaymentService.class)
  PaymentService paymentService(App app, OrderService orders, PaymentGateway gateway) {
    return new PaymentService(app.store, orders, gateway, Clock.systemUTC());
  }
}
