package vn.edu.utex.bookstore.review;

import java.util.List;

public interface ReviewRepository {
  boolean deliveredPurchase(long userId, long productId);
  long completedOrderFor(long userId, long productId);
  Review review(long userId, long productId);
  Review saveReview(Review review);
  List<Review> reviews(long productId);
  List<Review> reviews(long productId, int offset, int limit);
  ReviewStats reviewStats(long productId);

  record ReviewStats(long count, double averageRating) {}
}
