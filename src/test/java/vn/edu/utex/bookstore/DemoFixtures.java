package vn.edu.utex.bookstore;

import java.time.*;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.category.*;
import vn.edu.utex.bookstore.product.*;

public final class DemoFixtures {
  public static MemoryStore store() {
    var store = new MemoryStore();
    var passwords = new Passwords();
    for (String role : new String[] {"ADMIN", "USER"}) {
      var u = new User();
      u.username = role.equals("ADMIN") ? "admin" : "reader";
      u.email = u.username + "@example.test";
      u.passwordHash = passwords.hash("demo-password-123");
      u.role = role;
      u.active = true;
      u.createdAt = Instant.now();
      store.saveUser(u);
    }
    for (String name : new String[] {"Văn học", "Sống chậm", "Công nghệ", "Khám phá"}) {
      var c = new Category();
      c.name = name;
      c.image = "";
      c.active = true;
      store.saveCategory(c);
    }
    String[] titles = {
      "Miền ký ức xanh",
      "Những ngày có nắng",
      "Lập trình từ trang đầu",
      "Một hành trình nhỏ",
      "Bên kia mùa hạ",
      "Chuyện của những vì sao",
      "Sống một đời sâu sắc",
      "Tư duy của người làm nghề",
      "Gửi những ngày mai",
      "Đi qua miền gió",
      "Khoảng lặng giữa thành phố",
      "Nghệ thuật bắt đầu lại",
      "Một đời đáng đọc"
    };
    var products =
        new ProductService(
            store, Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC));
    for (int i = 0; i < titles.length; i++)
      products.save(
          null,
          i % 4 + 1,
          titles[i],
          "Tủ sách Góc",
          "Dữ liệu minh họa cho dự án học tập. Cuốn sách mời bạn dành một khoảng thời gian yên"
              + " tĩnh, mở ra góc nhìn mới và tìm lại niềm vui đọc sách.",
          Integer.toString(75000 + i * 5000),
          Integer.toString(5 + i),
          "");
    return store;
  }
}
