package vn.edu.utex.bookstore.payment;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import vn.payos.core.ClientOptions;

/** Credentials may only be sent to the fixed merchant API, never through a redirect. */
final class PayOsClientFactory {
  private static final String API_ORIGIN = "https://api-merchant.payos.vn";
  private PayOsClientFactory() {}

  static ClientOptions runtimeOptions(PayOsSettings settings) { return options(settings, 10_000, 15_000); }
  static ClientOptions probeOptions(PayOsSettings settings) { return options(settings, 5_000, 10_000); }

  private static ClientOptions options(PayOsSettings settings, int timeoutMs, int callTimeoutMs) {
    var http = new OkHttpClient.Builder().connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS).writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS).retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).build();
    return ClientOptions.builder().clientId(settings.clientId()).apiKey(settings.apiKey())
        .checksumKey(settings.checksumKey()).baseURL(API_ORIGIN).timeoutMs(timeoutMs).maxRetries(0)
        .logLevel(ClientOptions.LogLevel.NONE).httpClient(http).build();
  }
}
