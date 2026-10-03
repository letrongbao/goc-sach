package vn.edu.utex.bookstore.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.context.NullSecurityContextRepository;
import vn.edu.utex.bookstore.config.App;
import vn.edu.utex.bookstore.web.SecurityFilter;

@Configuration
public class WebSecurityConfiguration {
  private void common(HttpSecurity http) throws Exception {
    http.formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
        .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
            "default-src 'self'; connect-src 'self' ws: wss:; style-src 'self'; img-src 'self' data: https://res.cloudinary.com; "
            + "script-src 'self'; frame-ancestors 'none'; form-action 'self' https://pay.payos.vn; base-uri 'self'"))
            .frameOptions(frame -> frame.deny())
            .referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));
  }

  @Bean @Order(1)
  SecurityFilterChain apiSecurity(HttpSecurity http, JwtDecoder decoder) throws Exception {
    common(http);
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt ->
        java.util.List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
    http.securityMatcher("/api", "/api/**").csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET, "/api/products/*/reviews").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().authenticated())
        .exceptionHandling(errors -> errors.authenticationEntryPoint((req, res, ex) -> json(res, 401, "not_authenticated"))
            .accessDeniedHandler((req, res, ex) -> json(res, 403, "forbidden")))
        .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter))
            .authenticationEntryPoint((req, res, ex) -> json(res, 401, "invalid_token")));
    return http.build();
  }

  @Bean @Order(0)
  SecurityFilterChain payosWebhookSecurity(HttpSecurity http) throws Exception {
    common(http);
    http.securityMatcher("/payments/payos/webhook").csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.POST, "/payments/payos/webhook").permitAll()
            .anyRequest().denyAll());
    return http.build();
  }

  @Bean @Order(2)
  SecurityFilterChain webSecurity(HttpSecurity http, App app) throws Exception {
    common(http);
    http.securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
        .addFilterBefore(new SecurityFilter(app), CsrfFilter.class)
        .csrf(csrf -> csrf.csrfTokenRepository(new SessionCsrfRepository())
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
            .requestMatchers("/account/**", "/seller/**", "/manager/**", "/favorites", "/favorites/**", "/recently-viewed", "/cart", "/cart/**", "/checkout", "/orders", "/orders/**").authenticated()
            .anyRequest().permitAll())
        .exceptionHandling(errors -> errors.authenticationEntryPoint((req, res, ex) ->
            res.sendRedirect(req.getContextPath() + "/auth/login")));
    return http.build();
  }

  private static void json(jakarta.servlet.http.HttpServletResponse response, int status, String error)
      throws java.io.IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "no-store");
    if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
    response.getWriter().write("{\"error\":\"" + error + "\"}");
  }
}
