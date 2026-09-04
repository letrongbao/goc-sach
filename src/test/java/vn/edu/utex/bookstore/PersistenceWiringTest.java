package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import jakarta.persistence.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.orm.jpa.JpaTransactionManager;
import vn.edu.utex.bookstore.config.*;
import vn.edu.utex.bookstore.auth.AuthService;

class PersistenceWiringTest {
  @Test void productionWiringOwnsOneFactoryAndUsesValidateOnly() {
    var properties = new Properties();
    properties.setProperty("db.url", "jdbc:postgresql://unused/goc_sach_test");
    properties.setProperty("db.user", "test");
    properties.setProperty("db.password", "test-only");
    properties.setProperty("uploads.dir", "target/test-uploads");
    properties.setProperty("otp.secret", "test-only-secret-not-for-production-0123456789");
    var factory = mock(EntityManagerFactory.class);
    when(factory.getProperties()).thenReturn(Map.of());
    try (var settings = mockStatic(Settings.class); var persistence = mockStatic(Persistence.class)) {
      settings.when(Settings::load).thenReturn(new Settings(properties));
      persistence.when(() -> Persistence.createEntityManagerFactory(eq("bookstore"), anyMap())).thenReturn(factory);
      new ApplicationContextRunner().withUserConfiguration(PersistenceConfiguration.class)
          .withBean(Clock.class, Clock::systemUTC).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(EntityManagerFactory.class).size());
            assertNotNull(context.getBean(JpaTransactionManager.class));
            assertSame(context.getBean(AuthService.class), context.getBean(App.class).auth);
            assertFalse(context.getBean(App.class).settings.get("db.url", "").contains("bookstore_03"));
          });
      persistence.verify(() -> Persistence.createEntityManagerFactory(eq("bookstore"), argThat(map ->
          "validate".equals(map.get("hibernate.hbm2ddl.auto")))), times(1));
      verify(factory, times(1)).close();
    }
  }
}
