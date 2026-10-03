package vn.edu.utex.bookstore.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.config.Settings;
import vn.payos.PayOS;
import vn.payos.exception.APIException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;

class PayOsGatewayTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC);
  private static final String ID = "124c33293c934a85be5b7f8761a27a07";
  private static final String CHECKOUT = "https://pay.payos.vn/web/" + ID;
  private final PayOsSettings settings = PayOsSettings.from(
      new Settings(PayOsSettingsTest.properties("https://gocsach.example")));

  @Test
  void disabledGatewayRefusesAllOperations() {
    var gateway = new PayOsGateway(PayOsSettings.from(new Settings(new Properties())));
    assertFalse(gateway.enabled());
    assertEquals(503, assertThrows(Problem.class,
        () -> gateway.create(123, 10000, "GS123", CLOCK.instant().plusSeconds(900))).status);
    assertThrows(Problem.class, () -> gateway.query(123));
    assertThrows(Problem.class, () -> gateway.cancel(123, "Huy"));
    assertThrows(Problem.class, () -> gateway.verify("{}"));
    gateway.close();
  }

  @Test
  void createsFromServerValuesWithCorrectCallbacksAndExpiry() {
    var client = mock(PayOS.class);
    var requests = mock(PaymentRequestsService.class);
    when(client.paymentRequests()).thenReturn(requests);
    when(requests.create(any(CreatePaymentLinkRequest.class))).thenReturn(created(CHECKOUT));
    var gateway = new PayOsGateway(settings, client, CLOCK);

    var result = gateway.create(123, 10000, "GS123", CLOCK.instant().plusSeconds(900));

    var capture = ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
    verify(requests, times(1)).create(capture.capture());
    assertEquals(123, capture.getValue().getOrderCode());
    assertEquals(10000, capture.getValue().getAmount());
    assertEquals(CLOCK.instant().plusSeconds(900).getEpochSecond(), capture.getValue().getExpiredAt());
    assertEquals("https://gocsach.example/payments/payos/return", capture.getValue().getReturnUrl());
    assertEquals("https://gocsach.example/payments/payos/cancel", capture.getValue().getCancelUrl());
    assertEquals(CHECKOUT, result.checkoutUrl());
    assertEquals(ID, result.paymentLinkId());
  }

  @Test
  void rejectsMismatchAndUntrustedCheckoutUrls() {
    var client = mock(PayOS.class);
    var requests = mock(PaymentRequestsService.class);
    when(client.paymentRequests()).thenReturn(requests);
    var gateway = new PayOsGateway(settings, client, CLOCK);
    for (String url : new String[] {"https://pay.payos.vn.evil.example/web/" + ID,
        "https://pay.payos.vn@evil.example/web/" + ID, "http://pay.payos.vn/web/" + ID,
        CHECKOUT + "?redirect=evil", CHECKOUT + "#evil", "https://pay.payos.vn/web/other",
        "https://user:secret@pay.payos.vn/web/" + ID}) {
      when(requests.create(any(CreatePaymentLinkRequest.class))).thenReturn(created(url));
      assertEquals(503, assertThrows(Problem.class,
          () -> gateway.create(123, 10000, "GS123", CLOCK.instant().plusSeconds(900))).status);
    }
    var wrong = created(CHECKOUT);
    wrong.setAmount(9999L);
    when(requests.create(any(CreatePaymentLinkRequest.class))).thenReturn(wrong);
    assertThrows(Problem.class, () -> gateway.create(123, 10000, "GS123", CLOCK.instant().plusSeconds(900)));
  }

  @Test
  void invalidInputsNeverCallProviderAndExceptionsDoNotLeakBodiesOrCredentials() {
    var client = mock(PayOS.class);
    var requests = mock(PaymentRequestsService.class);
    when(client.paymentRequests()).thenReturn(requests);
    var gateway = new PayOsGateway(settings, client, CLOCK);
    assertThrows(Problem.class, () -> gateway.create(0, 10000, "GS123", CLOCK.instant().plusSeconds(900)));
    assertThrows(Problem.class, () -> gateway.create(123, -1, "GS123", CLOCK.instant().plusSeconds(900)));
    assertThrows(Problem.class, () -> gateway.create(123, 10000, "more than 9", CLOCK.instant().plusSeconds(900)));
    assertThrows(Problem.class, () -> gateway.create(123, 10000, "GS123", CLOCK.instant().minusSeconds(1)));
    verifyNoInteractions(requests);
    when(requests.create(any(CreatePaymentLinkRequest.class)))
        .thenThrow(new IllegalStateException("api-test checksum-test raw provider response"));
    var error = assertThrows(Problem.class,
        () -> gateway.create(123, 10000, "GS123", CLOCK.instant().plusSeconds(900)));
    assertNull(error.getCause());
    assertFalse(error.getMessage().contains("api-test"));
    assertFalse(error.getMessage().contains("provider response"));
    verify(requests, times(1)).create(any(CreatePaymentLinkRequest.class));
  }

  @Test
  void queriesAndCancelsOnlyRequestedOrderCode() {
    var client = mock(PayOS.class);
    var requests = mock(PaymentRequestsService.class);
    when(client.paymentRequests()).thenReturn(requests);
    var paid = snapshot(PaymentLinkStatus.PAID, 10000L);
    when(requests.get(123L)).thenReturn(paid);
    var cancelled = snapshot(PaymentLinkStatus.CANCELLED, 0L);
    when(requests.cancel(123L, "Khach huy")).thenReturn(cancelled);
    var gateway = new PayOsGateway(settings, client, CLOCK);
    assertEquals("PAID", gateway.query(123).status());
    assertEquals(10000, gateway.query(123).amountPaid());
    assertEquals(CHECKOUT, gateway.query(123).checkoutUrl());
    assertEquals("CANCELLED", gateway.cancel(123, " Khach huy ").status());
    paid.setOrderCode(456L);
    assertThrows(Problem.class, () -> gateway.query(123));
  }

  @Test
  void verifiesActualSdkSignaturesAndRejectsTamperingAndMalformedInputs() throws Exception {
    var client = new PayOS("client-test", "api-test", "checksum-test");
    var gateway = new PayOsGateway(settings, client, CLOCK);
    var data = receipt("00");
    String signed = signed(client, data, false);
    var result = gateway.verify(signed);
    assertTrue(result.success()); // Only signed data.code is authoritative; wrapper success is unsigned.
    assertEquals(123, result.orderCode());
    assertEquals(10000, result.amount());
    assertEquals("TXN123", result.reference());
    assertThrows(Problem.class, () -> gateway.verify(signed.replace("10000", "20000")));
    assertThrows(Problem.class, () -> gateway.verify(signed.replace("10000", "10000.1")));
    assertThrows(Problem.class, () -> gateway.verify(signed.replace("\"amount\":10000", "\"amount\":10000,\"amount\":10000")));
    assertThrows(Problem.class, () -> gateway.verify(signed + " {}"));
    assertThrows(Problem.class, () -> gateway.verify("{"));
    assertFalse(gateway.verify(signed(client, receipt("01"), true)).success());
    data.setReference("");
    assertThrows(Problem.class, () -> gateway.verify(signed(client, data, true)));
    gateway.close();
  }

  @Test
  void refusesMalformedLinkIdsAndProviderErrorsNeverBecomeNotFound() {
    var client = mock(PayOS.class);
    var requests = mock(PaymentRequestsService.class);
    when(client.paymentRequests()).thenReturn(requests);
    var gateway = new PayOsGateway(settings, client, CLOCK);
    var response = created("https://pay.payos.vn/web/../other");
    response.setPaymentLinkId("../other");
    when(requests.create(any(CreatePaymentLinkRequest.class))).thenReturn(response);
    assertThrows(Problem.class, () -> gateway.create(123, 10000, "GS123", CLOCK.instant().plusSeconds(900)));
    for (RuntimeException failure : new RuntimeException[] {
        new IllegalStateException("401 unauthorized api-test"),
        APIException.fromResponse(401, "401", "Unauthorized", "api-test"),
        APIException.fromResponse(404, "404", "Not found", "provider route or link may be missing"),
        APIException.fromResponse(200, "231", "Unconfirmed business code", "body"),
        APIException.fromResponse(500, "500", "Server error", "body")}) {
      doThrow(failure).when(requests).get(123L);
      var error = assertThrows(Problem.class, () -> gateway.query(123));
      assertEquals(503, error.status);
      assertNull(error.getCause());
    }
  }

  private static CreatePaymentLinkResponse created(String url) {
    var result = new CreatePaymentLinkResponse();
    result.setOrderCode(123L);
    result.setAmount(10000L);
    result.setCurrency("VND");
    result.setPaymentLinkId(ID);
    result.setStatus(PaymentLinkStatus.PENDING);
    result.setCheckoutUrl(url);
    return result;
  }

  private static PaymentLink snapshot(PaymentLinkStatus status, long amountPaid) {
    var result = new PaymentLink();
    result.setOrderCode(123L);
    result.setId(ID);
    result.setAmount(10000L);
    result.setAmountPaid(amountPaid);
    result.setStatus(status);
    return result;
  }

  private static WebhookData receipt(String code) {
    return WebhookData.builder().orderCode(123L).amount(10000L).description("GS123")
        .accountNumber("12345678").reference("TXN123").transactionDateTime("2026-10-02 07:00:00")
        .currency("VND").paymentLinkId(ID).code(code).desc("success").build();
  }

  private static String signed(PayOS client, WebhookData data, boolean success) throws Exception {
    var body = Webhook.builder().code("00").desc("success").success(success).data(data)
        .signature(client.getCrypto().createSignatureFromObj(data, "checksum-test")).build();
    return new ObjectMapper().writeValueAsString(body);
  }
}
