package vn.edu.utex.bookstore.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.config.App;

@RestController
public final class ApiController {
  private final App app;
  private final JwtTokenService tokens;

  public ApiController(App app, JwtTokenService tokens) {
    this.app = app;
    this.tokens = tokens;
  }

  @PostMapping("/auth/api-token")
  public ResponseEntity<?> token(HttpServletRequest request) {
    Identity identity = (Identity) request.getAttribute("identity");
    JwtTokenService service = tokens;
    if (identity == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "not_authenticated"));
    if (!service.enabled()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "jwt_not_configured"));
    try {
      app.limits.check("jwt:" + identity.id(), 10, java.time.Duration.ofMinutes(1));
      var session = request.getSession();
      synchronized (session) {
        Identity current = app.auth.sessionIdentity((Identity) session.getAttribute("identity"));
        if (current == null) current = app.auth.cookieIdentity(vn.edu.utex.bookstore.web.SecurityFilter.cookie(request));
        if (!identity.equals(current)) return ResponseEntity.status(401).body(Map.of("error", "session_changed"));
        session.removeAttribute(SessionGrant.ATTRIBUTE);
        String grant = service.grant(identity);
        try {
          String token = service.issue(grant);
          session.setAttribute(SessionGrant.ATTRIBUTE, new SessionGrant(grant, service));
          return ResponseEntity.ok().header("Cache-Control", "no-store").body(Map.of(
              "access_token", token, "token_type", "Bearer", "expires_in", service.lifetimeSeconds()));
        } catch (RuntimeException failure) {
          service.revoke(grant);
          throw failure;
        }
      }
    } catch (vn.edu.utex.bookstore.common.Problem failure) {
      return ResponseEntity.status(failure.status).body(Map.of("error", failure.getMessage()));
    }
  }

  @GetMapping("/api/me")
  public ResponseEntity<?> me(Authentication authentication) {
    if (!(authentication instanceof JwtAuthenticationToken jwt))
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "not_authenticated"));
    var token = jwt.getToken();
    return ResponseEntity.ok(Map.<String, Object>of(
        "username", token.getClaimAsString("username"),
        "user_id", token.getClaim("user_id"),
        "role", token.getClaimAsString("role"),
        "expires_at", token.getExpiresAt().toString()));
  }
}
