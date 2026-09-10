package vn.edu.utex.bookstore.review;

import java.time.Clock;
import java.util.List;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.persistence.Store;

public final class ReviewService {
  private final Store store; private final Clock clock;
  public ReviewService(Store store, Clock clock){this.store=store;this.clock=clock;}
  public Review add(long userId,long productId,int rating,String body){
    if(rating<1||rating>5)throw Problem.invalid("Điểm đánh giá từ 1 đến 5.");
    String text=body==null?"":body.trim(); if(text.length()<50)throw Problem.invalid("Nội dung đánh giá tối thiểu 50 ký tự."); if(text.length()>5000)throw Problem.invalid("Nội dung đánh giá tối đa 5.000 ký tự.");
    return store.tx(d->{if(d.product(productId)==null)throw Problem.missing();long orderId=d.completedOrderFor(userId,productId);if(orderId<=0)throw new Problem(403,"Chỉ người đã nhận hàng mới được đánh giá.");if(d.review(userId,productId)!=null)throw new Problem(409,"Bạn đã đánh giá sản phẩm này.");Review r=new Review();r.userId=userId;r.productId=productId;r.rating=rating;r.body=text;r.status="PUBLISHED";r.createdAt=clock.instant();r.updatedAt=r.createdAt;r.orderId=orderId;return d.saveReview(r);});
  }
  public List<Review> list(long productId){return store.tx(d->d.reviews(productId));}
}
