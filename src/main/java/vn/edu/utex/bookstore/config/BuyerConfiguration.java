package vn.edu.utex.bookstore.config;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import vn.edu.utex.bookstore.buyer.BuyerService;

/** Wires account-scoped features for both the real and preview App instances. */
@Configuration
public class BuyerConfiguration {
  @Bean
  @ConditionalOnMissingBean
  BuyerService buyerService(App app) { return new BuyerService(app.store, Clock.systemUTC()); }
}
