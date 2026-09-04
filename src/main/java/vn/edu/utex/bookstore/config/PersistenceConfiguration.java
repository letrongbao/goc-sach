package vn.edu.utex.bookstore.config;

import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.orm.jpa.*;
import vn.edu.utex.bookstore.persistence.*;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.category.CategoryService;
import vn.edu.utex.bookstore.product.ProductService;
import vn.edu.utex.bookstore.mail.*;
import vn.edu.utex.bookstore.image.LocalImageStorage;

/** Production-only wiring; tests/preview supply an App without opening a database. */
@Configuration
@ConditionalOnMissingBean(App.class)
public class PersistenceConfiguration {
  @Bean Settings settings() { return Settings.load(); }

  @Bean
  LocalEntityManagerFactoryBean entityManagerFactory(Settings settings) {
    var factory = new LocalEntityManagerFactoryBean();
    factory.setPersistenceUnitName("bookstore");
    factory.setJpaPropertyMap(java.util.Map.of(
        "jakarta.persistence.jdbc.url", settings.require("db.url"),
        "jakarta.persistence.jdbc.user", settings.require("db.user"),
        "jakarta.persistence.jdbc.password", settings.require("db.password"),
        "hibernate.hbm2ddl.auto", "validate"));
    return factory;
  }

  @Bean
  JpaTransactionManager transactionManager(EntityManagerFactory factory) {
    return new JpaTransactionManager(factory);
  }

  @Bean(destroyMethod = "")
  Store store(EntityManagerFactory factory, JpaTransactionManager transactions) {
    return new JpaStore(factory, transactions);
  }

  @Bean Passwords passwords() { return new Passwords(); }
  @Bean AuthService authService(Store store, Passwords passwords, Clock clock) { return new AuthService(store, passwords, clock); }
  @Bean CategoryService categoryService(Store store) { return new CategoryService(store); }
  @Bean ProductService productService(Store store, Clock clock) { return new ProductService(store, clock); }
  @Bean vn.edu.utex.bookstore.shop.ShopService shopService(Store store, Clock clock) {
    return new vn.edu.utex.bookstore.shop.ShopService(store, clock);
  }
  @Bean RateLimiter rateLimiter(Clock clock) { return new RateLimiter(clock); }
  @Bean vn.edu.utex.bookstore.product.CatalogService catalogService(Store store) {
    return new vn.edu.utex.bookstore.product.CatalogService(store);
  }
  @Bean vn.edu.utex.bookstore.inventory.InventoryService inventoryService(Store store, Clock clock) {
    return new vn.edu.utex.bookstore.inventory.InventoryService(store, clock);
  }
  @Bean EmailSender emailSender(Settings settings) { return new SmtpEmailSender(settings); }
  @Bean LocalImageStorage imageStorage(Settings settings) {
    return new LocalImageStorage(java.nio.file.Path.of(settings.require("uploads.dir")));
  }
  @Bean OtpService otpService(Store store, Passwords passwords, EmailSender sender, Clock clock, Settings settings) {
    return new OtpService(store, passwords, sender, clock, settings.require("otp.secret"));
  }
  @Bean(destroyMethod = "close")
  App app(Settings settings, Store store, AuthService auth, CategoryService categories, LocalImageStorage images,
      RateLimiter limits, OtpService otp, ProductService products, vn.edu.utex.bookstore.shop.ShopService shops,
      vn.edu.utex.bookstore.inventory.InventoryService inventory, vn.edu.utex.bookstore.product.CatalogService catalog) {
    return new App(settings, store, auth, categories, images, limits, otp, products, shops, inventory, catalog);
  }
}
