package vn.edu.utex.bookstore.operations;
import java.util.List;
import vn.edu.utex.bookstore.auth.User;
import vn.edu.utex.bookstore.review.Review;
public interface OperationsRepository {
  Voucher voucher(String code, boolean lock);
  Voucher saveVoucher(Voucher voucher);
  List<Voucher> vouchers(long shopId);
  Carrier carrier(long id, boolean lock);
  Carrier saveCarrier(Carrier carrier);
  List<Carrier> carriers();
  ReturnRequest returnForOrder(long orderId, boolean lock);
  ReturnRequest saveReturn(ReturnRequest request);
  List<User> userAccounts();
  List<Review> moderationReviews();
  Review reviewById(long id, boolean lock);
}
