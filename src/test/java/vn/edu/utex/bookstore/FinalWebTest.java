package vn.edu.utex.bookstore;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import vn.edu.utex.bookstore.config.*;

class FinalWebTest {
  @Test void realHttpPagesCsrfPrivilegesAndPrivateSocketUpdates() throws Exception {
    var props=new Properties();props.setProperty("cookie.secure","false");props.setProperty("otp.secret","final-test-only-secret-01234567890123456789");
    var store=FinalDemoFixtures.create(Clock.systemUTC());var app=new App(new Settings(props),store,Clock.systemUTC(),new OtpTest.Mail());
    try(var context=(ServletWebServerApplicationContext)new SpringApplicationBuilder(GocSachApplication.class)
        .properties("server.address=127.0.0.1","logging.level.root=ERROR")
        .initializers(ctx->((GenericApplicationContext)ctx).registerBean("testApp",App.class,()->app)).run("--server.port=0")) {
      String base="http://127.0.0.1:"+context.getWebServer().getPort();var buyer=new Client(base);var admin=new Client(base);var seller=new Client(base);
      assertEquals(200,buyer.get("/").statusCode());
      for(String sort:List.of("newest","bestselling","rating","favorites")){assertEquals(200,buyer.get("/rankings/"+sort+"?page=2").statusCode());}
      buyer.login("buyer");seller.login("reader");admin.login("admin");
      assertEquals(403,buyer.get("/admin/operations").statusCode());assertEquals(200,admin.get("/admin/operations").statusCode());
      assertEquals(200,seller.get("/seller/operations").statusCode());assertEquals(403,seller.get("/seller/operations?shopId=2").statusCode());
      assertEquals(200,buyer.get("/checkout?voucherCode=GOCSACH10&carrierId=1").statusCode());
      assertEquals(403,admin.post("/admin/operations/carriers",Map.of("name","Should not exist","fee","2000","active","true")).statusCode());
      long pending=store.ordersFor(store.findLogin("buyer").id).stream().filter(o->"PENDING".equals(o.status)).findFirst().orElseThrow().id;
      var messages=new LinkedBlockingQueue<String>();
      var socket=buyer.http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
        .buildAsync(URI.create(base.replace("http:","ws:")+"/ws"),new WebSocket.Listener(){
          private final StringBuilder text=new StringBuilder();
          @Override public void onOpen(WebSocket ws){ws.request(1);}
          @Override public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last){text.append(data);if(last){messages.add(text.toString());text.setLength(0);}ws.request(1);return null;}
        }).join();
      String first=messages.poll(12,TimeUnit.SECONDS);assertNotNull(first,"Initial socket update");assertTrue(first.contains("\"initial\":true"));
      long strangerOrder=store.ordersFor(store.findLogin("reviewer").id).getFirst().id;
      assertFalse(first.contains("\"id\":"+strangerOrder+","));
      var orders=context.getBean(vn.edu.utex.bookstore.order.OrderService.class);
      orders.cancel(store.findLogin("buyer").id,pending);
      String second=messages.poll(12,TimeUnit.SECONDS);assertNotNull(second,"Changed socket update");assertTrue(second.contains("\"initial\":false"));assertTrue(second.contains("CANCELLED"));
      socket.sendClose(WebSocket.NORMAL_CLOSURE,"done").join();
      assertEquals(200,buyer.get("/orders?status=CANCELLED").statusCode());
      long returned=store.returnRows.values().iterator().next().orderId;
      assertEquals(200,buyer.get("/orders/"+returned+"/return").statusCode());assertEquals(404,seller.get("/orders/"+returned+"/return").statusCode());
      var update=admin.post("/admin/operations/carriers",Map.of("_csrf",admin.csrf("/admin/operations"),"name","Giao demo","fee","12000","active","true"));
      assertEquals(302,update.statusCode());assertEquals(3,store.carriers().size());
    }
  }
  private static final class Client {
    final String base;final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
    Client(String base){this.base=base;}
    HttpResponse<String> get(String path) throws Exception{return http.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
    HttpResponse<String> post(String path,Map<String,String> params) throws Exception {
      String form=params.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
      return http.send(HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String csrf(String path) throws Exception {var m=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(get(path).body());assertTrue(m.find(),path);return m.group(1);}
    void login(String user) throws Exception {assertEquals(302,post("/auth/login",Map.of("_csrf",csrf("/auth/login"),"login",user,"password","demo-password-123","mode","session")).statusCode());}
  }
}
