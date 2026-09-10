package vn.edu.utex.bookstore;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.buyer.BuyerService;
import vn.edu.utex.bookstore.order.OrderService;
import vn.edu.utex.bookstore.review.ReviewService;

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
}
