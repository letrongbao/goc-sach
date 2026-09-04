package vn.edu.utex.bookstore.security;

import java.nio.file.Path;
import java.time.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import vn.edu.utex.bookstore.config.App;

@Configuration
public class JwtConfiguration {
  record Material(JwtEncoder encoder, RSAPublicKey publicKey, String issuer, String audience, Duration lifetime) {}

  @Bean @ConditionalOnMissingBean(Clock.class)
  Clock applicationClock() { return Clock.systemUTC(); }

  @Bean
  Material jwtMaterial(App app) {
    return material(app.settings);
  }

  public static boolean checkConfiguration(vn.edu.utex.bookstore.config.Settings settings) {
    return material(settings).encoder() != null;
  }

  private static Material material(vn.edu.utex.bookstore.config.Settings settings) {
    var keys = List.of("jwt.private-key-file", "jwt.public-key-file", "jwt.issuer", "jwt.audience");
    boolean configured = keys.stream().anyMatch(key -> !settings.get(key, "").isBlank())
        || !settings.get("jwt.lifetime-seconds", "").isBlank();
    if (!configured) return new Material(null, null, "", "", Duration.ofMinutes(10));
    keys.forEach(settings::require);
    RSAPrivateKey privateKey = (RSAPrivateKey) PemKeys.privateKey(Path.of(settings.require(keys.get(0))));
    RSAPublicKey publicKey = (RSAPublicKey) PemKeys.publicKey(Path.of(settings.require(keys.get(1))));
    if (publicKey.getModulus().bitLength() < 2048 || !publicKey.getModulus().equals(privateKey.getModulus()))
      throw new IllegalStateException("JWT cần cặp khóa RSA khớp nhau, ít nhất 2048 bit.");
    long seconds = Long.parseLong(settings.get("jwt.lifetime-seconds", "600"));
    if (seconds < 60 || seconds > 3600) throw new IllegalStateException("JWT cần thời hạn từ 60 đến 3600 giây.");
    RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("goc-sach-rs256").build();
    return new Material(new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk))),
        publicKey, settings.require("jwt.issuer"), settings.require("jwt.audience"), Duration.ofSeconds(seconds));
  }

  @Bean
  JwtTokenService jwtTokenService(Material material, App app, Clock clock) {
    return new JwtTokenService(material.encoder(), app.auth, material.issuer(), material.audience(), material.lifetime(), clock);
  }

  @Bean
  JwtDecoder jwtDecoder(Material material, JwtTokenService tokens, Clock clock) {
    if (material.publicKey() == null) return raw -> { throw new BadJwtException("JWT chưa được cấu hình."); };
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(material.publicKey())
        .signatureAlgorithm(SignatureAlgorithm.RS256).build();
    var timestamps = new JwtTimestampValidator(Duration.ZERO);
    timestamps.setClock(clock);
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
        new JwtIssuerValidator(material.issuer()),
        new JwtClaimValidator<List<String>>("aud", values -> values != null && values.contains(material.audience())), tokens));
    return decoder;
  }
}
