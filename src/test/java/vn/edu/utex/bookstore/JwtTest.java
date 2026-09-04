package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.security.JwtTokenService;

class JwtTest {
  @Test
  void tokenIsSignedAndCarriesBoundIdentityClaims() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
    RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();
    RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("goc-sach-rs256").build();
    var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    var auth = org.mockito.Mockito.mock(vn.edu.utex.bookstore.auth.AuthService.class);
    Identity identity = new Identity(7, "bao", "USER", 3);
    org.mockito.Mockito.when(auth.sessionIdentity(identity)).thenReturn(identity);
    var service = new JwtTokenService(encoder, auth, "https://goc-sach.test", "goc-sach-api",
        Duration.ofMinutes(10), java.time.Clock.systemUTC());
    String grant = service.grant(identity);
    String raw = service.issue(grant);
    JwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).signatureAlgorithm(SignatureAlgorithm.RS256).build();
    Jwt token = decoder.decode(raw);

    assertEquals("7", token.getSubject());
    assertEquals("bao", token.getClaimAsString("username"));
    assertEquals(7L, ((Number) token.getClaim("user_id")).longValue());
    assertEquals("USER", token.getClaimAsString("role"));
    assertEquals(3, ((Number) token.getClaim("auth_version")).intValue());
    assertEquals(grant, token.getClaimAsString("sid"));
    assertEquals(List.of("goc-sach-api"), token.getAudience());
    assertTrue(token.getExpiresAt().isAfter(token.getIssuedAt()));
    assertFalse(service.validate(token).hasErrors());
    service.revoke(grant);
    assertTrue(service.validate(token).hasErrors());
  }
}
