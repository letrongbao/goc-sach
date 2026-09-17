package vn.edu.utex.bookstore.auth;

import java.nio.charset.StandardCharsets;
import org.mindrot.jbcrypt.BCrypt;
import vn.edu.utex.bookstore.common.Problem;

public class Passwords {
  public String hash(String password) {
    validate(password);
    return BCrypt.hashpw(password, BCrypt.gensalt(12));
  }

  public boolean matches(String password, String hash) {
    if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72 || hash == null)
      return false;
    try {
      return BCrypt.checkpw(password, hash);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  public void validate(String password) {
    if (password == null
        || password.length() < 10
        || password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw Problem.invalid("Mật khẩu cần ít nhất 10 ký tự và không được quá dài.");
  }
}
