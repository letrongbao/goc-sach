package vn.edu.utex.bookstore.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import vn.edu.utex.bookstore.config.Settings;

class PayOsSettingsTest {
  @Test
  void disabledWithoutCredentials() {
    var settings = PayOsSettings.from(new Settings(new Properties()));
    assertFalse(settings.enabled());
    assertThrows(IllegalStateException.class, settings::returnUrl);
  }

  @Test
  void enabledRequiresAllCredentialsAndAnApplicationUrl() {
    for (String name : new String[] {"payos.client-id", "payos.api-key", "payos.checksum-key", "payos.base-url"}) {
      var values = properties("https://gocsach.example");
      values.remove(name);
      var error = assertThrows(IllegalStateException.class, () -> PayOsSettings.from(new Settings(values)));
      assertFalse(error.getMessage().contains("api-test"));
      assertFalse(error.getMessage().contains("checksum-test"));
    }
  }

  @Test
  void derivesRoutesAndNeverIncludesCredentialsInToString() {
    var settings = PayOsSettings.from(new Settings(properties("https://gocsach.example/bookstore/")));
    assertEquals("https://gocsach.example/bookstore/payments/payos/return", settings.returnUrl());
    assertEquals("https://gocsach.example/bookstore/payments/payos/cancel", settings.cancelUrl());
    assertEquals("https://gocsach.example/bookstore/payments/payos/webhook", settings.webhookUrl());
    assertFalse(settings.toString().contains("api-test"));
    assertFalse(settings.toString().contains("checksum-test"));
  }

  @Test
  void localhostReturnWorksButCannotBeRegisteredForWebhook() {
    for (String value : new String[] {"http://localhost:8080", "http://127.0.0.1:8080", "https://localhost:8080"}) {
      var settings = PayOsSettings.from(new Settings(properties(value)));
      assertTrue(settings.returnUrl().startsWith(value));
      assertThrows(IllegalStateException.class, settings::webhookUrl);
    }
  }

  @Test
  void separateWebhookAllowsExposingOnlyTheReceiverWhileBrowserRemainsLocal() {
    var values = properties("http://localhost:8080");
    values.setProperty("payos.webhook-url", "https://gocsach.example/payments/payos/webhook");
    var settings = PayOsSettings.from(new Settings(values));
    assertEquals("http://localhost:8080/payments/payos/return", settings.returnUrl());
    assertEquals("http://localhost:8080/payments/payos/cancel", settings.cancelUrl());
    assertEquals("https://gocsach.example/payments/payos/webhook", settings.webhookUrl());
  }

  @Test
  void explicitWebhookMustBePublicHttpsAndTheExactReceiverRoute() {
    for (String url : new String[] {"http://gocsach.example/payments/payos/webhook",
        "https://localhost/payments/payos/webhook", "https://127.0.0.1/payments/payos/webhook",
        "https://gocsach.internal/payments/payos/webhook", "https://gocsach.example/admin",
        "https://gocsach.example/payments/payos/webhook?key=secret",
        "https://user:password@gocsach.example/payments/payos/webhook"}) {
      var values = properties("http://localhost:8080");
      values.setProperty("payos.webhook-url", url);
      assertThrows(IllegalStateException.class, () -> PayOsSettings.from(new Settings(values)), url);
    }
  }

  @Test
  void rejectsAmbiguousUrlsAndMisspelledEnabledFlag() {
    for (String value : new String[] {"http://example.org", "ftp://example.org", "https://user:password@example.org",
        "https://example.org?key=secret", "https://example.org#fragment", "https://example.org/../x",
        "https://example.org/%2e%2e/x", "https://example.org:0", "https://example.org//other"}) {
      assertThrows(IllegalStateException.class, () -> PayOsSettings.from(new Settings(properties(value))), value);
    }
    var values = properties("https://example.org");
    values.setProperty("payos.enabled", "treu");
    assertThrows(IllegalStateException.class, () -> PayOsSettings.from(new Settings(values)));
  }

  static Properties properties(String baseUrl) {
    var values = new Properties();
    values.setProperty("payos.enabled", "true");
    values.setProperty("payos.client-id", "client-test");
    values.setProperty("payos.api-key", "api-test");
    values.setProperty("payos.checksum-key", "checksum-test");
    values.setProperty("payos.base-url", baseUrl);
    return values;
  }
}
