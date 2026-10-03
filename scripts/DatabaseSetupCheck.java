import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Set;
import vn.edu.utex.bookstore.config.Settings;

/** Explicit operator CLI, never called by application startup or automatic tests. */
public final class DatabaseSetupCheck {
  private static final Set<String> ALLOWED = Set.of("goc_sach", "goc_sach_test");
  private final Settings settings = Settings.load();
  private final URI uri;

  private DatabaseSetupCheck() {
    String url = settings.require("db.url");
    if (!url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
    uri = URI.create(url.substring(5));
    if (!Set.of("127.0.0.1", "localhost").contains(uri.getHost()) || uri.getPort() != 5434
        || !"/goc_sach".equals(uri.getPath()) || uri.getUserInfo() != null || uri.getQuery() != null)
      throw new IllegalArgumentException();
  }

  public static void main(String[] args) {
    try {
      DriverManager.setLoginTimeout(5);
      var check = new DatabaseSetupCheck();
      check.listRelatedDatabases();
      if (args.length == 1 && "--inspect".equals(args[0])) {
        check.inspect("goc_sach");
        check.inspect("goc_sach_test");
      } else if (args.length == 2 && "--prepare".equals(args[0]) && ALLOWED.contains(args[1])) {
        check.prepare(args[1]);
      } else throw new IllegalArgumentException();
    } catch (SQLException failure) {
      System.err.println("DATABASE CHECK FAILED: SQLState=" + failure.getSQLState() + "; no credentials displayed.");
      System.exit(1);
    } catch (Exception failure) {
      System.err.println("DATABASE CHECK FAILED: verify local configuration, allowed arguments and reviewed schema; no credentials displayed.");
      System.exit(1);
    }
  }

  private Connection connect(String database) throws SQLException {
    if (!ALLOWED.contains(database) && !"postgres".equals(database)) throw new IllegalArgumentException();
    return DriverManager.getConnection("jdbc:postgresql://" + uri.getHost() + ":5434/" + database,
        settings.require("db.user"), settings.require("db.password"));
  }

  private boolean exists(String database) throws SQLException {
    try (var connection = connect("postgres"); var query = connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname=?")) {
      connection.setReadOnly(true);
      query.setString(1, database);
      query.setQueryTimeout(5);
      try (var rows = query.executeQuery()) { return rows.next(); }
    }
  }

  private void listRelatedDatabases() throws SQLException {
    try (var connection = connect("postgres"); var query = connection.prepareStatement(
        "SELECT datname FROM pg_database WHERE datname IN ('goc_sach','goc_sach_test') OR datname LIKE 'bookstore%' ORDER BY datname")) {
      connection.setReadOnly(true);
      query.setQueryTimeout(5);
      System.out.println("RELATED DATABASES: isolated localhost PostgreSQL port 5434 (names only).");
      try (var rows = query.executeQuery()) {
        while (rows.next()) System.out.println("DATABASE NAME: " + rows.getString(1));
      }
    }
  }

  private void prepare(String database) throws Exception {
    if (!ALLOWED.contains(database)) throw new IllegalArgumentException();
    if (!exists(database)) {
      try (var connection = connect("postgres"); var statement = connection.createStatement()) {
        statement.setQueryTimeout(30);
        statement.execute("CREATE DATABASE " + database);
        System.out.println("CREATED DATABASE: " + database);
      }
    } else System.out.println("EXISTING DATABASE: " + database);
    try (var connection = connect(database)) {
      long objects = scalar(connection, "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace "
          + "WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','S','f')");
      if (objects != 0) {
        System.out.println("SCHEMA NOT MODIFIED: " + database + " has " + objects + " existing objects; inspect and back up first.");
        inspect(database);
        return;
      }
      var schema = Files.readString(Path.of("sql/schema.sql"), StandardCharsets.UTF_8);
      var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(schema.getBytes(StandardCharsets.UTF_8)));
      try (var statement = connection.createStatement()) {
        statement.setQueryTimeout(90);
        statement.execute(schema);
      } catch (SQLException failure) {
        try (var statement = connection.createStatement()) { statement.execute("ROLLBACK"); }
        throw failure;
      }
      System.out.println("EXECUTED REVIEWED SCHEMA: " + database + "; SHA256=" + hash);
    }
    inspect(database);
  }

  private void inspect(String database) throws SQLException {
    if (!exists(database)) {
      System.out.println("DATABASE ABSENT: " + database);
      return;
    }
    try (var connection = connect(database)) {
      connection.setReadOnly(true);
      connection.setAutoCommit(false);
      long tables = scalar(connection, "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'");
      long columns = scalar(connection, "SELECT count(*) FROM information_schema.columns WHERE table_schema='public'");
      long constraints = scalar(connection, "SELECT count(*) FROM information_schema.table_constraints WHERE table_schema='public'");
      long indexes = scalar(connection, "SELECT count(*) FROM pg_indexes WHERE schemaname='public'");
      System.out.println("DATABASE: " + database + "; baseTables=" + tables + "; columns=" + columns
          + "; constraints=" + constraints + "; indexes=" + indexes);
      try (var query = connection.prepareStatement("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name")) {
        query.setQueryTimeout(5);
        try (var rows = query.executeQuery()) {
          while (rows.next()) {
            String table = rows.getString(1);
            if (!table.matches("[a-z_]+")) throw new IllegalArgumentException();
            System.out.println("ROWS " + database + "." + table + ": " + scalar(connection, "SELECT count(*) FROM public.\"" + table + "\""));
          }
        }
      }
      connection.rollback();
    }
  }

  private static long scalar(Connection connection, String sql) throws SQLException {
    try (Statement query = connection.createStatement()) {
      query.setQueryTimeout(5);
      try (ResultSet rows = query.executeQuery(sql)) { rows.next(); return rows.getLong(1); }
    }
  }
}
