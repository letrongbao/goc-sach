package vn.edu.utex.bookstore.payment;

import java.net.URI;
import vn.edu.utex.bookstore.config.Settings;

/** Credentials are deliberately excluded from generated record/toString methods. */
public final class PayOsSettings {
  private final boolean enabled;
  private final String clientId;
  private final String apiKey;
  private final String checksumKey;
  private final String baseUrl;
  private final String webhookUrl;

  private PayOsSettings(boolean enabled, String clientId, String apiKey,
      String checksumKey, String baseUrl, String webhookUrl) {
    this.enabled = enabled;
    this.clientId = clientId;
    this.apiKey = apiKey;
    this.checksumKey = checksumKey;
    this.baseUrl = baseUrl;
    this.webhookUrl = webhookUrl;
  }

  public static PayOsSettings from(Settings settings) {
    String flag = settings.get("payos.enabled", "false");
    if (!"true".equalsIgnoreCase(flag) && !"false".equalsIgnoreCase(flag))
      throw new IllegalStateException("payos.enabled phải là true hoặc false.");
    if (!Boolean.parseBoolean(flag)) return new PayOsSettings(false, null, null, null, null, null);
    String webhook = settings.get("payos.webhook-url", "").strip();
    return new PayOsSettings(true, credential(settings, "payos.client-id"),
        credential(settings, "payos.api-key"), credential(settings, "payos.checksum-key"),
        validateBaseUrl(settings.require("payos.base-url")),
        webhook.isEmpty() ? null : validateWebhookUrl(webhook));
  }

  public boolean enabled() { return enabled; }
  String clientId() { return clientId; }
  String apiKey() { return apiKey; }
  String checksumKey() { return checksumKey; }

  public String returnUrl() { return route("return"); }
  public String cancelUrl() { return route("cancel"); }

  public String webhookUrl() {
    return validateWebhookUrl(webhookUrl == null ? route("webhook") : webhookUrl);
  }

  private static String validateWebhookUrl(String value) {
    String normalized = validateBaseUrl(value);
    URI uri = URI.create(normalized);
    if (!"https".equalsIgnoreCase(uri.getScheme()) || !isPublicHost(uri.getHost())
        || !uri.getRawPath().endsWith("/payments/payos/webhook"))
      throw new IllegalStateException("Webhook PayOS cần URL HTTPS công khai đến /payments/payos/webhook.");
    return normalized;
  }

  private String route(String suffix) {
    if (!enabled) throw new IllegalStateException("PayOS chưa được bật.");
    return baseUrl + "/payments/payos/" + suffix;
  }

  private static String credential(Settings settings, String key) {
    String value = settings.require(key);
    if (value.length() > 512 || value.chars().anyMatch(Character::isWhitespace)
        || value.chars().anyMatch(Character::isISOControl))
      throw new IllegalStateException("Cấu hình PayOS không hợp lệ: " + key);
    return value;
  }

  private static String validateBaseUrl(String value) {
    try {
      URI uri = URI.create(value);
      String host = uri.getHost();
      boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
          || "[::1]".equals(host) || "::1".equals(host);
      boolean secure = "https".equalsIgnoreCase(uri.getScheme());
      if (host == null || (!secure && !("http".equalsIgnoreCase(uri.getScheme()) && local))
          || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
          || (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535))
          || !uri.getRawPath().matches("[A-Za-z0-9/_-]*") || uri.getRawPath().contains("//"))
        throw new IllegalArgumentException();
      String result = uri.toString();
      while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
      return result;
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("payos.base-url phải là URL ứng dụng HTTPS hoặc localhost.");
    }
  }

  private static boolean isPublicHost(String host) {
    if (host == null) return false;
    String value = host.toLowerCase(java.util.Locale.ROOT);
    return value.contains(".") && !value.matches("[0-9.]+") && !value.contains(":")
        && !value.endsWith(".localhost") && !value.endsWith(".local")
        && !value.endsWith(".internal") && !"localhost".equals(value);
  }
}
