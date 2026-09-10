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
    MemoryStore store = DemoFixtures.store();
    DemoFixtures.catalogStock(store);
    App app = new App(new Settings(props), store, Clock.systemUTC(),
        (to, subject, body) -> { throw new IllegalStateException("Preview does not deliver email"); });
    var context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(GocSachApplication.class)
        .properties("server.port=18080", "server.address=127.0.0.1")
        .initializers(ctx -> ((org.springframework.context.support.GenericApplicationContext) ctx)
            .registerBean("previewApp", App.class, () -> app))
        .run("--server.port=18080", "--server.address=127.0.0.1");
    context.getServletContext().setAttribute("preview", true);
    System.out.println("PREVIEW ONLY: http://127.0.0.1:18080/ — no PostgreSQL or email.");
    System.out.println("Press Enter to stop.");
    try { System.in.read(); } finally { context.close(); }
  }
}
