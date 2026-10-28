# Kiến trúc Góc Sách

Java 21, Spring Boot, MVC/Thymeleaf, Spring Security, JPA mapping XML và PostgreSQL 17. Controller gọi service; service giữ quyền, quy tắc và transaction; Store/JpaStore thực hiện truy cập dữ liệu. Giao diện dùng Bootstrap cùng CSS/JS riêng.

## Quyền và API

Web dùng session/remember cookie + CSRF. REST riêng dùng Bearer JWT RS256, kiểm tra issuer/audience/hạn và trạng thái tài khoản hiện hành. Grant gắn với phiên để logout/cấp token mới/khóa user thu hồi quyền. Seller và Manager có quyền theo shop; Admin có quyền toàn hệ thống. JWT grant nằm trong bộ nhớ một instance.

## Kho và đơn

`products` lưu đầu sách; `book_copies` lưu từng bản, shop, giá, tình trạng và version. Vòng đời UNAVAILABLE → AVAILABLE → RESERVED → SOLD. Checkout khóa bản, tính lại báo giá/fingerprint, tạo group và đơn con theo shop; idempotency ngăn tạo lặp. Đơn chốt giá, phí, voucher và commission để không đổi theo cấu hình sau này. Tiền dùng BigDecimal.

PayOS gọi qua adapter ngoài transaction giữ khóa. Webhook xác minh chữ ký, mã đơn/link, VND, số tiền và mã giao dịch; callback lặp không ghi nhận hai lần. Return URL không chứng minh đã thanh toán. Sai tiền/đến muộn chuyển REVIEW_REQUIRED. Hoàn tiền là ghi nhận bên ngoài có tham chiếu; chưa có refund/payout tự động.

## Catalog và vận hành

Catalog chỉ công khai bản có thể bán trong shop/danh mục hoạt động. Có tìm/lọc, bốn top20 (10 đầu sách/trang) và sách bán trên10 bản. Review yêu cầu đã mua đơn hoàn tất, nội dung50–5000 ký tự, ảnh/MP4 tối đa5MB; chỉ review công khai tham gia thống kê. Cloudinary lưu media.

Vận hành gồm shop/thành viên/kho, voucher, hãng giao/phí nội bộ, chiết khấu/doanh thu, khóa tài khoản và ẩn/hiện review. Trả hàng trong7 ngày cho đơn COMPLETED/PAID; hoàn tiền đưa bản về UNAVAILABLE để kiểm tra. WebSocket native `/ws` dùng phiên, kiểm tra thay đổi khoảng5 giây, chỉ phát đơn được phép xem; không dùng STOMP.

## Chạy và kiểm chứng

Compose có app, PostgreSQL và Mailpit; tự seed volume trống, sinh khóa JWT/OTP và giữ dữ liệu qua recreate. Key dịch vụ mount từ file riêng ngoài image. Hibernate validate schema, không tự migrate dữ liệu cũ.

Xem [API và phân công](API_REFERENCE.md), [Docker](DOCKER_QUICKSTART.md), [OTP](OTP.md) và [bằng chứng kiểm thử](VERIFICATION.md). Kiểm thử giả lập không thay nghiệm thu ngân hàng, email Internet hoặc upload Cloudinary thật.
