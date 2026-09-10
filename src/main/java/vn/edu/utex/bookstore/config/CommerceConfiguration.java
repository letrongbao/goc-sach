package vn.edu.utex.bookstore.config;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import vn.edu.utex.bookstore.order.OrderService;
import vn.edu.utex.bookstore.review.ReviewService;

@Configuration
public class CommerceConfiguration {
  @Bean
  @ConditionalOnMissingBean
  OrderService orderService(App app) { return new OrderService(app.store, Clock.systemUTC()); }
  @Bean
  @ConditionalOnMissingBean
  ReviewService reviewService(App app) { return new ReviewService(app.store, Clock.systemUTC()); }
}
