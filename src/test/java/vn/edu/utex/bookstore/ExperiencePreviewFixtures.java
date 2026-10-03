package vn.edu.utex.bookstore;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.auth.Passwords;
import vn.edu.utex.bookstore.auth.User;
import vn.edu.utex.bookstore.buyer.BuyerService;
import vn.edu.utex.bookstore.category.Category;
import vn.edu.utex.bookstore.inventory.InventoryService;
import vn.edu.utex.bookstore.order.OrderService;
import vn.edu.utex.bookstore.product.ProductService;
import vn.edu.utex.bookstore.review.ReviewService;
import vn.edu.utex.bookstore.shop.ShopService;

/** Rich, disposable UI examples. Never included in the production application. */
final class ExperiencePreviewFixtures {
  private ExperiencePreviewFixtures() {}

  static MemoryStore create(Clock clock) {
    MemoryStore store = DemoFixtures.store();
    addUser(store, clock, "buyer");
    addUser(store, clock, "seller");
    addUser(store, clock, "reviewer");
    for (String name : List.of("Kỹ năng", "Thiếu nhi")) {
      Category category = new Category();
      category.name = name;
      category.image = "";
      category.active = true;
      store.saveCategory(category);
    }
    enrichBooks(store, clock);

    var shops = new ShopService(store, clock);
    var inventory = new InventoryService(store, Clock.offset(clock, Duration.ofDays(-30)));
    Identity admin = Identity.of(store.findLogin("admin"));
    Identity reader = Identity.of(store.findLogin("reader"));
    Identity seller = Identity.of(store.findLogin("seller"));
    long firstShop = shops.approve(admin, shops.apply(reader, "Góc Sách Bình Yên",
        "12 Đường Sách, TP. Hồ Chí Minh · Địa chỉ minh họa"));
    long secondShop = shops.approve(admin, shops.apply(seller, "Tiệm Sách Tháng Tư",
        "24 Phố Nhỏ, Hà Nội · Địa chỉ minh họa"));
    long thirdShop = shops.approve(admin, shops.apply(seller, "Nhà Của Những Trang Sách",
        "8 Đường Nắng, Đà Nẵng · Địa chỉ minh họa"));
    List<Long> firstCopies = new ArrayList<>();
    List<Long> secondCopies = new ArrayList<>();
    List<Long> thirdCopies = new ArrayList<>();
    for (long productId = 1; productId <= 24; productId++) {
      int price = 35000 + (int) (productId % 9) * 10000;
      firstCopies.add(available(inventory, reader, firstShop, productId, "GOOD",
          "Bìa có nếp gấp nhẹ; ruột sách sạch, đủ trang, không ghi chú. Bản minh họa.", price));
      secondCopies.add(available(inventory, seller, secondShop, productId, "LIKE_NEW",
          "Bìa đẹp, gáy chắc, không gạch chân hay ghi chú. Bản minh họa.", price + 20000));
      thirdCopies.add(available(inventory, seller, thirdShop, productId, "ACCEPTABLE",
          "Giấy ngả màu và có vài ghi chú bằng bút chì; vẫn đủ trang, đọc rõ. Bản minh họa.", price - 15000));
    }

    var buyer = new BuyerService(store, clock);
    long buyerId = store.findLogin("buyer").id;
    buyer.saveProfile(buyerId, "Minh Anh", "0900000000");
    long addressId = buyer.saveAddress(buyerId, null, "Nhà của mình", "Minh Anh", "0900000000",
        "18 Đường Sách (minh họa)", "Phường Linh Chiểu", "Thủ Đức", "TP. Hồ Chí Minh", true).id;
    buyer.saveAddress(buyerId, null, "Văn phòng", "Minh Anh", "0900000000",
        "42 Phố Nhỏ (minh họa)", "Phường Bến Nghé", "Quận 1", "TP. Hồ Chí Minh", false);

    seedOrder(store, clock, buyerId, addressId, reader, List.of(firstCopies.get(23), firstCopies.get(22)),
        "preview-completed-order", "COMPLETED", 8);
    seedOrder(store, clock, buyerId, addressId, reader, List.of(firstCopies.get(21)),
        "preview-shipping-order", "SHIPPING", 3);
    seedOrder(store, clock, buyerId, addressId, reader, List.of(firstCopies.get(20)),
        "preview-pending-order", "PENDING", 0);
    seedOrder(store, clock, buyerId, addressId, seller, List.of(secondCopies.get(19)),
        "preview-confirmed-order", "CONFIRMED", 1);
    seedOrder(store, clock, buyerId, addressId, seller, List.of(thirdCopies.get(18)),
        "preview-cancelled-order", "CANCELLED", 5);
    new ReviewService(store, Clock.offset(clock, Duration.ofDays(-2))).add(buyerId, 24, 5,
        "Sách được đóng gói cẩn thận, tình trạng đúng mô tả. Mình thích cách các chương ngắn gợi mở một thói quen đọc mỗi ngày, rất dễ bắt đầu lại sau thời gian bận rộn.");

    long reviewerId = store.findLogin("reviewer").id;
    buyer.saveProfile(reviewerId, "An Nhiên", "0900000001");
    long reviewerAddress = buyer.saveAddress(reviewerId, null, "Nhà", "An Nhiên", "0900000001",
        "7 Đường Mây (minh họa)", "Phường 1", "Quận 3", "TP. Hồ Chí Minh", true).id;
    seedOrder(store, clock, reviewerId, reviewerAddress, seller,
        List.of(secondCopies.get(23), secondCopies.get(22)), "preview-reader-reviews", "COMPLETED", 12);
    var reviews = new ReviewService(store, Clock.offset(clock, Duration.ofDays(-4)));
    reviews.add(reviewerId, 24, 4,
        "Một cuốn sách nhẹ nhàng, phù hợp để đọc vào buổi tối. Bản mình nhận gần như mới, cửa hàng mô tả rõ tình trạng nên chọn mua sách cũ cũng rất yên tâm.");
    reviews.add(reviewerId, 23, 5,
        "Những câu chuyện nhỏ nhưng để lại nhiều cảm xúc. Giấy sách còn đẹp và không thiếu trang. Mình đã đánh dấu vài đoạn muốn đọc lại cùng bạn bè vào cuối tuần.");

    for (long id : List.of(24L, 23L, 17L, 9L, 3L)) buyer.addFavorite(buyerId, id);
    for (long id : List.of(3L, 8L, 17L, 20L, 23L, 24L)) buyer.recordViewed(buyerId, id);
    buyer.addToCart(buyerId, firstCopies.get(16));
    buyer.addToCart(buyerId, secondCopies.get(14));
    buyer.addToCart(buyerId, secondCopies.get(11));
    return store;
  }

  private static void enrichBooks(MemoryStore store, Clock clock) {
    String[] extraTitles = {"Điều kỳ diệu trong khu vườn", "Chú mèo và chuyến tàu mùa xuân",
        "Học cách học", "Một việc mỗi ngày", "Thiết kế cho cuộc sống",
        "Dữ liệu kể chuyện", "Những người bạn của rừng", "Thành phố và những giấc mơ",
        "Hành trình về phía biển", "Bản đồ của những điều nhỏ bé", "Mỗi ngày một trang sách"};
    long[] extraCategories = {6, 6, 5, 5, 3, 3, 6, 1, 4, 2, 2};
    String[] authors = {"An Nhiên", "Minh Thư", "Hoàng Nam", "Lam Chi", "Phương Linh", "Ngọc An"};
    String[] descriptions = {
        "Những câu chuyện đời thường được kể bằng giọng văn ấm áp, đưa người đọc qua ký ức, tình bạn và những điều nhỏ bé đáng trân trọng. Hợp với một chiều yên tĩnh bên tách trà.",
        "Một khoảng nghỉ dịu dàng giữa nhịp sống bận rộn. Từng chương ngắn gợi mở cách quan sát, chăm sóc bản thân và giữ lại những điều có ý nghĩa trong mỗi ngày.",
        "Khởi đầu từ những câu hỏi gần gũi, cuốn sách đưa người đọc đến với tư duy hệ thống, sáng tạo và công nghệ. Các ví dụ giúp biến ý tưởng thành điều có thể thực hành.",
        "Theo chân những chuyến đi để nhìn thế giới qua một lăng kính mới. Những trang viết về thiên nhiên và con người dành cho độc giả thích khám phá, học hỏi và bước ra ngoài vùng quen thuộc.",
        "Những phương pháp nhỏ, rõ ràng và dễ thử để xây dựng thói quen học tập, làm việc và sáng tạo. Bạn có thể đọc từng chương độc lập rồi chọn một điều để áp dụng ngay.",
        "Một thế giới giàu trí tưởng tượng với những người bạn đáng mến và các chuyến phiêu lưu trong trẻo. Phù hợp để trẻ tự đọc hoặc cùng người lớn trò chuyện sau mỗi chương."
    };
    for (int i = 0; i < 24; i++) {
      long id = i + 1L;
      var current = store.products.get(id);
      long category = current == null ? extraCategories[i - 13] : current.category.id;
      String title = current == null ? extraTitles[i - 13] : current.title;
      var products = new ProductService(store, Clock.offset(clock, Duration.ofDays(i - 24L)));
      products.saveDetails(current == null ? null : id, category, title, authors[i % authors.length],
          descriptions[(int) category - 1] + " Tác phẩm và thông tin xuất bản thuộc bộ dữ liệu minh họa của Góc Sách.",
          "", null, "Tủ sách Góc · Minh họa", 2020 + i % 6, "Tiếng Việt");
    }
  }

  private static void addUser(MemoryStore store, Clock clock, String username) {
    User user = new User();
    user.username = username;
    user.email = username + "@example.test";
    user.passwordHash = new Passwords().hash("demo-password-123");
    user.role = "USER";
    user.active = true;
    user.createdAt = clock.instant();
    store.saveUser(user);
  }

  private static long available(InventoryService inventory, Identity owner, long shopId,
      long productId, String condition, String defects, int price) {
    String amount = Integer.toString(price);
    long id = inventory.receive(owner, shopId, productId, condition, defects, amount, "Nhập bản minh họa");
    inventory.update(owner, shopId, id, 0, condition, defects, amount, "AVAILABLE", "Mở bán bản minh họa");
    return id;
  }

  private static void seedOrder(MemoryStore store, Clock clock, long buyerId, long addressId,
      Identity seller, List<Long> copies, String key, String finalStatus, int daysAgo) {
    Clock orderClock = Clock.offset(clock, Duration.ofDays(-daysAgo));
    var buyers = new BuyerService(store, orderClock);
    var orders = new OrderService(store, orderClock);
    copies.forEach(id -> buyers.addToCart(buyerId, id));
    long id = orders.checkout(buyerId, addressId, key, orders.quote(buyerId).fingerprint(), "COD")
        .orders().getFirst().id;
    if ("CANCELLED".equals(finalStatus)) {
      orders.cancel(buyerId, id);
      return;
    }
    for (String status : List.of("PENDING", "CONFIRMED", "SHIPPING", "COMPLETED")) {
      if (!"PENDING".equals(status)) orders.advance(seller, id, status);
      if (status.equals(finalStatus)) break;
    }
  }
}
