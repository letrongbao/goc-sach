package vn.edu.utex.bookstore.config;

import java.sql.*;
import vn.edu.utex.bookstore.mail.SmtpEmailSender;
import vn.edu.utex.bookstore.security.JwtConfiguration;

/** Explicit local diagnostic: reads metadata only; never creates schema or sends email. */
public final class EnvironmentCheck {
  private EnvironmentCheck() {}

  public static void main(String[] args) {
    Settings settings = Settings.load();
    boolean ready = true;
    DriverManager.setLoginTimeout(5);
    try (Connection connection = DriverManager.getConnection(settings.require("db.url"),
        settings.require("db.user"), settings.require("db.password"))) {
      connection.setReadOnly(true);
      connection.setAutoCommit(false);
      try (PreparedStatement query = connection.prepareStatement(
          "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' "
          + "AND table_name IN ('users','categories','products','remember_tokens','otp_challenges')")) {
        query.setQueryTimeout(5);
        try (ResultSet rows = query.executeQuery()) {
          rows.next();
          if (rows.getInt(1) != 5) throw new SQLException("Schema incomplete", "GS001");
        }
      }
      try (PreparedStatement query = connection.prepareStatement(
          "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' "
          + "AND table_name = 'users' AND column_name = 'blocked'")) {
        query.setQueryTimeout(5);
        try (ResultSet rows = query.executeQuery()) {
          rows.next();
          if (rows.getInt(1) != 1) throw new SQLException("Schema incomplete", "GS002");
        }
      }
      connection.rollback();
      System.out.println("DATABASE: connected, foundation tables present (read-only check).");
    } catch (SQLException failure) {
      System.out.println("DATABASE: not ready; SQLState=" + failure.getSQLState() + ". No schema was created.");
      ready = false;
    }
    try {
      new SmtpEmailSender(settings).checkConnection();
      System.out.println("SMTP: connected/authenticated. No email was sent.");
    } catch (IllegalStateException failure) {
      System.out.println("SMTP: connection failed; check private configuration.");
      ready = false;
    }
    try {
      if (!JwtConfiguration.checkConfiguration(settings)) throw new IllegalStateException();
      System.out.println("JWT: key pair and configuration valid. No keys displayed.");
    } catch (IllegalStateException | IllegalArgumentException failure) {
      System.out.println("JWT: configuration missing or invalid.");
      ready = false;
    }
    System.out.println("This is not an end-to-end acceptance test.");
    if (!ready) System.exit(1);
  }
}
