package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.config.*;
import vn.edu.utex.bookstore.security.*;

class SessionGrantTest {
  @Test void expiredOrReplacedSessionRevokesBoundGrant() {
    var tokens = mock(JwtTokenService.class);
    var session = new MockHttpSession();
    session.setAttribute(SessionGrant.ATTRIBUTE, new SessionGrant("first", tokens));
    session.setAttribute(SessionGrant.ATTRIBUTE, new SessionGrant("second", tokens));
    verify(tokens).revoke("first");
    session.invalidate();
    verify(tokens).revoke("second");
  }

  @Test void requestFromPreviousLoginCannotMintTokenForNewSession() {
    var values = new java.util.Properties();
    values.setProperty("otp.secret", "test-only-secret-not-for-production-0123456789");
    var store = DemoFixtures.store();
    var app = new App(new Settings(values), store, java.time.Clock.systemUTC(), (to, subject, body) -> {});
    var tokens = mock(JwtTokenService.class);
    when(tokens.enabled()).thenReturn(true);
    var request = new MockHttpServletRequest();
    request.setAttribute("identity", Identity.of(store.users.get(1L)));
    request.getSession().setAttribute("identity", Identity.of(store.users.get(2L)));
    assertEquals(401, new ApiController(app, tokens).token(request).getStatusCode().value());
    verify(tokens, never()).grant(any());
  }
}
