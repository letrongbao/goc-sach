package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.hibernate.boot.*;
import org.hibernate.boot.registry.*;
import vn.edu.utex.bookstore.persistence.CatalogQueries;
import vn.edu.utex.bookstore.product.Product;
import vn.edu.utex.bookstore.product.CatalogRepository;

class CatalogQueryTest {
  @Test void actualCatalogJpqlParsesWithMappingsWithoutDatabaseOrDdl() {
    var registry = new StandardServiceRegistryBuilder()
        .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
        .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
        .applySetting("hibernate.connection.provider_class", "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl")
        .applySetting("hibernate.hbm2ddl.auto", "none").build();
    try (var factory = new MetadataSources(registry).addResource("META-INF/orm.xml")
        .addResource("META-INF/shop-orm.xml").addResource("META-INF/inventory-orm.xml")
        .addResource("META-INF/buyer-orm.xml").addResource("META-INF/order-orm.xml").addResource("META-INF/review-orm.xml").addResource("META-INF/operations-orm.xml").buildMetadata().buildSessionFactory();
        var session = factory.openSession()) {
      assertDoesNotThrow(() -> filtered(session.createQuery(CatalogQueries.PRODUCTS, Product.class)));
      assertDoesNotThrow(() -> filtered(session.createQuery(CatalogQueries.COUNT, Long.class)));
      for (var sort : CatalogRepository.Sort.values()) {
        for (boolean favoritesOnly : List.of(false, true)) {
          var filter = new CatalogRepository.Filter("", 0L, java.math.BigDecimal.ZERO,
              new java.math.BigDecimal("9999999999.99"), "", 0L, sort, favoritesOnly);
          assertDoesNotThrow(() -> filtered(session.createQuery(CatalogQueries.products(filter), Product.class)));
          assertDoesNotThrow(() -> filtered(session.createQuery(CatalogQueries.count(filter), Long.class)));
        }
      }
      assertDoesNotThrow(() -> offerFiltered(session.createQuery(CatalogQueries.STOCK, Object[].class)
          .setParameter("ids", List.of(1L))));
      assertDoesNotThrow(() -> session.createQuery(CatalogQueries.OFFERS, Object[].class).setParameter("product", 1L));
      assertDoesNotThrow(() -> session.createQuery(CatalogQueries.SHOPS, Object[].class));
      assertDoesNotThrow(() -> session.createQuery(CatalogQueries.SHOP, Object[].class).setParameter("shop", 1L));
      // No query execution or transaction begin: the connection provider has no connections.
    } finally { StandardServiceRegistryBuilder.destroy(registry); }
  }

  private static <T> org.hibernate.query.Query<T> filtered(org.hibernate.query.Query<T> query) {
    return offerFiltered(query.setParameter("q", "book").setParameter("category", 0L));
  }

  private static <T> org.hibernate.query.Query<T> offerFiltered(org.hibernate.query.Query<T> query) {
    return query.setParameter("shop", 0L).setParameter("condition", "")
        .setParameter("minimum", java.math.BigDecimal.ZERO)
        .setParameter("maximum", new java.math.BigDecimal("9999999999.99"));
  }
}
