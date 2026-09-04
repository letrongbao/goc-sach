package vn.edu.utex.bookstore.auth;

import java.time.Instant;

public class User {
  public Long id;
  public String username, email, passwordHash, role;
  public boolean active;
  public boolean blocked;
  public int authVersion;
  public Instant createdAt;

  public User() {}

  public Long getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public String getRole() {
    return role;
  }

  public boolean isActive() {
    return active;
  }
}
