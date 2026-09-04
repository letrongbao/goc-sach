package vn.edu.utex.bookstore.auth;

import java.time.*;
import java.util.Locale;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.persistence.Store;

public class AuthService {
  private final Store store;
  private final Passwords passwords;
  private final Clock clock;
  private final String dummyHash;

  public AuthService(Store store, Passwords passwords, Clock clock) {
    this.store = store;
    this.passwords = passwords;
    this.clock = clock;
    this.dummyHash = passwords.hash("not-a-user-password");
  }

  public Identity login(String login, String password) {
    User user = store.tx(d -> d.findLogin(normalize(login)));
    boolean valid = passwords.matches(password, user == null ? dummyHash : user.passwordHash);
    if (user == null || !valid || !user.active || user.blocked)
      throw new Problem(401, "Thông tin đăng nhập không đúng hoặc tài khoản chưa kích hoạt.");
    return Identity.of(user);
  }

  public Identity sessionIdentity(Identity identity) {
    if (identity == null) return null;
    return store.tx(
        d -> {
          User user = d.user(identity.id());
          return user != null && user.active && !user.blocked && user.authVersion == identity.authVersion()
              ? Identity.of(user)
              : null;
        });
  }

  public String remember(Identity identity) {
    String raw = Tokens.random();
    store.tx(
        d -> {
          User current = d.lockUser(identity.id());
          if (current == null || !current.active || current.blocked || current.authVersion != identity.authVersion())
            throw new Problem(401, "Phiên đăng nhập đã hết hiệu lực.");
          RememberToken t = new RememberToken();
          t.tokenHash = Tokens.digest(raw);
          t.userId = identity.id();
          t.expiresAt = clock.instant().plus(Duration.ofDays(7));
          d.saveToken(t);
          return null;
        });
    return raw;
  }

  public Identity cookieIdentity(String raw) {
    if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) return null;
    return store.tx(
        d -> {
          RememberToken t = d.token(Tokens.digest(raw));
          if (t == null) return null;
          if (!t.expiresAt.isAfter(clock.instant())) {
            d.deleteToken(t.tokenHash);
            return null;
          }
          User user = d.user(t.userId);
          return user != null && user.active && !user.blocked ? Identity.of(user) : null;
        });
  }

  public void logout(String raw) {
    if (raw != null)
      store.tx(
          d -> {
            d.deleteToken(Tokens.digest(raw));
            return null;
          });
  }

  public static String normalize(String text) {
    return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
  }
}
