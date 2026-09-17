# Kết quả kiểm tra

Cập nhật: 17/09/2026.

## Kết quả hiện tại

- `mvnw.cmd -B -ntp verify`: **136/136 test đạt**, 0 lỗi, 0 bỏ qua; đóng gói `target/goc-sach.jar` thành công.
- Đã kiểm tra Chrome tại 320, 390, 768 và 1366 px trên 11 trang chính: trang chủ, tủ sách, chi tiết sách, cửa hàng, địa chỉ, yêu thích, đã xem, giỏ hàng, đặt hàng, đơn người mua và đơn người bán. Không có tràn ngang hoặc câu chữ kỹ thuật trên giao diện.
- Bộ lọc giữ điều kiện khi chuyển trang; sản phẩm ẩn không xuất hiện trong yêu thích/đã xem; địa chỉ báo lỗi cạnh đúng trường.
- Checkout chốt đúng các dòng trong giỏ, khóa dữ liệu theo thứ tự ổn định, tách đơn theo cửa hàng và không xóa nhầm sách vừa thêm. Khóa yêu cầu được chuẩn hóa để chống tạo đơn trùng.
- Đơn hàng lưu ảnh/tên/tình trạng/giá tại thời điểm mua. Người mua và người bán chỉ xem, hủy hoặc đổi trạng thái đơn đúng phạm vi và đúng thứ tự.
- Cửa hàng bị tạm khóa không nhận đơn mới nhưng vẫn có thể hoàn tất hoặc hủy an toàn những đơn đã nhận, tránh giữ sách vô thời hạn.
- Lỗi nghiệp vụ API được trả về JSON với mã 4xx phù hợp; không biến dữ liệu sai thành trang lỗi 500.
- JPA tiếp tục dùng `hibernate.hbm2ddl.auto=validate`; không có SQL hoặc migration nào được chạy trong lượt kiểm tra.

## Phần Chưa Nghiệm Thu Thật

- PostgreSQL `goc_sach`: chưa xác nhận schema, transaction cạnh tranh, rollback và dữ liệu sau restart trong lượt này.
- Gmail/Cloudinary: cấu hình đã có nhưng lượt này không gửi OTP hoặc upload/xóa ảnh thật; test tự động không thay cho nghiệm thu hai dịch vụ.
- Các phần cuối kỳ còn thiếu: các bảng top 20 theo lượt bán/đánh giá/yêu thích, đơn vị vận chuyển, voucher, VNPAY, trả/hoàn tiền, media review, thông báo WebSocket và báo cáo doanh thu.
- Một số controller cũ và `App`/`Store` service locator vẫn cần tiếp tục tách theo module; đây là nợ kiến trúc, không phải lỗi build hiện tại.

## Cách Kiểm Tra Lại

1. Chạy `mvnw.cmd -B -ntp verify`.
2. Chạy `scripts/preview.ps1` để xem UI không cần database.
3. Sau khi chủ dự án tự chuẩn bị database/schema, chạy JAR với `config/local.properties` và nghiệm thu PostgreSQL, Gmail, Cloudinary bằng dữ liệu kiểm thử.

Chi tiết phạm vi còn lại nằm trong [MILESTONES.md](MILESTONES.md) và [ROADMAP.md](ROADMAP.md).
