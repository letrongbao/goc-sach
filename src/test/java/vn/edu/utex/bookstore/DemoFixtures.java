package vn.edu.utex.bookstore;

import java.time.*;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.category.*;
import vn.edu.utex.bookstore.product.*;

public final class DemoFixtures {
  /** Opt-in disposable catalog stock; never used by production or automatic schema setup. */
  public static void catalogStock(MemoryStore store) {
    var clock = Clock.systemUTC();
    var shops = new vn.edu.utex.bookstore.shop.ShopService(store, clock);
    var inventory = new vn.edu.utex.bookstore.inventory.InventoryService(store, clock);
    var admin = Identity.of(store.findLogin("admin"));
    var owner = Identity.of(store.findLogin("reader"));
    long shop = shops.approve(admin, shops.apply(owner, "Góc Sách Demo", "Địa chỉ minh họa cho bản xem thử"));
    int index = 0;
    for (Product product : store.products.values()) {
      String price = Integer.toString(75000 + index++ * 5000);
      long copy = inventory.receive(owner, shop, product.id, "GOOD", "Bản sách minh họa, không phải hàng thật.",
          price, "Nhập dữ liệu preview");
      inventory.update(owner, shop, copy, 0, "GOOD", "Bản sách minh họa, không phải hàng thật.",
          price, "AVAILABLE", "Mở bán dữ liệu preview");
    }
  }

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
          "");
    return store;
  }
}
