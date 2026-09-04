package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.*;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.config.*;

class WebTest {
  @Test
  void normalizedAdminPathsCannotBypassAuthorization() throws Exception {
    for (String path :
        List.of("/admin;ignored/categories", "/%61dmin/categories", "/admin;ignored/products")) {
      var response = get(path);
      assertEquals(302, response.statusCode(), path + response.body());
    }
  }

  private Tomcat tomcat;
  private HttpClient client;
  private String base;
  private OtpTest.Mail mail;
  private OtpTest.MutableClock clock;

  @BeforeEach
  void start() throws Exception {
    var props = new Properties();
    props.setProperty("uploads.dir", Path.of("target/test-uploads").toAbsolutePath().toString());
    props.setProperty("cookie.secure", "false");
    props.setProperty("otp.secret", "test-only-secret-not-for-production-0123456789");
    MemoryStore store = DemoFixtures.store();
    mail = new OtpTest.Mail();
    clock = new OtpTest.MutableClock();
    tomcat = new Tomcat();
    tomcat.setBaseDir("target/tomcat-test");
    tomcat.setPort(0);
    tomcat.getConnector().setProperty("address", "127.0.0.1");
    var context =
        tomcat.addWebapp("/bookstore", Path.of("src/main/webapp").toAbsolutePath().toString());
    context.setParentClassLoader(getClass().getClassLoader());
    Tomcat.addServlet(
        context,
        "testFailure",
        new jakarta.servlet.http.HttpServlet() {
          protected void doGet(
              jakarta.servlet.http.HttpServletRequest req,
              jakarta.servlet.http.HttpServletResponse res)
              throws java.io.IOException {
            if (req.getParameter("status") != null) {
              res.sendError(Integer.parseInt(req.getParameter("status")), "internal-test-detail");
            } else throw new IllegalStateException("internal-test-detail");
          }
        });
    context.addServletMappingDecoded("/_test/failure", "testFailure");
    context
        .getServletContext()
        .setAttribute("app", new App(new Settings(props), store, clock, mail));
    tomcat.start();
    base = "http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/bookstore";
    client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
  }

  @AfterEach
  void stop() throws Exception {
    if (tomcat != null) {
      tomcat.stop();
      tomcat.destroy();
    }
  }

  HttpResponse<String> get(String route) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(base + route)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> post(String route, String body) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(base + route))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void jspLoginAndAdminFlow() throws Exception {
    var home = get("/");
    assertEquals(200, home.statusCode(), home.body());
    assertTrue(home.body().contains("góc"));
    var login = get("/auth/login");
    assertEquals(200, login.statusCode(), login.body());
    var matcher =
        java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(login.body());
    assertTrue(matcher.find());
    String csrf = matcher.group(1);
    assertEquals(403, post("/auth/login", "login=admin&password=demo-password-123").statusCode());
    var logged =
        post(
            "/auth/login",
            "_csrf=" + csrf + "&login=admin&password=demo-password-123&mode=session");
    assertEquals(302, logged.statusCode(), logged.body());
    var list = get("/admin/categories");
    assertEquals(200, list.statusCode(), list.body());
    assertTrue(list.body().contains("Danh mục sách"));
    assertEquals(200, get("/admin/category/add").statusCode());
    assertEquals(404, get("/admin/category/edit?id=999").statusCode());
  }

  @Test
  void unauthenticatedCannotEnterAdminAndAssetsWork() throws Exception {
    assertEquals(302, get("/admin/categories").statusCode());
    assertEquals(200, get("/assets/app.css").statusCode());
    assertEquals(404, get("/media/invalid").statusCode());
  }

  String csrf(String route) throws Exception {
    var response = get(route);
    assertEquals(200, response.statusCode(), response.body());
    var matcher =
        java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"")
            .matcher(response.body());
    assertTrue(matcher.find(), response.body());
    return matcher.group(1);
  }

  long countCards(String body) {
    return java.util.regex.Pattern.compile("data-product-id=").matcher(body).results().count();
  }

  void assertSafeError(HttpResponse<String> response, int expected) {
    assertEquals(expected, response.statusCode(), response.body());
    for (String internal :
        List.of(
            "Exception Report",
            "Root Cause",
            "NumberFormatException",
            "internal-test-detail",
            "vn.edu.utex.bookstore")) {
      assertFalse(response.body().contains(internal), internal + response.body());
    }
  }

  @Test
  void malformedFormIdsRenderValidationWithoutLosingFields() throws Exception {
    assertEquals(
        302,
        post(
                "/auth/login",
                "_csrf="
                    + csrf("/auth/login")
                    + "&login=admin&password=demo-password-123&mode=session")
            .statusCode());
    String token = csrf("/admin/category/add");
    for (String invalid : List.of("abc", "0", "-1", "9223372036854775808")) {
      var category =
          post(
              "/admin/category/save",
              "_csrf=" + token + "&id=" + invalid + "&name=Keep+category&image=&active=on");
      assertSafeError(category, 400);
      assertTrue(category.body().contains("Keep category"));
      assertTrue(category.body().contains("name=\"id\" value=\"" + invalid + "\""));
      var product =
          post(
              "/admin/product/save",
              "_csrf="
                  + token
                  + "&categoryId="
                  + invalid
                  + "&title=Keep+title&author=Writer&price=100&stock=1&image=");
      assertSafeError(product, 400);
      assertTrue(product.body().contains("Keep title"));
      assertSafeError(
          post(
              "/admin/product/save",
              "_csrf="
                  + token
                  + "&id="
                  + invalid
                  + "&categoryId=1&title=Book&author=Writer&price=100&stock=1&image="),
          400);
    }
    assertSafeError(
        post(
            "/admin/product/save",
            "_csrf="
                + token
                + "&categoryId=&title=Keep+title&author=Writer&price=100&stock=1&image="),
        400);
    assertTrue(get("/product").body().contains("13 cuốn sách"));
  }

  @Test
  void publicOtpResponsesDoNotRevealSmtpFailure() throws Exception {
    mail.fail = true;
    String token = csrf("/auth/forgot");
    var existing = post("/auth/forgot", "_csrf=" + token + "&email=reader%40example.test");
    String existingNotice = get("/auth/reset").body();
    var missing = post("/auth/forgot", "_csrf=" + token + "&email=nobody%40example.test");
    String missingNotice = get("/auth/reset").body();
    assertEquals(302, existing.statusCode(), existing.body());
    assertEquals(existing.statusCode(), missing.statusCode());
    assertEquals(
        existing.headers().firstValue("location"), missing.headers().firstValue("location"));
    for (String body : List.of(existingNotice, missingNotice)) {
      assertTrue(body.contains(OtpService.SENT_MESSAGE));
      assertFalse(body.contains("Chưa gửi được email"));
    }
    var registered =
        post(
            "/auth/register",
            "_csrf="
                + token
                + "&username=pending&email=pending%40example.test&password=test-password-123&confirm=test-password-123");
    assertEquals(503, registered.statusCode());
    clock.now = clock.now.plusSeconds(61);
    for (String address : List.of("pending", "nobody", "reader")) {
      var resend = post("/auth/resend", "_csrf=" + token + "&email=" + address + "%40example.test");
      assertEquals(200, resend.statusCode(), resend.body());
      assertTrue(resend.body().contains(OtpService.SENT_MESSAGE));
    }
  }

  @Test
  void unexpectedAndHttpErrorsUseSafeBrandedPage() throws Exception {
    var unexpected = get("/_test/failure");
    assertSafeError(unexpected, 500);
    assertTrue(unexpected.body().contains("Về trang chủ"));
    for (int status : List.of(400, 403, 404, 405, 413, 429, 500, 503)) {
      var response = get("/_test/failure?status=" + status);
      assertSafeError(response, status);
      assertTrue(response.body().contains("Về trang chủ"));
      assertTrue(response.body().contains("/bookstore/assets/app.css"));
      assertEquals("no-store", response.headers().firstValue("cache-control").orElseThrow());
    }
    assertSafeError(get("/does-not-exist"), 404);
    assertSafeError(get("/error"), 404);
  }

  HttpResponse<String> upload(String route, Map<String, String> fields, byte[] file)
      throws Exception {
    String boundary = "BookstoreTestBoundary";
    var body = new java.io.ByteArrayOutputStream();
    for (var field : fields.entrySet()) {
      body.write(
          ("--"
                  + boundary
                  + "\r\nContent-Disposition: form-data; name=\""
                  + field.getKey()
                  + "\"\r\n\r\n"
                  + field.getValue()
                  + "\r\n")
              .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    body.write(
        ("--"
                + boundary
                + "\r\n"
                + "Content-Disposition: form-data; name=\"upload\"; filename=\"test.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    body.write(file);
    body.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return client.send(
        HttpRequest.newBuilder(URI.create(base + route))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void multipartUploadOverridesUrlAndRejectsInvalidFiles() throws Exception {
    assertEquals(
        302,
        post(
                "/auth/login",
                "_csrf="
                    + csrf("/auth/login")
                    + "&login=admin&password=demo-password-123&mode=session")
            .statusCode());
    var fields =
        new HashMap<>(
            Map.of(
                "_csrf",
                csrf("/admin/product/add"),
                "categoryId",
                "1",
                "title",
                "Uploaded book",
                "author",
                "Writer",
                "price",
                "100",
                "stock",
                "1",
                "image",
                "invalid-url"));
    var png = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        png);
    var created = upload("/admin/product/save", fields, png.toByteArray());
    assertEquals(302, created.statusCode(), created.body());
    var detail = get("/product/detail?id=14");
    var media =
        java.util.regex.Pattern.compile("/bookstore(/media/[a-f0-9-]{36}\\.png)")
            .matcher(detail.body());
    assertTrue(media.find(), detail.body());
    assertEquals(200, get(media.group(1)).statusCode());
    assertSafeError(upload("/admin/product/save", fields, new byte[] {1, 2, 3}), 400);
    var tooLarge = upload("/admin/product/save", fields, new byte[5 * 1024 * 1024 + 1]);
    assertSafeError(tooLarge, 413);
    assertTrue(tooLarge.body().contains("Về trang chủ"));
  }

  @Test
  void catalogPagesAndDetailsRender() throws Exception {
    assertEquals(10, countCards(get("/").body()));
    for (int n = 1; n <= 3; n++) {
      var page = get("/product?page=" + n);
      assertEquals(200, page.statusCode(), page.body());
      assertEquals(n == 3 ? 1 : 6, countCards(page.body()));
    }
    var detail = get("/product/detail?id=13");
    assertEquals(200, detail.statusCode(), detail.body());
    assertTrue(detail.body().contains("Một đời đáng đọc"));
    assertEquals(404, get("/product/detail?id=999").statusCode());
    assertEquals(400, get("/product?page=0").statusCode());
    assertEquals(404, get("/product?page=4").statusCode());
    for (String route :
        List.of("/auth/register", "/auth/activate", "/auth/forgot", "/auth/reset")) {
      var response = get(route);
      assertEquals(200, response.statusCode(), response.body());
    }
  }

  @Test
  void otpRegistrationActivationAndResetThroughHttp() throws Exception {
    String token = csrf("/auth/register");
    var registered =
        post(
            "/auth/register",
            "_csrf="
                + token
                + "&username=webreader&email=webreader%40example.test&password=web-password-123&confirm=web-password-123");
    assertEquals(302, registered.statusCode(), registered.body());
    String code = mail.code();
    assertEquals(
        302,
        post(
                "/auth/activate",
                "_csrf=" + csrf("/auth/activate") + "&email=webreader%40example.test&code=" + code)
            .statusCode());
    var login =
        post(
            "/auth/login",
            "_csrf="
                + csrf("/auth/login")
                + "&login=webreader&password=web-password-123&mode=session");
    assertEquals(302, login.statusCode(), login.body());
    assertEquals(403, get("/admin/products").statusCode());
    assertEquals(
        302,
        post("/auth/forgot", "_csrf=" + csrf("/auth/forgot") + "&email=webreader%40example.test")
            .statusCode());
    String resetCode = mail.code();
    var reset =
        post(
            "/auth/reset",
            "_csrf="
                + csrf("/auth/reset")
                + "&email=webreader%40example.test&code="
                + resetCode
                + "&password=changed-password-123&confirm=changed-password-123");
    assertEquals(302, reset.statusCode(), reset.body());
    assertEquals(
        302,
        post(
                "/auth/login",
                "_csrf="
                    + csrf("/auth/login")
                    + "&login=webreader&password=changed-password-123&mode=cookie")
            .statusCode());
  }

  @Test
  void productAndCategoryAdminForms() throws Exception {
    assertEquals(
        302,
        post(
                "/auth/login",
                "_csrf="
                    + csrf("/auth/login")
                    + "&login=admin&password=demo-password-123&mode=session")
            .statusCode());
    for (String route :
        List.of(
            "/admin/products",
            "/admin/product/add",
            "/admin/product/edit?id=1",
            "/admin/category/edit?id=1")) {
      var page = get(route);
      assertEquals(200, page.statusCode(), page.body());
    }
    String token = csrf("/admin/product/add");
    var added =
        post(
            "/admin/product/save",
            "_csrf="
                + token
                + "&categoryId=1&title=%3Cscript%3Ealert(1)%3C%2Fscript%3E&author=Test&price=99000&stock=3&description=Demo&image=");
    assertEquals(302, added.statusCode(), added.body());
    var detail = get("/product/detail?id=14");
    assertTrue(detail.body().contains("&lt;script&gt;"));
    assertFalse(detail.body().contains("<script>alert(1)</script>"));
    assertEquals(409, post("/admin/category/delete", "_csrf=" + token + "&id=1").statusCode());
    assertEquals(
        400,
        post(
                "/admin/product/save",
                "_csrf=" + token + "&categoryId=1&title=Book&author=Test&price=-1&stock=3")
            .statusCode());
    assertEquals(302, post("/admin/product/delete", "_csrf=" + token + "&id=14").statusCode());
    assertEquals(404, get("/product/detail?id=14").statusCode());
  }
}
