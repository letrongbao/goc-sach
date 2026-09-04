package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.mail.EmailSender;

class OtpTest {
  static class MutableClock extends Clock {
    Instant now = Instant.parse("2026-09-03T00:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }

  static class Mail implements EmailSender {
    String body;
    int sends;
    boolean fail;

    public void send(String to, String subject, String body) {
      if (fail) throw new IllegalStateException("SMTP unavailable");
      this.body = body;
      sends++;
    }

    String code() {
      var m = java.util.regex.Pattern.compile("[0-9]{6}").matcher(body);
      assertTrue(m.find());
      return m.group();
    }
  }

  MemoryStore store;
  MutableClock clock;
  Mail mail;
  OtpService otp;
  final String password = "test-password-123";

  @BeforeEach
  void setup() {
    store = new MemoryStore();
    clock = new MutableClock();
    mail = new Mail();
    otp =
        new OtpService(
            store, new Passwords(), mail, clock, "test-secret-at-least-32-bytes-long-0123456789");
  }

  void register() {
    otp.register("reader", "reader@example.test", password, password);
  }

  @Test
  void registrationActivationAndSingleUse() {
    register();
    User u = store.findLogin("reader");
    assertFalse(u.active);
    assertEquals(1, mail.sends);
    var c = store.challenge(u.id, OtpService.ACTIVATE);
    assertEquals(64, c.otpHash.length());
    assertTrue(c.delivered);
    String code = mail.code();
    otp.activate(u.email, code);
    assertTrue(u.active);
    assertThrows(Problem.class, () -> otp.activate(u.email, code));
  }

  @Test
  void otpCannotActivateOrResetAnAdministrativelyBlockedAccount() {
    register();
    User user = store.findLogin("reader");
    String code = mail.code();
    user.blocked = true;
    assertThrows(Problem.class, () -> otp.activate(user.email, code));
    clock.now = clock.now.plusSeconds(61);
    otp.issue(user.email, OtpService.ACTIVATE);
    assertEquals(1, mail.sends);
    assertFalse(user.active);
    user.blocked = false;
    otp.activate(user.email, code);
    otp.issue(user.email, OtpService.RESET);
    String reset = mail.code(), oldHash = user.passwordHash;
    user.blocked = true;
    assertThrows(Problem.class, () -> otp.reset(user.email, reset, password, password));
    assertEquals(oldHash, user.passwordHash);
    var auth = new AuthService(store, new Passwords(), clock);
    assertThrows(Problem.class, () -> auth.login(user.username, password));
    assertNull(auth.sessionIdentity(Identity.of(user)));
  }

  @Test
  void rejectsExpiredOtp() {
    register();
    String code = mail.code();
    clock.now = clock.now.plusSeconds(600);
    assertThrows(Problem.class, () -> otp.activate("reader@example.test", code));
  }

  @Test
  void fifthFailureLocksChallenge() {
    register();
    String code = mail.code(), wrong = code.equals("000000") ? "111111" : "000000";
    for (int i = 0; i < 5; i++)
      assertThrows(Problem.class, () -> otp.activate("reader@example.test", wrong));
    assertEquals(5, store.challenge(1, OtpService.ACTIVATE).attempts);
    assertThrows(Problem.class, () -> otp.activate("reader@example.test", code));
  }

  @Test
  void resendCooldownAndOldCodeInvalidation() {
    register();
    String old = mail.code();
    otp.issue("reader@example.test", OtpService.ACTIVATE);
    assertEquals(1, mail.sends);
    clock.now = clock.now.plusSeconds(61);
    otp.issue("reader@example.test", OtpService.ACTIVATE);
    assertEquals(2, mail.sends);
    assertThrows(Problem.class, () -> otp.activate("reader@example.test", old));
    otp.activate("reader@example.test", mail.code());
  }

  @Test
  void resetRevokesSessionsAndCookies() {
    register();
    otp.activate("reader@example.test", mail.code());
    AuthService auth = new AuthService(store, new Passwords(), clock);
    Identity id = auth.login("reader", password);
    String cookie = auth.remember(id);
    otp.issue("reader@example.test", OtpService.RESET);
    String code = mail.code();
    otp.reset("reader@example.test", code, "new-password-456", "new-password-456");
    assertNull(auth.sessionIdentity(id));
    assertNull(auth.cookieIdentity(cookie));
    assertNotNull(auth.login("reader", "new-password-456"));
    assertThrows(Problem.class, () -> otp.reset("reader@example.test", code, password, password));
  }

  @Test
  void activationOtpCannotReset() {
    register();
    assertThrows(
        Problem.class, () -> otp.reset("reader@example.test", mail.code(), password, password));
    assertFalse(store.user(1).active);
  }

  @Test
  void missingEmailDoesNotSendOrReveal() {
    assertDoesNotThrow(() -> otp.issue("missing@example.test", OtpService.RESET));
    assertEquals(0, mail.sends);
  }

  @Test
  void failedSmtpLeavesPendingAndCanRetry() {
    mail.fail = true;
    assertThrows(Problem.class, this::register);
    assertFalse(store.user(1).active);
    assertTrue(store.challenge(1, OtpService.ACTIVATE).consumed);
    clock.now = clock.now.plusSeconds(61);
    mail.fail = false;
    otp.issue("reader@example.test", OtpService.ACTIVATE);
    otp.activate("reader@example.test", mail.code());
    assertTrue(store.user(1).active);
  }

  @Test
  void resetDeliveryFailureCannotChangePasswordAndCanRetry() {
    register();
    otp.activate("reader@example.test", mail.code());
    String originalHash = store.user(1).passwordHash;
    mail.fail = true;
    assertThrows(
        OtpDeliveryFailure.class, () -> otp.issue("reader@example.test", OtpService.RESET));
    var challenge = store.challenge(1, OtpService.RESET);
    assertTrue(challenge.consumed);
    assertFalse(challenge.delivered);
    assertThrows(
        Problem.class, () -> otp.reset("reader@example.test", "000000", password, password));
    assertEquals(originalHash, store.user(1).passwordHash);
    clock.now = clock.now.plusSeconds(61);
    mail.fail = false;
    otp.issue("reader@example.test", OtpService.RESET);
    otp.reset("reader@example.test", mail.code(), "new-password-123", "new-password-123");
    assertNotEquals(originalHash, store.user(1).passwordHash);
  }

  @Test
  void duplicateAndValidation() {
    assertThrows(
        Problem.class, () -> otp.register("bad@user", "x@example.test", password, password));
    assertThrows(Problem.class, () -> otp.register("reader", "bad-email", password, password));
    assertThrows(
        Problem.class, () -> otp.register("reader", "x@example.test", password, "mismatch"));
    register();
    assertThrows(Problem.class, this::register);
  }

  @Test
  void concurrentReuseOnlyOneSuccess() throws Exception {
    register();
    String code = mail.code();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var actions =
          java.util.stream.IntStream.range(0, 2)
              .mapToObj(
                  i ->
                      (Callable<Boolean>)
                          () -> {
                            try {
                              otp.activate("reader@example.test", code);
                              return true;
                            } catch (Problem e) {
                              return false;
                            }
                          })
              .toList();
      int successes = 0;
      for (var result : executor.invokeAll(actions)) if (result.get()) successes++;
      assertEquals(1, successes);
    }
  }
}
