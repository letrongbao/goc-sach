package vn.edu.utex.bookstore.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.mail.EmailSender;
import vn.edu.utex.bookstore.persistence.Store;

public final class OtpService {
  public static final String ACTIVATE = "ACTIVATE", RESET = "RESET";
  public static final String SENT_MESSAGE =
      "Nếu thông tin phù hợp, mã xác minh sẽ được gửi đến email của bạn. Mã có hiệu lực 10 phút.";
  private static final SecureRandom RANDOM = new SecureRandom();
  private final Store store;
  private final Passwords passwords;
  private final EmailSender sender;
  private final Clock clock;
  private final byte[] key;

  private record Delivery(long userId, String email, String purpose, String id, String code) {}

  public OtpService(
      Store store, Passwords passwords, EmailSender sender, Clock clock, String secret) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new IllegalStateException("otp.secret cần ít nhất 32 byte ngẫu nhiên.");
    this.store = store;
    this.passwords = passwords;
    this.sender = sender;
    this.clock = clock;
    this.key = secret.getBytes(StandardCharsets.UTF_8);
  }

  public void register(String username, String email, String password, String confirm) {
    String name = AuthService.normalize(username), address = email(email);
    if (!name.matches("[a-z0-9][a-z0-9_.-]{2,49}"))
      throw Problem.invalid(
          "Tên đăng nhập cần 3–50 ký tự: chữ thường, số, dấu chấm, gạch ngang hoặc gạch dưới.");
    confirm(password, confirm);
    String hash = passwords.hash(password);
    store.tx(
        d -> {
          if (d.findLogin(name) != null || d.findLogin(address) != null)
            throw new Problem(409, "Không thể sử dụng tên đăng nhập hoặc email này.");
          User u = new User();
          u.username = name;
          u.email = address;
          u.passwordHash = hash;
          u.role = "USER";
          u.active = false;
          u.createdAt = clock.instant();
          d.saveUser(u);
          return null;
        });
    issue(address, ACTIVATE);
  }

  public void issue(String email, String purpose) {
    String address = email(email);
    purpose(purpose);
    Delivery delivery =
        store.tx(
            d -> {
              User found = d.findLogin(address);
              if (found == null || !found.email.equals(address)) return null;
              User user = d.lockUser(found.id);
              if ((ACTIVATE.equals(purpose) && user.active)
                  || (RESET.equals(purpose) && !user.active)) return null;
              OtpChallenge old = d.challenge(user.id, purpose);
              if (old != null && old.sentAt.plusSeconds(60).isAfter(clock.instant())) return null;
              String code;
              do {
                code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
              } while (old != null && old.otpHash.equals(hash(old, code)));
              OtpChallenge c = new OtpChallenge();
              c.id = old == null ? UUID.randomUUID().toString() : old.id;
              c.userId = user.id;
              c.purpose = purpose;
              c.sentAt = clock.instant();
              c.expiresAt = c.sentAt.plus(Duration.ofMinutes(10));
              c.otpHash = hash(c, code);
              d.saveChallenge(c);
              return new Delivery(user.id, user.email, purpose, c.id, code);
            });
    if (delivery == null) return;
    try {
      sender.send(
          delivery.email,
          "Góc Sách — " + (ACTIVATE.equals(purpose) ? "Kích hoạt tài khoản" : "Đặt lại mật khẩu"),
          "Mã xác minh của bạn: "
              + delivery.code
              + "\n"
              + "Mã hết hạn sau 10 phút và chỉ dùng một lần. Không chia sẻ mã này.\n"
              + "Nếu bạn không yêu cầu, hãy bỏ qua email.");
      store.tx(
          d -> {
            d.lockUser(delivery.userId);
            OtpChallenge c = d.challenge(delivery.userId, delivery.purpose);
            if (c != null
                && MessageDigest.isEqual(
                    c.otpHash.getBytes(StandardCharsets.US_ASCII),
                    hash(c, delivery.code).getBytes(StandardCharsets.US_ASCII))) {
              c.delivered = true;
              d.saveChallenge(c);
            }
            return null;
          });
    } catch (RuntimeException e) {
      store.tx(
          d -> {
            d.lockUser(delivery.userId);
            OtpChallenge c = d.challenge(delivery.userId, delivery.purpose);
            if (c != null && c.otpHash.equals(hash(c, delivery.code))) {
              c.consumed = true;
              d.saveChallenge(c);
            }
            return null;
          });
      throw new OtpDeliveryFailure();
    }
  }

  public void activate(String email, String code) {
    verify(email, code, ACTIVATE, null);
  }

  public void reset(String email, String code, String password, String confirm) {
    confirm(password, confirm);
    passwords.validate(password);
    String newHash = passwords.hash(password);
    verify(email, code, RESET, newHash);
  }

  private void verify(String email, String code, String purpose, String newHash) {
    String address = email(email);
    if (code == null || !code.matches("[0-9]{6}")) throw Problem.invalid("Mã OTP gồm 6 chữ số.");
    boolean valid =
        store.tx(
            d -> {
              User found = d.findLogin(address);
              if (found == null || !found.email.equals(address)) return false;
              User user = d.lockUser(found.id);
              OtpChallenge c = d.challenge(user.id, purpose);
              if (c == null
                  || c.consumed
                  || !c.delivered
                  || c.attempts >= 5
                  || !c.expiresAt.isAfter(clock.instant())) return false;
              if (!MessageDigest.isEqual(
                  c.otpHash.getBytes(StandardCharsets.US_ASCII),
                  hash(c, code).getBytes(StandardCharsets.US_ASCII))) {
                c.attempts++;
                if (c.attempts >= 5) c.consumed = true;
                d.saveChallenge(c);
                return false;
              }
              c.consumed = true;
              d.saveChallenge(c);
              if (ACTIVATE.equals(purpose)) {
                if (user.active) return false;
                user.active = true;
              } else {
                if (!user.active) return false;
                user.passwordHash = newHash;
                user.authVersion++;
                d.deleteUserTokens(user.id);
              }
              d.saveUser(user);
              return true;
            });
    // Throw after commit so failed-attempt counters are not rolled back.
    if (!valid) throw Problem.invalid("Mã không hợp lệ, đã dùng hoặc hết hạn. Hãy yêu cầu mã mới.");
  }

  private String hash(OtpChallenge c, String code) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return HexFormat.of()
          .formatHex(
              mac.doFinal(
                  (c.id + ":" + c.userId + ":" + c.purpose + ":" + code)
                      .getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void confirm(String password, String confirm) {
    if (password == null || !password.equals(confirm))
      throw Problem.invalid("Mật khẩu xác nhận không khớp.");
  }

  public static String email(String raw) {
    String email = AuthService.normalize(raw);
    if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw Problem.invalid("Email không hợp lệ.");
    return email;
  }

  private static void purpose(String purpose) {
    if (!ACTIVATE.equals(purpose) && !RESET.equals(purpose))
      throw Problem.invalid("Mục đích OTP không hợp lệ.");
  }
}
