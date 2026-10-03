package vn.edu.utex.bookstore.payment;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Explicit test-only relay. It never exposes another application path or reads private configuration. */
public final class WebhookRelayServer implements AutoCloseable {
  static final String WEBHOOK_PATH = "/payments/payos/webhook";
  static final int MAX_BODY = 32_768;
  private static final URI UPSTREAM = URI.create("http://127.0.0.1:8080" + WEBHOOK_PATH);

  @FunctionalInterface
  interface Forwarder { int forward(byte[] body) throws IOException, InterruptedException; }

  private final HttpServer server;
  private final ExecutorService workers;
  private final Forwarder forwarder;
  private final HttpClient client;

  private WebhookRelayServer() throws IOException {
    client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).proxy(HttpClient.Builder.NO_PROXY)
        .version(HttpClient.Version.HTTP_1_1).build();
    forwarder = body -> {
      var response = client.send(upstreamRequest(body), HttpResponse.BodyHandlers.ofInputStream());
      // Only headers/status are needed. Closing immediately cancels the body subscription instead of draining it.
      try (var ignored = response.body()) { return response.statusCode(); }
    };
    server = createServer();
    workers = createWorkers();
    configureServer();
  }

  /** Tests supply an in-memory forwarder; the executable always uses the fixed loopback upstream. */
  WebhookRelayServer(Forwarder forwarder) throws IOException {
    this.forwarder = forwarder;
    client = null;
    server = createServer();
    workers = createWorkers();
    configureServer();
  }

  private static HttpServer createServer() throws IOException {
    return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
  }

  private static ExecutorService createWorkers() {
    return new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8),
        runnable -> { Thread worker = new Thread(runnable, "payos-webhook-relay"); worker.setDaemon(true); return worker; },
        new ThreadPoolExecutor.AbortPolicy());
  }

  private void configureServer() {
    server.setExecutor(workers);
    server.createContext("/", this::handle);
  }

  public static void main(String[] args) {
    if (args.length != 0) {
      System.out.println("Webhook relay accepts no arguments and forwards only to the fixed local receiver.");
      return;
    }
    System.setProperty("jdk.httpserver.maxConnections", "16");
    System.setProperty("sun.net.httpserver.maxIdleConnections", "2");
    System.setProperty("sun.net.httpserver.maxReqHeaders", "32");
    System.setProperty("sun.net.httpserver.maxReqHeaderSize", "8192");
    // JDK 21 ServerImpl converts these values from seconds, not milliseconds.
    System.setProperty("sun.net.httpserver.maxReqTime", "15");
    System.setProperty("sun.net.httpserver.maxRspTime", "15");
    try (WebhookRelayServer relay = new WebhookRelayServer()) {
      relay.start();
      System.out.println("WEBHOOK_RELAY_URL=" + relay.endpoint());
      System.out.println("Loopback only; exact JSON POST webhook; expires after 30 minutes. No tunnel was started.");
      new CountDownLatch(1).await(30, TimeUnit.MINUTES);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    } catch (IOException | RuntimeException failure) {
      System.out.println("WEBHOOK_RELAY: unavailable; no request body or configuration displayed.");
    }
  }

  void start() { server.start(); }

  URI endpoint() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + WEBHOOK_PATH); }

  static HttpRequest upstreamRequest(byte[] body) {
    return HttpRequest.newBuilder(UPSTREAM).timeout(Duration.ofSeconds(10))
        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
  }

  private void handle(HttpExchange exchange) throws IOException {
    byte[] body = null;
    try {
      URI target = exchange.getRequestURI();
      if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress()
          || !WEBHOOK_PATH.equals(target.getRawPath()) || target.getRawQuery() != null
          || target.getRawFragment() != null || target.getRawAuthority() != null) {
        respond(exchange, 404);
        return;
      }
      if (!"POST".equals(exchange.getRequestMethod())) {
        respond(exchange, 405);
        return;
      }
      List<String> types = exchange.getRequestHeaders().get("Content-Type");
      if (types == null || types.size() != 1 || !isJson(types.getFirst())
          || exchange.getRequestHeaders().containsKey("Content-Encoding")) {
        respond(exchange, 415);
        return;
      }
      List<String> lengths = exchange.getRequestHeaders().get("Content-Length");
      if (lengths != null) {
        if (lengths.size() != 1 || !lengths.getFirst().matches("[0-9]{1,10}")) {
          respond(exchange, 400);
          return;
        }
        if (Long.parseLong(lengths.getFirst()) > MAX_BODY) {
          respond(exchange, 413);
          return;
        }
      }
      body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
      if (body.length > MAX_BODY) {
        respond(exchange, 413);
        return;
      }
      if (body.length == 0) {
        respond(exchange, 400);
        return;
      }
      int status = forwarder.forward(body);
      // Redirects, HTML error pages, cookies and arbitrary upstream response bodies never pass through.
      respond(exchange, status == 200 || status >= 400 && status <= 599 ? status : 502);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      respond(exchange, 503);
    } catch (IOException | RuntimeException failure) {
      respond(exchange, 503);
    } finally {
      if (body != null) Arrays.fill(body, (byte) 0);
      exchange.close();
    }
  }

  private static boolean isJson(String value) {
    String[] parts = value.split(";", -1);
    return parts.length >= 1 && parts.length <= 2 && "application/json".equalsIgnoreCase(parts[0].strip())
        && (parts.length == 1 || "charset=utf-8".equalsIgnoreCase(parts[1].strip())
            || "charset=\"utf-8\"".equalsIgnoreCase(parts[1].strip()));
  }

  private static void respond(HttpExchange exchange, int status) throws IOException {
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
    byte[] result = (status == 200 ? "{\"received\":true}" : "{\"received\":false}")
        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if ("HEAD".equals(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(status, -1);
      return;
    }
    exchange.sendResponseHeaders(status, result.length);
    try (var response = exchange.getResponseBody()) { response.write(result); }
  }

  @Override
  public void close() {
    server.stop(1);
    workers.shutdownNow();
    if (client != null) client.close();
  }
}
