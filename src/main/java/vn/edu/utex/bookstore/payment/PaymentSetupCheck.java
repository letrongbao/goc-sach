package vn.edu.utex.bookstore.payment;

import java.sql.*;
import java.util.*;
import vn.edu.utex.bookstore.config.Settings;
import vn.payos.PayOS;
import vn.payos.exception.APIException;

/** Explicit diagnostics: metadata only; --probe adds one authenticated, read-only provider GET. */
public final class PaymentSetupCheck {
  private static final List<String> PAYOS_KEYS = List.of(
      "payos.client-id", "payos.api-key", "payos.checksum-key", "payos.base-url");
  private static final List<String> DB_KEYS = List.of("db.url", "db.user", "db.password");
  private static final Map<String, List<String>> REQUIRED_COLUMNS = Map.of(
      "orders", List.of("payment_method", "payment_status", "payment_expires_at"),
      "payment_attempts", List.of("id", "order_id", "order_code", "amount", "currency", "status",
          "payment_link_id", "checkout_url", "created_at", "updated_at", "expires_at"),
      "payment_events", List.of("id", "attempt_id", "amount", "reference", "outcome", "source", "created_at"),
      "inventory_reservations", List.of("expires_at"));
  private record PaymentConfiguration(PayOsSettings settings, boolean webhookReady) {}

  private PaymentSetupCheck() {}

  public static void main(String[] args) {
    boolean probe = args.length == 1 && "--probe".equals(args[0]);
    if (args.length != 0 && !probe) {
      System.out.println("Usage: PaymentSetupCheck [--probe]");
      System.exit(2);
      return;
    }
    Settings settings;
    try {
      settings = Settings.load();
    } catch (IllegalStateException failure) {
      System.out.println("CONFIG: unavailable; set bookstore.config / BOOKSTORE_CONFIG to the private properties file.");
      System.exit(1);
      return;
    }

    boolean ready = checkDatabase(settings);
    PaymentConfiguration payment = checkPaymentConfiguration(settings);
    ready = payment.settings() != null && payment.webhookReady() && ready;
    if (probe && payment.settings() != null) ready = probe(payment.settings()) && ready;
    System.out.println("No SQL, email, payment link, transfer or webhook configuration was changed.");
    System.out.println("This check does not replace JPA validation or end-to-end payment acceptance.");
    if (!ready) System.exit(1);
  }

  private static PaymentConfiguration checkPaymentConfiguration(Settings settings) {
    List<String> missing = missing(settings, PAYOS_KEYS);
    if (!missing.isEmpty()) System.out.println("PAYOS: missing key names=" + String.join(",", missing));
    if (!"true".equalsIgnoreCase(settings.get("payos.enabled", "false"))) {
      System.out.println("PAYOS: disabled or invalid payos.enabled; no provider request made.");
      return new PaymentConfiguration(null, false);
    }
    try {
      PayOsSettings payment = PayOsSettings.from(settings);
      System.out.println("PAYOS: enabled; keys and return/cancel configuration valid (values hidden).");
      try {
        payment.webhookUrl();
        System.out.println("WEBHOOK: application address is public HTTPS; endpoint registration/reachability not checked.");
        return new PaymentConfiguration(payment, true);
      } catch (IllegalStateException failure) {
        System.out.println("WEBHOOK: public HTTPS application address is missing; return/cancel may still be tested locally.");
        return new PaymentConfiguration(payment, false);
      }
    } catch (IllegalStateException failure) {
      System.out.println("PAYOS: configuration incomplete/invalid; check the named keys and public HTTPS payos.base-url.");
      return new PaymentConfiguration(null, false);
    }
  }

  private static boolean checkDatabase(Settings settings) {
    List<String> missing = missing(settings, DB_KEYS);
    if (!missing.isEmpty()) {
      System.out.println("DATABASE: missing key names=" + String.join(",", missing));
      return false;
    }
    DriverManager.setLoginTimeout(5);
    Properties connectionProperties = new Properties();
    connectionProperties.setProperty("user", settings.require("db.user"));
    connectionProperties.setProperty("password", settings.require("db.password"));
    connectionProperties.setProperty("connectTimeout", "5");
    connectionProperties.setProperty("socketTimeout", "5");
    try (Connection connection = DriverManager.getConnection(settings.require("db.url"), connectionProperties)) {
      connection.setReadOnly(true);
      connection.setAutoCommit(false);
      boolean ready = true;
      try (PreparedStatement query = connection.prepareStatement(
          "SELECT current_database(), (SELECT count(*) FROM information_schema.tables "
              + "WHERE table_schema='public' AND table_type='BASE TABLE')")) {
        query.setQueryTimeout(5);
        try (ResultSet rows = query.executeQuery()) {
          rows.next();
          String database = rows.getString(1);
          System.out.println("DATABASE: current_database=" + safe(database) + "; public table count=" + rows.getInt(2));
          if (!"goc_sach".equals(database)) {
            System.out.println("DATABASE: this is not the configured final-project database goc_sach; check db.url.");
            ready = false;
          }
        }
      }
      Map<String, Set<String>> present = new HashMap<>();
      try (PreparedStatement query = connection.prepareStatement(
          "SELECT table_name,column_name FROM information_schema.columns WHERE table_schema='public' "
              + "AND table_name IN ('orders','payment_attempts','payment_events','inventory_reservations')")) {
        query.setQueryTimeout(5);
        try (ResultSet rows = query.executeQuery()) {
          while (rows.next()) present.computeIfAbsent(rows.getString(1), unused -> new HashSet<>()).add(rows.getString(2));
        }
      }
      for (String table : new TreeSet<>(REQUIRED_COLUMNS.keySet())) {
        Set<String> columns = present.getOrDefault(table, Set.of());
        for (String column : REQUIRED_COLUMNS.get(table)) {
          if (!columns.contains(column)) {
            System.out.println("SCHEMA: missing " + table + "." + column);
            ready = false;
          }
        }
      }
      connection.rollback();
      if (ready) System.out.println("SCHEMA: payment columns present (read-only metadata; types/constraints require JPA validation).");
      return ready;
    } catch (SQLException failure) {
      System.out.println("DATABASE: unavailable; SQLState=" + safe(failure.getSQLState()) + ". No schema was created.");
      return false;
    }
  }

  private static boolean probe(PayOsSettings settings) {
    var client = new PayOS(PayOsClientFactory.probeOptions(settings));
    try {
      // Reserved base code is never issued by PaymentService (generated codes are strictly greater).
      var result = client.paymentRequests().get(1_000_000_000_000L);
      System.out.println("PAYOS PROBE: provider responded; status=" + (result.getStatus() == null ? "unknown" : result.getStatus().name()));
      return true;
    } catch (APIException failure) {
      System.out.println("PAYOS PROBE: HTTP=" + failure.getStatusCode().map(Object::toString).orElse("unknown")
          + "; code=" + safe(failure.getErrorCode().orElse("unknown")));
      return false;
    } catch (RuntimeException failure) {
      System.out.println("PAYOS PROBE: unavailable; no provider body or credential displayed.");
      return false;
    } finally {
      client.close();
    }
  }

  private static List<String> missing(Settings settings, List<String> names) {
    return names.stream().filter(name -> {
      String value = settings.get(name, "");
      return value.isBlank() || value.startsWith("REPLACE_") || value.contains("REPLACE_WITH_");
    }).toList();
  }

  private static String safe(String value) {
    return value != null && value.matches("[A-Za-z0-9_-]{1,63}") ? value : "unknown";
  }
}
