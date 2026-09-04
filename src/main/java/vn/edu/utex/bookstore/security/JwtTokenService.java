package vn.edu.utex.bookstore.security;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.auth.AuthService;
import vn.edu.utex.bookstore.common.Problem;

/** Single-instance grants: restart revokes all API access, never extends it. */
public final class JwtTokenService implements OAuth2TokenValidator<Jwt> {
  private record Grant(Identity identity, Instant expiresAt) {}
  private final Map<String, Grant> grants = new ConcurrentHashMap<>();
  private final JwtEncoder encoder;
  private final AuthService auth;
  private final String issuer;
  private final String audience;
  private final Duration lifetime;
  private final Clock clock;

  public JwtTokenService(JwtEncoder encoder, AuthService auth, String issuer,
      String audience, Duration lifetime, Clock clock) {
    this.encoder = encoder;
    this.auth = auth;
    this.issuer = issuer;
    this.audience = audience;
    this.lifetime = lifetime;
    this.clock = clock;
  }

  public boolean enabled() { return encoder != null; }

  public synchronized String grant(Identity identity) {
    if (!enabled()) throw new Problem(503, "JWT chưa được cấu hình.");
    Identity current = auth.sessionIdentity(identity);
    if (current == null) throw new Problem(401, "Phiên đăng nhập đã hết hiệu lực.");
    grants.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(clock.instant()));
    if (grants.size() >= 10000) throw new Problem(503, "Vui lòng thử lại sau.");
    String id = UUID.randomUUID().toString();
    grants.put(id, new Grant(current, clock.instant().plus(lifetime)));
    return id;
  }

  public void revoke(String grantId) { grants.remove(grantId); }

  public String issue(String grantId) {
    Grant grant = grants.get(grantId);
    if (grant == null || !grant.expiresAt().isAfter(clock.instant()))
      throw new Problem(401, "Phiên API đã hết hiệu lực.");
    Identity identity = grant.identity();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(issuer).audience(List.of(audience)).issuedAt(clock.instant())
        .expiresAt(grant.expiresAt()).subject(Long.toString(identity.id()))
        .id(UUID.randomUUID().toString()).claim("username", identity.username())
        .claim("user_id", identity.id()).claim("role", identity.role())
        .claim("auth_version", identity.authVersion()).claim("sid", grantId).build();
    return encoder.encode(JwtEncoderParameters.from(
        JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").keyId("goc-sach-rs256").build(), claims))
        .getTokenValue();
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    try {
      Grant grant = grants.get(jwt.getClaimAsString("sid"));
      if (grant != null && grant.expiresAt().isAfter(clock.instant())
          && jwt.getExpiresAt() != null && jwt.getExpiresAt().isAfter(clock.instant())
          && !jwt.getExpiresAt().isAfter(grant.expiresAt())) {
        Identity current = auth.sessionIdentity(grant.identity());
        if (current != null && Long.toString(current.id()).equals(jwt.getSubject())
            && current.username().equals(jwt.getClaimAsString("username"))
            && current.role().equals(jwt.getClaimAsString("role"))
            && ((Number) jwt.getClaim("user_id")).longValue() == current.id()
            && ((Number) jwt.getClaim("auth_version")).longValue() == current.authVersion())
          return OAuth2TokenValidatorResult.success();
      }
    } catch (IllegalArgumentException | NullPointerException | ClassCastException ignored) {
      // Malformed claims must never authenticate a caller.
    }
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token hết hiệu lực.", null));
  }

  public long lifetimeSeconds() { return lifetime.toSeconds(); }
}
