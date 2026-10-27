package vn.edu.utex.bookstore.security;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.config.App;

/** Resolve the current account rather than accepting an actor from request JSON. */
public final class ApiActor {
  private ApiActor() {}
  public static Identity require(App app, JwtAuthenticationToken auth) {
    if (auth == null) throw new Problem(401, "not_authenticated");
    var jwt = auth.getToken();
    Number id = jwt.getClaim("user_id"), version = jwt.getClaim("auth_version");
    if (id == null || version == null) throw new Problem(401, "invalid_token");
    var actor = app.auth.sessionIdentity(new Identity(id.longValue(), jwt.getClaimAsString("username"),
        jwt.getClaimAsString("role"), version.intValue()));
    if (actor == null) throw new Problem(401, "invalid_token");
    return actor;
  }
  public static Identity admin(App app, JwtAuthenticationToken auth) {
    var actor = require(app, auth);
    if (!actor.isAdmin()) throw new Problem(403, "forbidden");
    return actor;
  }
}
