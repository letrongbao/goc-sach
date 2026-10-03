package vn.edu.utex.bookstore.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Only loopback requests and fake forwarding; no application database, PayOS or private configuration. */
class WebhookRelayServerTest {
  @Test
  void forwardsExactJsonPostAndSanitizesTheResponse() throws Exception {
    AtomicReference<byte[]> forwarded = new AtomicReference<>();
    try (WebhookRelayServer relay = new WebhookRelayServer(body -> { forwarded.set(body.clone()); return 200; });
        HttpClient browser = localClient()) {
      relay.start();
      String payload = "{\"signature\":\"fake-local-test\"}";
      var response = send(browser, relay.endpoint(), "POST", "application/json; charset=utf-8", payload);
      assertEquals(200, response.statusCode());
      assertEquals("{\"received\":true}", response.body());
      assertEquals(payload, new String(forwarded.get(), StandardCharsets.UTF_8));
      assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
    }
  }

  @Test
  void refusesEveryOtherPathQueryAndMethodBeforeForwarding() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    try (WebhookRelayServer relay = new WebhookRelayServer(body -> { calls.incrementAndGet(); return 200; });
        HttpClient browser = localClient()) {
      relay.start();
      String base = relay.endpoint().resolve("/").toString();
      for (String path : new String[] {"/", "/admin", "/payments/payos/return", "/payments/payos/webhook/",
          "/payments/payos/%77ebhook", "/payments/payos/webhook?x=1"}) {
        assertEquals(404, send(browser, URI.create(base.substring(0, base.length() - 1) + path),
            "POST", "application/json", "{}").statusCode());
      }
      for (String method : new String[] {"GET", "HEAD", "PUT", "DELETE", "OPTIONS", "PATCH", "post"}) {
        assertEquals(405, send(browser, relay.endpoint(), method, "application/json", "{}").statusCode());
      }
      assertEquals(0, calls.get());
    }
  }

  @Test
  void rejectsUnsupportedContentTypesEmptyAndOversizedBodies() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    try (WebhookRelayServer relay = new WebhookRelayServer(body -> { calls.incrementAndGet(); return 200; });
        HttpClient browser = localClient()) {
      relay.start();
      for (String type : new String[] {"text/plain", "application/json; charset=latin1", "application/json; x=y"}) {
        assertEquals(415, send(browser, relay.endpoint(), "POST", type, "{}").statusCode());
      }
      assertEquals(400, send(browser, relay.endpoint(), "POST", "application/json", "").statusCode());
      assertEquals(413, send(browser, relay.endpoint(), "POST", "application/json",
          "x".repeat(WebhookRelayServer.MAX_BODY + 1)).statusCode());
      var chunked = HttpRequest.newBuilder(relay.endpoint()).timeout(Duration.ofSeconds(5))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(
              new byte[WebhookRelayServer.MAX_BODY + 1]))).build();
      assertEquals(413, browser.send(chunked, HttpResponse.BodyHandlers.ofString()).statusCode());
      var compressed = HttpRequest.newBuilder(relay.endpoint()).timeout(Duration.ofSeconds(5))
          .header("Content-Type", "application/json").header("Content-Encoding", "gzip")
          .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
      assertEquals(415, browser.send(compressed, HttpResponse.BodyHandlers.ofString()).statusCode());
      assertEquals(0, calls.get());
    }
  }

  @Test
  void preservesErrorsButNeverFollowsRedirectsOrReturnsUpstreamContent() throws Exception {
    for (int upstream : new int[] {400, 503, 302, 201}) {
      try (WebhookRelayServer relay = new WebhookRelayServer(body -> upstream); HttpClient browser = localClient()) {
        relay.start();
        var response = send(browser, relay.endpoint(), "POST", "application/json", "{}");
        assertEquals(upstream >= 400 ? upstream : 502, response.statusCode());
        assertEquals("{\"received\":false}", response.body());
      }
    }
  }

  @Test
  void generatedUpstreamRequestIsFixedAndContainsNoForwardedHeaders() {
    HttpRequest request = WebhookRelayServer.upstreamRequest("{}".getBytes(StandardCharsets.UTF_8));
    assertEquals(URI.create("http://127.0.0.1:8080/payments/payos/webhook"), request.uri());
    assertEquals("POST", request.method());
    assertEquals(Duration.ofSeconds(10), request.timeout().orElseThrow());
    assertEquals(Map.of("Content-Type", java.util.List.of("application/json")), request.headers().map());
  }

  private static HttpClient localClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).proxy(HttpClient.Builder.NO_PROXY)
        .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
  }

  private static HttpResponse<String> send(HttpClient client, URI uri, String method, String type, String body)
      throws Exception {
    return client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).header("Content-Type", type)
        .header("Cookie", "fake-local-test").header("Authorization", "Bearer fake-local-test")
        .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
  }
}
