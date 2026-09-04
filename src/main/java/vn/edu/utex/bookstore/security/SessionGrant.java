package vn.edu.utex.bookstore.security;

import jakarta.servlet.http.*;

/** Invalidating, replacing or expiring the web session immediately revokes its token. */
public record SessionGrant(String id, JwtTokenService tokens) implements HttpSessionBindingListener {
  public static final String ATTRIBUTE = "apiGrant";
  @Override public void valueUnbound(HttpSessionBindingEvent event) { tokens.revoke(id); }
}
