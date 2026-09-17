# Phạm vi đồ án

Đề tài 14: website quản lý chuỗi cửa hàng sách cũ bằng Spring Boot, Thymeleaf, Bootstrap, JPA, PostgreSQL, JWT, WebSocket và Cloudinary.

## Giai đoạn hiện tại

Đã chuyển nền sang Spring Boot/Thymeleaf, bổ sung bảo mật và JWT. Có cửa hàng, Seller/Manager, kho từng bản sách, tìm kiếm/lọc, trang cửa hàng và chi tiết các bản đang bán. Luồng người mua đã có hồ sơ, địa chỉ, yêu thích, đã xem, giỏ và đặt hàng COD; người bán xử lý đơn theo đúng thứ tự. Các phần này đã có test nghiệp vụ/HTTP nhưng chưa nghiệm thu trên database thật. Tiến độ từng mốc ở [MILESTONES.md](MILESTONES.md).

Chương trình đóng gói JAR, có máy chủ web tích hợp. Không cần cài Tomcat riêng. Database phải được chuẩn bị thủ công; chương trình không tạo hoặc sửa bảng khi chạy.

## Còn thiếu

Kiểm thử PostgreSQL, email và upload ảnh thật; hoàn thiện các bảng xếp hạng sách; tích hợp VNPAY, khuyến mãi, vận chuyển, trả/hoàn tiền, đánh giá ảnh/video, thông báo WebSocket và báo cáo doanh thu.

Xem thứ tự triển khai và điều kiện hoàn thành trong [kế hoạch](ROADMAP.md). Bảng [đối chiếu yêu cầu](REQUIREMENTS.md) đã cập nhật từ trang UTEx ngày 04/09/2026. Đề tài/thành viên, cách áp dụng Seller cho chuỗi cửa hàng, các câu mô tả còn mơ hồ và hạn nộp cần giảng viên xác nhận.

Lịch sử phát triển được giữ nguyên; không coi chức năng kế thừa là đóng góp mới.
