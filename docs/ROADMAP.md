# Lộ trình và quyết định

Ba bài phát triển cùng repository: JDBC → JPA EntityManager → OTP/sản phẩm. Chưa dùng Spring Boot/Spring Data/REST, không làm cart/order/payment trước Bài 03.

Sau Bài 03, đồ án phát triển ở repository riêng [goc-sach](https://github.com/letrongbao/goc-sach), không dùng repository bài tập để nộp cuối kỳ. Giữ lịch sử kế thừa và ghi rõ commit nguồn; tiếp tục Spring Boot/MVC/Security, giữ JSP/JSTL và WAR. Tiếp theo hồ sơ/địa chỉ → giỏ hàng → đơn hàng/tồn kho → COD/VNPAY sandbox → cửa hàng/Seller/Admin → đánh giá/khuyến mãi/báo cáo. Mỗi giai đoạn cần test riêng.

Đề tài chuỗi cửa hàng sách cũ và nhóm cần giảng viên xác nhận. Chưa có ngày nộp cuối kỳ được xác minh. Không coi việc chọn đề tài tại đây là đăng ký trên UTEx.

Nguồn: https://utexlms.hcmute.edu.vn/mod/page/view.php?id=1452010

## Ràng buộc

- Không chạy tạo bảng/migration/DDL, kể cả trong test; SQL để chủ project đọc và thực thi thủ công.
- Tạo remote/push khi chủ project yêu cầu; không tự nộp UTEx, không giả lịch sử commit.
- Java 21, Jakarta Servlet 6, PostgreSQL; không phụ thuộc kiểu SQL Server `nvarchar`.
- Chủ project giữ bí mật cấu hình; không tài khoản/mật khẩu thật trong Git.
