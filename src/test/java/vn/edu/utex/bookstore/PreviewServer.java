package vn.edu.utex.bookstore;

import java.time.Clock;
import java.util.Properties;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import vn.edu.utex.bookstore.config.*;

public final class PreviewServer {
  public static void main(String[] args) throws java.io.IOException {
    Properties props = new Properties();
    props.setProperty("cookie.secure", "false");
    props.setProperty("otp.secret", "preview-only-not-production-secret-0123456789");
    Clock clock = Clock.systemUTC();
    MemoryStore store = FinalDemoFixtures.create(clock);
    App app = new App(new Settings(props), store, clock,
        (to, subject, body) -> { throw new IllegalStateException("Preview does not deliver email"); });
    var context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(GocSachApplication.class)
        .properties("server.port=18080", "server.address=127.0.0.1")
        .initializers(ctx -> ((org.springframework.context.support.GenericApplicationContext) ctx)
            .registerBean("previewApp", App.class, () -> app))
        .run("--server.port=18080", "--server.address=127.0.0.1");
    context.getServletContext().setAttribute("preview", true);
    System.out.println("PREVIEW ONLY: http://127.0.0.1:18080/ — no PostgreSQL or email.");
    System.out.println("Buyer: buyer / demo-password-123 — profile, addresses, favorites, cart and orders.");
    System.out.println("Seller: reader or seller / demo-password-123. Administrator: admin / demo-password-123.");
    System.out.println("24 books, 6 categories, 3 shops. Reviews: /product/detail?id=24. Review eligible: /product/detail?id=23.");
    System.out.println("Buyer has completed, shipping, pending, confirmed and cancelled orders. Restart resets all data.");
    System.out.println("Press Enter to stop.");
    try { System.in.read(); } finally { context.close(); }
  }
}
