package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.edu.utex.bookstore.config.*;

class ExpandedApiWebTest {
  @TempDir(cleanup = CleanupMode.NEVER) Path directory;
  private ServletWebServerApplicationContext context;
  private HttpClient client;
  private CookieManager cookies;
  private String base;
  private MemoryStore store;
  private KeyPair pair;
  private TestClock clock;
  private final ObjectMapper json = new ObjectMapper();

  static class TestClock extends Clock {
    Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return now; }
  }

  @BeforeEach void start() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    pair = generator.generateKeyPair();
    Path privateKey = directory.resolve("private.pem"), publicKey = directory.resolve("public.pem");
    Files.writeString(privateKey, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
    Files.writeString(publicKey, pem("PUBLIC KEY", pair.getPublic().getEncoded()));
    var properties = new Properties();
    properties.setProperty("cookie.secure", "false");
    properties.setProperty("otp.secret", "test-only-secret-not-for-production-0123456789");
    properties.setProperty("jwt.private-key-file", privateKey.toString());
    properties.setProperty("jwt.public-key-file", publicKey.toString());
    properties.setProperty("jwt.issuer", "https://goc-sach.test");
    properties.setProperty("jwt.audience", "goc-sach-api");
    store = FinalDemoFixtures.create(Clock.systemUTC());
    clock = new TestClock();
    App app = new App(new Settings(properties), store, clock, (to, subject, body) -> {});
    context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(GocSachApplication.class)
        .properties("server.port=0", "server.address=127.0.0.1", "server.servlet.context-path=/bookstore", "logging.level.root=ERROR")
        .initializers(ctx -> {
          var registry = (GenericApplicationContext) ctx;
          registry.registerBean("fixtureApp", App.class, () -> app);
          registry.registerBean("fixtureClock", Clock.class, () -> clock);
        }).run("--server.port=0");
    base = "http://127.0.0.1:" + context.getWebServer().getPort() + "/bookstore";
    cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    client = HttpClient.newBuilder().cookieHandler(cookies).build();
  }

  private String pem(String label, byte[] bytes) {
    return "-----BEGIN " + label + "-----\r\n"
        + Base64.getMimeEncoder(64, new byte[] {13, 10}).encodeToString(bytes)
        + "\r\n-----END " + label + "-----\r\n";
  }

  @AfterEach void stop() {
    if (context != null) context.close();
    TempDirectorySupport.deleteEventually(directory);
  }
  HttpResponse<String> get(String path) throws Exception {
    return client.send(HttpRequest.newBuilder(URI.create(base + path)).build(), HttpResponse.BodyHandlers.ofString());
  }
  HttpResponse<String> post(String path, String body) throws Exception {
    return client.send(HttpRequest.newBuilder(URI.create(base + path))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
  }
  String csrf() throws Exception {
    var page = get("/auth/login");
    assertEquals(200, page.statusCode(), page.body());
    var matcher = java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
    assertTrue(matcher.find(), page.body());
    return matcher.group(1);
  }
  void login(String mode) throws Exception {
    var response = post("/auth/login", "_csrf=" + csrf() + "&login=reader&password=demo-password-123&mode=" + mode);
    assertEquals(302, response.statusCode(), response.body());
  }
  String token() throws Exception {
    var response = post("/auth/api-token", "_csrf=" + csrf());
    assertEquals(200, response.statusCode(), response.body());
    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
    return json.readTree(response.body()).get("access_token").asText();
  }
  HttpResponse<String> api(String token, String path) throws Exception {
    return client.send(HttpRequest.newBuilder(URI.create(base + path)).header("Authorization", "Bearer " + token)
        .build(), HttpResponse.BodyHandlers.ofString());
  }
  HttpResponse<String> apiPost(String token, String path, String body) throws Exception {
    return client.send(HttpRequest.newBuilder(URI.create(base + path))
        .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
  }
  String resign(String raw, java.util.function.Consumer<Map<String, Object>> edit) {
    var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build();
    Map<String, Object> claims = new HashMap<>(decoder.decode(raw).getClaims());
    edit.accept(claims);
    var jwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate())
        .keyID("goc-sach-rs256").build();
    var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
        JwtClaimsSet.builder().claims(values -> values.putAll(claims)).build())).getTokenValue();
  }


  String loginAs(String name) throws Exception {
    var response=post("/auth/login","_csrf="+csrf()+"&login="+name+"&password=demo-password-123&mode=session");
    assertEquals(302,response.statusCode(),response.body());return token();
  }
  HttpResponse<String> call(String raw,String method,String path,String body) throws Exception {
    var builder=HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","application/json");
    if(raw!=null)builder.header("Authorization","Bearer "+raw);
    return client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
  }
  void status(int expected,HttpResponse<String> result){assertEquals(expected,result.statusCode(),result.body());}
  @Test void publicCatalogAndPrivateBoundaries() throws Exception {
    for(String url:List.of("/api/categories","/api/products","/api/products/1","/api/rankings/bestselling","/api/shops"))status(200,get(url));
    status(400,get("/api/products/not-a-number"));
    status(401,get("/api/products/1/reviews/mine"));
    status(401,call(null,"POST","/api/admin/categories","{}"));
    String raw=loginAs("buyer");
    status(401,get("/api/account/profile"));
    status(403,api(raw,"/api/admin/users"));
    status(400,call(raw,"PUT","/api/account/profile","{"));
    status(400,call(raw,"PUT","/api/account/profile","{}"));
  }
  @Test void addressOwnershipAndProfile() throws Exception {
    String raw=loginAs("buyer");
    status(200,call(raw,"PUT","/api/account/profile","{\"displayName\":\"API Buyer\",\"phone\":\"0901234567\"}"));
    var created=call(raw,"POST","/api/account/addresses","{\"label\":\"Nhà\",\"recipientName\":\"API Buyer\",\"phone\":\"0901234567\",\"line1\":\"12 Đường A\",\"ward\":\"Phường A\",\"district\":\"Thủ Đức\",\"city\":\"Hồ Chí Minh\",\"defaultAddress\":true}");
    status(201,created);long id=json.readTree(created.body()).get("id").asLong();
    status(200,api(raw,"/api/account/addresses/"+id));
    String other=loginAs("reader");
    status(404,api(other,"/api/account/addresses/"+id));
    status(404,call(other,"DELETE","/api/account/addresses/"+id,null));
    raw=loginAs("buyer");status(204,call(raw,"DELETE","/api/account/addresses/"+id,null));
    status(404,api(raw,"/api/account/addresses/"+id));
  }
  @Test void inventoryPermissionsAndOptimisticVersion() throws Exception {
    String raw=loginAs("reader");
    status(200,api(raw,"/api/seller/shops/1/copies"));
    status(403,api(raw,"/api/seller/shops/2/copies"));
    var created=call(raw,"POST","/api/seller/shops/1/copies","{\"productId\":1,\"condition\":\"GOOD\",\"defects\":\"\",\"price\":\"80000\",\"reason\":\"Nhập sách kiểm thử\"}");
    status(201,created);long id=json.readTree(created.body()).get("id").asLong();
    String body="{\"version\":0,\"condition\":\"GOOD\",\"defects\":\"\",\"price\":\"85000\",\"status\":\"AVAILABLE\",\"reason\":\"Mở bán kiểm thử\"}";
    status(204,call(raw,"PUT","/api/seller/shops/1/copies/"+id,body));
    status(409,call(raw,"PUT","/api/seller/shops/1/copies/"+id,body));
    status(400,call(raw,"PUT","/api/seller/shops/1/copies/"+id,"{}"));
    status(200,api(raw,"/api/seller/shops/1/copies/"+id+"/history"));
  }
  @Test void adminCatalogAndBlockedAccountRevokeToken() throws Exception {
    String buyer=loginAs("buyer");
    // Keep the buyer grant in a different browser session.
    cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);client=HttpClient.newBuilder().cookieHandler(cookies).build();
    String admin=loginAs("admin");
    status(201,call(admin,"POST","/api/admin/categories","{\"name\":\"API Category\",\"active\":true}"));
    status(200,api(admin,"/api/admin/categories"));
    status(400,call(admin,"POST","/api/admin/products","{\"categoryId\":1,\"title\":\"Test\",\"author\":\"Test\",\"image\":\"javascript:alert(1)\"}"));
    status(409,call(admin,"DELETE","/api/admin/products/1",null));
    status(200,api(admin,"/api/admin/users"));
    long buyerId=store.findLogin("buyer").id;
    status(200,api(buyer,"/api/account/profile"));
    status(204,call(admin,"PUT","/api/admin/users/"+buyerId+"/block","{\"blocked\":true}"));
    status(401,api(buyer,"/api/account/profile"));
  }
  @Test void checkoutCarriesVoucherAndShippingAndIsIdempotent() throws Exception {
    String raw=loginAs("buyer");
    var quote=api(raw,"/api/checkout/quote?carrierId=1&voucherCode=GOCSACH10");status(200,quote);
    var q=json.readTree(quote.body());assertTrue(q.get("shippingFee").asDouble()>0);
    long address=store.addresses(store.findLogin("buyer").id).getFirst().id;
    String body=json.writeValueAsString(Map.of("addressId",address,"idempotencyKey","expanded-api-checkout-001","quoteFingerprint",q.get("fingerprint").asText(),"paymentMethod","COD","carrierId",1,"voucherCode","GOCSACH10"));
    var first=call(raw,"POST","/api/checkout",body);status(200,first);
    var again=call(raw,"POST","/api/checkout",body);status(200,again);assertEquals(first.body(),again.body());
    status(200,api(raw,"/api/orders?status=PENDING"));status(400,api(raw,"/api/orders?status=INVALID"));
  }
  @Test void returnsRequireOwnerAndExternalRefundConfirmation() throws Exception {
    long buyerId=store.findLogin("buyer").id;
    long id=store.ordersFor(buyerId).stream().filter(o->"final-demo-completed-0".equals(o.idempotencyKey)).findFirst().orElseThrow().id;
    String raw=loginAs("buyer");status(200,api(raw,"/api/orders/"+id+"/return"));
    raw=loginAs("reader");
    status(200,api(raw,"/api/seller/shops/1/returns"));
    status(400,call(raw,"POST","/api/seller/orders/"+id+"/return/resolve","{}"));
    status(204,call(raw,"POST","/api/seller/orders/"+id+"/return/resolve","{\"status\":\"APPROVED\",\"note\":\"Đồng ý nhận lại sách\"}"));
    status(400,call(raw,"POST","/api/seller/orders/"+id+"/return/resolve","{\"status\":\"REFUNDED\",\"note\":\"Đã kiểm tra và hoàn tiền\",\"reference\":\"FAKE-REFUND-001\"}"));
    status(204,call(raw,"POST","/api/seller/orders/"+id+"/return/resolve","{\"status\":\"REFUNDED\",\"note\":\"Đã kiểm tra và hoàn tiền\",\"reference\":\"FAKE-REFUND-001\",\"settled\":true}"));
  }
  @Test void shopAndOperationsReadPermissions() throws Exception {
    String raw=loginAs("reader");
    for(String path:List.of("/api/seller/shops","/api/seller/shops/1/vouchers","/api/seller/shops/1/revenue","/api/seller/orders","/api/account/shop-applications"))status(200,api(raw,path));
    status(403,api(raw,"/api/seller/shops/1/members"));
    status(403,api(raw,"/api/seller/shops/2/vouchers"));
    status(403,api(raw,"/api/seller/shops/2/revenue"));
    raw=loginAs("manager");status(200,api(raw,"/api/seller/shops/1/members"));
    raw=loginAs("admin");
    for(String path:List.of("/api/admin/shop-applications","/api/admin/carriers","/api/admin/reviews","/api/admin/products"))status(200,api(raw,path));
    status(400,call(raw,"PUT","/api/admin/shops/1/commission","{\"rate\":\"101\"}"));
  }
  @Test void reviewRequiresCompletedPurchaseAndValidRating() throws Exception {
    String raw=loginAs("buyer");
    status(200,api(raw,"/api/products/1/reviews/mine"));
    status(400,call(raw,"PUT","/api/products/1/reviews/mine","{\"rating\":6,\"body\":\"Sách hay và hữu ích, nội dung rõ ràng, giấy còn tốt và giao hàng đúng hẹn.\"}"));
    raw=loginAs("manager");
    status(403,api(raw,"/api/products/1/reviews/mine"));
    status(403,call(raw,"POST","/api/products/1/reviews","{\"rating\":5,\"body\":\"Sách hay và hữu ích, nội dung rõ ràng, giấy còn tốt và giao hàng đúng hẹn.\"}"));
  }
  @Test void applicationApprovalAndCatalogCrud() throws Exception {
    String raw=loginAs("manager");
    var request=call(raw,"POST","/api/account/shop-applications","{\"name\":\"API Shop mới\",\"address\":\"12 Đường A, Thủ Đức\"}");
    status(201,request);long id=json.readTree(request.body()).get("id").asLong();
    status(403,call(raw,"POST","/api/admin/shop-applications/"+id+"/approve",null));
    raw=loginAs("admin");status(200,call(raw,"POST","/api/admin/shop-applications/"+id+"/approve",null));
    status(201,call(raw,"POST","/api/admin/categories","{\"name\":\"Danh mục API CRUD\",\"active\":true}"));
    long cat=store.categories("").stream().filter(c->c.name.equals("Danh mục API CRUD")).findFirst().orElseThrow().id;
    status(201,call(raw,"POST","/api/admin/products",json.writeValueAsString(Map.of("categoryId",cat,"title","Đầu sách API CRUD","author","Nhóm 27"))));
    long book=store.products.values().stream().filter(b->b.title.equals("Đầu sách API CRUD")).findFirst().orElseThrow().id;
    status(200,api(raw,"/api/admin/products/"+book));
    status(204,call(raw,"PUT","/api/admin/products/"+book,json.writeValueAsString(Map.of("categoryId",cat,"title","Đầu sách đã sửa","author","Nhóm 27"))));
    status(204,call(raw,"DELETE","/api/admin/products/"+book,null));
    status(204,call(raw,"DELETE","/api/admin/categories/"+cat,null));
    status(404,api(raw,"/api/admin/categories/"+cat));
  }
  @Test void multipartReviewChecksPurchaseBeforeUpload() throws Exception {
    String raw=loginAs("manager");
    String body="--apiBoundary\r\nContent-Disposition: form-data; name=\"rating\"\r\n\r\n5\r\n--apiBoundary\r\nContent-Disposition: form-data; name=\"body\"\r\n\r\nA detailed review with more than fifty characters for this test.\r\n--apiBoundary\r\nContent-Disposition: form-data; name=\"attachment\"; filename=\"invalid.png\"\r\nContent-Type: image/png\r\n\r\ninvalid-content\r\n--apiBoundary--\r\n";
    var req=HttpRequest.newBuilder(URI.create(base+"/api/products/1/reviews")).header("Authorization","Bearer "+raw).header("Content-Type","multipart/form-data; boundary=apiBoundary").POST(HttpRequest.BodyPublishers.ofString(body)).build();
    status(403,client.send(req,HttpResponse.BodyHandlers.ofString()));
  }
}
