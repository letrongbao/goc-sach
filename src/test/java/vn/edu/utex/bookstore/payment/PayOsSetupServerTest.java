package vn.edu.utex.bookstore.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.Headers;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Parsing tests never read or write the real private configuration. */
class PayOsSetupServerTest {
  @Test
  void responseKeepsSameOriginFormAndNoStoreProtections() {
    Headers headers = new Headers();
    PayOsSetupServer.prepareResponseHeaders(headers);
    assertEquals("same-origin", headers.getFirst("Referrer-Policy"));
    assertEquals("no-store", headers.getFirst("Cache-Control"));
    assertEquals("DENY", headers.getFirst("X-Frame-Options"));
    assertTrue(headers.getFirst("Content-Security-Policy").contains("form-action 'self'"));
    assertTrue(headers.getFirst("Content-Security-Policy").contains("frame-ancestors 'none'"));
  }

  @Test
  void acceptsOnlyTheFourExpectedFormFields() throws Exception {
    var result = PayOsSetupServer.decodeForm(bytes("_csrf=token&clientId=test-client&apiKey=test-api&checksumKey=test-checksum"));
    assertEquals("test-client", result.get("clientId"));
    assertEquals(4, result.size());
  }

  @Test
  void rejectsDuplicateUnexpectedMissingAndWhitespaceFields() {
    for (String body : new String[] {
        "_csrf=token&clientId=test&apiKey=test&checksumKey=test&clientId=other",
        "_csrf=token&clientId=test&apiKey=test&checksumKey=test&path=other",
        "_csrf=token&clientId=test&apiKey=test",
        "_csrf=token&clientId=test&apiKey=test+api&checksumKey=test",
        "_csrf=token&clientId=test&apiKey=test%0Aapi&checksumKey=test",
        "_csrf=token&clientId=test&apiKey=%C3%28&checksumKey=test",
        "_csrf=token&clientId=test&apiKey=test&checksumKey=",
        "_csrf=token&clientId=test&apiKey=%ZZ&checksumKey=test"}) {
      assertThrows(IllegalArgumentException.class, () -> PayOsSetupServer.decodeForm(bytes(body)));
    }
  }

  @Test
  void rejectsMalformedUtf8AndOversizedValues() {
    assertThrows(java.nio.charset.CharacterCodingException.class,
        () -> PayOsSetupServer.decodeForm(new byte[] {(byte) 0xc3, 0x28}));
    assertThrows(IllegalArgumentException.class,
        () -> PayOsSetupServer.decodeForm(bytes("_csrf=token&clientId=test&apiKey=" + "x".repeat(513) + "&checksumKey=test")));
  }

  private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
