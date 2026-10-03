package vn.edu.utex.bookstore.payment;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import vn.edu.utex.bookstore.common.Problem;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.PaymentLink;

/** Only signed provider data may change payment state; browser return parameters never do. */
public final class PayOsGateway implements PaymentGateway {
  private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
  private static final ObjectMapper JSON = new ObjectMapper()
      .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  private final PayOsSettings settings;
  private final PayOS client;
  private final Clock clock;

  public PayOsGateway(PayOsSettings settings) {
    this(settings, settings.enabled() ? sdk(settings) : null, Clock.systemUTC());
  }

  PayOsGateway(PayOsSettings settings, PayOS client, Clock clock) {
    this.settings = java.util.Objects.requireNonNull(settings);
    this.client = client;
    this.clock = java.util.Objects.requireNonNull(clock);
    if (settings.enabled() && client == null) throw new IllegalArgumentException("Thiếu kết nối PayOS.");
  }

  @Override
  public boolean enabled() { return settings.enabled(); }

  @Override
  public Link create(long orderCode, long amount, String description, Instant expiresAt) {
    requireEnabled();
    requireCode(orderCode);
    if (amount <= 0 || amount > MAX_SAFE_INTEGER || description == null
        || !description.matches("[A-Za-z0-9 ]{1,9}") || expiresAt == null
        || !expiresAt.isAfter(clock.instant()) || expiresAt.getEpochSecond() > Integer.MAX_VALUE)
      throw Problem.invalid("Thông tin thanh toán không hợp lệ.");
    var request = CreatePaymentLinkRequest.builder()
        .orderCode(orderCode).amount(amount).description(description)
        .returnUrl(settings.returnUrl()).cancelUrl(settings.cancelUrl())
        .expiredAt(expiresAt.getEpochSecond()).build();
    try {
      var response = client.paymentRequests().create(request);
      if (response == null || response.getOrderCode() == null || response.getOrderCode() != orderCode
          || response.getAmount() == null || response.getAmount() != amount
          || !"VND".equals(response.getCurrency()) || response.getStatus() == null)
        throw unavailable();
      String id = identifier(response.getPaymentLinkId());
      return new Link(orderCode, amount, id, checkoutUrl(response.getCheckoutUrl(), id),
          response.getStatus().name());
    } catch (RuntimeException e) {
      throw unavailable();
    }
  }

  @Override
  public Snapshot query(long orderCode) {
    requireEnabled();
    requireCode(orderCode);
    try {
      return snapshot(orderCode, client.paymentRequests().get(orderCode));
    } catch (RuntimeException e) {
      throw unavailable();
    }
  }

  @Override
  public Snapshot cancel(long orderCode, String reason) {
    requireEnabled();
    requireCode(orderCode);
    if (reason == null || reason.isBlank() || reason.length() > 255
        || reason.chars().anyMatch(Character::isISOControl))
      throw Problem.invalid("Vui lòng nhập lý do hủy thanh toán.");
    try {
      return snapshot(orderCode, client.paymentRequests().cancel(orderCode, reason.trim()));
    } catch (RuntimeException e) {
      throw unavailable();
    }
  }

  @Override
  public Receipt verify(String rawJson) {
    requireEnabled();
    if (rawJson == null || rawJson.isBlank() || rawJson.length() > 32_768) throw invalidWebhook();
    try {
      JsonNode body = JSON.readTree(rawJson);
      if (!body.isObject() || !body.path("data").isObject()
          || !body.path("signature").isTextual()
          || !body.path("signature").asText().matches("[a-f0-9]{64}")) throw invalidWebhook();
      JsonNode rawData = body.path("data");
      if (!rawData.path("orderCode").isIntegralNumber() || !rawData.path("orderCode").canConvertToLong()
          || !rawData.path("amount").isIntegralNumber() || !rawData.path("amount").canConvertToLong())
        throw invalidWebhook();
      var data = client.webhooks().verify(body);
      if (data == null || data.getOrderCode() == null || data.getAmount() == null
          || data.getAmount() <= 0 || data.getAmount() > MAX_SAFE_INTEGER
          || data.getCode() == null || !"VND".equals(data.getCurrency())) throw invalidWebhook();
      requireCode(data.getOrderCode());
      return new Receipt(data.getOrderCode(), identifier(data.getPaymentLinkId()), data.getAmount(),
          identifier(data.getReference()), data.getCurrency(), "00".equals(data.getCode()));
    } catch (Exception e) {
      throw invalidWebhook();
    }
  }

  /** Explicit setup action, never invoked automatically during startup. */
  public void confirmWebhook() {
    requireEnabled();
    String url = settings.webhookUrl();
    try {
      var response = client.webhooks().confirm(url);
      if (response == null || !url.equals(response.getWebhookUrl())) throw unavailable();
    } catch (RuntimeException e) {
      throw unavailable();
    }
  }

  @Override
  public void close() { if (client != null) client.close(); }

  private Snapshot snapshot(long orderCode, PaymentLink response) {
    if (response == null || response.getOrderCode() == null || response.getOrderCode() != orderCode
        || response.getAmount() == null || response.getAmount() <= 0
        || response.getAmount() > MAX_SAFE_INTEGER || response.getAmountPaid() == null
        || response.getAmountPaid() < 0 || response.getAmountPaid() > MAX_SAFE_INTEGER
        || response.getStatus() == null) throw unavailable();
    String id = identifier(response.getId());
    // The signed ID is used only with the checkout host/path published in payOS API docs.
    String checkout = id.matches("[a-f0-9]{32}") ? "https://pay.payos.vn/web/" + id : null;
    return new Snapshot(orderCode, id, response.getAmount(),
        response.getAmountPaid(), response.getStatus().name(), checkout);
  }

  private void requireEnabled() {
    if (!enabled()) throw new Problem(503, "Thanh toán trực tuyến chưa sẵn sàng. Vui lòng chọn thanh toán khi nhận hàng.");
  }

  private static void requireCode(long value) {
    if (value <= 0 || value > MAX_SAFE_INTEGER) throw Problem.invalid("Mã thanh toán không hợp lệ.");
  }

  private static String identifier(String value) {
    if (value == null || value.isBlank() || value.length() > 128
        || value.chars().anyMatch(Character::isISOControl)) throw invalidWebhook();
    return value;
  }

  private static String checkoutUrl(String value, String paymentLinkId) {
    try {
      URI uri = URI.create(value);
      if (!paymentLinkId.matches("[a-f0-9]{32}")
          || !"https".equalsIgnoreCase(uri.getScheme()) || !"pay.payos.vn".equalsIgnoreCase(uri.getHost())
          || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getUserInfo() != null
          || uri.getQuery() != null || uri.getFragment() != null
          || !("/web/" + paymentLinkId).equals(uri.getRawPath())) throw unavailable();
      return uri.toString();
    } catch (IllegalArgumentException | NullPointerException e) {
      throw unavailable();
    }
  }

  private static PayOS sdk(PayOsSettings settings) {
    return new PayOS(PayOsClientFactory.runtimeOptions(settings));
  }

  private static Problem unavailable() {
    return new Problem(503, "Chưa thể kết nối thanh toán. Vui lòng thử kiểm tra lại sau.");
  }

  private static Problem invalidWebhook() {
    return Problem.invalid("Thông báo thanh toán không hợp lệ.");
  }
}
