package vn.edu.utex.bookstore.payment;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Request;
import org.junit.jupiter.api.Test;
import vn.edu.utex.bookstore.config.Settings;
import vn.payos.core.ClientOptions;

class PayOsClientFactoryTest {
  private final PayOsSettings settings = PayOsSettings.from(new Settings(
      PayOsSettingsTest.properties("http://localhost:8080")));

  @Test void bothClientsPinTheOfficialApiAndDisableRedirectsRetriesAndLogging() {
    var runtime = PayOsClientFactory.runtimeOptions(settings);
    var probe = PayOsClientFactory.probeOptions(settings);
    try {
      for (var options : List.of(runtime, probe)) {
        assertEquals("https://api-merchant.payos.vn", options.getBaseURL());
        assertEquals(0, options.getMaxRetries());
        assertEquals(ClientOptions.LogLevel.NONE, options.getLogLevel());
        assertFalse(options.getHttpClient().followRedirects());
        assertFalse(options.getHttpClient().followSslRedirects());
        assertFalse(options.getHttpClient().retryOnConnectionFailure());
      }
      assertEquals(15_000, runtime.getHttpClient().callTimeoutMillis());
      assertEquals(10_000, probe.getHttpClient().callTimeoutMillis());
    } finally { runtime.close(); probe.close(); }
  }

  @Test void unexpectedRedirectNeverForwardsTheApiKeyToAnotherHost() throws Exception {
    var sinkCalls = new AtomicInteger();
    var sink = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
    var redirect = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
    sink.createContext("/sink", exchange -> {
      sinkCalls.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close();
    });
    redirect.createContext("/redirect", exchange -> {
      exchange.getResponseHeaders().set("Location", "http://localhost:" + sink.getAddress().getPort() + "/sink");
      exchange.sendResponseHeaders(302, -1); exchange.close();
    });
    var runtime = PayOsClientFactory.runtimeOptions(settings);
    var probe = PayOsClientFactory.probeOptions(settings);
    try {
      sink.start(); redirect.start();
      for (var options : List.of(runtime, probe)) {
        var request = new Request.Builder().url("http://127.0.0.1:" + redirect.getAddress().getPort() + "/redirect")
            .header("x-client-id", "fake-client-id").header("x-api-key", "fake-api-key").build();
        try (var response = options.getHttpClient().newCall(request).execute()) { assertEquals(302, response.code()); }
      }
      assertEquals(0, sinkCalls.get());
    } finally {
      runtime.close(); probe.close(); redirect.stop(0); sink.stop(0);
    }
  }
}
