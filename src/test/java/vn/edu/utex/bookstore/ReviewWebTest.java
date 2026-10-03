package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import vn.edu.utex.bookstore.buyer.BuyerService;
import vn.edu.utex.bookstore.config.App;
import vn.edu.utex.bookstore.config.Settings;
import vn.edu.utex.bookstore.order.OrderService;

class ReviewWebTest {
  private ServletWebServerApplicationContext context;
  private HttpClient client;
  private String base;
  private MemoryStore store;

  @BeforeEach
  void start() {
    var properties=new Properties();
    properties.setProperty("cookie.secure","false");
    properties.setProperty("otp.secret","test-only-secret-not-for-production-0123456789");
    store=DemoFixtures.store(); DemoFixtures.catalogStock(store);
    var app=new App(new Settings(properties),store,Clock.systemUTC(),new OtpTest.Mail());
    context=(ServletWebServerApplicationContext)new SpringApplicationBuilder(GocSachApplication.class)
        .properties("server.port=0","server.address=127.0.0.1","server.servlet.context-path=/bookstore","logging.level.root=ERROR")
        .initializers(ctx->((GenericApplicationContext)ctx).registerBean("fixtureApp",App.class,()->app))
        .run("--server.port=0");
    base="http://127.0.0.1:"+context.getWebServer().getPort()+"/bookstore";
    client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
  }

  @AfterEach
  void stop(){if(context!=null)context.close();}

  @Test
  void completedBuyerCanCreateAndEditWithFieldErrorsCsrfAndEscapedContent() throws Exception {
    completedPurchase(); login("reader");
    String route="/account/reviews/1";
    var editor=get(route);
    assertEquals(200,editor.statusCode(),editor.body());
    assertTrue(editor.body().contains("Mỗi chia sẻ, thêm một người yêu sách."));
    assertTrue(editor.body().contains("/bookstore/account/reviews/1"));
    assertEquals(403,post(route,Map.of("rating","5","body","x".repeat(50))).statusCode());
    assertTrue(store.reviews.isEmpty());

    var invalid=post(route,Map.of("_csrf",csrf(editor),"rating","4","body","<b>Nội dung ngắn</b>"));
    assertEquals(400,invalid.statusCode(),invalid.body());
    assertTrue(invalid.body().contains("ít nhất 50 ký tự"));
    assertTrue(invalid.body().contains("&lt;b&gt;Nội dung ngắn&lt;/b&gt;"));
    assertTrue(invalid.body().contains("aria-invalid=\"true\""));
    assertTrue(hasAttribute(inputTag(invalid.body(),"rating","4"),"checked"));
    var noRating=post(route,Map.of("_csrf",csrf(editor),"rating","bogus","body","x".repeat(50)));
    assertEquals(400,noRating.statusCode());
    assertTrue(noRating.body().contains("Hãy chọn điểm đánh giá"));
    assertTrue(store.reviews.isEmpty());

    String body="Sách còn đẹp, nội dung dễ đọc và cách đóng gói rất cẩn thận. <script>alert('test')</script>";
    var saved=post(route,Map.of("_csrf",csrf(editor),"rating","5","body",body,"mode","create"));
    assertEquals(302,saved.statusCode(),saved.body());
    assertRedirect(saved,"/bookstore/product/detail?id=1#reviews");
    assertEquals(1,store.reviews.size());
    assertEquals(409,post(route,Map.of("_csrf",csrf(editor),"rating","5","body",body,"mode","create")).statusCode());
    var edit=get(route);
    assertEquals(200,edit.statusCode(),edit.body());
    assertEquals("update",attribute(inputTag(edit.body(),"mode",null),"value"));
    assertFalse(edit.body().contains("<script>alert"));
    assertTrue(edit.body().contains("&lt;script&gt;"));
    assertEquals(302,post(route,Map.of("_csrf",csrf(edit),"rating","3","body",body+" Tôi đã đọc xong.","mode","update")).statusCode());
    assertEquals(1,store.reviews.size());
    assertEquals(3,store.review(2,1).rating);

    var publicReviews=get("/api/products/1/reviews");
    assertEquals(200,publicReviews.statusCode(),publicReviews.body());
    assertEquals("1",publicReviews.headers().firstValue("X-Total-Count").orElseThrow());
    for(String privateField:new String[]{"userId","orderId","email","passwordHash"})
      assertFalse(publicReviews.body().contains(privateField));
    assertEquals(400,get("/api/products/1/reviews?page=bad").statusCode());
    assertEquals(404,get("/api/products/1/reviews?page=2").statusCode());
  }

  @Test
  void guestOtherBuyerAndIncompleteOrdersCannotWriteReviews() throws Exception {
    var guest=get("/account/reviews/1");
    assertEquals(302,guest.statusCode());
    assertRedirect(guest,"/bookstore/auth/login");
    login("reader");
    assertEquals(403,get("/account/reviews/1").statusCode());
    String token=csrf(get("/auth/login"));
    assertEquals(403,post("/account/reviews/1",Map.of("_csrf",token,"rating","5","body","x".repeat(50))).statusCode());
    completedPurchase();
    login("admin");
    assertEquals(403,get("/account/reviews/1").statusCode());
    assertEquals(403,post("/account/reviews/1",Map.of("_csrf",csrf(get("/auth/login")),"rating","5","body","x".repeat(50),"mode","update")).statusCode());
    assertTrue(store.reviews.isEmpty());
  }

  @Test
  void hiddenBookReviewsStayPrivateWhileBuyerCanEditTheirPurchaseHistory() throws Exception {
    completedPurchase();
    store.product(1).category.active=false;
    assertEquals(404,get("/api/products/1/reviews").statusCode());
    login("reader");
    String route="/account/reviews/1";
    var editor=get(route);
    assertEquals(200,editor.statusCode(),editor.body());
    assertTrue(editor.body().contains("tạm ẩn khỏi tủ sách công khai"));
    assertTrue(editor.body().contains("Quay lại đơn hàng"));
    assertFalse(editor.body().contains("/bookstore/product/detail?id=1"));
    String body="Đây là một cuốn sách có nội dung hay, tình trạng nhận được đúng mô tả và được đóng gói cẩn thận.";
    var created=post(route,Map.of("_csrf",csrf(editor),"rating","5","body",body,"mode","create"));
    assertEquals(302,created.statusCode(),created.body());
    assertRedirect(created,"/bookstore"+route);
    var historicalEdit=get(route);
    assertEquals(200,historicalEdit.statusCode(),historicalEdit.body());
    assertEquals("update",attribute(inputTag(historicalEdit.body(),"mode",null),"value"));
    var updated=post(route,Map.of("_csrf",csrf(historicalEdit),"rating","4","body",body+" Tôi đã đọc xong.","mode","update"));
    assertEquals(302,updated.statusCode(),updated.body());
    assertRedirect(updated,"/bookstore"+route);
    assertEquals(4,store.review(2,1).rating);
    assertEquals(404,get("/api/products/1/reviews").statusCode());
  }

  private void completedPurchase() {
    var buyer=new BuyerService(store,Clock.systemUTC());
    var address=buyer.saveAddress(2,null,"Nhà","Bạn đọc","0900000000","1 Đường Sách","","","TP HCM",true);
    buyer.addToCart(2,store.copies.values().iterator().next().id);
    new OrderService(store,Clock.systemUTC()).checkout(2,address.id,"review-web-purchase-001").orders().getFirst().status="COMPLETED";
  }

  private void login(String username) throws Exception {
    assertEquals(302,post("/auth/login",Map.of("_csrf",csrf(get("/auth/login")),"login",username,
        "password","demo-password-123","mode","session")).statusCode());
  }

  private static String csrf(HttpResponse<String> page) {
    assertEquals(200,page.statusCode(),page.body());
    return attribute(inputTag(page.body(),"_csrf",null),"value");
  }

  private void assertRedirect(HttpResponse<String> response,String expectedLocation) {
    URI origin=URI.create(base);
    URI actual=origin.resolve(response.headers().firstValue("location").orElseThrow());
    URI expected=URI.create(expectedLocation);
    assertEquals(origin.getScheme(),actual.getScheme());
    assertEquals(origin.getHost(),actual.getHost());
    assertEquals(origin.getPort(),actual.getPort());
    assertNull(actual.getUserInfo());
    assertEquals(expected.getRawPath(),actual.getRawPath());
    assertEquals(expected.getRawQuery(),actual.getRawQuery());
    assertEquals(expected.getRawFragment(),actual.getRawFragment());
  }

  private static String inputTag(String html,String name,String value) {
    var inputs=Pattern.compile("<input\\b[^>]*>",Pattern.CASE_INSENSITIVE).matcher(html);
    while(inputs.find()) {
      String tag=inputs.group();
      if(name.equals(optionalAttribute(tag,"name"))&&(value==null||value.equals(optionalAttribute(tag,"value"))))
        return tag;
    }
    fail("Missing input "+name+" with value "+value);
    return "";
  }

  private static String attribute(String tag,String name) {
    String value=optionalAttribute(tag,name);
    assertNotNull(value,"Missing attribute "+name+" in "+tag);
    return value;
  }

  private static String optionalAttribute(String tag,String name) {
    var match=Pattern.compile("\\s"+Pattern.quote(name)+"\\s*=\\s*\"([^\"]*)\"").matcher(tag);
    return match.find()?match.group(1):null;
  }

  private static boolean hasAttribute(String tag,String name) {
    return Pattern.compile("\\s"+Pattern.quote(name)+"(?:\\s*=\\s*\"[^\"]*\")?(?=\\s|/?>)").matcher(tag).find();
  }

  private HttpResponse<String> get(String route) throws Exception {
    return client.send(HttpRequest.newBuilder(URI.create(base+route)).GET().build(),HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String route,Map<String,String> values) throws Exception {
    String form=values.entrySet().stream().map(entry->URLEncoder.encode(entry.getKey(),StandardCharsets.UTF_8)+"="
        +URLEncoder.encode(entry.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
    return client.send(HttpRequest.newBuilder(URI.create(base+route)).header("Content-Type","application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(form)).build(),HttpResponse.BodyHandlers.ofString());
  }
}
