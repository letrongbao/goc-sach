package vn.edu.utex.bookstore.security;

import jakarta.servlet.http.*;
import org.springframework.security.web.csrf.*;
import vn.edu.utex.bookstore.auth.Tokens;

/** Keeps existing forms and login-time token rotation while Spring checks CSRF. */
public final class SessionCsrfRepository implements CsrfTokenRepository {
  @Override public CsrfToken generateToken(HttpServletRequest request) {
    return new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", Tokens.random());
  }
  @Override public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
    if (token != null) request.getSession().setAttribute("csrf", token.getToken());
    else if (request.getSession(false) != null) request.getSession(false).removeAttribute("csrf");
  }
  @Override public CsrfToken loadToken(HttpServletRequest request) {
    var session = request.getSession(false);
    String value = session == null ? null : (String) session.getAttribute("csrf");
    return value == null ? null : new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", value);
  }
}
