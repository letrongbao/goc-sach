package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;

import com.icegreen.greenmail.util.*;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import vn.edu.utex.bookstore.config.Settings;
import vn.edu.utex.bookstore.mail.SmtpEmailSender;

class SmtpTest {
  @Test
  void realSmtpProtocolLoopbackOnly() throws Exception {
    var smtp = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
    smtp.setUser("reader@example.test", "reader", "test-password");
    smtp.start();
    try {
      Properties p = new Properties();
      p.setProperty("smtp.host", "127.0.0.1");
      p.setProperty("smtp.port", Integer.toString(smtp.getSmtp().getPort()));
      new SmtpEmailSender(new Settings(p))
          .send("reader@example.test", "Góc Sách — OTP", "Mã kiểm thử: 123456");
      assertTrue(smtp.waitForIncomingEmail(3000, 1));
      assertEquals(1, smtp.getReceivedMessages().length);
      assertTrue(smtp.getReceivedMessages()[0].getSubject().contains("Góc Sách"));
      assertTrue(smtp.getReceivedMessages()[0].getContent().toString().contains("123456"));
    } finally {
      smtp.stop();
    }
  }

  @Test
  void doesNotPermitPlaintextSmtpAuthentication() {
    Properties p = new Properties();
    p.setProperty("smtp.auth", "true");
    assertThrows(IllegalStateException.class, () -> new SmtpEmailSender(new Settings(p)));
  }
}
