package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import vn.edu.utex.bookstore.order.Order;
import vn.edu.utex.bookstore.payment.PaymentAttempt;
import vn.edu.utex.bookstore.payment.PaymentEvent;
import vn.edu.utex.bookstore.persistence.PaymentQueries;

class PaymentQueryTest {
  @Test void allMappingsBootAndActualPaymentQueriesParseWithoutDatabaseOrDdl() {
    var registry = new StandardServiceRegistryBuilder()
        .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
        .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
        .applySetting("hibernate.connection.provider_class", "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl")
        .applySetting("hibernate.hbm2ddl.auto", "none").build();
    try {
      var sources = new MetadataSources(registry);
      for (String resource : new String[] {"orm", "shop-orm", "inventory-orm", "buyer-orm", "order-orm", "review-orm", "media-orm", "payment-orm"}) {
        sources.addResource("META-INF/" + resource + ".xml");
      }
      var metadata = sources.buildMetadata();
      assertColumn(metadata, Order.class, "paymentMethod", "payment_method", false);
      assertColumn(metadata, Order.class, "paymentStatus", "payment_status", false);
      assertColumn(metadata, Order.class, "paymentExpiresAt", "payment_expires_at", true);
      assertColumn(metadata, PaymentAttempt.class, "orderCode", "order_code", false);
      assertColumn(metadata, PaymentAttempt.class, "amount", "amount", false);
      assertColumn(metadata, PaymentAttempt.class, "status", "status", false);
      assertColumn(metadata, PaymentAttempt.class, "paymentLinkId", "payment_link_id", false);
      assertColumn(metadata, PaymentAttempt.class, "expiresAt", "expires_at", false);
      assertColumn(metadata, PaymentEvent.class, "reference", "reference", false);
      assertColumn(metadata, PaymentEvent.class, "outcome", "outcome", false);
      assertColumn(metadata, PaymentEvent.class, "source", "source", false);
      try (var factory = metadata.buildSessionFactory(); var session = factory.openSession()) {
        assertDoesNotThrow(() -> session.createQuery(PaymentQueries.DUE_ORDERS, Long.class)
            .setParameter("now", Instant.parse("2026-10-02T03:00:00Z")).setMaxResults(25));
        assertDoesNotThrow(() -> session.createQuery(PaymentQueries.OPEN_ORDERS, Long.class)
            .setParameter("after", 10L).setMaxResults(25));
        // No execution or transaction: this provider cannot open a database connection.
      }
    } finally { StandardServiceRegistryBuilder.destroy(registry); }
  }

  private static void assertColumn(Metadata metadata, Class<?> entity, String property, String name, boolean nullable) {
    var binding = metadata.getEntityBinding(entity.getName());
    assertNotNull(binding, entity.getSimpleName());
    var column = binding.getProperty(property).getValue().getColumns().getFirst();
    assertEquals(name, column.getName(), entity.getSimpleName() + "." + property);
    assertEquals(nullable, column.isNullable(), entity.getSimpleName() + "." + property);
  }
}
