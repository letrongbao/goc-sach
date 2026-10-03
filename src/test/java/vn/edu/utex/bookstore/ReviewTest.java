package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.buyer.BuyerService;
import vn.edu.utex.bookstore.order.OrderService;
import vn.edu.utex.bookstore.review.ReviewService;
import vn.edu.utex.bookstore.review.Review;
import vn.edu.utex.bookstore.common.FormProblem;
import vn.edu.utex.bookstore.common.Problem;

class ReviewTest {
  @Test
  void onlyCompletedBuyerCanReviewOnceAndMinimumLengthIsEnforced() {
    MemoryStore store=DemoFixtures.store(); DemoFixtures.catalogStock(store);
    Identity admin=Identity.of(store.findLogin("admin")), reader=Identity.of(store.findLogin("reader"));
    BuyerService buyer=new BuyerService(store,Clock.systemUTC());
    var address=buyer.saveAddress(reader.id(),null,"Nhà","Bạn đọc","0900000000","1 Đường Sách","","","TP HCM",true);
    long copy=store.copies.values().iterator().next().id; buyer.addToCart(reader.id(),copy);
    var order=new OrderService(store,Clock.systemUTC()).checkout(reader.id(),address.id,"review-order-20260909").orders().getFirst();
    order.status="COMPLETED";
    ReviewService reviews=new ReviewService(store,Clock.systemUTC());
    String body="Cuốn sách được đóng gói cẩn thận, nội dung hữu ích và chất lượng đúng như mô tả ban đầu.";
    var saved=reviews.add(reader.id(),1,5,body);
    assertEquals("PUBLISHED",saved.status); assertEquals(1,reviews.list(1).size());
    assertThrows(vn.edu.utex.bookstore.common.Problem.class,()->reviews.add(reader.id(),1,4,body));
    assertThrows(vn.edu.utex.bookstore.common.Problem.class,()->reviews.add(admin.id(),1,5,body));
    assertThrows(vn.edu.utex.bookstore.common.Problem.class,()->reviews.add(reader.id(),2,5,"ngắn"));
  }

  @Test
  void eligibilityAndEditingRespectPurchaseOwnershipAndHiddenReviews() {
    MemoryStore store=DemoFixtures.store(); DemoFixtures.catalogStock(store);
    long userId=store.findLogin("reader").id;
    BuyerService buyer=new BuyerService(store,Clock.systemUTC());
    var address=buyer.saveAddress(userId,null,"Nhà","Bạn đọc","0900000000","1 Đường Sách","","","TP HCM",true);
    buyer.addToCart(userId,store.copies.values().iterator().next().id);
    var order=new OrderService(store,Clock.systemUTC()).checkout(userId,address.id,"review-eligibility-001").orders().getFirst();
    ReviewService reviews=new ReviewService(store,Clock.fixed(Instant.parse("2026-10-03T10:00:00Z"),ZoneOffset.UTC));
    String body="Cuốn sách có nội dung rất thú vị và bản sách nhận được còn tốt, đúng với mô tả của cửa hàng.";
    assertFalse(reviews.summary(userId,1).canReview());
    assertEquals(403,assertThrows(Problem.class,()->reviews.add(userId,1,5,body)).status);
    order.status="COMPLETED";
    assertTrue(reviews.summary(userId,1).canReview());
    assertEquals(403,assertThrows(Problem.class,()->reviews.editor(userId,2)).status);
    Review original=reviews.add(userId,1,5,body);
    assertFalse(reviews.summary(userId,1).canReview());
    assertTrue(reviews.summary(userId,1).alreadyReviewed());
    assertEquals(order.id.longValue(),reviews.editor(userId,1).orderId());
    assertEquals(403,assertThrows(Problem.class,()->reviews.update(1,1,4,body)).status);
    Review updated=reviews.update(userId,1,4,body + " Tôi sẽ tiếp tục đọc lại.");
    assertEquals(original.id,updated.id);
    assertEquals(1,reviews.summary(null,1).count());
    assertEquals(4,reviews.summary(null,1).averageRating());
    original.status="HIDDEN";
    assertFalse(reviews.editor(userId,1).editable());
    assertEquals(409,assertThrows(Problem.class,()->reviews.update(userId,1,3,body)).status);
    assertEquals(0,reviews.summary(null,1).count());
    assertEquals(0,reviews.summary(null,1).averageRating());
    assertTrue(reviews.summary(null,1).items().isEmpty());
  }

  @Test
  void publishedPagesHaveGlobalStatisticsAndDeterministicOrder() {
    MemoryStore store=DemoFixtures.store();
    ReviewService reviews=new ReviewService(store,Clock.systemUTC());
    for(int i=0;i<23;i++) {
      Review review=new Review();review.productId=1;review.userId=i+10;review.orderId=i+100;
      review.body="Một nội dung đánh giá minh họa đủ dài để kiểm tra phân trang danh sách đánh giá.";
      review.rating=i==22?1:5;review.status=i==22?"HIDDEN":"PUBLISHED";
      review.createdAt=Instant.parse("2026-10-03T10:00:00Z");review.updatedAt=review.createdAt;
      store.saveReview(review);
    }
    var first=reviews.summary(null,1);
    assertEquals(22,first.count());assertEquals(5,first.averageRating());assertEquals(3,first.pages());
    assertEquals(10,first.items().size());assertEquals(10,reviews.list(1).size());
    assertEquals(2,reviews.summary(null,1,"3").items().size());
    assertEquals("Bạn đọc Góc Sách",first.items().getFirst().authorName());
    assertEquals(404,assertThrows(Problem.class,()->reviews.summary(null,1,"4")).status);
    for(String invalid:new String[]{"0","-1","abc","99999999999999"})
      assertEquals(400,assertThrows(Problem.class,()->reviews.summary(null,1,invalid)).status);
    assertEquals(404,assertThrows(Problem.class,()->reviews.summary(null,999)).status);
  }

  @Test
  void invalidInputIsBoundToTheCorrectFieldBeforeSaving() {
    var reviews=new ReviewService(DemoFixtures.store(),Clock.systemUTC());
    assertEquals("rating",assertThrows(FormProblem.class,()->reviews.add(2,1,0,"x".repeat(50))).field);
    assertEquals("rating",assertThrows(FormProblem.class,()->reviews.add(2,1,6,"x".repeat(50))).field);
    assertEquals("body",assertThrows(FormProblem.class,()->reviews.add(2,1,5," "+"x".repeat(49)+" ")).field);
    assertEquals("body",assertThrows(FormProblem.class,()->reviews.add(2,1,5,"x".repeat(5001))).field);
  }
}
