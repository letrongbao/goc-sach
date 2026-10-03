# Phạm vi đồ án

Đề tài 14: website quản lý chuỗi cửa hàng sách cũ bằng Spring Boot, Thymeleaf, Bootstrap, JPA, PostgreSQL, JWT, WebSocket và Cloudinary.

## Giai đoạn hiện tại

Đã chuyển nền sang Spring Boot/Thymeleaf, bổ sung bảo mật và JWT. Có cửa hàng, Seller/Manager, kho từng bản sách, tìm kiếm/lọc, trang cửa hàng và chi tiết các bản đang bán. Luồng người mua đã có hồ sơ, địa chỉ, yêu thích, đã xem, giỏ, COD và chuyển khoản PayOS; người bán xử lý đơn theo đúng thứ tự. Có test nghiệp vụ/HTTP và các ca nghiệm thu PostgreSQL, tạo/query/hủy link PayOS thật trong phạm vi ghi tại VERIFICATION.md; chưa nghiệm thu nhận tiền thật hoặc toàn bộ màn hình với dữ liệu thật. Tiến độ từng mốc ở [MILESTONES.md](MILESTONES.md).

Chương trình đóng gói JAR, có máy chủ web tích hợp. Không cần cài Tomcat riêng. Database phải được chuẩn bị thủ công; chương trình không tạo hoặc sửa bảng khi chạy.

Ngày 03/10/2026 bổ sung UI khám phá sách theo danh mục/ngân sách, sắp xếp giá/yêu thích, cẩm nang và tạo/sửa đánh giá sau mua. Các bản ghi kết quả DB/PayOS ở VERIFICATION.md là phạm vi nghiệm thu riêng của ngày 02/10; đợt UI mới không chạy dịch vụ thật. Chưa hoàn thành bốn bảng top 20 hay review media.

## Còn thiếu

Nghiệm thu đầy đủ UI và query mới trên PostgreSQL, email, upload ảnh và nhận tiền/webhook PayOS thật; xin thầy xác nhận PayOS thay VNPAY/MOMO. Hoàn thiện các bảng xếp hạng sách, khuyến mãi, vận chuyển, trả/hoàn tiền, đánh giá ảnh/video, thông báo WebSocket và báo cáo doanh thu.

Xem thứ tự triển khai và điều kiện hoàn thành trong [kế hoạch](ROADMAP.md). Bảng [đối chiếu yêu cầu](REQUIREMENTS.md) đã cập nhật từ trang UTEx ngày 04/09/2026. Đề tài/thành viên, cách áp dụng Seller cho chuỗi cửa hàng, các câu mô tả còn mơ hồ và hạn nộp cần giảng viên xác nhận.

Lịch sử phát triển được giữ nguyên; không coi chức năng kế thừa là đóng góp mới.
