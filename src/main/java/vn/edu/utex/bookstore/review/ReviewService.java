package vn.edu.utex.bookstore.review;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import vn.edu.utex.bookstore.common.FormProblem;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.persistence.Store;
import vn.edu.utex.bookstore.product.ProductView;

public final class ReviewService {
  public static final int PAGE_SIZE = 10;
  private final Store store;
  private final Clock clock;

  public ReviewService(Store store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  public record ReviewView(int rating, String body, String authorName, Instant createdAt, Instant updatedAt, String mediaUrl,String mediaType) {
    public String displayTime() {
      return java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
          .withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).format(createdAt);
    }
  }
  public record ProductReviews(List<ReviewView> items, long count, double averageRating,
      boolean canReview, boolean alreadyReviewed, int page, int pages) {}
  public record Editor(ProductView product, ReviewView existingReview, long orderId, boolean editable,
      boolean accessiblePublicProduct) {}

  public Review add(long userId,long productId,int rating,String body) {return add(userId,productId,rating,body,"","");}
  public Review add(long userId, long productId, int rating, String body,String mediaUrl,String mediaType) {
    media(mediaUrl,mediaType);
    String text = validate(rating, body);
    return store.tx(data -> {
      requireBuyer(data, userId);
      long orderId = completedOrder(data, userId, productId);
      if (data.review(userId, productId) != null)
        throw new Problem(409, "Bạn đã đánh giá cuốn sách này. Hãy mở đánh giá để chỉnh sửa.");
      Review review = new Review();
      review.userId = userId;
      review.productId = productId;
      review.orderId = orderId;
      review.rating = rating;
      review.body = text;
      if(mediaUrl!=null){review.mediaUrl=mediaUrl;review.mediaType=mediaType;}
      review.status = "PUBLISHED";
      review.createdAt = clock.instant();
      review.updatedAt = review.createdAt;
      return data.saveReview(review);
    });
  }

  public Review update(long userId,long productId,int rating,String body){return update(userId,productId,rating,body,null,null);}
  public Review update(long userId, long productId, int rating, String body,String mediaUrl,String mediaType) {
    media(mediaUrl,mediaType);
    String text = validate(rating, body);
    return store.tx(data -> {
      requireBuyer(data, userId);
      completedOrder(data, userId, productId);
      Review review = data.review(userId, productId);
      if (review == null) throw Problem.missing();
      if (!"PUBLISHED".equals(review.status))
        throw new Problem(409, "Đánh giá này hiện không thể chỉnh sửa.");
      review.rating = rating;
      review.body = text;
      if(mediaUrl!=null){review.mediaUrl=mediaUrl;review.mediaType=mediaType;}
      review.updatedAt = clock.instant();
      return data.saveReview(review);
    });
  }

  public Editor editor(long userId, long productId) {
    return store.tx(data -> {
      long orderId = completedOrder(data, userId, productId);
      Review existing = data.review(userId, productId);
      return new Editor(ProductView.from(data.product(productId)), existing == null ? null : view(data, existing),
          orderId, existing == null || "PUBLISHED".equals(existing.status), data.product(productId).category.active);
    });
  }

  public ProductReviews summary(Long userId, long productId) {
    return summary(userId, productId, null);
  }

  public ProductReviews summary(Long userId, long productId, String rawPage) {
    int page = pageNumber(rawPage);
    return store.tx(data -> {
      var product = data.product(productId);
      if (product == null || product.category == null || !product.category.active) throw Problem.missing();
      var stats = data.reviewStats(productId);
      int pages = (int) Math.max(1, (stats.count() + PAGE_SIZE - 1) / PAGE_SIZE);
      if (page > pages) throw Problem.missing();
      Review own = userId == null ? null : data.review(userId, productId);
      boolean canReview = userId != null && own == null && data.completedOrderFor(userId, productId) > 0;
      var items = data.reviews(productId, Math.multiplyExact(page - 1, PAGE_SIZE), PAGE_SIZE).stream()
          .map(review -> view(data, review)).toList();
      return new ProductReviews(items, stats.count(), stats.averageRating(), canReview, own != null, page, pages);
    });
  }

  /** Public reviews omit account and order identifiers. */
  public List<ReviewView> list(long productId) {
    return summary(null, productId).items();
  }

  private static void requireBuyer(Store.Data data, long userId) {
    var user = data.lockUser(userId);
    if (user == null || !user.active || user.blocked)
      throw new Problem(403, "Tài khoản hiện không thể gửi đánh giá.");
  }

  private static long completedOrder(Store.Data data, long userId, long productId) {
    if (data.product(productId) == null) throw Problem.missing();
    long orderId = data.completedOrderFor(userId, productId);
    if (orderId <= 0) throw new Problem(403, "Bạn có thể đánh giá sau khi đơn hàng chứa cuốn sách này đã hoàn tất.");
    return orderId;
  }

  public static void validateInput(int rating,String body){validate(rating,body);}
  private static void media(String url,String type) {
    if(url==null)return;
    if(url.isEmpty()&&"".equals(type))return;
    try {var uri=java.net.URI.create(url);
      if(url.length()<=1000&&"https".equals(uri.getScheme())&&"res.cloudinary.com".equals(uri.getHost())&&uri.getUserInfo()==null&&java.util.Set.of("image","video").contains(type))return;
    }catch(RuntimeException ignored){}
    throw Problem.invalid("Tệp đính kèm phải được tải lên Cloudinary qua biểu mẫu.");
  }
  private static String validate(int rating, String body) {
    if (rating < 1 || rating > 5) throw new FormProblem("rating", "Hãy chọn điểm đánh giá từ 1 đến 5 sao.");
    String text = body == null ? "" : body.trim();
    if (text.length() < 50) throw new FormProblem("body", "Chia sẻ thêm một chút nhé: nội dung cần ít nhất 50 ký tự.");
    if (text.length() > 5000) throw new FormProblem("body", "Nội dung đánh giá tối đa 5.000 ký tự.");
    return text;
  }

  private static ReviewView view(Store.Data data, Review review) {
    var profile = data.profile(review.userId);
    String name = profile == null || profile.displayName == null || profile.displayName.isBlank()
        ? "Bạn đọc Góc Sách" : profile.displayName;
    return new ReviewView(review.rating, review.body, name, review.createdAt, review.updatedAt, review.mediaUrl,review.mediaType);
  }

  private static int pageNumber(String raw) {
    if (raw == null || raw.isBlank()) return 1;
    try {
      int page = Integer.parseInt(raw);
      if (page > 0 && page <= Integer.MAX_VALUE / PAGE_SIZE) return page;
    } catch (NumberFormatException ignored) {
      // Render the same validation message for malformed and out-of-range values.
    }
    throw Problem.invalid("Trang đánh giá phải là số nguyên dương hợp lệ.");
  }
}
