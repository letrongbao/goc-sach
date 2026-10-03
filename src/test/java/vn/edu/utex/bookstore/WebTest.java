package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.junit.jupiter.api.*;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.config.*;

class WebTest {
  @Test
  void inventoryReceiveEditHistoryAndOwnershipWorkThroughHttp() throws Exception {
    App app = context.getBean(App.class);
    Identity admin = app.store.tx(d -> Identity.of(d.findLogin("admin")));
    Identity reader = app.store.tx(d -> Identity.of(d.findLogin("reader")));
    long shop = app.shops.approve(admin, app.shops.apply(reader, "Kho sách thử", "123 Book Street City"));
    long otherShop = app.shops.approve(admin, app.shops.apply(admin, "Kho khác", "456 Book Street City"));
    assertEquals(302, get("/seller/inventory?shopId=" + shop).statusCode());
    assertEquals(302, post("/auth/login", "_csrf=" + csrf("/auth/login")
        + "&login=reader&password=demo-password-123&mode=session").statusCode());
    String token = csrf("/seller/copy/add?shopId=" + shop);
    String input = "shopId=" + shop + "&productId=1&condition=GOOD&defects=%3Cscript%3Ex%3C%2Fscript%3E&price=40000&reason=Received+for+testing";
    assertEquals(403, post("/seller/copy/add", input).statusCode());
    var invalid = post("/seller/copy/add", "_csrf=" + token + "&" + input.replace("price=40000", "price=-1"));
    assertEquals(400, invalid.statusCode(), invalid.body());
    assertTrue(invalid.body().contains("&lt;script&gt;x&lt;/script&gt;"));
    assertEquals(302, post("/seller/copy/add", "_csrf=" + token + "&" + input).statusCode());
    var form = get("/seller/copy/edit?shopId=" + shop + "&id=1");
    assertEquals(200, form.statusCode(), form.body()); assertTrue(form.body().contains("Lịch sử kho"));
    assertTrue(form.body().contains("&lt;script&gt;x&lt;/script&gt;"));
    String update = "_csrf=" + token + "&shopId=" + shop + "&id=1&version=0&condition=GOOD&price=45000&status=AVAILABLE&reason=Checked+for+sale";
    assertEquals(302, post("/seller/copy/edit", update).statusCode());
    assertEquals(409, post("/seller/copy/edit", update).statusCode());
    var list = get("/seller/inventory?shopId=" + shop);
    assertEquals(200, list.statusCode(), list.body()); assertTrue(list.body().contains("Sẵn bán"));
    assertEquals(403, get("/seller/copy/edit?shopId=" + otherShop + "&id=1").statusCode());
    assertEquals(403, post("/seller/copy/edit", update.replace("shopId=" + shop, "shopId=" + otherShop)).statusCode());
    app.shops.setSuspended(admin, shop, true, "Testing suspension");
    assertEquals(409, post("/seller/copy/add", "_csrf=" + token + "&" + input).statusCode());
    var readonly = get("/seller/copy/edit?shopId=" + shop + "&id=1");
    assertEquals(200, readonly.statusCode(), readonly.body()); assertFalse(readonly.body().contains("id=\"copy-price\""));
  }

  @Test
  void shopApplicationApprovalAndMembershipScreensWork() throws Exception {
    assertEquals(302, get("/account/shops").statusCode());
    assertEquals(302, post("/auth/login", "_csrf=" + csrf("/auth/login")
        + "&login=reader&password=demo-password-123&mode=session").statusCode());
    String userCsrf = csrf("/account/shops");
    assertEquals(403, post("/account/shop/apply", "name=Shop&address=Some+business+address").statusCode());
    var invalid = post("/account/shop/apply", "_csrf=" + userCsrf + "&name=Keep+name&address=short");
    assertEquals(400, invalid.statusCode(), invalid.body());
    assertTrue(invalid.body().contains("Keep name"));
    var submitted = post("/account/shop/apply", "_csrf=" + userCsrf
        + "&name=%3Cscript%3Ebook%3C%2Fscript%3E&address=123+Book+Street+City");
    assertEquals(302, submitted.statusCode(), submitted.body());
    var history = get("/account/shops");
    assertEquals(200, history.statusCode(), history.body());
    assertTrue(history.body().contains("&lt;script&gt;book&lt;/script&gt;"));
    assertEquals(403, post("/admin/shop/approve", "_csrf=" + userCsrf + "&id=1").statusCode());
    assertEquals(302, post("/auth/login", "_csrf=" + csrf("/auth/login")
        + "&login=admin&password=demo-password-123&mode=session").statusCode());
    String adminCsrf = csrf("/admin/shops");
    assertEquals(302, post("/admin/shop/approve", "_csrf=" + adminCsrf + "&id=1").statusCode());
    assertEquals(302, post("/admin/shop/approve", "_csrf=" + adminCsrf + "&id=1").statusCode());
    var adminShop = get("/seller/shop?id=1");
    assertEquals(200, adminShop.statusCode(), adminShop.body());
    assertTrue(adminShop.body().contains("Chủ cửa hàng"));
    assertEquals(409, post("/seller/shop/member", "_csrf=" + adminCsrf
        + "&shopId=1&login=reader&role=SELLER").statusCode());
    assertEquals(302, post("/admin/shop/status", "_csrf=" + adminCsrf
        + "&shopId=1&status=SUSPENDED&reason=Verify+business+details").statusCode());
    assertEquals(302, post("/auth/login", "_csrf=" + csrf("/auth/login")
        + "&login=reader&password=demo-password-123&mode=session").statusCode());
    var ownerShop = get("/seller/shop?id=1");
    assertEquals(200, ownerShop.statusCode(), ownerShop.body());
    assertTrue(ownerShop.body().contains("Cửa hàng đang tạm khóa"));
    assertEquals(200, get("/seller/shops").statusCode());
  }

  @Test
  void shopRejectionReasonRemainsVisibleToApplicant() throws Exception {
    assertEquals(302, post("/auth/login", "_csrf=" + csrf("/auth/login")
        + "&login=reader&password=demo-password-123&mode=session").statusCode());
    assertEquals(302, post("/account/shop/apply", "_csrf=" + csrf("/account/shops")
        + "&name=Book+Corner&address=123+Book+Street+City").statusCode());
    assertEquals(302, post("/auth/login", "_csrf=" + csrf("/auth/login")
        + "&login=admin&password=demo-password-123&mode=session").statusCode());
    assertEquals(302, post("/admin/shop/reject", "_csrf=" + csrf("/admin/shops")
        + "&id=1&reason=Please+complete+the+address").statusCode());
    assertEquals(302, post("/auth/login", "_csrf=" + csrf("/auth/login")
        + "&login=reader&password=demo-password-123&mode=session").statusCode());
    var page = get("/account/shops");
    assertEquals(200, page.statusCode(), page.body());
    assertTrue(page.body().contains("Please complete the address"));
    assertFalse(get("/seller/shops").body().contains("Mở cửa hàng</a>"));
  }

  @Test
  void bootRendersThymeleafAndServesAssetsWithoutDatabaseInitialization() throws Exception {
    assertNotNull(context.getBean(org.thymeleaf.spring6.SpringTemplateEngine.class));
    assertNotNull(context.getBean(vn.edu.utex.bookstore.web.FrontController.class));
    assertEquals("never", context.getEnvironment().getProperty("spring.sql.init.mode"));
    assertEquals("validate", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
    assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
    var page = get("/");
    assertEquals(200, page.statusCode());
    assertFalse(page.body().contains("th:replace"));
    assertFalse(page.body().contains("<c:"));
    assertFalse(page.body().contains("${"));
    for (String asset : List.of("app.css", "bootstrap.min.css", "typography.css", "responsive.css"))
      assertEquals(200, get("/assets/" + asset).statusCode(), asset);
  }

  @Test
  void normalizedAdminPathsCannotBypassAuthorization() throws Exception {
    for (String path :
        List.of("/admin;ignored/categories", "/%61dmin/categories", "/admin;ignored/products")) {
      var response = get(path);
      assertTrue(response.statusCode() == 302 || response.statusCode() == 400, path + response.body());
    }
  }

  private ServletWebServerApplicationContext context;
  private HttpClient client;
  private String base;
  private OtpTest.Mail mail;
  private OtpTest.MutableClock clock;

  @BeforeEach
  void start() throws Exception {
    var props = new Properties();
    props.setProperty("cookie.secure", "false");
    props.setProperty("otp.secret", "test-only-secret-not-for-production-0123456789");
    MemoryStore store = DemoFixtures.store();
    mail = new OtpTest.Mail();
    clock = new OtpTest.MutableClock();
    App app = new App(new Settings(props), store, clock, mail);
    context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(GocSachApplication.class)
        .properties("server.port=0", "server.address=127.0.0.1", "server.servlet.context-path=/bookstore", "logging.level.root=ERROR")
        .initializers(ctx -> {
          var registry = (org.springframework.context.support.GenericApplicationContext) ctx;
          registry.registerBean("fixtureApp", App.class, () -> app);
          registry.registerBean("testFailure", ServletRegistrationBean.class, () ->
              new ServletRegistrationBean<>(new jakarta.servlet.http.HttpServlet() {
                protected void doGet(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res)
                    throws java.io.IOException {
                  if (req.getParameter("status") != null)
                    res.sendError(Integer.parseInt(req.getParameter("status")), "internal-test-detail");
                  else throw new IllegalStateException("internal-test-detail");
                }
              }, "/_test/failure"));
        }).run("--server.port=0");
    base = "http://127.0.0.1:" + context.getWebServer().getPort() + "/bookstore";
    client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
  }

  @AfterEach
  void stop() throws Exception {
    if (context != null) context.close();
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

  @Test
  void jwtApiIsNotAvailableBeforeExternalKeysAreConfigured() throws Exception {
    var response = get("/api/me");
    assertEquals(401, response.statusCode());
    assertTrue(response.body().contains("not_authenticated"));
    var forged = client.send(HttpRequest.newBuilder(URI.create(base + "/api/me"))
        .header("Authorization", "Bearer forged").build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(401, forged.statusCode());
    assertEquals(401, post("/api/future-endpoint", "").statusCode());
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
    assertTrue(get("/admin/products").body().contains("13 đầu sách"));
    assertEquals(0, countCards(get("/product").body()));
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
    assertTrue(detail.body().contains("data:image/png;base64,"), detail.body());
    assertFalse(detail.body().contains("/media/"), detail.body());
    assertSafeError(upload("/admin/product/save", fields, new byte[] {1, 2, 3}), 400);
    var tooLarge = upload("/admin/product/save", fields, new byte[5 * 1024 * 1024 + 1]);
    assertSafeError(tooLarge, 413);
    assertTrue(tooLarge.body().contains("Về trang chủ"), tooLarge.body());
  }

  @Test
  void catalogPagesAndDetailsRender() throws Exception {
    DemoFixtures.catalogStock((MemoryStore) context.getBean(App.class).store);
    assertEquals(5, countCards(get("/").body()));
    var page = get("/product?page=1");
    assertEquals(200, page.statusCode(), page.body());
    assertEquals(13, countCards(page.body()));
    var search = get("/product?q=" + URLEncoder.encode("Lập trình", java.nio.charset.StandardCharsets.UTF_8));
    assertEquals(200, search.statusCode(), search.body());
    assertEquals(1, countCards(search.body()));
    var cheapest = get("/product?sort=price_asc&minPrice=80000&maxPrice=90000&condition=GOOD");
    assertEquals(200, cheapest.statusCode(), cheapest.body());
    assertEquals(3, countCards(cheapest.body()));
    assertTrue(cheapest.body().indexOf("Những ngày có nắng") < cheapest.body().indexOf("Một hành trình nhỏ"));
    assertTrue(cheapest.body().contains("value=\"price_asc\" selected=\"selected\""));
    var expensive = get("/product?sort=price_desc&minPrice=80000&maxPrice=90000&condition=GOOD");
    assertEquals(200, expensive.statusCode(), expensive.body());
    assertTrue(expensive.body().indexOf("Một hành trình nhỏ") < expensive.body().indexOf("Những ngày có nắng"));
    assertEquals(400, get("/product?sort=unknown").statusCode());
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
  void publicCatalogTracksInventoryAndAdminCannotOverwriteStock() throws Exception {
    App app = context.getBean(App.class);
    DemoFixtures.catalogStock((MemoryStore) app.store);
    Identity admin = app.store.tx(d -> Identity.of(d.findLogin("admin")));
    var detail = get("/product/detail?id=1");
    assertEquals(200, detail.statusCode(), detail.body());
    assertTrue(detail.body().contains("data-copy-id=\"1\""));
    assertTrue(detail.body().contains("Góc Sách Demo"));
    app.shops.setSuspended(admin, 1, true, "Testing catalog withdrawal");
    assertEquals(0, countCards(get("/").body()));
    var withdrawn = get("/product/detail?id=1");
    assertTrue(withdrawn.body().contains("Cuốn sách này đang tạm hết hàng"));
    assertFalse(withdrawn.body().contains("data-copy-id="));
    assertFalse(withdrawn.body().contains("class=\"detail-price\""));
    assertEquals(302, post("/auth/login", "_csrf=" + csrf("/auth/login")
        + "&login=admin&password=demo-password-123&mode=session").statusCode());
    var form = get("/admin/product/edit?id=1");
    assertFalse(form.body().contains("name=\"stock\"")); assertFalse(form.body().contains("name=\"price\""));
    assertEquals(302, post("/admin/product/save", "_csrf=" + csrf("/admin/product/edit?id=1")
        + "&id=1&categoryId=1&title=Updated&author=Writer&description=Metadata&price=1&stock=999&image=").statusCode());
    assertNotNull(app.store.tx(d -> d.product(1)));
    app.shops.setSuspended(admin, 1, false, "Catalog reactivated");
    assertEquals(13, countCards(get("/product").body()));
    assertTrue(get("/product/detail?id=1").body().contains("75.000"));
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
                + "&categoryId=1&title=%3Cscript%3Ealert(1)%3C%2Fscript%3E&author=Test&price=99000&stock=3&description=Demo&image=https%3A%2F%2Fevil.example%2Fcover.png");
    assertEquals(302, added.statusCode(), added.body());
    var detail = get("/product/detail?id=14");
    assertTrue(detail.body().contains("&lt;script&gt;"));
    assertFalse(detail.body().contains("<script>alert(1)</script>"));
    assertFalse(detail.body().contains("evil.example"));
    assertEquals(409, post("/admin/category/delete", "_csrf=" + token + "&id=1").statusCode());
    assertEquals(
        400,
        post(
                "/admin/product/save",
                "_csrf=" + token + "&categoryId=1&title=&author=Test")
            .statusCode());
    assertEquals(302, post("/admin/product/delete", "_csrf=" + token + "&id=14").statusCode());
    assertEquals(404, get("/product/detail?id=14").statusCode());
  }

  @Test
  void buyerAccountPagesAreProtectedAndPersistPreviewData() throws Exception {
    assertEquals(302, get("/account/profile").statusCode());
    assertEquals(302, get("/checkout").statusCode());
    assertEquals(302, get("/orders").statusCode());
    assertEquals(302, post("/auth/login", "_csrf=" + csrf("/auth/login")
        + "&login=reader&password=demo-password-123&mode=session").statusCode());
    App app = context.getBean(App.class);
    MemoryStore memory = (MemoryStore) app.store;
    DemoFixtures.catalogStock(memory);
    var profile = get("/account/profile");
    assertEquals(200, profile.statusCode(), profile.body());
    assertTrue(profile.body().contains("Thông tin cá nhân"));
    assertEquals(302, post("/account/profile", "_csrf=" + csrf("/account/profile")
        + "&displayName=Bạn+đọc&phone=0900000000").statusCode());
    assertEquals(200, get("/account/addresses").statusCode());
    String token = csrf("/account/addresses");
    assertEquals(302, post("/account/address/save", "_csrf=" + token
        + "&label=Nhà&recipientName=Bạn+đọc&phone=0900000000&line1=1+Đường+Sách&city=TP+HCM&defaultAddress=on").statusCode());
    var addresses = get("/account/addresses");
    assertTrue(addresses.body().contains("Địa chỉ mặc định"));
    long copyId = memory.copies.values().iterator().next().id;
    assertEquals(302, post("/cart/items/add", "_csrf=" + csrf("/product/detail?id=1") + "&copyId=" + copyId).statusCode());
    var cart = get("/cart");
    assertEquals(200, cart.statusCode());
    assertTrue(cart.body().contains("Tiền sách"));
    assertFalse(cart.body().contains("Đầu sách</th>"));
    var checkout = get("/checkout");
    assertEquals(200, checkout.statusCode(), checkout.body());
    assertTrue(checkout.body().contains("idempotencyKey"));
    var key = java.util.regex.Pattern.compile("name=\"idempotencyKey\" value=\"([^\"]+)\"").matcher(checkout.body());
    assertTrue(key.find(), checkout.body());
    var secondView = get("/checkout");
    assertTrue(secondView.body().contains("value=\"" + key.group(1) + "\""));
    var fingerprint = java.util.regex.Pattern.compile("name=\"quoteFingerprint\" value=\"([^\"]+)\"")
        .matcher(checkout.body());
    assertTrue(fingerprint.find(), checkout.body());
    long addressId = memory.addresses(2).getFirst().id;
    var placed = post("/checkout", "_csrf=" + csrf("/checkout") + "&addressId=" + addressId
        + "&idempotencyKey=" + key.group(1) + "&quoteFingerprint=" + fingerprint.group(1));
    assertEquals(302, placed.statusCode(), placed.body());
    long orderId = memory.orders.values().iterator().next().id;
    assertEquals(200, get("/orders/" + orderId).statusCode());
    assertEquals(200, get("/seller/orders").statusCode());
    assertEquals(200, get("/seller/orders/" + orderId).statusCode());
    assertEquals(403, post("/seller/orders/" + orderId + "/status", "status=CONFIRMED").statusCode());
    assertEquals(302, post("/seller/orders/" + orderId + "/status",
        "_csrf=" + csrf("/seller/orders/" + orderId) + "&status=CONFIRMED").statusCode());
    assertEquals("CONFIRMED", memory.orders.get(orderId).status);
  }
}
