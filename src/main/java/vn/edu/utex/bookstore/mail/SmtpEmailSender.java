package vn.edu.utex.bookstore.mail;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.util.Properties;
import vn.edu.utex.bookstore.config.Settings;

public final class SmtpEmailSender implements EmailSender {
  private final Session session;
  private final String from;

  public SmtpEmailSender(Settings config) {
    Properties p = new Properties();
    p.setProperty("mail.smtp.host", config.get("smtp.host", "127.0.0.1"));
    p.setProperty("mail.smtp.port", config.get("smtp.port", "1025"));
    boolean auth = Boolean.parseBoolean(config.get("smtp.auth", "false"));
    boolean tls = Boolean.parseBoolean(config.get("smtp.starttls", "false"));
    p.setProperty("mail.smtp.auth", Boolean.toString(auth));
    p.setProperty("mail.smtp.starttls.enable", Boolean.toString(tls));
    p.setProperty("mail.smtp.starttls.required", Boolean.toString(tls));
    p.setProperty("mail.smtp.ssl.checkserveridentity", "true");
    p.setProperty("mail.smtp.connectiontimeout", "5000");
    p.setProperty("mail.smtp.timeout", "5000");
    p.setProperty("mail.smtp.writetimeout", "5000");
    if (auth && !tls) throw new IllegalStateException("SMTP xác thực bắt buộc bật STARTTLS.");
    String user = auth ? config.require("smtp.user") : "",
        password = auth ? config.require("smtp.password") : "";
    session =
        Session.getInstance(
            p,
            auth
                ? new Authenticator() {
                  protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, password);
                  }
                }
                : null);
    from = config.get("smtp.from", "noreply@example.test");
  }

  @Override
  public void send(String to, String subject, String body) {
    try {
      MimeMessage message = new MimeMessage(session);
      message.setFrom(new InternetAddress(from, true));
      message.setRecipient(Message.RecipientType.TO, new InternetAddress(to, true));
      message.setSubject(subject, "UTF-8");
      message.setText(body, "UTF-8");
      Transport.send(message);
    } catch (MessagingException e) {
      throw new IllegalStateException("Không gửi được email. Kiểm tra cấu hình SMTP.");
    }
  }

  /** Authenticate and disconnect; no email is sent. */
  public void checkConnection() {
    try (Transport transport = session.getTransport("smtp")) {
      transport.connect();
    } catch (MessagingException failure) {
      throw new IllegalStateException("Không kết nối được SMTP; kiểm tra cấu hình riêng.");
    }
  }
}
