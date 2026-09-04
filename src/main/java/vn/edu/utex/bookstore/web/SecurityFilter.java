package vn.edu.utex.bookstore.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.config.App;

/** Session/remember identity adapter inside Spring Security; it does not authorize routes. */
public final class SecurityFilter implements Filter {
  private final App app;
  public SecurityFilter(App app) { this.app = app; }
  public static String cookie(HttpServletRequest req) {
    if (req.getCookies() != null)
      for (Cookie c : req.getCookies())
        if ("BOOKSTORE_REMEMBER".equals(c.getName())) return c.getValue();
    return null;
  }

  public static void cookie(
      HttpServletRequest req, HttpServletResponse res, String raw, boolean secure) {
    Cookie c = new Cookie("BOOKSTORE_REMEMBER", raw == null ? "" : raw);
    c.setHttpOnly(true);
    c.setSecure(secure);
    c.setPath(req.getContextPath().isEmpty() ? "/" : req.getContextPath());
    c.setMaxAge(raw == null ? 0 : 7 * 24 * 3600);
    c.setAttribute("SameSite", "Lax");
    res.addCookie(c);
  }

  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = (HttpServletResponse) response;
    if (req.getDispatcherType() == DispatcherType.ERROR) {
      chain.doFilter(req, res);
      return;
    }
    req.setCharacterEncoding("UTF-8");
    res.setCharacterEncoding("UTF-8");
    String path = req.getServletPath() + (req.getPathInfo() == null ? "" : req.getPathInfo());
    if (path.startsWith("/assets/") || path.startsWith("/media/")) {
      chain.doFilter(req, res);
      return;
    }
    res.setHeader("Cache-Control", "no-store");
    HttpSession session = req.getSession();
    session.setMaxInactiveInterval(30 * 60);
    if (session.getAttribute("csrf") == null) session.setAttribute("csrf", Tokens.random());
    Identity previous = (Identity) session.getAttribute("identity");
    Identity identity = app.auth.sessionIdentity(previous);
    if (previous != null && identity == null) {
      session.removeAttribute("identity");
      session.removeAttribute(vn.edu.utex.bookstore.security.SessionGrant.ATTRIBUTE);
    }
    if (identity == null) identity = app.auth.cookieIdentity(cookie(req));
    if (identity == null) session.removeAttribute(vn.edu.utex.bookstore.security.SessionGrant.ATTRIBUTE);
    if (identity != null) {
      var authentication = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
          identity, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + identity.role())));
      org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    req.setAttribute("identity", identity);
    req.setAttribute("csrf", session.getAttribute("csrf"));
    if (session.getAttribute("notice") != null) {
      req.setAttribute("notice", session.getAttribute("notice"));
      session.removeAttribute("notice");
    }
    if (!"GET".equals(req.getMethod()) && !"HEAD".equals(req.getMethod())) {
      try {
        if (req.getContentType() != null
            && req.getContentType()
                .toLowerCase(java.util.Locale.ROOT)
                .startsWith("multipart/form-data")) {
          req.getParts();
        }
      } catch (IllegalStateException e) {
        res.sendError(413, "Tệp tải lên quá lớn.");
        return;
      } catch (ServletException e) {
        res.sendError(400, "Biểu mẫu tải ảnh không hợp lệ.");
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
