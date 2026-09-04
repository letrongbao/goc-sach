# Phạm vi đồ án

Đề tài 14: website quản lý chuỗi cửa hàng sách cũ bằng Spring Boot, Thymeleaf, Bootstrap, JPA, PostgreSQL, JWT, WebSocket và Cloudinary.

## Giai đoạn hiện tại

Đã chuyển nền sang Spring Boot/Thymeleaf, bổ sung bảo mật và JWT. Có cửa hàng, Seller/Manager, kho từng bản sách và tủ sách lấy giá/tồn từ kho. Tìm kiếm tên/tác giả và chi tiết các bản đang bán đã có màn hình, test nghiệp vụ và HTTP. Chưa nghiệm thu database thật hoặc có mua hàng. Tiến độ từng mốc ở [MILESTONES.md](MILESTONES.md).

Chương trình đóng gói JAR, có máy chủ web tích hợp. Không cần cài Tomcat riêng. Database phải được chuẩn bị thủ công; chương trình không tạo hoặc sửa bảng khi chạy.

## Còn thiếu

Kiểm thử database/email thật; chuyển tồn cũ, metadata và bộ lọc sách; WebSocket, Cloudinary; nối JWT vào giao diện giỏ/yêu thích; hồ sơ/địa chỉ, giỏ hàng, đơn hàng, thanh toán, yêu thích/đã xem, đánh giá ảnh/video, khuyến mãi, vận chuyển và doanh thu.

Xem thứ tự triển khai và điều kiện hoàn thành trong [kế hoạch](ROADMAP.md). Bảng [đối chiếu yêu cầu](REQUIREMENTS.md) đã cập nhật từ trang UTEx ngày 04/09/2026. Đề tài/thành viên, cách áp dụng Seller cho chuỗi cửa hàng, các câu mô tả còn mơ hồ và hạn nộp cần giảng viên xác nhận.

Lịch sử phát triển được giữ nguyên; không coi chức năng kế thừa là đóng góp mới.
